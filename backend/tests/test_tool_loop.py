"""The litellm provider must run an agentic tool loop — call tools, feed
results back, and only then answer. No state is preloaded."""
from __future__ import annotations

import json
import sys
import types

import pytest

from app.config import settings
from app.services import llm_service


class FakeFn:
    def __init__(self, name, arguments):
        self.name = name
        self.arguments = arguments


class FakeToolCall:
    def __init__(self, id, name, arguments):
        self.id = id
        self.function = FakeFn(name, arguments)
        self.type = "function"


class FakeMsg:
    def __init__(self, content=None, tool_calls=None):
        self.content = content
        self.tool_calls = tool_calls

    def model_dump(self):
        return {
            "role": "assistant",
            "content": self.content,
            "tool_calls": [
                {"id": t.id, "type": "function",
                 "function": {"name": t.function.name, "arguments": t.function.arguments}}
                for t in (self.tool_calls or [])
            ] or None,
        }


def fake_litellm(script):
    """A litellm stand-in returning scripted responses and recording calls."""
    mod = types.ModuleType("litellm")
    mod.calls = []

    async def acompletion(**kwargs):
        mod.calls.append(kwargs)
        msg = script.pop(0)
        choice = types.SimpleNamespace(message=msg)
        return types.SimpleNamespace(choices=[choice])

    mod.acompletion = acompletion
    return mod


@pytest.fixture
def live(monkeypatch):
    monkeypatch.setattr(settings, "MOCK_MODE", False)
    monkeypatch.setattr(settings, "CHAT_PROVIDER", "litellm")
    yield monkeypatch


async def test_tools_are_offered_and_results_fed_back(live):
    from app.database import init_db

    await init_db()
    script = [
        FakeMsg(tool_calls=[FakeToolCall("c1", "list_tasks", json.dumps({"status": "today"}))]),
        FakeMsg(content="You have tasks today."),
    ]
    fake = fake_litellm(script)
    live.setitem(sys.modules, "litellm", fake)

    reply = await llm_service.complete("what's on today?")
    assert reply == "You have tasks today."

    first, second = fake.calls
    # tools offered on every round
    names = [t["function"]["name"] for t in first["tools"]]
    assert "list_tasks" in names and "get_system_status" in names
    # second round carries the tool result message
    roles = [m["role"] for m in second["messages"]]
    assert "tool" in roles
    tool_msg = next(m for m in second["messages"] if m["role"] == "tool")
    assert tool_msg["tool_call_id"] == "c1"
    json.loads(tool_msg["content"])  # valid JSON payload


async def test_plain_answer_needs_one_round(live):
    fake = fake_litellm([FakeMsg(content="hi there")])
    live.setitem(sys.modules, "litellm", fake)
    assert await llm_service.complete("hello") == "hi there"
    assert len(fake.calls) == 1


async def test_loop_is_bounded(live):
    # a model that calls tools forever must still terminate
    endless = [
        FakeMsg(tool_calls=[FakeToolCall(f"c{i}", "get_agenda", "{}")])
        for i in range(20)
    ]
    fake = fake_litellm(endless)
    live.setitem(sys.modules, "litellm", fake)
    reply = await llm_service.complete("loop forever")
    assert isinstance(reply, str) and reply  # graceful, no crash
    assert len(fake.calls) <= 8
