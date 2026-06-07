"""Persona + memory (OpenClaw/nanobot pattern): workspace bootstrap files
feed the system prompt; live API state does NOT — the model uses tools."""
from __future__ import annotations

import os

from app.config import settings
from app.services import memory_service
from app.services.llm_service import build_system_prompt


async def test_prompt_carries_persona_and_memory():
    prompt = build_system_prompt()
    assert "Persona-marker-soul" in prompt        # SOUL.md
    assert "Persona-marker-user" in prompt        # USER.md
    assert "Memory-marker-longterm" in prompt     # memory/MEMORY.md
    assert "History-marker-entry" in prompt       # memory/HISTORY.md tail


async def test_prompt_has_no_live_state_preload():
    prompt = build_system_prompt()
    for forbidden in ("CPU ", "RAM ", "SERVICES ·", "ALERTS ·", "Live state"):
        assert forbidden not in prompt
    # instead it tells the model tools exist
    assert "tool" in prompt.lower()


async def test_save_memory_appends_history_and_rewrites_memory():
    memory_service.append_history("user asked about backups")
    hist = open(os.path.join(settings.WORKSPACE_PATH, "memory", "HISTORY.md")).read()
    assert "user asked about backups" in hist
    assert hist.strip().splitlines()[-1].startswith("[")  # timestamped

    memory_service.update_memory("- New consolidated fact.")
    mem = open(os.path.join(settings.WORKSPACE_PATH, "memory", "MEMORY.md")).read()
    assert "New consolidated fact" in mem
    # history survives a memory rewrite
    assert "user asked about backups" in open(
        os.path.join(settings.WORKSPACE_PATH, "memory", "HISTORY.md")
    ).read()
