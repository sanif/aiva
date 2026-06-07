"""Aiva MCP server — the same tool registry, for ANY MCP client.

Run a Claude Code session with these tools (CHAT_PROVIDER=cli recipe):

    claude -p "{message}" --mcp-config aiva-mcp.json --continue ...

Start manually for testing:  .venv/bin/python mcp_server.py
The server speaks stdio and reads backend/.env for DB/workspace paths,
so launch it with this directory as cwd (see aiva-mcp.json).
"""
from __future__ import annotations

from typing import Optional

from mcp.server.fastmcp import FastMCP

from app.services import tools_service

mcp = FastMCP("aiva")


@mcp.tool()
async def get_system_status() -> str:
    """Live Mac status: metrics, service health, docker containers, alerts."""
    return await tools_service.execute_tool("get_system_status")


@mcp.tool()
async def list_tasks(
    status: Optional[str] = None,
    project: Optional[str] = None,
    q: Optional[str] = None,
    tag: Optional[str] = None,
) -> str:
    """List tracker tasks; filter by status (today|upcoming|done), project, text q, or tag."""
    return await tools_service.execute_tool(
        "list_tasks", {"status": status, "project": project, "q": q, "tag": tag}
    )


@mcp.tool()
async def create_task(
    title: str,
    status: Optional[str] = None,
    priority: Optional[str] = None,
    category: Optional[str] = None,
    project: Optional[str] = None,
    notes: Optional[str] = None,
    tags: Optional[str] = None,
    due: Optional[str] = None,
    parent_id: Optional[int] = None,
) -> str:
    """Create a task (priority high|med|low, tags comma-separated, parent_id for subtasks)."""
    args = {k: v for k, v in locals().items() if v is not None}
    return await tools_service.execute_tool("create_task", args)


@mcp.tool()
async def update_task(
    task_id: int,
    title: Optional[str] = None,
    status: Optional[str] = None,
    priority: Optional[str] = None,
    project: Optional[str] = None,
    notes: Optional[str] = None,
    tags: Optional[str] = None,
    due: Optional[str] = None,
) -> str:
    """Update a task; status=done completes it, anything else reopens it."""
    args = {k: v for k, v in locals().items() if v is not None}
    return await tools_service.execute_tool("update_task", args)


@mcp.tool()
async def list_projects() -> str:
    """Projects in the tracker with total/done counts."""
    return await tools_service.execute_tool("list_projects")


@mcp.tool()
async def get_agenda() -> str:
    """Today's agenda items."""
    return await tools_service.execute_tool("get_agenda")


@mcp.tool()
async def list_notes(limit: int = 10) -> str:
    """Latest notes."""
    return await tools_service.execute_tool("list_notes", {"limit": limit})


@mcp.tool()
async def create_note(body: str, title: Optional[str] = None) -> str:
    """Save a note."""
    return await tools_service.execute_tool("create_note", {"body": body, "title": title})


@mcp.tool()
async def run_action(action_id: str) -> str:
    """Run an ALLOWLISTED quick action (focus, restart_backend, lock_display, ...)."""
    return await tools_service.execute_tool("run_action", {"action_id": action_id})


@mcp.tool()
async def save_memory(history_entry: Optional[str] = None, memory_update: Optional[str] = None) -> str:
    """Persist memory: history_entry appends to the log; memory_update appends long-term facts."""
    return await tools_service.execute_tool(
        "save_memory", {"history_entry": history_entry, "memory_update": memory_update}
    )


@mcp.tool()
async def remember(content: str, tags: Optional[str] = None) -> str:
    """Store a durable memory in the self-learning store (deduped, searchable)."""
    return await tools_service.execute_tool("remember", {"content": content, "tags": tags})


@mcp.tool()
async def recall(query: str, limit: int = 5) -> str:
    """Search episodic memory ranked by relevance × recency × score."""
    return await tools_service.execute_tool("recall", {"query": query, "limit": limit})


@mcp.tool()
async def schedule_task(name: str, prompt: str, when: str) -> str:
    """Schedule the agent to DO something later. when: 'HH:MM' daily, 5-field cron, or 'YYYY-MM-DDTHH:MM' one-shot."""
    return await tools_service.execute_tool("schedule_task", {"name": name, "prompt": prompt, "when": when})


@mcp.tool()
async def list_schedules() -> str:
    """List scheduled jobs with last run results."""
    return await tools_service.execute_tool("list_schedules")


@mcp.tool()
async def delete_schedule(schedule_id: int) -> str:
    """Delete a scheduled job by id."""
    return await tools_service.execute_tool("delete_schedule", {"schedule_id": schedule_id})


if __name__ == "__main__":
    mcp.run()
