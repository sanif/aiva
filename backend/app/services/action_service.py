"""Allowlisted actions loaded from actions.yaml, executed and logged.

Only the `id` of an action defined in actions.yaml can ever be run. Shell
actions run a fixed command from the YAML (no user input interpolated).
"""
from __future__ import annotations

import asyncio
import os
from functools import lru_cache
from typing import Dict, List, Optional

import yaml
from sqlalchemy.ext.asyncio import AsyncSession

from ..models.db_models import ActionLog
from ..models.schemas import ActionInfo, ActionResult

_ACTIONS_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(__file__))), "actions.yaml"
)


@lru_cache
def _load() -> Dict[str, dict]:
    with open(_ACTIONS_PATH, "r", encoding="utf-8") as fh:
        data = yaml.safe_load(fh) or {}
    actions = data.get("actions", []) or []
    return {a["id"]: a for a in actions}


def list_actions() -> List[ActionInfo]:
    return [
        ActionInfo(id=a["id"], label=a.get("label", a["id"]), description=a.get("description", ""))
        for a in _load().values()
    ]


def get_action(action_id: str) -> Optional[dict]:
    return _load().get(action_id)


async def _run_shell(command: str) -> str:
    try:
        proc = await asyncio.create_subprocess_shell(
            command,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.STDOUT,
        )
        stdout, _ = await asyncio.wait_for(proc.communicate(), timeout=10.0)
        out = stdout.decode("utf-8", "replace").strip()
        return out[:200] if out else "(no output)"
    except asyncio.TimeoutError:
        return "command timed out"
    except Exception as exc:  # pragma: no cover
        return f"error: {exc}"


async def _log(session: AsyncSession, action_id: str, ok: bool, message: str) -> None:
    session.add(ActionLog(action_id=action_id, ok=ok, message=message))
    await session.commit()


async def run_action(session: AsyncSession, action_id: str) -> Optional[ActionResult]:
    """Run an allowlisted action. Returns None if the id is unknown."""
    action = get_action(action_id)
    if action is None:
        return None

    kind = action.get("type", "internal")
    label = action.get("label", action_id)
    ok = True

    if kind == "internal":
        if action_id == "focus":
            message = "Focus mode engaged · 45 min"
        elif action_id == "note":
            from . import note_service

            await note_service.create_empty_note(session)
            message = "New note created"
        elif action_id == "voice":
            message = "Recording voice note…"
        elif action_id == "lock_display":
            message = "Display locked"
        else:
            message = f"{label} done"
    elif kind == "shell":
        command = action.get("command", "")
        message = await _run_shell(command)
    elif kind == "open_url":
        url = action.get("url", "")
        message = f"Opening {url}"
        await _log(session, action_id, ok, message)
        return ActionResult(ok=ok, message=message, url=url)
    else:
        ok = False
        message = f"Unsupported action type: {kind}"

    await _log(session, action_id, ok, message)
    return ActionResult(ok=ok, message=message)
