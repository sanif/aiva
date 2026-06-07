"""Note CRUD operations."""
from __future__ import annotations

from typing import List

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from ..models.db_models import Note
from ..models.schemas import NoteCreate


async def list_notes(session: AsyncSession, limit: int = 20) -> List[Note]:
    stmt = select(Note).order_by(Note.created_at.desc()).limit(limit)
    result = await session.scalars(stmt)
    return list(result.all())


async def create_note(session: AsyncSession, data: NoteCreate) -> Note:
    note = Note(**data.model_dump())
    session.add(note)
    await session.commit()
    await session.refresh(note)
    return note


async def create_empty_note(session: AsyncSession, title: str = "Quick note") -> Note:
    note = Note(title=title, body="", kind="text")
    session.add(note)
    await session.commit()
    await session.refresh(note)
    return note
