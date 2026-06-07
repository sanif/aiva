# CLAUDE.md

Working notes for agents on this repo. Read this before changing anything.

## What this is

A cover-screen assistant for the Razr 60 Ultra. `backend/` is FastAPI,
`android/` is Kotlin + Compose (package `com.aiva.console`), `workspace/` is
user data created at runtime and never tracked.

## Process rules

- TDD. Write failing tests first, then implement. Backend: `pytest` from
  `backend/` (in its `.venv`). Android: `./gradlew :app:testDebugUnitTest`.
- Build Android with JDK 17: `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`.
- Normal commits only. Never rewrite published history. Never push the local
  `private-history` branch if it exists.
- No personal info in tracked files: names, emails, real LAN IPs, tokens.
  Demo data uses "Alex" and `192.168.x.x`. User data belongs in `workspace/`.

## Architecture decisions (and why)

**Chat is tools, not preload.** The system prompt carries only persona +
memory from `workspace/` (SOUL.md, USER.md, memory/). Live state is fetched
on demand via the tool registry in `app/services/tools_service.py`. That file
is the single source of truth for agent capabilities; `mcp_server.py` mirrors
it 1:1 for CLI sessions. Add a tool in both places or not at all.

**Two chat providers** (`CHAT_PROVIDER` in `.env`):
- `litellm`: any model, OpenAI function calling, bounded loop (6 rounds) in
  `llm_service._tool_loop`. Leave `LLM_API_BASE` unset for hosted providers.
- `cli`: command templates (`{message}`, `{system}`) spawn a local agent CLI.
  Headless sessions cannot answer permission prompts, so the recipe must
  pre-approve tools: `--allowedTools mcp__aiva,WebFetch,WebSearch`. Bash stays
  blocked by design.

**Self-learning memory** (`recall_service.py`, credit ruvnet/ruflo): every
chat exchange auto-stores as an episode (the hook lives in the chat router,
WS handler, and scheduler — keep them in sync). Recall ranks by
`token-overlap x recency-decay x score`. Behavioral scores upgrade gradually
(x1.15) and downgrade instantly (x0.5); the app's approval cards feed this
via `POST /api/chat/feedback`. Don't replace the markdown memory layer; the
two are complementary (identity vs episodes).

**Scheduler** (`scheduler_service.py`): jobs run *through the agent*
(`llm_service.complete`), not as shell commands. The looper lives in the
FastAPI lifespan next to the dashboard broadcaster, ticks every 30s, and
dedupes by minute. One-shots (`YYYY-MM-DDTHH:MM`) auto-disable. New run
results ride on WS snapshots as `last_schedule`; the app diffs that for
notifications.

**Security is structural, not advisory.** Token auth on everything but
`/api/health`; WS rejects with close code 4401. Actions are ids from
`actions.yaml` only — never add a code path that executes arbitrary input,
including from the model.

**Persistence**: SQLite via async SQLAlchemy. No Alembic — migrations are
idempotent `ALTER TABLE` try/excepts in `database.init_db`. Add new columns
there. `run.sh` must never `source .env` (values contain spaces/apostrophes);
pydantic-settings parses it.

## Android decisions

- One `AppViewModel`; repository pattern with `MockRepository` (demo mode,
  default on first launch) and `RemoteRepository` (REST + WS), swapped live
  when settings change. Any new data must work in both.
- WS snapshots lack REST-only fields (agenda, top tasks); `mergeFrame` in
  `RemoteRepository` preserves them. Keep that merge when extending the
  snapshot.
- Server-side mutations (agent, scheduler) reach the UI by diffing
  `tasks_summary` across snapshots and refetching — not by restart.
- All bottom-of-screen geometry derives from lens calibration: `LensCircle`
  fractions of window size, persisted in DataStore, with measured
  `HARDCODED_LENSES` defaults in `Chrome.kt`. The cutout API reports nothing
  on this device. Interactive content is width-clamped left of the optics;
  never draw or place touch targets over the lenses.
- The HUD is custom (`ui/components/`): HudCard, chips, monoStyle/sansStyle,
  Orb, Gauge. Don't introduce stock Material widgets into screens.
- Ambient clocks are battery-first: pure black, one redraw per minute, no
  infinite animations, pixel drift. Keep new clock styles within that budget.
- Kotlin gotcha that has bitten repeatedly: modifier extensions cannot be
  fully qualified inline (`.androidx.compose...foo()` does not parse).
  Import them.

## API contract

Wire format is snake_case JSON; Android models map with `@SerialName`.
Breaking the names breaks the app silently (defaults mask errors), so change
both sides in the same commit and cover it with the JSON decode tests in
`android/app/src/test/`.
