"""Metrics, services and docker endpoints."""
from __future__ import annotations

from typing import List

from fastapi import APIRouter, Depends

from ..auth import require_token
from ..models.schemas import DockerInfo, Metrics, Service
from ..services.docker_service import get_docker_info
from ..services.monitor_service import check_services, monitor

router = APIRouter(prefix="/api", tags=["metrics"], dependencies=[Depends(require_token)])


@router.get("/metrics", response_model=Metrics)
async def get_metrics() -> Metrics:
    return await monitor.get_metrics()


@router.get("/services", response_model=List[Service])
async def get_services() -> List[Service]:
    return await check_services()


@router.get("/docker", response_model=DockerInfo)
async def get_docker() -> DockerInfo:
    return await get_docker_info()
