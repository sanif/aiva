"""Task CRUD operations + summary helpers."""
from __future__ import annotations

from typing import List, Optional

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from ..models.db_models import Task
from ..models.schemas import TaskCreate, TaskUpdate, TasksSummary


async def list_tasks(
    session: AsyncSession,
    status: Optional[str] = None,
    project: Optional[str] = None,
    q: Optional[str] = None,
    tag: Optional[str] = None,
) -> List[Task]:
    stmt = select(Task).order_by(Task.created_at.asc())
    if status:
        stmt = stmt.where(Task.status == status)
    if project:
        stmt = stmt.where(Task.project == project)
    if q:
        like = f"%{q}%"
        stmt = stmt.where(Task.title.ilike(like) | Task.notes.ilike(like) | Task.description.ilike(like))
    if tag:
        stmt = stmt.where(Task.tags.ilike(f"%{tag}%"))
    result = await session.scalars(stmt)
    return list(result.all())


async def get_task(session: AsyncSession, task_id: int) -> Optional[Task]:
    return await session.get(Task, task_id)


async def create_task(session: AsyncSession, data: TaskCreate) -> Task:
    task = Task(**data.model_dump())
    session.add(task)
    await session.commit()
    await session.refresh(task)
    return task


async def update_task(session: AsyncSession, task_id: int, data: TaskUpdate) -> Optional[Task]:
    task = await session.get(Task, task_id)
    if task is None:
        return None
    fields = data.model_dump(exclude_unset=True)
    for key, value in fields.items():
        setattr(task, key, value)
    # tracker semantics: completing stamps the time, reopening clears it
    if "status" in fields:
        from datetime import datetime, timezone

        task.completed_at = datetime.now(timezone.utc) if fields["status"] == "done" else None
    await session.commit()
    await session.refresh(task)
    return task


async def projects(session: AsyncSession) -> List[dict]:
    """Distinct projects with total/done counts (unfiled tasks excluded)."""
    tasks = await list_tasks(session)
    by_name: dict[str, dict] = {}
    for t in tasks:
        if not t.project:
            continue
        entry = by_name.setdefault(t.project, {"name": t.project, "total": 0, "done": 0})
        entry["total"] += 1
        if t.status == "done":
            entry["done"] += 1
    return sorted(by_name.values(), key=lambda p: p["name"])


async def delete_task(session: AsyncSession, task_id: int) -> bool:
    task = await session.get(Task, task_id)
    if task is None:
        return False
    await session.delete(task)
    await session.commit()
    return True


async def summary(session: AsyncSession) -> TasksSummary:
    tasks = await list_tasks(session)
    today = sum(1 for t in tasks if t.status == "today")
    done = sum(1 for t in tasks if t.status == "done")
    upcoming = sum(1 for t in tasks if t.status == "upcoming")
    return TasksSummary(today=today, done=done, upcoming=upcoming)


async def top_tasks(session: AsyncSession, limit: int = 3) -> List[Task]:
    """Up to `limit` tasks with status == 'today' (not done)."""
    stmt = (
        select(Task)
        .where(Task.status == "today")
        .order_by(Task.created_at.asc())
        .limit(limit)
    )
    result = await session.scalars(stmt)
    return list(result.all())
