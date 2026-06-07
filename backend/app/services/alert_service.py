"""Derive alerts from metric thresholds, service health, and docker state."""
from __future__ import annotations

from datetime import datetime
from typing import List

from ..models.schemas import Alert, DockerInfo, Metrics, Service


def _now_hm() -> str:
    return datetime.now().strftime("%H:%M")


def derive_alerts(
    metrics: Metrics,
    services: List[Service],
    docker: DockerInfo,
) -> List[Alert]:
    alerts: List[Alert] = []
    ts = _now_hm()

    # disk > 90 -> warn
    if metrics.disk_pct > 90:
        alerts.append(
            Alert(level="warn", title="Disk almost full",
                  meta=f"{metrics.disk_pct:.0f}% used", ts=ts)
        )

    # temp > 75 -> warn
    if metrics.temp_c is not None and metrics.temp_c > 75:
        alerts.append(
            Alert(level="warn", title="High temperature",
                  meta=f"{metrics.temp_c:.0f}°C", ts=ts)
        )

    # service down -> err
    for svc in services:
        if svc.status == "down":
            alerts.append(
                Alert(level="err", title=f"{svc.name} is down",
                      meta=svc.target, ts=ts)
            )

    # docker exited containers -> err
    if docker.available:
        for c in docker.containers:
            if c.status == "down":
                alerts.append(
                    Alert(level="err", title=f"Container {c.name} exited",
                          meta=c.raw_status or "exited", ts=ts)
                )

    return alerts


def ai_status_from_alerts(alerts: List[Alert]) -> str:
    return "alert" if any(a.level == "err" for a in alerts) else "idle"
