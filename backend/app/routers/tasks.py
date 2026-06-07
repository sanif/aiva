"""Task CRUD endpoints."""
from __future__ import annotations

from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from ..auth import require_token
from ..database import get_session
from ..models.schemas import ProjectOut, TaskCreate, TaskOut, TaskUpdate
from ..services import task_service

router = APIRouter(prefix="/api/tasks", tags=["tasks"], dependencies=[Depends(require_token)])
projects_router = APIRouter(prefix="/api/projects", tags=["tasks"], dependencies=[Depends(require_token)])


@router.get("", response_model=List[TaskOut])
async def list_tasks(
    status: Optional[str] = None,
    project: Optional[str] = None,
    q: Optional[str] = None,
    tag: Optional[str] = None,
    session: AsyncSession = Depends(get_session),
) -> List[TaskOut]:
    tasks = await task_service.list_tasks(session, status, project=project, q=q, tag=tag)
    return [TaskOut.model_validate(t) for t in tasks]


@projects_router.get("", response_model=List[ProjectOut])
async def list_projects(session: AsyncSession = Depends(get_session)) -> List[ProjectOut]:
    return [ProjectOut(**p) for p in await task_service.projects(session)]


@router.post("", response_model=TaskOut, status_code=status.HTTP_201_CREATED)
async def create_task(
    body: TaskCreate,
    session: AsyncSession = Depends(get_session),
) -> TaskOut:
    task = await task_service.create_task(session, body)
    return TaskOut.model_validate(task)


@router.patch("/{task_id}", response_model=TaskOut)
async def update_task(
    task_id: int,
    body: TaskUpdate,
    session: AsyncSession = Depends(get_session),
) -> TaskOut:
    task = await task_service.update_task(session, task_id, body)
    if task is None:
        raise HTTPException(status_code=404, detail="task not found")
    return TaskOut.model_validate(task)


@router.delete("/{task_id}")
async def delete_task(
    task_id: int,
    session: AsyncSession = Depends(get_session),
) -> dict:
    ok = await task_service.delete_task(session, task_id)
    if not ok:
        raise HTTPException(status_code=404, detail="task not found")
    return {"ok": True}
