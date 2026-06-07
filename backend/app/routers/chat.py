"""Non-streaming chat endpoint + history."""
from __future__ import annotations

from typing import List

from fastapi import APIRouter, Depends, Query
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from ..auth import require_token
from ..config import settings
from ..database import get_session
from ..models.db_models import ChatMessage
from ..models.schemas import ChatHistoryEntry, ChatRequest, ChatResponse
from ..services import alert_service, llm_service, monitor_service, task_service
from ..services.docker_service import get_docker_info

router = APIRouter(prefix="/api/chat", tags=["chat"], dependencies=[Depends(require_token)])


async def build_chat_context(session: AsyncSession) -> dict:
    """EVERYTHING the API knows, for grounding the model's replies."""
    from ..services import note_service

    metrics = await monitor_service.monitor.get_metrics()
    services = await monitor_service.check_services()
    docker = await get_docker_info()
    alerts = alert_service.derive_alerts(metrics, services, docker)
    tasks_summary = await task_service.summary(session)
    today_tasks = await task_service.list_tasks(session, "today")
    latest_notes = await note_service.list_notes(session, 3)
    return {
        "metrics": metrics,
        "services": services,
        "alerts": alerts,
        "agenda": settings.agenda,
        "tasks_summary": tasks_summary,
        "tasks": [
            {"title": t.title, "status": t.status, "priority": t.priority, "project": t.project}
            for t in today_tasks[:8]
        ],
        "notes": [{"body": n.body, "kind": n.kind} for n in latest_notes],
    }


async def store_history(session: AsyncSession, user_msg: str, reply: str, source: str = "app") -> None:
    if not settings.STORE_CHAT_HISTORY:
        return
    session.add(ChatMessage(role="user", content=user_msg, source=source))
    session.add(ChatMessage(role="assistant", content=reply, source=source))
    await session.commit()


@router.post("", response_model=ChatResponse)
async def chat(
    body: ChatRequest,
    session: AsyncSession = Depends(get_session),
) -> ChatResponse:
    context = await build_chat_context(session) if settings.MOCK_MODE else None
    reply = await llm_service.complete(body.message, context=context)
    await store_history(session, body.message, reply)
    # learning hook (ruflo principle): every exchange becomes an episode
    from ..services import recall_service

    await recall_service.on_chat_exchange(body.message, reply)
    return ChatResponse(reply=reply)


@router.post("/feedback")
async def suggestion_feedback(body: dict, ) -> dict:
    """Approval-card outcome → behavioral score (gradual up, instant down)."""
    from ..services import recall_service

    action_id = str(body.get("action_id", "")).strip()
    if not action_id:
        return {"ok": False, "error": "action_id required"}
    result = await recall_service.reinforce(action_id, bool(body.get("approved")))
    return {"ok": True, "score": result["score"]}


@router.get("/history", response_model=List[ChatHistoryEntry])
async def chat_history(
    limit: int = Query(50, ge=1, le=500),
    session: AsyncSession = Depends(get_session),
) -> List[ChatHistoryEntry]:
    """Latest chat exchanges (app + telegram), oldest first."""
    rows = await session.execute(
        select(ChatMessage).order_by(ChatMessage.id.desc()).limit(limit)
    )
    messages = list(reversed(rows.scalars().all()))
    return [
        ChatHistoryEntry(
            role=m.role,
            text=m.content,
            ts=m.created_at.isoformat() if m.created_at else "",
            source=m.source or "app",
        )
        for m in messages
    ]
