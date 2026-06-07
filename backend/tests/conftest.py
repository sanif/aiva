"""Test fixtures. Environment is pinned BEFORE app modules import settings."""
from __future__ import annotations

import os
import tempfile

_TMP = tempfile.mkdtemp(prefix="aiva-test-")
_TMP_DB = os.path.join(_TMP, "test.db")
_WORKSPACE = os.path.join(_TMP, "workspace")

# OpenClaw-style workspace: persona bootstrap + memory files
os.makedirs(os.path.join(_WORKSPACE, "memory"), exist_ok=True)
with open(os.path.join(_WORKSPACE, "SOUL.md"), "w") as fh:
    fh.write("# Soul\nYou are Aiva, the calm cover-screen assistant. Persona-marker-soul.")
with open(os.path.join(_WORKSPACE, "USER.md"), "w") as fh:
    fh.write("# User\nThe user. Persona-marker-user.")
with open(os.path.join(_WORKSPACE, "memory", "MEMORY.md"), "w") as fh:
    fh.write("- The user prefers violet accents. Memory-marker-longterm.")
with open(os.path.join(_WORKSPACE, "memory", "HISTORY.md"), "w") as fh:
    fh.write("[2026-06-06 09:00] History-marker-entry.\n")

os.environ.update(
    {
        "MOCK_MODE": "true",
        "API_TOKEN": "test-token",
        "DB_PATH": _TMP_DB,
        "WORKSPACE_PATH": _WORKSPACE,
        "STORE_CHAT_HISTORY": "true",
        "PUSH_INTERVAL_SECONDS": "3600",  # never ticks during tests
        "SERVICES_JSON": "[]",  # no real network health checks
    }
)

import httpx  # noqa: E402
import pytest  # noqa: E402

from app.database import init_db  # noqa: E402
from app.main import app  # noqa: E402

AUTH = {"X-API-Token": "test-token"}


@pytest.fixture()
async def client():
    await init_db()
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as c:
        yield c
