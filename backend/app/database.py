"""Async SQLAlchemy engine/session and DB initialisation + seeding."""
from __future__ import annotations

from collections.abc import AsyncGenerator

from sqlalchemy import select
from sqlalchemy.ext.asyncio import (
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)

from .config import settings
from .models.db_models import Base, Task

engine = create_async_engine(settings.db_url, echo=False, future=True)
AsyncSessionLocal = async_sessionmaker(engine, expire_on_commit=False, class_=AsyncSession)


SEED_TASKS = [
    {"title": "Review Q3 architecture doc", "category": "work", "priority": "high", "due": "09:30", "status": "today"},
    {"title": "Migrate backend to FastAPI", "category": "system", "priority": "high", "due": "11:00", "status": "today"},
    {"title": "Call dentist", "category": "personal", "priority": "med", "due": "14:00", "status": "today"},
    {"title": "Push Aiva v0.3 to TestFlight", "category": "work", "priority": "med", "due": "Tomorrow", "status": "upcoming"},
    {"title": "Renew domain aiva.local", "category": "system", "priority": "low", "due": "Jun 9", "status": "upcoming"},
    {"title": "Set up nightly DB backup", "category": "system", "priority": "med", "due": "Yesterday", "status": "done"},
]


async def init_db() -> None:
    """Create tables and seed initial tasks on an empty DB."""
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
        # lightweight migrations for DBs created before newer columns
        from sqlalchemy import text

        for ddl in (
            "ALTER TABLE chat_messages ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'app'",
            "ALTER TABLE tasks ADD COLUMN project VARCHAR(64)",
            "ALTER TABLE tasks ADD COLUMN notes TEXT",
            "ALTER TABLE tasks ADD COLUMN tags VARCHAR(256)",
            "ALTER TABLE tasks ADD COLUMN parent_id INTEGER",
            "ALTER TABLE tasks ADD COLUMN completed_at TIMESTAMP",
        ):
            try:
                await conn.execute(text(ddl))
            except Exception:
                pass  # column already exists

    async with AsyncSessionLocal() as session:
        existing = await session.scalar(select(Task).limit(1))
        if existing is None:
            for data in SEED_TASKS:
                session.add(Task(**data))
            await session.commit()


async def get_session() -> AsyncGenerator[AsyncSession, None]:
    async with AsyncSessionLocal() as session:
        yield session
