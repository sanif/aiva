"""Note endpoints."""
from __future__ import annotations

from typing import List

from fastapi import APIRouter, Depends, status
from sqlalchemy.ext.asyncio import AsyncSession

from ..auth import require_token
from ..database import get_session
from ..models.schemas import NoteCreate, NoteOut
from ..services import note_service

router = APIRouter(prefix="/api/notes", tags=["notes"], dependencies=[Depends(require_token)])


@router.get("", response_model=List[NoteOut])
async def list_notes(
    limit: int = 20,
    session: AsyncSession = Depends(get_session),
) -> List[NoteOut]:
    notes = await note_service.list_notes(session, limit)
    return [NoteOut.model_validate(n) for n in notes]


@router.post("", response_model=NoteOut, status_code=status.HTTP_201_CREATED)
async def create_note(
    body: NoteCreate,
    session: AsyncSession = Depends(get_session),
) -> NoteOut:
    note = await note_service.create_note(session, body)
    return NoteOut.model_validate(note)
