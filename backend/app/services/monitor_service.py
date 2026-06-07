"""System metrics via psutil + rolling history buffers + service health checks."""
from __future__ import annotations

import asyncio
import random
import socket
import time
from collections import deque
from typing import Deque, List, Optional

import httpx
import psutil

from ..config import settings
from ..models.schemas import Metrics, Service

HISTORY_LEN = 34

_boot_time = psutil.boot_time()


class MonitorService:
    """Holds rolling history and computes network throughput from deltas.

    The background dashboard loop calls `sample()` on each tick so net Mbps
    is derived from real counter deltas. REST callers use `get_metrics()`
    which performs a single fresh sample without disturbing the loop state.
    """

    def __init__(self) -> None:
        self.cpu_history: Deque[float] = deque(maxlen=HISTORY_LEN)
        self.net_history: Deque[float] = deque(maxlen=HISTORY_LEN)
        self._last_net = psutil.net_io_counters()
        self._last_net_ts = time.monotonic()
        self._mock_temp = 52.0
        self._lock = asyncio.Lock()

    # ---- helpers ----
    @staticmethod
    def _uptime_s() -> float:
        return max(0.0, time.time() - _boot_time)

    def _read_temp(self) -> Optional[float]:
        if settings.MOCK_MODE:
            self._mock_temp += random.uniform(-3, 3)
            self._mock_temp = max(40.0, min(67.0, self._mock_temp))
            return round(self._mock_temp, 1)
        try:
            temps = psutil.sensors_temperatures()  # type: ignore[attr-defined]
        except (AttributeError, NotImplementedError):
            return None
        if not temps:
            return None
        for entries in temps.values():
            for entry in entries:
                if entry.current:
                    return round(float(entry.current), 1)
        return None

    def _net_mbps(self) -> tuple[float, float]:
        """Compute up/down Mbps from counter delta since last call."""
        now = time.monotonic()
        counters = psutil.net_io_counters()
        dt = max(1e-6, now - self._last_net_ts)
        up_bytes = max(0, counters.bytes_sent - self._last_net.bytes_sent)
        down_bytes = max(0, counters.bytes_recv - self._last_net.bytes_recv)
        self._last_net = counters
        self._last_net_ts = now
        up_mbps = (up_bytes * 8) / 1e6 / dt
        down_mbps = (down_bytes * 8) / 1e6 / dt
        return round(up_mbps, 3), round(down_mbps, 3)

    def _battery(self) -> tuple[Optional[float], Optional[bool]]:
        try:
            batt = psutil.sensors_battery()  # type: ignore[attr-defined]
        except (AttributeError, NotImplementedError):
            return None, None
        if batt is None:
            return None, None
        return round(float(batt.percent), 1), bool(batt.power_plugged)

    def _build(self, up: float, down: float) -> Metrics:
        cpu = psutil.cpu_percent(interval=None)
        ram = psutil.virtual_memory().percent
        disk = psutil.disk_usage("/").percent
        if settings.MOCK_MODE and cpu == 0.0:
            cpu = round(random.uniform(8, 45), 1)
        self.cpu_history.append(round(float(cpu), 1))
        self.net_history.append(round(float(down), 3))
        batt_pct, plugged = self._battery()
        if settings.MOCK_MODE and batt_pct is None:
            batt_pct, plugged = round(random.uniform(55, 95), 1), True
        return Metrics(
            cpu_pct=round(float(cpu), 1),
            ram_pct=round(float(ram), 1),
            disk_pct=round(float(disk), 1),
            temp_c=self._read_temp(),
            net_up_mbps=up,
            net_down_mbps=down,
            uptime_s=round(self._uptime_s(), 1),
            battery_pct=batt_pct,
            power_plugged=plugged,
            cpu_history=list(self.cpu_history),
            net_history=list(self.net_history),
        )

    async def sample(self) -> Metrics:
        """Called by the background loop each tick (feeds history buffers)."""
        async with self._lock:
            up, down = self._net_mbps()
            return self._build(up, down)

    async def get_metrics(self) -> Metrics:
        """Fresh sample for a REST call (also updates the shared history)."""
        return await self.sample()


# ---- service health checks ----
async def _check_http(name: str, target: str) -> Service:
    start = time.monotonic()
    try:
        async with httpx.AsyncClient(timeout=2.0) as client:
            resp = await client.get(target)
        latency = round((time.monotonic() - start) * 1000, 1)
        if 200 <= resp.status_code < 300:
            status = "up"
        else:
            status = "warn"
        return Service(name=name, target=target, status=status, latency_ms=latency)
    except Exception:
        return Service(name=name, target=target, status="down", latency_ms=None)


async def _check_tcp(name: str, target: str) -> Service:
    host, _, port_s = target.partition(":")
    try:
        port = int(port_s)
    except ValueError:
        return Service(name=name, target=target, status="down", latency_ms=None)

    start = time.monotonic()

    def _connect() -> None:
        with socket.create_connection((host, port), timeout=1.5):
            pass

    try:
        await asyncio.wait_for(asyncio.to_thread(_connect), timeout=1.6)
        latency = round((time.monotonic() - start) * 1000, 1)
        return Service(name=name, target=target, status="up", latency_ms=latency)
    except Exception:
        return Service(name=name, target=target, status="down", latency_ms=None)


async def check_services() -> List[Service]:
    tasks = []
    for svc in settings.SERVICES_JSON:
        name = svc.get("name", "?")
        target = svc.get("target", "")
        kind = svc.get("kind", "http")
        if kind == "tcp":
            tasks.append(_check_tcp(name, target))
        else:
            tasks.append(_check_http(name, target))
    if not tasks:
        return []
    return list(await asyncio.gather(*tasks))


monitor = MonitorService()
