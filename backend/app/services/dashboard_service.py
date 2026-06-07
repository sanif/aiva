"""Aggregate a full dashboard snapshot (shared by REST and WebSocket)."""
from __future__ import annotations

import asyncio
from typing import Optional

from sqlalchemy.ext.asyncio import AsyncSession

from ..config import settings
from ..models.schemas import (
    AgendaItem,
    Dashboard,
    Metrics,
)
from . import alert_service, monitor_service, task_service
from .docker_service import get_docker_info


async def build_snapshot(
    session: AsyncSession,
    *,
    metrics: Optional[Metrics] = None,
) -> Dashboard:
    """Build a complete dashboard payload.

    If `metrics` is supplied (e.g. from the background loop), it is reused so
    history buffers are not double-sampled.
    """
    if metrics is None:
        metrics_task = monitor_service.monitor.get_metrics()
    else:
        async def _passthrough() -> Metrics:
            return metrics

        metrics_task = _passthrough()

    metrics_val, services, docker, tasks_summary, top = await asyncio.gather(
        metrics_task,
        monitor_service.check_services(),
        get_docker_info(),
        task_service.summary(session),
        task_service.top_tasks(session, limit=3),
    )

    alerts = alert_service.derive_alerts(metrics_val, services, docker)
    ai_status = alert_service.ai_status_from_alerts(alerts)
    agenda = [AgendaItem(**a) for a in settings.agenda]

    return Dashboard(
        greeting_name=settings.GREETING_NAME,
        ai_status=ai_status,
        metrics=metrics_val,
        services=services,
        docker=docker,
        alerts=alerts,
        tasks_summary=tasks_summary,
        top_tasks=top,
        agenda=agenda,
    )
