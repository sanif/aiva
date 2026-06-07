"""Self-learning episodic memory — design credit: ruflo (ruvnet/ruflo).

Principles borrowed: automatic learning hooks, relevance-ranked recall,
behavioral scoring (gradual upgrades, instant downgrades), recency decay.
Implemented natively over Aiva's SQLite — no external services.
"""
from __future__ import annotations

import json
from datetime import datetime, timedelta, timezone

from app.database import init_db
from app.services import recall_service
from tests.conftest import AUTH


async def test_remember_and_relevance_ranked_recall(client):
    await init_db()
    await recall_service.remember("The user's NAS backups run nightly at 02:00", tags="backup,nas")
    await recall_service.remember("The Razr lens calibration values are saved", tags="device")
    await recall_service.remember("Plex container is flaky after updates", tags="docker")

    hits = await recall_service.recall("when do the nas backups run?")
    assert hits, "recall returned nothing"
    assert "backups run nightly" in hits[0]["content"]


async def test_remember_dedupes_and_bumps(client):
    await init_db()
    first = await recall_service.remember("duplicate fact about uptime")
    again = await recall_service.remember("duplicate fact about uptime")
    assert first["id"] == again["id"]
    assert again["uses"] >= first["uses"]


async def test_reinforce_upgrades_slowly_downgrades_fast(client):
    await init_db()
    base = await recall_service.reinforce("focus", success=True)
    up = await recall_service.reinforce("focus", success=True)
    assert up["score"] >= base["score"] > 1.0

    down = await recall_service.reinforce("focus", success=False)
    assert down["score"] < up["score"]
    # one failure undoes more than one success gained
    assert (up["score"] - down["score"]) > (up["score"] - base["score"])


async def test_recency_decay_ranks_fresh_first(client):
    await init_db()
    old = await recall_service.remember("kernel panic incident review")
    await recall_service.remember("kernel panic incident follow-up")
    # age the first memory artificially
    from app.database import AsyncSessionLocal
    from app.models.db_models import Memory

    async with AsyncSessionLocal() as session:
        row = await session.get(Memory, old["id"])
        row.created_at = datetime.now(timezone.utc) - timedelta(days=120)
        row.last_used_at = row.created_at
        await session.commit()

    hits = await recall_service.recall("kernel panic incident")
    assert "follow-up" in hits[0]["content"]


async def test_chat_exchanges_auto_learn(client):
    await init_db()
    await recall_service.on_chat_exchange("what's my disk usage?", "Disk is at 71%.")
    hits = await recall_service.recall("disk usage")
    assert any(h["kind"] == "episode" for h in hits)


async def test_feedback_endpoint_reinforces(client):
    r = await client.post(
        "/api/chat/feedback",
        json={"action_id": "focus", "approved": True},
        headers=AUTH,
    )
    assert r.status_code == 200
    score_up = r.json()["score"]

    r = await client.post(
        "/api/chat/feedback",
        json={"action_id": "focus", "approved": False},
        headers=AUTH,
    )
    assert r.json()["score"] < score_up


async def test_remember_and_recall_are_chat_tools(client):
    from app.services import tools_service

    names = [s["function"]["name"] for s in tools_service.TOOL_SPECS]
    assert "remember" in names and "recall" in names

    await init_db()
    out = json.loads(await tools_service.execute_tool("remember", {"content": "tool-stored fact", "tags": "t"}))
    assert out["id"]
    hits = json.loads(await tools_service.execute_tool("recall", {"query": "tool-stored fact"}))
    assert any("tool-stored" in h["content"] for h in hits)
