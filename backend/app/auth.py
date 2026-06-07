"""Token authentication helpers.

REST: requires `X-API-Token` header.
WebSocket: requires `?token=` query parameter.
"""
from __future__ import annotations

from fastapi import Header, HTTPException, status

from .config import settings


async def require_token(x_api_token: str | None = Header(default=None)) -> None:
    if x_api_token != settings.API_TOKEN:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="invalid token",
        )


def check_ws_token(token: str | None) -> bool:
    return token == settings.API_TOKEN
