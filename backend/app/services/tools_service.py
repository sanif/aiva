"""Tool registry: the chat model reaches the Aiva API via tool calls.

OpenAI function-calling schemas (nanobot pattern) + an async executor.
Each tool opens its own DB session, so any provider/process can call it.
Every result is a JSON string; errors come back as {"error": ...} so the
model can recover instead of the loop crashing.
"""
from __future__ import annotations

import json
from typing import Any

from ..database import AsyncSessionLocal
from ..models.schemas import NoteCreate, TaskCreate, TaskUpdate

TOOL_SPECS: list[dict] = [
    {
        "type": "function",
        "function": {
            "name": "get_system_status",
            "description": "Live Mac status: CPU/RAM/disk/temp/network metrics, service health, docker containers and active alerts.",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_tasks",
            "description": "List todo-tracker tasks. Filter by status (today|upcoming|done), project, free-text q, or tag.",
            "parameters": {
                "type": "object",
                "properties": {
                    "status": {"type": "string", "enum": ["today", "upcoming", "done"]},
                    "project": {"type": "string"},
                    "q": {"type": "string", "description": "search in title/notes/description"},
                    "tag": {"type": "string"},
                },
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "create_task",
            "description": "Create a task in the tracker.",
            "parameters": {
                "type": "object",
                "properties": {
                    "title": {"type": "string"},
                    "status": {"type": "string", "enum": ["today", "upcoming", "done"]},
                    "priority": {"type": "string", "enum": ["high", "med", "low"]},
                    "category": {"type": "string", "enum": ["work", "personal", "system"]},
                    "project": {"type": "string"},
                    "notes": {"type": "string"},
                    "tags": {"type": "string", "description": "comma-separated"},
                    "due": {"type": "string"},
                    "parent_id": {"type": "integer", "description": "make this a subtask of another task"},
                },
                "required": ["title"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "update_task",
            "description": "Update a task (status done completes it, anything else reopens). Any field can change.",
            "parameters": {
                "type": "object",
                "properties": {
                    "task_id": {"type": "integer"},
                    "title": {"type": "string"},
                    "status": {"type": "string", "enum": ["today", "upcoming", "done"]},
                    "priority": {"type": "string", "enum": ["high", "med", "low"]},
                    "project": {"type": "string"},
                    "notes": {"type": "string"},
                    "tags": {"type": "string"},
                    "due": {"type": "string"},
                },
                "required": ["task_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_projects",
            "description": "Projects in the tracker with total/done counts.",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_agenda",
            "description": "Today's agenda/calendar items.",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_notes",
            "description": "Latest notes.",
            "parameters": {
                "type": "object",
                "properties": {"limit": {"type": "integer", "default": 10}},
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "create_note",
            "description": "Save a note for the user.",
            "parameters": {
                "type": "object",
                "properties": {
                    "body": {"type": "string"},
                    "title": {"type": "string"},
                },
                "required": ["body"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "run_action",
            "description": "Run an ALLOWLISTED quick action (focus, restart_backend, lock_display, backup_status, open_dashboard, ...). Unknown ids are rejected.",
            "parameters": {
                "type": "object",
                "properties": {"action_id": {"type": "string"}},
                "required": ["action_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "schedule_task",
            "description": "Schedule the agent to DO something later/recurringly. when: 'HH:MM' daily, 5-field cron ('30 8 * * 1', dow 0=Sun), or 'YYYY-MM-DDTHH:MM' one-shot. The prompt is executed by the agent at that time.",
            "parameters": {
                "type": "object",
                "properties": {
                    "name": {"type": "string", "description": "short label"},
                    "prompt": {"type": "string", "description": "what the agent should do when it fires"},
                    "when": {"type": "string"},
                },
                "required": ["name", "prompt", "when"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_schedules",
            "description": "List scheduled jobs with their last run results.",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "delete_schedule",
            "description": "Delete a scheduled job by id.",
            "parameters": {
                "type": "object",
                "properties": {"schedule_id": {"type": "integer"}},
                "required": ["schedule_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "remember",
            "description": "Store a durable memory in the self-learning store (deduped, relevance-searchable). Use for facts/preferences worth keeping.",
            "parameters": {
                "type": "object",
                "properties": {
                    "content": {"type": "string"},
                    "tags": {"type": "string", "description": "comma-separated"},
                },
                "required": ["content"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "recall",
            "description": "Search episodic memory (facts, past chats, learned patterns) ranked by relevance × recency × score.",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {"type": "string"},
                    "limit": {"type": "integer", "default": 5},
                },
                "required": ["query"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "save_memory",
            "description": "Persist memory: history_entry appends one line to the timestamped log; memory_update appends consolidated long-term facts. Use after learning something worth remembering.",
            "parameters": {
                "type": "object",
                "properties": {
                    "history_entry": {"type": "string"},
                    "memory_update": {"type": "string"},
                },
            },
        },
    },
]


def _dump(obj: Any) -> str:
    return json.dumps(obj, default=str)


async def execute_tool(name: str, args: dict | None = None) -> str:
    """Run one tool by name; never raises — errors return as JSON."""
    args = args or {}
    try:
        return await _dispatch(name, args)
    except Exception as exc:
        return _dump({"error": f"{type(exc).__name__}: {exc}"})


async def _dispatch(name: str, args: dict) -> str:
    from . import action_service, alert_service, memory_service, monitor_service, note_service, task_service
    from .docker_service import get_docker_info

    if name == "get_system_status":
        metrics = await monitor_service.monitor.get_metrics()
        services = await monitor_service.check_services()
        docker = await get_docker_info()
        alerts = alert_service.derive_alerts(metrics, services, docker)
        return _dump({
            "metrics": metrics.model_dump(),
            "services": [s.model_dump() for s in services],
            "docker": docker.model_dump(),
            "alerts": [a.model_dump() for a in alerts],
        })

    if name == "list_tasks":
        async with AsyncSessionLocal() as session:
            tasks = await task_service.list_tasks(
                session,
                status=args.get("status"),
                project=args.get("project"),
                q=args.get("q"),
                tag=args.get("tag"),
            )
            return _dump([_task(t) for t in tasks])

    if name == "create_task":
        async with AsyncSessionLocal() as session:
            task = await task_service.create_task(session, TaskCreate(**args))
            return _dump(_task(task))

    if name == "update_task":
        task_id = int(args.pop("task_id"))
        async with AsyncSessionLocal() as session:
            task = await task_service.update_task(session, task_id, TaskUpdate(**args))
            if task is None:
                return _dump({"error": "task not found"})
            return _dump(_task(task))

    if name == "list_projects":
        async with AsyncSessionLocal() as session:
            return _dump(await task_service.projects(session))

    if name == "get_agenda":
        from ..config import settings

        return _dump(settings.agenda)

    if name == "list_notes":
        async with AsyncSessionLocal() as session:
            notes = await note_service.list_notes(session, int(args.get("limit", 10)))
            return _dump([
                {"id": n.id, "title": n.title, "body": n.body, "kind": n.kind, "created_at": n.created_at}
                for n in notes
            ])

    if name == "create_note":
        async with AsyncSessionLocal() as session:
            note = await note_service.create_note(
                session, NoteCreate(body=args["body"], title=args.get("title"))
            )
            return _dump({"id": note.id, "title": note.title, "body": note.body, "kind": note.kind})

    if name == "run_action":
        async with AsyncSessionLocal() as session:
            result = await action_service.run_action(session, args.get("action_id", ""))
            if result is None:
                return _dump({"ok": False, "error": "unknown action — only allowlisted ids run"})
            return _dump(result.model_dump())

    if name == "schedule_task":
        from . import scheduler_service

        return _dump(await scheduler_service.create(args["name"], args["prompt"], args["when"]))

    if name == "list_schedules":
        from . import scheduler_service

        return _dump(await scheduler_service.list_all())

    if name == "delete_schedule":
        from . import scheduler_service

        ok = await scheduler_service.delete(int(args["schedule_id"]))
        return _dump({"ok": ok} if ok else {"ok": False, "error": "schedule not found"})

    if name == "remember":
        from . import recall_service

        return _dump(await recall_service.remember(args["content"], tags=args.get("tags")))

    if name == "recall":
        from . import recall_service

        return _dump(await recall_service.recall(args["query"], int(args.get("limit", 5))))

    if name == "save_memory":
        if entry := args.get("history_entry"):
            memory_service.append_history(entry)
        if update := args.get("memory_update"):
            memory_service.update_memory(update)
        return _dump({"ok": True})

    return _dump({"error": f"unknown tool: {name}"})


def _task(t) -> dict:
    return {
        "id": t.id, "title": t.title, "status": t.status, "priority": t.priority,
        "category": t.category, "due": t.due, "project": t.project, "notes": t.notes,
        "tags": t.tags, "parent_id": t.parent_id, "completed_at": t.completed_at,
    }
