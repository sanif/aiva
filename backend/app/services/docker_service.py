"""Docker container info via `docker ps` subprocess, cached, graceful fallback.

No docker SDK dependency. If docker is not installed or the daemon is not
running, returns available=False without raising.
"""
from __future__ import annotations

import asyncio
import json
import time

from ..models.schemas import DockerContainer, DockerInfo

_CACHE_TTL = 5.0
_cache: tuple[float, DockerInfo] | None = None


def _classify(state: str) -> str:
    s = state.lower()
    if s.startswith("up") or "running" in s:
        return "up"
    return "down"


async def _run_docker_ps() -> DockerInfo:
    try:
        proc = await asyncio.create_subprocess_exec(
            "docker",
            "ps",
            "-a",
            "--format",
            "{{json .}}",
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
    except FileNotFoundError:
        return DockerInfo(available=False, running=0, total=0, containers=[])

    try:
        stdout, _ = await asyncio.wait_for(proc.communicate(), timeout=5.0)
    except asyncio.TimeoutError:
        try:
            proc.kill()
        except ProcessLookupError:
            pass
        return DockerInfo(available=False, running=0, total=0, containers=[])

    if proc.returncode != 0:
        # docker present but daemon down / permission error
        return DockerInfo(available=False, running=0, total=0, containers=[])

    containers: list[DockerContainer] = []
    running = 0
    for line in stdout.decode("utf-8", "replace").splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            obj = json.loads(line)
        except json.JSONDecodeError:
            continue
        raw_status = obj.get("Status", "") or obj.get("State", "")
        state = obj.get("State", "") or raw_status
        status = _classify(state if state else raw_status)
        if status == "up":
            running += 1
        containers.append(
            DockerContainer(
                name=obj.get("Names", obj.get("Name", "?")),
                status=status,
                raw_status=raw_status,
            )
        )
    return DockerInfo(
        available=True,
        running=running,
        total=len(containers),
        containers=containers,
    )


async def get_docker_info() -> DockerInfo:
    global _cache
    now = time.monotonic()
    if _cache is not None and (now - _cache[0]) < _CACHE_TTL:
        return _cache[1]
    info = await _run_docker_ps()
    _cache = (now, info)
    return info
