"""Workspace persona + memory — the nanobot/OpenClaw pattern.

  {WORKSPACE_PATH}/SOUL.md            persona / identity
  {WORKSPACE_PATH}/AGENTS.md          optional extra instructions
  {WORKSPACE_PATH}/USER.md            who the user is
  {WORKSPACE_PATH}/memory/MEMORY.md   consolidated long-term facts
  {WORKSPACE_PATH}/memory/HISTORY.md  append-only timestamped log

Bootstrap + memory feed the system prompt; the model maintains them via
the `save_memory` tool.
"""
from __future__ import annotations

import os
from datetime import datetime

from ..config import settings

BOOTSTRAP_FILES = ("SOUL.md", "AGENTS.md", "USER.md")
MAX_FILE_CHARS = 20_000
HISTORY_TAIL_LINES = 20


def _ws(*parts: str) -> str:
    return os.path.join(settings.WORKSPACE_PATH, *parts)


def _read(path: str) -> str:
    try:
        with open(path, "r", encoding="utf-8") as fh:
            return fh.read(MAX_FILE_CHARS).strip()
    except OSError:
        return ""


DEFAULT_SOUL = """# Soul

You are **Aiva**, a calm, concise personal assistant living on the cover
screen of a folding phone, backed by the user's computer.

- Replies are short; they render on a 4-inch screen.
- Read live state through your tools before answering. Never guess.
- Suggest allowlisted actions when useful; the user approves them in-app.
- Persist durable facts and preferences with the remember/save_memory tools.
"""

DEFAULT_USER = """# User

Describe yourself here so Aiva knows who she is talking to.
Aiva also appends durable preferences via her memory tools.
"""


def ensure_workspace() -> None:
    """Create the workspace skeleton on first run (it is never in git)."""
    os.makedirs(_ws("memory"), exist_ok=True)
    for name, body in (("SOUL.md", DEFAULT_SOUL), ("USER.md", DEFAULT_USER)):
        path = _ws(name)
        if not os.path.exists(path):
            with open(path, "w", encoding="utf-8") as fh:
                fh.write(body)
    for name in ("MEMORY.md", "HISTORY.md"):
        path = _ws("memory", name)
        if not os.path.exists(path):
            open(path, "a", encoding="utf-8").close()


def load_bootstrap() -> str:
    """Persona files, concatenated in priority order."""
    parts = [body for name in BOOTSTRAP_FILES if (body := _read(_ws(name)))]
    return "\n\n".join(parts)


def load_memory() -> str:
    """Long-term memory + the tail of the history log."""
    parts: list[str] = []
    if memory := _read(_ws("memory", "MEMORY.md")):
        parts.append("# Memory\n" + memory)
    if history := _read(_ws("memory", "HISTORY.md")):
        tail = "\n".join(history.splitlines()[-HISTORY_TAIL_LINES:])
        parts.append("# Recent history\n" + tail)
    return "\n\n".join(parts)


def append_history(entry: str) -> None:
    ensure_workspace()
    stamp = datetime.now().strftime("[%Y-%m-%d %H:%M]")
    with open(_ws("memory", "HISTORY.md"), "a", encoding="utf-8") as fh:
        fh.write(f"{stamp} {entry.strip()}\n")


def update_memory(content: str) -> None:
    """Append consolidated facts to long-term memory."""
    ensure_workspace()
    path = _ws("memory", "MEMORY.md")
    prefix = "\n" if os.path.exists(path) and os.path.getsize(path) > 0 else ""
    with open(path, "a", encoding="utf-8") as fh:
        fh.write(prefix + content.strip() + "\n")
