"""API contract tests: auth, chat history, action URLs."""
from __future__ import annotations

from tests.conftest import AUTH


async def test_health_is_open(client):
    r = await client.get("/api/health")
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "ok"
    assert body["mock"] is True


async def test_metrics_requires_token(client):
    r = await client.get("/api/metrics")
    assert r.status_code == 401
    r = await client.get("/api/metrics", headers=AUTH)
    assert r.status_code == 200
    assert "cpu_pct" in r.json()


async def test_chat_history_roundtrip(client):
    r = await client.post("/api/chat", json={"message": "any urgent alerts?"}, headers=AUTH)
    assert r.status_code == 200
    reply = r.json()["reply"]
    assert reply

    r = await client.get("/api/chat/history", headers=AUTH, params={"limit": 10})
    assert r.status_code == 200
    history = r.json()
    assert isinstance(history, list)
    assert len(history) >= 2
    # chronological: user message precedes its assistant reply
    roles = [h["role"] for h in history[-2:]]
    assert roles == ["user", "assistant"]
    assert history[-2]["text"] == "any urgent alerts?"
    assert history[-1]["text"] == reply
    for h in history:
        assert h["source"] in ("app", "telegram")
        assert "ts" in h


async def test_chat_history_requires_token(client):
    r = await client.get("/api/chat/history")
    assert r.status_code == 401


async def test_open_url_action_returns_url(client):
    r = await client.post("/api/actions/open_dashboard", headers=AUTH)
    assert r.status_code == 200
    body = r.json()
    assert body["ok"] is True
    assert body["url"] == "http://localhost:8420/docs"


async def test_non_url_action_has_no_url(client):
    r = await client.post("/api/actions/focus", headers=AUTH)
    assert r.status_code == 200
    assert r.json().get("url") in (None, "")


async def test_unknown_action_404(client):
    r = await client.post("/api/actions/rm_rf_slash", headers=AUTH)
    assert r.status_code == 404


async def test_notes_roundtrip(client):
    r = await client.post("/api/notes", json={"body": "voice memo", "kind": "voice"}, headers=AUTH)
    assert r.status_code == 201
    r = await client.get("/api/notes", headers=AUTH)
    assert any(n["body"] == "voice memo" and n["kind"] == "voice" for n in r.json())


async def test_web_dashboard_is_served(client):
    r = await client.get("/")
    assert r.status_code == 200
    assert "text/html" in r.headers["content-type"]
    assert "Aiva" in r.text
    # the page must not embed any secrets — token is entered client-side
    assert "change-me" not in r.text and "test-token" not in r.text


async def test_tools_listing_endpoint(client):
    r = await client.get("/api/tools")
    assert r.status_code == 401  # token required
    r = await client.get("/api/tools", headers=AUTH)
    assert r.status_code == 200
    tools = r.json()
    names = [t["name"] for t in tools]
    assert "get_system_status" in names and "schedule_task" in names
    assert all(t["description"] for t in tools)
