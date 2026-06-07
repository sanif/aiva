"""Schedule CRUD — visibility/management for chat-created jobs."""
from __future__ import annotations

from typing import List

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel

from ..auth import require_token
from ..services import scheduler_service

router = APIRouter(prefix="/api/schedules", tags=["schedules"], dependencies=[Depends(require_token)])


class ScheduleCreate(BaseModel):
    name: str
    prompt: str
    when: str  # "HH:MM" | 5-field cron | "YYYY-MM-DDTHH:MM"


@router.get("")
async def list_schedules() -> List[dict]:
    return await scheduler_service.list_all()


@router.post("", status_code=status.HTTP_201_CREATED)
async def create_schedule(body: ScheduleCreate) -> dict:
    return await scheduler_service.create(body.name, body.prompt, body.when)


@router.delete("/{schedule_id}")
async def delete_schedule(schedule_id: int) -> dict:
    if not await scheduler_service.delete(schedule_id):
        raise HTTPException(status_code=404, detail="schedule not found")
    return {"ok": True}
