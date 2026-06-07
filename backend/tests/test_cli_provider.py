"""Generic CLI session provider tests.

The agent-channels principle pointed at ANY local agent CLI: the chat is
bridged to a configurable command template — nothing is hardcoded to one
tool. `{message}` and `{system}` placeholders are substituted per call;
an optional continue-template keeps session continuity (e.g. Claude Code's
`--continue`), with a fresh retry when continuing fails.
"""
from __future__ import annotations

import pytest

from app.services.cli_service import CliSession

CMD = ["mybot", "-p", "{message}", "--sys", "{system}"]
CONT = ["mybot", "-p", "{message}", "--resume-last", "--sys", "{system}"]


class FakeRunner:
    def __init__(self, results: list[tuple[int, str]]):
        self.results = results
        self.commands: list[list[str]] = []

    async def __call__(self, cmd: list[str], timeout: float) -> tuple[int, str]:
        self.commands.append(cmd)
        return self.results.pop(0)


def make(runner, cmd=CMD, cont=CONT) -> CliSession:
    return CliSession(
        cmd_template=cmd,
        continue_template=cont,
        system_prompt="You are Aiva.",
        timeout=5,
        runner=runner,
    )


async def test_placeholders_are_substituted():
    runner = FakeRunner([(0, "hello back")])
    s = make(runner)

    reply = await s.ask("hello")
    assert reply == "hello back"
    assert runner.commands[0] == ["mybot", "-p", "hello", "--sys", "You are Aiva."]


async def test_followups_use_continue_template():
    runner = FakeRunner([(0, "first"), (0, "second")])
    s = make(runner)

    await s.ask("one")
    await s.ask("two")
    assert "--resume-last" not in runner.commands[0]
    assert "--resume-last" in runner.commands[1]


async def test_without_continue_template_every_call_is_fresh():
    runner = FakeRunner([(0, "a"), (0, "b")])
    s = make(runner, cont=None)

    await s.ask("one")
    await s.ask("two")
    assert runner.commands[0][:2] == runner.commands[1][:2]
    assert all("--resume-last" not in c for c in runner.commands)


async def test_failed_continue_retries_fresh():
    runner = FakeRunner([(0, "first"), (1, "no session"), (0, "recovered")])
    s = make(runner)

    await s.ask("one")
    reply = await s.ask("two")
    assert reply == "recovered"
    assert "--resume-last" in runner.commands[1]
    assert "--resume-last" not in runner.commands[2]


async def test_error_yields_friendly_message():
    runner = FakeRunner([(1, "command not found")])
    s = make(runner)
    reply = await s.ask("hello")
    assert reply.startswith("⚠")
    assert "command not found" in reply


async def test_timeout_yields_friendly_message():
    async def exploding(cmd, timeout):
        import asyncio

        raise asyncio.TimeoutError()

    s = make(exploding)
    reply = await s.ask("hello")
    assert "timed out" in reply.lower()


async def test_empty_template_is_rejected():
    s = CliSession(cmd_template=[], continue_template=None, system_prompt="", timeout=5,
                   runner=FakeRunner([]))
    reply = await s.ask("hello")
    assert reply.startswith("⚠")
    assert "CHAT_CLI_CMD" in reply


async def test_llm_service_routes_to_cli_provider(monkeypatch):
    from app.config import settings
    from app.services import cli_service, llm_service

    runner = FakeRunner([(0, "routed through the cli")])
    monkeypatch.setattr(settings, "CHAT_PROVIDER", "cli")
    monkeypatch.setattr(settings, "MOCK_MODE", False)
    monkeypatch.setattr(cli_service, "_session", make(runner))

    reply = await llm_service.complete("ping")
    assert reply == "routed through the cli"
    assert runner.commands


async def test_mock_mode_wins_over_provider(monkeypatch):
    from app.config import settings
    from app.services import llm_service

    monkeypatch.setattr(settings, "CHAT_PROVIDER", "cli")
    monkeypatch.setattr(settings, "MOCK_MODE", True)

    reply = await llm_service.complete("any urgent alerts?")
    assert "alert" in reply.lower()
