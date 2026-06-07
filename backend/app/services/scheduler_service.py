"""Scheduler: jobs created from chat, executed BY the agent.

"Remind me to check backups every morning at 8" → the model calls
`schedule_task(...)`; at 08:00 the backend invokes the configured chat
brain (`llm_service.complete`) with the job's prompt — with
CHAT_PROVIDER=cli that means a real Claude session with Aiva's MCP tools
does the work, not just a text reply. Results land in chat history
(source "scheduler") and the self-learning memory.

Schedule formats:
  "HH:MM"             daily
  "m h dom mon dow"   5-field cron subset (*, numbers, lists, */n; dow 0=Sun)
  "YYYY-MM-DDTHH:MM"  one-shot (auto-disables after running)
"""
from __future__ import annotations

import asyncio
import logging
import re
from datetime import datetime
from typing import List, Optional

from sqlalchemy import select

from ..database import AsyncSessionLocal
from ..models.db_models import Schedule

log = logging.getLogger("aiva.scheduler")

_HHMM = re.compile(r"^(\d{1,2}):(\d{2})$")
_ISO = re.compile(r"^(\d{4})-(\d{2})-(\d{2})[T ](\d{1,2}):(\d{2})$")


def is_oneshot(spec: str) -> bool:
    return bool(_ISO.match((spec or "").strip()))


def cron_matches(spec: str, now: datetime) -> bool:
    """Does `spec` fire at minute `now`? Unparseable specs never fire."""
    spec = (spec or "").strip()
    if not spec:
        return False

    if m := _HHMM.match(spec):
        return now.hour == int(m[1]) and now.minute == int(m[2])

    if m := _ISO.match(spec):
        return (now.year, now.month, now.day, now.hour, now.minute) == tuple(int(g) for g in m.groups())

    fields = spec.split()
    if len(fields) != 5:
        return False
    cron_dow = (now.weekday() + 1) % 7  # cron: 0 = Sunday
    values = [now.minute, now.hour, now.day, now.month, cron_dow]

    def field_matches(field: str, value: int) -> bool:
        for part in field.split(","):
            part = part.strip()
            if part == "*":
                return True
            if part.startswith("*/"):
                try:
                    step = int(part[2:])
                except ValueError:
                    continue
                if step > 0 and value % step == 0:
                    return True
            else:
                try:
                    if int(part) == value:
                        return True
                except ValueError:
                    continue
        return False

    return all(field_matches(f, v) for f, v in zip(fields, values))


def _row(s: Schedule) -> dict:
    return {
        "id": s.id,
        "name": s.name,
        "prompt": s.prompt,
        "when": s.when_spec,
        "enabled": bool(s.enabled),
        "last_run_at": s.last_run_at.isoformat() if s.last_run_at else None,
        "last_result": s.last_result,
        "created_at": s.created_at.isoformat() if s.created_at else None,
    }


async def create(name: str, prompt: str, when: str) -> dict:
    async with AsyncSessionLocal() as session:
        schedule = Schedule(name=name, prompt=prompt, when_spec=when.strip(), enabled=True)
        session.add(schedule)
        await session.commit()
        await session.refresh(schedule)
        return _row(schedule)


async def list_all() -> List[dict]:
    async with AsyncSessionLocal() as session:
        rows = await session.scalars(select(Schedule).order_by(Schedule.id.asc()))
        return [_row(s) for s in rows]


async def delete(schedule_id: int) -> bool:
    async with AsyncSessionLocal() as session:
        row = await session.get(Schedule, schedule_id)
        if row is None:
            return False
        await session.delete(row)
        await session.commit()
        return True


async def run_due(now: Optional[datetime] = None) -> int:
    """Run every enabled schedule that fires this minute. Returns run count."""
    now = now or datetime.now()
    minute_key = now.strftime("%Y%m%d%H%M")
    ran = 0
    async with AsyncSessionLocal() as session:
        rows = list(await session.scalars(select(Schedule).where(Schedule.enabled == True)))  # noqa: E712

    for sched in rows:
        if not cron_matches(sched.when_spec, now):
            continue
        if sched.last_run_at and sched.last_run_at.strftime("%Y%m%d%H%M") == minute_key:
            continue  # already ran this minute
        await _run_one(sched.id, now)
        ran += 1
    return ran


async def _run_one(schedule_id: int, now: datetime) -> None:
    from . import llm_service, recall_service

    async with AsyncSessionLocal() as session:
        sched = await session.get(Schedule, schedule_id)
        if sched is None:
            return
        log.info("running schedule #%s '%s'", sched.id, sched.name)
        try:
            reply = await llm_service.complete(sched.prompt)
        except Exception as exc:  # the looper must survive job failures
            reply = f"⚠ schedule failed: {exc}"
        sched.last_run_at = now
        sched.last_result = (reply or "")[:2000]
        if is_oneshot(sched.when_spec):
            sched.enabled = False
        name, prompt = sched.name, sched.prompt
        await session.commit()

    # surface the outcome in chat history + learning memory (best-effort)
    try:
        from ..config import settings
        from ..models.db_models import ChatMessage

        if settings.STORE_CHAT_HISTORY:
            async with AsyncSessionLocal() as session:
                session.add(ChatMessage(role="assistant", content=f"⏰ [{name}] {reply}", source="scheduler"))
                await session.commit()
        await recall_service.on_chat_exchange(f"[scheduled: {name}] {prompt}", reply)
    except Exception:
        pass


async def scheduler_loop(stop_event: asyncio.Event) -> None:
    """Background looper — checks twice a minute, exactly like a tiny cron."""
    log.info("scheduler loop started")
    while not stop_event.is_set():
        try:
            await run_due()
        except Exception:
            log.warning("scheduler tick failed", exc_info=True)
        try:
            await asyncio.wait_for(stop_event.wait(), 30.0)
        except asyncio.TimeoutError:
            pass
    log.info("scheduler loop stopped")
