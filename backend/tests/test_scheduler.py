"""Scheduler: chat-created jobs that invoke the agent on schedule.

Schedule formats:
  "HH:MM"               → daily at that time
  "m h dom mon dow"     → 5-field cron subset (*, numbers, lists, */n)
  "YYYY-MM-DDTHH:MM"    → one-shot (auto-disables after running)
"""
from __future__ import annotations

import json
from datetime import datetime

import pytest

from app.database import init_db
from app.services import scheduler_service
from app.services.scheduler_service import cron_matches
from tests.conftest import AUTH


def at(y=2026, mo=6, d=7, h=9, mi=30) -> datetime:
    return datetime(y, mo, d, h, mi)


# ---- matcher ----

def test_daily_hhmm():
    assert cron_matches("09:30", at(h=9, mi=30))
    assert not cron_matches("09:30", at(h=9, mi=31))


def test_cron_five_field():
    # Sunday 2026-06-07 09:30
    assert cron_matches("30 9 * * *", at())
    assert cron_matches("*/15 * * * *", at(mi=30))
    assert not cron_matches("*/15 * * * *", at(mi=31))
    assert cron_matches("30 9 7 6 *", at())
    assert not cron_matches("30 9 8 6 *", at())
    assert cron_matches("30 9 * * 0", at())      # dow 0 = Sunday
    assert not cron_matches("30 9 * * 1", at())
    assert cron_matches("0,30 9 * * *", at())


def test_oneshot_iso():
    assert cron_matches("2026-06-07T09:30", at())
    assert not cron_matches("2026-06-07T09:31", at())


def test_garbage_never_matches():
    assert not cron_matches("whenever", at())
    assert not cron_matches("", at())


# ---- CRUD + due running ----

async def test_schedule_crud_via_tools(client):
    await init_db()
    from app.services import tools_service

    created = json.loads(await tools_service.execute_tool(
        "schedule_task",
        {"name": "morning brief", "prompt": "Summarize my day and system health", "when": "08:00"},
    ))
    assert created["id"]
    assert created["when"] == "08:00"

    listed = json.loads(await tools_service.execute_tool("list_schedules"))
    assert any(s["name"] == "morning brief" for s in listed)

    gone = json.loads(await tools_service.execute_tool("delete_schedule", {"schedule_id": created["id"]}))
    assert gone["ok"] is True
    listed = json.loads(await tools_service.execute_tool("list_schedules"))
    assert not any(s["id"] == created["id"] for s in listed)


async def test_due_schedule_invokes_agent_and_records(client, monkeypatch):
    await init_db()
    ran: list[str] = []

    async def fake_complete(message, *, context=None):
        ran.append(message)
        return "scheduled work done"

    from app.services import llm_service

    monkeypatch.setattr(llm_service, "complete", fake_complete)

    sched = await scheduler_service.create("tick", "do the scheduled thing", "09:30")
    await scheduler_service.run_due(now=at(h=9, mi=30))

    assert ran == ["do the scheduled thing"]
    rows = await scheduler_service.list_all()
    row = next(s for s in rows if s["id"] == sched["id"])
    assert row["last_result"] == "scheduled work done"
    assert row["last_run_at"]

    # same minute → must not double-run
    await scheduler_service.run_due(now=at(h=9, mi=30))
    assert len(ran) == 1


async def test_oneshot_disables_after_run(client, monkeypatch):
    await init_db()

    async def fake_complete(message, *, context=None):
        return "done"

    from app.services import llm_service

    monkeypatch.setattr(llm_service, "complete", fake_complete)

    sched = await scheduler_service.create("once", "one shot", "2026-06-07T09:30")
    await scheduler_service.run_due(now=at(h=9, mi=30))
    rows = await scheduler_service.list_all()
    row = next(s for s in rows if s["id"] == sched["id"])
    assert row["enabled"] is False


async def test_schedules_rest_api(client):
    r = await client.post(
        "/api/schedules",
        json={"name": "api made", "prompt": "p", "when": "07:00"},
        headers=AUTH,
    )
    assert r.status_code == 201
    sid = r.json()["id"]

    r = await client.get("/api/schedules", headers=AUTH)
    assert any(s["id"] == sid for s in r.json())

    r = await client.delete(f"/api/schedules/{sid}", headers=AUTH)
    assert r.json()["ok"] is True


async def test_snapshot_carries_last_schedule_run(client, monkeypatch):
    await init_db()

    async def fake_complete(message, *, context=None):
        return "summary done"

    from app.services import llm_service

    monkeypatch.setattr(llm_service, "complete", fake_complete)

    await scheduler_service.create("morning brief", "summarize", "10:15")
    await scheduler_service.run_due(now=at(h=10, mi=15))

    from app.routers.websocket import _snapshot_payload
    from app.services import monitor_service

    metrics = await monitor_service.monitor.get_metrics()
    payload = await _snapshot_payload(metrics)
    last = payload["last_schedule"]
    assert last is not None
    assert last["name"] == "morning brief"
    assert last["result"] == "summary done"
    assert last["ts"]
