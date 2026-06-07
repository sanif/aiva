"""Self-learning episodic memory.

Design credit: **ruflo** (https://github.com/ruvnet/ruflo) — this module
borrows its self-learning memory principles: automatic learning hooks
(events store memories without the model asking), relevance-ranked recall,
behavioral scoring where upgrades are gradual and downgrades are instant,
and recency decay. Implemented natively over Aiva's SQLite — token-overlap
hybrid ranking instead of HNSW, which is plenty at personal scale.

Memory kinds:
  fact     — durable knowledge stored via the `remember` tool
  episode  — auto-captured chat exchanges (learning hook)
  pattern  — behavioral scores for action suggestions (approval feedback)
"""
from __future__ import annotations

import math
import re
from datetime import datetime, timezone
from typing import List, Optional

from sqlalchemy import select

from ..database import AsyncSessionLocal
from ..models.db_models import Memory

# scoring constants — upgrades gradual, downgrades instant (ruflo principle)
UPGRADE_FACTOR = 1.15
UPGRADE_BONUS = 0.05
DOWNGRADE_FACTOR = 0.5
SCORE_MAX = 5.0
SCORE_MIN = 0.1
RECENCY_HALF_LIFE_DAYS = 30.0


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _tokens(text: str) -> set:
    return set(re.findall(r"[a-z0-9]+", text.lower()))


def _row(m: Memory) -> dict:
    return {
        "id": m.id, "content": m.content, "kind": m.kind, "tags": m.tags,
        "score": m.score, "uses": m.uses,
        "created_at": m.created_at.isoformat() if m.created_at else None,
    }


async def remember(content: str, kind: str = "fact", tags: Optional[str] = None, score: float = 1.0) -> dict:
    """Store a memory; exact duplicates reinforce instead of duplicating."""
    async with AsyncSessionLocal() as session:
        existing = await session.scalar(select(Memory).where(Memory.content == content))
        if existing is not None:
            existing.uses += 1
            existing.score = min(existing.score * UPGRADE_FACTOR, SCORE_MAX)
            existing.last_used_at = _now()
            await session.commit()
            await session.refresh(existing)
            return _row(existing)
        memory = Memory(content=content, kind=kind, tags=tags, score=score, uses=0)
        session.add(memory)
        await session.commit()
        await session.refresh(memory)
        return _row(memory)


async def recall(query: str, limit: int = 5) -> List[dict]:
    """Relevance-ranked recall: token overlap × recency decay × score."""
    q = _tokens(query)
    if not q:
        return []
    now = _now()
    async with AsyncSessionLocal() as session:
        rows = list(await session.scalars(select(Memory)))
        ranked = []
        for m in rows:
            c = _tokens(m.content + " " + (m.tags or ""))
            match = len(q & c) / len(q)
            if match <= 0:
                continue
            anchor = m.last_used_at or m.created_at or now
            if anchor.tzinfo is None:
                anchor = anchor.replace(tzinfo=timezone.utc)
            age_days = max((now - anchor).total_seconds() / 86_400, 0.0)
            recency = max(math.exp(-age_days / RECENCY_HALF_LIFE_DAYS), 0.15)
            ranked.append((match * recency * m.score, m))
        ranked.sort(key=lambda pair: pair[0], reverse=True)
        top = [m for _, m in ranked[:limit]]
        for m in top:  # touching a memory keeps it warm
            m.uses += 1
            m.last_used_at = now
        await session.commit()
        return [_row(m) for m in top]


async def reinforce(pattern_key: str, success: bool) -> dict:
    """Behavioral score for a suggestion pattern (e.g. an action id)."""
    content = f"pattern:{pattern_key}"
    async with AsyncSessionLocal() as session:
        row = await session.scalar(select(Memory).where(Memory.content == content))
        if row is None:
            row = Memory(content=content, kind="pattern", tags=pattern_key, score=1.0, uses=0)
            session.add(row)
        if success:
            row.score = min(row.score * UPGRADE_FACTOR + UPGRADE_BONUS, SCORE_MAX)
        else:
            row.score = max(row.score * DOWNGRADE_FACTOR, SCORE_MIN)
        row.uses += 1
        row.last_used_at = _now()
        await session.commit()
        await session.refresh(row)
        return _row(row)


async def on_chat_exchange(user_msg: str, reply: str) -> None:
    """Learning hook: every completed exchange becomes an episode."""
    snippet = f"[chat] {user_msg.strip()[:140]} → {reply.strip()[:200]}"
    try:
        await remember(snippet, kind="episode", tags="chat", score=0.6)
    except Exception:
        pass  # learning must never break the conversation
