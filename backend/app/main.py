"""FastAPI application factory for Aiva."""
from __future__ import annotations

import asyncio
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .config import settings
from .database import init_db
from .routers import (
    actions as actions_router,
    chat as chat_router,
    dashboard as dashboard_router,
    metrics as metrics_router,
    notes as notes_router,
    schedules as schedules_router,
    tasks as tasks_router,
    websocket as ws_router,
)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: ensure the user workspace, init DB, start background loopers.
    from .services import memory_service

    memory_service.ensure_workspace()
    await init_db()
    stop_event = asyncio.Event()
    from .services import scheduler_service

    tasks = [
        asyncio.create_task(ws_router.dashboard_broadcaster(stop_event)),
        asyncio.create_task(scheduler_service.scheduler_loop(stop_event)),
    ]


    app.state.broadcaster_stop = stop_event
    app.state.background_tasks = tasks
    try:
        yield
    finally:
        # Shutdown: stop all loopers cleanly.
        stop_event.set()
        for task in tasks:
            task.cancel()
            try:
                await task
            except (asyncio.CancelledError, Exception):
                pass


def create_app() -> FastAPI:
    app = FastAPI(
        title="Aiva Backend",
        version="0.3.0",
        description="Local personal-assistant dashboard backend.",
        lifespan=lifespan,
    )

    # Minimal web dashboard (static, no secrets — token entered client-side).
    @app.get("/", include_in_schema=False)
    async def web_dashboard():
        from fastapi.responses import FileResponse

        page = os.path.join(os.path.dirname(__file__), "static", "dashboard.html")
        return FileResponse(page, media_type="text/html")

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.CORS_ORIGINS,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    app.include_router(dashboard_router.router)
    app.include_router(metrics_router.router)
    app.include_router(tasks_router.router)
    app.include_router(tasks_router.projects_router)
    app.include_router(schedules_router.router)
    app.include_router(notes_router.router)
    app.include_router(chat_router.router)
    app.include_router(actions_router.router)
    app.include_router(actions_router.tools_router)
    app.include_router(ws_router.router)

    return app


app = create_app()
