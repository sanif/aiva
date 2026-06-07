"""Tool registry: the chat model accesses the API via tool calls, on demand."""
from __future__ import annotations

import json

from app.database import init_db
from app.services import tools_service


async def call(name: str, args: dict | None = None) -> dict | list:
    out = await tools_service.execute_tool(name, args or {})
    return json.loads(out)


async def test_specs_are_valid_openai_schemas():
    specs = tools_service.TOOL_SPECS
    names = [s["function"]["name"] for s in specs]
    assert len(names) == len(set(names))
    for s in specs:
        assert s["type"] == "function"
        assert s["function"]["description"]
        assert "parameters" in s["function"]
    # the core surface is exposed
    for required in (
        "get_system_status", "list_tasks", "create_task", "update_task",
        "list_projects", "get_agenda", "list_notes", "create_note",
        "run_action", "save_memory",
    ):
        assert required in names


async def test_task_tools_roundtrip():
    await init_db()
    created = await call("create_task", {"title": "tool made me", "project": "tools", "priority": "high"})
    assert created["title"] == "tool made me"
    assert created["project"] == "tools"

    listed = await call("list_tasks", {"project": "tools"})
    assert any(t["id"] == created["id"] for t in listed)

    done = await call("update_task", {"task_id": created["id"], "status": "done"})
    assert done["status"] == "done"
    assert done["completed_at"]

    projects = await call("list_projects")
    assert any(p["name"] == "tools" for p in projects)


async def test_system_status_tool():
    status = await call("get_system_status")
    assert "metrics" in status and "services" in status and "alerts" in status
    assert "cpu_pct" in status["metrics"]


async def test_notes_tools():
    await init_db()
    note = await call("create_note", {"body": "made by a tool"})
    assert note["body"] == "made by a tool"
    notes = await call("list_notes", {"limit": 5})
    assert any(n["body"] == "made by a tool" for n in notes)


async def test_run_action_respects_allowlist():
    await init_db()
    ok = await call("run_action", {"action_id": "focus"})
    assert ok["ok"] is True

    bad = await call("run_action", {"action_id": "rm_rf"})
    assert bad.get("ok") is False
    assert "unknown" in bad.get("error", "").lower()


async def test_unknown_tool_returns_error():
    bad = await call("not_a_tool")
    assert "error" in bad


async def test_save_memory_tool():
    out = await call("save_memory", {"history_entry": "tool wrote history", "memory_update": "- tool fact"})
    assert out["ok"] is True
    from app.config import settings
    import os

    assert "tool wrote history" in open(os.path.join(settings.WORKSPACE_PATH, "memory", "HISTORY.md")).read()
    assert "tool fact" in open(os.path.join(settings.WORKSPACE_PATH, "memory", "MEMORY.md")).read()
