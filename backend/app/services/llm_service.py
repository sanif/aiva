"""LLM abstraction via LiteLLM with async streaming and mock replies.

Falls back to canned mock replies when MOCK_MODE is on or the LLM is
unreachable.
"""
from __future__ import annotations

import asyncio
from typing import AsyncGenerator, Optional

from ..config import settings


def _build_status_reply(metrics, services, alerts) -> str:
    healthy = all(s.status == "up" for s in services) if services else True
    core = "All core services are healthy." if healthy else "Some services need attention."
    base = (
        f"{core} CPU is at {metrics.cpu_pct:.0f}%, "
        f"RAM {metrics.ram_pct:.0f}%, disk {metrics.disk_pct:.0f}%."
    )
    if alerts:
        base += " Active alerts: " + "; ".join(a.title for a in alerts) + "."
    return base


def mock_reply(message: str, *, metrics=None, services=None, alerts=None,
               agenda=None, tasks_summary=None, **_extra) -> str:
    """Return a canned reply keyed off lowercase substrings."""
    m = message.lower()

    if "focus" in m or "now" in m:
        return (
            "Right now: finish the Q3 architecture review — it's high-priority "
            "and due at 09:30. After that, protect your 11:00 deep-work block. "
            "I can start a 45-minute focus timer if you'd like."
        )

    if "status" in m or "system" in m:
        if metrics is not None:
            return _build_status_reply(metrics, services or [], alerts or [])
        return "All core services are healthy."

    if "summar" in m or "today" in m:
        parts = []
        if tasks_summary is not None:
            parts.append(
                f"You have {tasks_summary.today} tasks today "
                f"({tasks_summary.done} done), {tasks_summary.upcoming} upcoming."
            )
        if agenda:
            nxt = agenda[0]
            parts.append(f"Next up: {nxt.get('title')} at {nxt.get('time')}.")
        return " ".join(parts) if parts else "Nothing scheduled. Enjoy the quiet."

    if "alert" in m or "urgent" in m:
        if alerts:
            return "Active alerts: " + "; ".join(f"{a.title} ({a.meta})" for a in alerts) + "."
        return "No active alerts."

    return (
        "Got it. I'm running locally on your Mac, so this stays on your network. "
        "I can pull system metrics, manage tasks, or trigger any allowlisted "
        "action — just ask."
    )


def build_system_prompt() -> str:
    """Persona + memory from the agent workspace — NO live-state preload.

    The model reaches live data on demand through tool calls (litellm
    function calling) or MCP tools (CLI sessions). Identity and memory
    follow the OpenClaw/nanobot pattern: SOUL.md / USER.md bootstrap,
    memory/MEMORY.md + memory/HISTORY.md maintained via `save_memory`.
    """
    from . import memory_service

    parts = [settings.LLM_SYSTEM_PROMPT]
    if bootstrap := memory_service.load_bootstrap():
        parts.append(bootstrap)
    if memory := memory_service.load_memory():
        parts.append(memory)
    parts.append(
        "Use your tools to read live system status, the task tracker, agenda "
        "and notes — never guess at state. Search past knowledge with the "
        "recall tool; store durable facts with remember (searchable) or "
        "save_memory (long-term notes). Only allowlisted actions can run."
    )
    return "\n\n".join(parts)


def _messages(message: str, system: Optional[str] = None) -> list[dict]:
    return [
        {"role": "system", "content": system or settings.LLM_SYSTEM_PROMPT},
        {"role": "user", "content": message},
    ]


def _llm_kwargs() -> dict:
    kwargs: dict = {"model": settings.LLM_MODEL}
    if settings.LLM_API_BASE:
        kwargs["api_base"] = settings.LLM_API_BASE
    if settings.LLM_API_KEY:
        kwargs["api_key"] = settings.LLM_API_KEY
    return kwargs


async def complete(message: str, *, context: Optional[dict] = None) -> str:
    """Non-streaming completion. Falls back to mock on MOCK_MODE / error."""
    context = context or {}
    if settings.MOCK_MODE:
        return mock_reply(message, **context)

    system = build_system_prompt()

    if settings.CHAT_PROVIDER == "cli":
        from . import cli_service

        return await cli_service.complete(message, system=system)

    try:
        return await _tool_loop(message, system)
    except Exception:
        # graceful fallback to canned reply
        return mock_reply(message, **context)


MAX_TOOL_ROUNDS = 6


async def _tool_loop(message: str, system: str) -> str:
    """Agentic loop: offer tools, execute calls, feed results back."""
    import litellm

    from . import tools_service

    messages: list[dict] = _messages(message, system)
    last_content = ""
    for _ in range(MAX_TOOL_ROUNDS):
        resp = await litellm.acompletion(
            messages=messages,
            tools=tools_service.TOOL_SPECS,
            stream=False,
            timeout=60,
            **_llm_kwargs(),
        )
        msg = resp.choices[0].message
        tool_calls = getattr(msg, "tool_calls", None)
        if not tool_calls:
            return msg.content or last_content or ""
        last_content = msg.content or last_content
        messages.append(msg.model_dump() if hasattr(msg, "model_dump") else dict(msg))
        for call in tool_calls:
            import json as _json

            try:
                args = _json.loads(call.function.arguments or "{}")
            except Exception:
                args = {}
            result = await tools_service.execute_tool(call.function.name, args)
            messages.append({
                "role": "tool",
                "tool_call_id": call.id,
                "content": result,
            })
    return last_content or "I ran out of tool budget — try a more specific question."


async def stream(message: str, *, context: Optional[dict] = None) -> AsyncGenerator[str, None]:
    """Yield text chunks. Mock streams word-by-word with ~40ms delays."""
    context = context or {}

    if settings.MOCK_MODE:
        reply = mock_reply(message, **context)
        for word in reply.split(" "):
            await asyncio.sleep(0.04)
            yield word + " "
        return

    system = build_system_prompt()

    if settings.CHAT_PROVIDER == "cli":
        # the CLI bridge is request/response; stream the reply word-wise
        from . import cli_service

        reply = await cli_service.complete(message, system=system)
        for word in reply.split(" "):
            await asyncio.sleep(0.02)
            yield word + " "
        return

    try:
        # tool-using turn is request/response; stream the final text word-wise
        reply = await _tool_loop(message, system)
        for word in reply.split(" "):
            await asyncio.sleep(0.02)
            yield word + " "
    except Exception:
        # fall back to streaming the canned reply
        reply = mock_reply(message, **context)
        for word in reply.split(" "):
            await asyncio.sleep(0.04)
            yield word + " "
