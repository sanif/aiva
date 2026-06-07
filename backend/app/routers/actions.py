"""Allowlisted action endpoints."""
from __future__ import annotations

from typing import List

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession

from ..auth import require_token
from ..database import get_session
from ..models.schemas import ActionInfo, ActionResult
from ..services import action_service

router = APIRouter(prefix="/api/actions", tags=["actions"], dependencies=[Depends(require_token)])
tools_router = APIRouter(prefix="/api/tools", tags=["actions"], dependencies=[Depends(require_token)])


@router.get("", response_model=List[ActionInfo])
async def list_actions() -> List[ActionInfo]:
    return action_service.list_actions()


@tools_router.get("")
async def list_tools() -> List[dict]:
    """The agent's tool registry — what the chat model is allowed to do."""
    from ..services.tools_service import TOOL_SPECS

    return [
        {"name": s["function"]["name"], "description": s["function"]["description"]}
        for s in TOOL_SPECS
    ]


@router.post("/{action_id}", response_model=ActionResult)
async def run_action(
    action_id: str,
    session: AsyncSession = Depends(get_session),
) -> ActionResult:
    result = await action_service.run_action(session, action_id)
    if result is None:
        raise HTTPException(status_code=404, detail="unknown action")
    return result
