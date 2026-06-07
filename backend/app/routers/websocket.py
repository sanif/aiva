"""WebSocket endpoints: dashboard snapshots and chat streaming."""
from __future__ import annotations

import asyncio
from datetime import datetime, timezone
from typing import List, Set

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from ..auth import check_ws_token
from ..config import settings
from ..database import AsyncSessionLocal
from ..models.schemas import Metrics
from ..services import alert_service, llm_service, monitor_service, task_service
from ..services.docker_service import get_docker_info

router = APIRouter(tags=["websocket"])


class ConnectionManager:
    def __init__(self) -> None:
        self.active: Set[WebSocket] = set()
        self._lock = asyncio.Lock()

    async def connect(self, ws: WebSocket) -> None:
        await ws.accept()
        async with self._lock:
            self.active.add(ws)

    async def disconnect(self, ws: WebSocket) -> None:
        async with self._lock:
            self.active.discard(ws)

    async def broadcast(self, message: dict) -> None:
        async with self._lock:
            targets = list(self.active)
        dead: List[WebSocket] = []
        for ws in targets:
            try:
                await ws.send_json(message)
            except Exception:
                dead.append(ws)
        if dead:
            async with self._lock:
                for ws in dead:
                    self.active.discard(ws)

    def has_clients(self) -> bool:
        return bool(self.active)


manager = ConnectionManager()


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


async def _snapshot_payload(metrics: Metrics) -> dict:
    """Build a dashboard snapshot dict from an already-sampled Metrics."""
    async with AsyncSessionLocal() as session:
        services, docker, tasks_summary = await asyncio.gather(
            monitor_service.check_services(),
            get_docker_info(),
            task_service.summary(session),
        )
    alerts = alert_service.derive_alerts(metrics, services, docker)
    ai_status = alert_service.ai_status_from_alerts(alerts)
    from ..services import scheduler_service

    return {
        "type": "snapshot",
        "ts": _now_iso(),
        "metrics": metrics.model_dump(),
        "services": [s.model_dump() for s in services],
        "docker": docker.model_dump(),
        "alerts": [a.model_dump() for a in alerts],
        "tasks_summary": tasks_summary.model_dump(),
        "ai_status": ai_status,
        "last_schedule": await scheduler_service.last_run(),
    }


async def dashboard_broadcaster(stop_event: asyncio.Event) -> None:
    """Single background task: sample metrics (feeding history) and broadcast."""
    while not stop_event.is_set():
        try:
            metrics = await monitor_service.monitor.sample()
            if manager.has_clients():
                payload = await _snapshot_payload(metrics)
                await manager.broadcast(payload)
        except Exception:
            # never let the loop die on a transient error
            pass
        try:
            await asyncio.wait_for(stop_event.wait(), timeout=settings.PUSH_INTERVAL_SECONDS)
        except asyncio.TimeoutError:
            continue


@router.websocket("/ws/dashboard")
async def ws_dashboard(websocket: WebSocket) -> None:
    token = websocket.query_params.get("token")
    if not check_ws_token(token):
        # Accept then close so the client receives the 4401 close code.
        await websocket.accept()
        await websocket.close(code=4401)
        return
    await manager.connect(websocket)
    try:
        # Send an immediate snapshot on connect.
        metrics = await monitor_service.monitor.get_metrics()
        await websocket.send_json(await _snapshot_payload(metrics))
        while True:
            # Keep the connection alive; ignore any inbound messages.
            await websocket.receive_text()
    except WebSocketDisconnect:
        pass
    except Exception:
        pass
    finally:
        await manager.disconnect(websocket)


@router.websocket("/ws/chat")
async def ws_chat(websocket: WebSocket) -> None:
    token = websocket.query_params.get("token")
    if not check_ws_token(token):
        # Accept then close so the client receives the 4401 close code.
        await websocket.accept()
        await websocket.close(code=4401)
        return
    await websocket.accept()
    try:
        while True:
            data = await websocket.receive_json()
            message = (data or {}).get("message", "")
            if not message:
                await websocket.send_json({"type": "error", "message": "empty message"})
                continue

            async with AsyncSessionLocal() as session:
                from .chat import build_chat_context, store_history

                context = await build_chat_context(session)
                parts: List[str] = []
                try:
                    async for chunk in llm_service.stream(message, context=context):
                        parts.append(chunk)
                        await websocket.send_json({"type": "token", "text": chunk})
                except Exception as exc:
                    await websocket.send_json({"type": "error", "message": str(exc)})
                    continue

                full = "".join(parts).strip()
                await store_history(session, message, full)
                from ..services import recall_service

                await recall_service.on_chat_exchange(message, full)
                await websocket.send_json({"type": "done", "reply": full})
    except WebSocketDisconnect:
        pass
    except Exception:
        pass
