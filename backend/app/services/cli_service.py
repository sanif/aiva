"""Generic CLI session chat provider — nothing hardcoded to one tool.

The agent-channels principle pointed at ANY local agent CLI: Aiva chat is
bridged to a configurable command template. `{message}` and `{system}`
placeholders are substituted per call; an optional continue-template keeps
conversation continuity, with a fresh retry when continuing fails.

Example recipes (CHAT_CLI_CMD / CHAT_CLI_CONTINUE_CMD as JSON lists):
  Claude Code : ["claude","-p","{message}","--append-system-prompt","{system}"]
                ["claude","-p","{message}","--continue","--append-system-prompt","{system}"]
  Codex CLI   : ["codex","exec","{message}"]
  Anything    : ["./my-agent.sh","{message}"]

The runner is injectable so the bridge is fully testable without spawning
real processes.
"""
from __future__ import annotations

import asyncio
from typing import Awaitable, Callable, List, Optional

Runner = Callable[[List[str], float], Awaitable[tuple]]


async def _subprocess_runner(cmd: List[str], timeout: float) -> tuple:
    proc = await asyncio.create_subprocess_exec(
        *cmd,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.STDOUT,
    )
    stdout, _ = await asyncio.wait_for(proc.communicate(), timeout=timeout)
    return proc.returncode or 0, stdout.decode("utf-8", "replace").strip()


class CliSession:
    def __init__(
        self,
        cmd_template: List[str],
        continue_template: Optional[List[str]],
        system_prompt: str = "",
        timeout: float = 120.0,
        runner: Optional[Runner] = None,
    ) -> None:
        self._cmd_template = list(cmd_template or [])
        self._continue_template = list(continue_template) if continue_template else None
        self._system_prompt = system_prompt
        self._timeout = timeout
        self._runner: Runner = runner or _subprocess_runner
        self._started = False

    def _render(self, template: List[str], message: str, system: Optional[str] = None) -> List[str]:
        sys_text = system if system is not None else self._system_prompt
        return [
            arg.replace("{message}", message).replace("{system}", sys_text)
            for arg in template
        ]

    async def ask(self, message: str, system: Optional[str] = None) -> str:
        """One exchange; falls back to a fresh session if continuing fails."""
        if not self._cmd_template:
            return "⚠ CLI provider not configured — set CHAT_CLI_CMD in .env."

        use_continue = self._started and self._continue_template is not None
        template = self._continue_template if use_continue else self._cmd_template
        try:
            rc, out = await self._runner(self._render(template, message, system), self._timeout)
            if rc != 0 and use_continue:
                # session store may be gone — retry fresh
                rc, out = await self._runner(self._render(self._cmd_template, message, system), self._timeout)
            if rc != 0:
                return f"⚠ CLI bridge failed: {out[:300] or 'unknown error'}"
            self._started = True
            return out
        except asyncio.TimeoutError:
            return "⚠ CLI bridge timed out — the agent may be busy. Try again."
        except FileNotFoundError:
            return f"⚠ CLI bridge failed: '{self._cmd_template[0]}' not found on PATH."
        except Exception as exc:  # never break the chat surface
            return f"⚠ CLI bridge failed: {exc}"


# module-level session (continuity across requests)
_session: Optional[CliSession] = None


def get_session() -> CliSession:
    global _session
    if _session is None:
        from ..config import settings

        _session = CliSession(
            cmd_template=settings.CHAT_CLI_CMD,
            continue_template=settings.CHAT_CLI_CONTINUE_CMD,
            system_prompt=settings.LLM_SYSTEM_PROMPT,
            timeout=settings.CHAT_CLI_TIMEOUT,
        )
    return _session


async def complete(message: str, system: Optional[str] = None) -> str:
    return await get_session().ask(message, system=system)
