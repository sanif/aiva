"""Pydantic v2 schemas for all API payloads."""
from __future__ import annotations

from datetime import datetime
from typing import List, Literal, Optional

from pydantic import BaseModel, ConfigDict, Field

Status = Literal["today", "upcoming", "done"]
Priority = Literal["high", "med", "low"]
Category = Literal["work", "personal", "system"]
NoteKind = Literal["text", "voice"]


# ---------- Health ----------
class Health(BaseModel):
    status: str = "ok"
    version: str = "0.3.0"
    uptime_s: float
    mock: bool


# ---------- Metrics ----------
class Metrics(BaseModel):
    cpu_pct: float
    ram_pct: float
    disk_pct: float
    temp_c: Optional[float] = None
    net_up_mbps: float
    net_down_mbps: float
    uptime_s: float
    battery_pct: Optional[float] = None
    power_plugged: Optional[bool] = None
    cpu_history: List[float] = Field(default_factory=list)
    net_history: List[float] = Field(default_factory=list)


# ---------- Services ----------
class Service(BaseModel):
    name: str
    target: str
    status: Literal["up", "warn", "down"]
    latency_ms: Optional[float] = None


# ---------- Docker ----------
class DockerContainer(BaseModel):
    name: str
    status: Literal["up", "down"]
    raw_status: str


class DockerInfo(BaseModel):
    available: bool
    running: int
    total: int
    containers: List[DockerContainer] = Field(default_factory=list)


# ---------- Alerts ----------
class Alert(BaseModel):
    level: Literal["warn", "err"]
    title: str
    meta: str
    ts: str


# ---------- Tasks ----------
class TaskOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    title: str
    description: Optional[str] = None
    status: Status
    priority: Priority
    category: Category
    due: Optional[str] = None
    project: Optional[str] = None
    notes: Optional[str] = None
    tags: Optional[str] = None
    parent_id: Optional[int] = None
    completed_at: Optional[datetime] = None
    created_at: datetime
    updated_at: datetime


class TaskCreate(BaseModel):
    title: str
    description: Optional[str] = None
    status: Status = "today"
    priority: Priority = "med"
    category: Category = "work"
    due: Optional[str] = None
    project: Optional[str] = None
    notes: Optional[str] = None
    tags: Optional[str] = None
    parent_id: Optional[int] = None


class TaskUpdate(BaseModel):
    title: Optional[str] = None
    description: Optional[str] = None
    status: Optional[Status] = None
    priority: Optional[Priority] = None
    category: Optional[Category] = None
    due: Optional[str] = None
    project: Optional[str] = None
    notes: Optional[str] = None
    tags: Optional[str] = None
    parent_id: Optional[int] = None


class ProjectOut(BaseModel):
    name: str
    total: int
    done: int


# ---------- Notes ----------
class NoteOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    title: Optional[str] = None
    body: str
    kind: NoteKind
    created_at: datetime


class NoteCreate(BaseModel):
    body: str
    title: Optional[str] = None
    kind: NoteKind = "text"


# ---------- Chat ----------
class ChatRequest(BaseModel):
    message: str


class ChatResponse(BaseModel):
    reply: str


# ---------- Actions ----------
class ActionInfo(BaseModel):
    id: str
    label: str
    description: str


class ActionResult(BaseModel):
    ok: bool
    message: str
    # set for open_url actions so clients can launch the link
    url: Optional[str] = None


# ---------- Chat history ----------
class ChatHistoryEntry(BaseModel):
    role: str  # "user" | "assistant"
    text: str
    ts: str
    source: str = "app"  # "app" | "telegram"


# ---------- Dashboard ----------
class AgendaItem(BaseModel):
    time: str
    title: str
    meta: str
    now: bool = False


class TasksSummary(BaseModel):
    today: int
    done: int
    upcoming: int


class Dashboard(BaseModel):
    greeting_name: str
    ai_status: Literal["idle", "thinking", "alert"]
    metrics: Metrics
    services: List[Service]
    docker: DockerInfo
    alerts: List[Alert]
    tasks_summary: TasksSummary
    top_tasks: List[TaskOut]
    agenda: List[AgendaItem]
