"""Provider config tests — any model via LiteLLM (Ollama, OpenRouter, ...)."""
from __future__ import annotations

from app.config import settings
from app.services.llm_service import _llm_kwargs


async def test_api_base_omitted_when_unset(monkeypatch):
    # OpenRouter & friends must NOT inherit a localhost Ollama base URL.
    monkeypatch.setattr(settings, "LLM_MODEL", "openrouter/anthropic/claude-sonnet-4-6")
    monkeypatch.setattr(settings, "LLM_API_BASE", None)
    monkeypatch.setattr(settings, "LLM_API_KEY", "sk-or-xxx")

    kwargs = _llm_kwargs()
    assert kwargs["model"] == "openrouter/anthropic/claude-sonnet-4-6"
    assert "api_base" not in kwargs
    assert kwargs["api_key"] == "sk-or-xxx"


async def test_api_base_passed_for_local_providers(monkeypatch):
    monkeypatch.setattr(settings, "LLM_MODEL", "ollama/llama3.1")
    monkeypatch.setattr(settings, "LLM_API_BASE", "http://localhost:11434")
    monkeypatch.setattr(settings, "LLM_API_KEY", None)

    kwargs = _llm_kwargs()
    assert kwargs["api_base"] == "http://localhost:11434"
    assert "api_key" not in kwargs
