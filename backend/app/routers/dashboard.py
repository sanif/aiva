"""Health + aggregated dashboard endpoints."""
from __future__ import annotations

import time

from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from ..auth import require_token
from ..config import settings
from ..database import get_session
from ..models.schemas import Dashboard, Health
from ..services.dashboard_service import build_snapshot

router = APIRouter(prefix="/api", tags=["dashboard"])

_START_TIME = time.monotonic()


@router.get("/health", response_model=Health)
async def health() -> Health:
    return Health(
        status="ok",
        version="0.3.0",
        uptime_s=round(time.monotonic() - _START_TIME, 3),
        mock=settings.MOCK_MODE,
    )


@router.get("/dashboard", response_model=Dashboard, dependencies=[Depends(require_token)])
async def dashboard(session: AsyncSession = Depends(get_session)) -> Dashboard:
    return await build_snapshot(session)
