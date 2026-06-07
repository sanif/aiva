"""Application settings loaded from environment / .env via pydantic-settings."""
from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path
from typing import Any, List, Optional

from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

# All user state (persona, memory, database) lives OUTSIDE the backend in
# <repo>/workspace — resolved from this file so any cwd works.
_REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_WORKSPACE = str(_REPO_ROOT / "workspace")


DEFAULT_SERVICES = [
    {"name": "Backend API", "target": "http://127.0.0.1:8420/api/health", "kind": "http"},
    {"name": "Ollama", "target": "127.0.0.1:11434", "kind": "tcp"},
]

# Static agenda used in MOCK_MODE (or when AGENDA_JSON is not provided).
MOCK_AGENDA = [
    {"time": "09:30", "title": "Architecture review", "meta": "Zoom · 45m", "now": True},
    {"time": "11:00", "title": "Backend migration block", "meta": "Deep work · 2h", "now": False},
    {"time": "14:00", "title": "Dentist", "meta": "Downtown · 30m", "now": False},
    {"time": "16:30", "title": "1:1 with the team", "meta": "Office · 30m", "now": False},
]

DEFAULT_SYSTEM_PROMPT = (
    "You are Aiva, a calm, concise local assistant running on the user's Mac. "
    "Keep replies short — they render on a 4-inch phone cover screen."
)


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    HOST: str = "0.0.0.0"
    PORT: int = 8420
    API_TOKEN: str = "change-me-aiva-token"
    PUSH_INTERVAL_SECONDS: float = 3.0
    MOCK_MODE: bool = False
    # CORS_ORIGINS defaults to ["*"]. For production, restrict to your app origin(s).
    CORS_ORIGINS: List[str] = ["*"]
    DB_PATH: str = str(Path(DEFAULT_WORKSPACE) / "aiva.db")

    # Any LiteLLM model string works: ollama/..., openrouter/<vendor>/<model>,
    # openai/<model>, anthropic/<model>, groq/..., etc.
    LLM_MODEL: str = "ollama/llama3.1"
    # Leave unset for hosted providers (OpenRouter/OpenAI/...); set for local
    # servers like Ollama or LM Studio.
    LLM_API_BASE: Optional[str] = "http://localhost:11434"
    LLM_API_KEY: Optional[str] = None
    LLM_SYSTEM_PROMPT: str = DEFAULT_SYSTEM_PROMPT

    # Chat provider:
    #   "litellm" — any model endpoint (Ollama, OpenRouter, OpenAI, ...)
    #   "cli"     — bridge to any local agent CLI (Claude Code, Codex, custom)
    CHAT_PROVIDER: str = "litellm"
    # Command templates for the cli provider (JSON lists). Placeholders:
    # {message} = the user's text, {system} = LLM_SYSTEM_PROMPT.
    CHAT_CLI_CMD: List[str] = []
    CHAT_CLI_CONTINUE_CMD: Optional[List[str]] = None
    CHAT_CLI_TIMEOUT: float = 120.0

    # JSON list of services to health-check.
    SERVICES_JSON: List[dict] = DEFAULT_SERVICES
    # Optional JSON list of agenda items used when MOCK_MODE is false.
    AGENDA_JSON: Optional[List[dict]] = None

    GREETING_NAME: str = "Alex"
    STORE_CHAT_HISTORY: bool = True

    # OpenClaw-style agent workspace: SOUL.md / USER.md persona bootstrap,
    # memory/MEMORY.md (long-term) + memory/HISTORY.md (append log).
    # Defaults to <repo>/workspace, OUTSIDE the backend code.
    WORKSPACE_PATH: str = DEFAULT_WORKSPACE


    @field_validator("CORS_ORIGINS", mode="before")
    @classmethod
    def _parse_cors(cls, v: Any) -> Any:
        if isinstance(v, str):
            v = v.strip()
            if v.startswith("["):
                return json.loads(v)
            return [o.strip() for o in v.split(",") if o.strip()]
        return v

    @field_validator(
        "SERVICES_JSON", "AGENDA_JSON", "CHAT_CLI_CMD", "CHAT_CLI_CONTINUE_CMD",
        mode="before",
    )
    @classmethod
    def _parse_json_list(cls, v: Any) -> Any:
        if isinstance(v, str):
            v = v.strip()
            if not v:
                return None
            return json.loads(v)
        return v

    @property
    def db_url(self) -> str:
        return f"sqlite+aiosqlite:///{self.DB_PATH}"

    @property
    def agenda(self) -> List[dict]:
        if self.MOCK_MODE:
            return MOCK_AGENDA
        if self.AGENDA_JSON:
            return self.AGENDA_JSON
        return []


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
