# Aiva Backend

Local personal-assistant dashboard backend. Runs on your Mac and serves an
Android app (Motorola Razr outer display) over local Wi-Fi. Built with FastAPI.

- System metrics (CPU / RAM / disk / temp / network / battery) via `psutil`
- Service health checks (HTTP + TCP) and Docker container status
- Tasks, notes, action logs and chat history in SQLite (async SQLAlchemy)
- LLM chat via [LiteLLM] (Ollama / LM Studio / Open WebUI / any OpenAI-compatible)
- Real-time dashboard + chat over WebSockets
- A `MOCK_MODE` for building the app with no LLM or live load

## Setup

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

Create your config:

```bash
cp .env.example .env
# edit .env — at minimum set a real API_TOKEN
```

## Run

Easiest (creates venv + installs deps on first run):

```bash
./run.sh
```

Or directly:

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8420
```

Interactive API docs: <http://localhost:8420/docs>

## Connect the Android app

1. Find your Mac's LAN IP:

   ```bash
   ipconfig getifaddr en0    # Wi-Fi; try en1 if on a different interface
   ```

2. Point the app at `http://<mac-ip>:8420` (e.g. `http://192.168.1.23:8420`).
3. Set the API token in the app to match `API_TOKEN` from your `.env`.
   - REST calls send it as the header `X-API-Token: <token>`.
   - WebSockets pass it as a query param: `ws://<mac-ip>:8420/ws/dashboard?token=<token>`.

The Mac and phone must be on the same Wi-Fi network. Allow incoming connections
if macOS firewall prompts.

## MOCK_MODE (development)

Set `MOCK_MODE=true` in `.env` (or `MOCK_MODE=true uvicorn ...`) to:

- Return plausible fake temperature/CPU/battery metrics (no real sensors needed)
- Return canned chat replies (no LLM required) and a static demo agenda

Great for building the Android UI without a running LLM.

## Chat providers

`CHAT_PROVIDER` picks the brain behind `/api/chat` and `/ws/chat`:

### `litellm` (default) — any model

Configure via `LLM_MODEL` + optional `LLM_API_BASE`/`LLM_API_KEY`. LiteLLM maps
the model string to the right provider — **any model works, including the whole
OpenRouter catalog**:

| Provider     | LLM_MODEL                                  | LLM_API_BASE               | LLM_API_KEY |
|--------------|--------------------------------------------|----------------------------|-------------|
| Ollama       | `ollama/llama3.1`                          | `http://localhost:11434`   | (none)      |
| LM Studio    | `openai/local-model`                       | `http://localhost:1234/v1` | `lm-studio` |
| **OpenRouter** | `openrouter/anthropic/claude-sonnet-4-6` · `openrouter/openai/gpt-4o` · `openrouter/google/gemini-2.5-pro` · … | *(leave unset)* | `sk-or-...` |
| OpenAI       | `openai/gpt-4o-mini`                       | *(leave unset)*            | `sk-...`    |

Leave `LLM_API_BASE` unset for hosted providers. If the model is unreachable,
the backend gracefully falls back to canned replies.

### `cli` — chat with a local agent session

Bridges chat to **any** local agent CLI via configurable command templates
(`{message}`, `{system}` placeholders) — nothing is hardcoded to one tool.
The optional continue-template keeps one ongoing session; if continuing fails
the bridge retries fresh automatically.

```env
CHAT_PROVIDER=cli
# Claude Code session WITH Aiva's tools via MCP (recommended):
CHAT_CLI_CMD=["claude","-p","{message}","--mcp-config","aiva-mcp.json","--allowedTools","mcp__aiva,WebFetch,WebSearch","--append-system-prompt","{system}"]
CHAT_CLI_CONTINUE_CMD=["claude","-p","{message}","--continue","--mcp-config","aiva-mcp.json","--allowedTools","mcp__aiva,WebFetch,WebSearch","--append-system-prompt","{system}"]
# or Codex: CHAT_CLI_CMD=["codex","exec","{message}"]
# or anything: CHAT_CLI_CMD=["./my-agent.sh","{message}"]
```

Messages typed on the Razr are answered by that agent session — and via
`aiva-mcp.json` the session gets **Aiva's own MCP tools** (`mcp_server.py`):
system status, the task tracker, agenda, notes, allowlisted actions and
`save_memory`. Run the backend from the `backend/` directory so the relative
paths in `aiva-mcp.json` resolve (or make them absolute).

## How the chatbot accesses data — tools, not preload

The system prompt carries only **persona + memory** from the agent workspace
(`workspace/SOUL.md`, `USER.md`, `workspace/memory/MEMORY.md` + `HISTORY.md`,
nanobot-style). Live state is fetched **on demand**:

- `litellm` provider → OpenAI function calling: the model loops over
  `get_system_status`, `list_tasks`, `create_task`, `update_task`,
  `list_projects`, `get_agenda`, `list_notes`, `create_note`, `run_action`,
  `save_memory` (max 6 rounds per turn).
- `cli` provider → the same ten tools over MCP (`mcp_server.py`).

The model persists what it learns with `save_memory` (timestamped
`HISTORY.md` log + consolidated `MEMORY.md`), so Aiva remembers across
sessions. `workspace/memory/` is gitignored — it's personal.

## Security notes

- **Token auth** — every REST route except `GET /api/health` requires the
  `X-API-Token` header; WebSockets require `?token=`. Bad tokens get
  `401 {"detail":"invalid token"}` (REST) or close code `4401` (WS).
- **Action allowlist** — the action API only accepts the `id` of an action
  defined in `actions.yaml`. Nothing else can be run.
- **No arbitrary shell** — `shell`-type actions execute a fixed command from the
  YAML; no user input is ever interpolated into a shell command.
- This server is designed for a trusted LAN. Set a strong `API_TOKEN` and
  restrict `CORS_ORIGINS` before exposing it anywhere broader.

## API summary

All routes are prefixed `/api`. All require `X-API-Token` except `GET /api/health`.

| Method | Path                  | Description                                   |
|--------|-----------------------|-----------------------------------------------|
| GET    | `/api/health`         | Health + version + uptime + mock flag         |
| GET    | `/api/metrics`        | Live system metrics + history buffers         |
| GET    | `/api/services`       | Service health (up/warn/down + latency)       |
| GET    | `/api/docker`         | Docker container summary (graceful fallback)  |
| GET    | `/api/dashboard`      | Aggregated snapshot for the home screen       |
| GET    | `/api/tasks`          | List tasks (optional `?status=`)              |
| POST   | `/api/tasks`          | Create task (201)                             |
| PATCH  | `/api/tasks/{id}`     | Update task (404 if missing)                  |
| DELETE | `/api/tasks/{id}`     | Delete task → `{"ok":true}`                   |
| GET    | `/api/notes`          | List notes (`?limit=20`)                      |
| POST   | `/api/notes`          | Create note (201)                             |
| POST   | `/api/chat`           | Non-streaming chat → `{"reply":...}`          |
| GET    | `/api/chat/history`   | Latest exchanges, oldest first (`?limit=50`)  |
| GET    | `/api/actions`        | List allowlisted actions                      |
| POST   | `/api/actions/{id}`   | Run an allowlisted action (404 if unknown)    |
| WS     | `/ws/dashboard`       | Snapshot pushes every `PUSH_INTERVAL_SECONDS` |
| WS     | `/ws/chat`            | Streaming chat tokens then a final reply      |

### WebSocket payloads

`/ws/dashboard` server → client:

```json
{"type":"snapshot","ts":"<iso>","metrics":{...},"services":[...],
 "docker":{...},"alerts":[...],"tasks_summary":{...},"ai_status":"idle"}
```

`/ws/chat` client → server: `{"message":"..."}`. Server → client:

```json
{"type":"token","text":"..."}   // repeated
{"type":"done","reply":"<full text>"}
{"type":"error","message":"..."} // on failure
```

[LiteLLM]: https://github.com/BerriAI/litellm


## Tests

```bash
pip install -r requirements-dev.txt
python -m pytest
```

## Scheduler — the agent on cron

Tell Aiva in chat: *"every morning at 8 summarize my day"* — she calls the
`schedule_task` tool. A background looper (twice a minute) finds due jobs and
**invokes the configured agent with the job's prompt** — with
`CHAT_PROVIDER=cli` that's a real Claude session with Aiva's MCP tools doing
the work. Results land in chat history (`source: scheduler`) and memory.

- `when` formats: `HH:MM` (daily) · `m h dom mon dow` cron subset
  (`*`, numbers, lists, `*/n`; dow 0=Sun) · `YYYY-MM-DDTHH:MM` (one-shot,
  auto-disables)
- Chat tools: `schedule_task` / `list_schedules` / `delete_schedule`
- REST: `GET|POST /api/schedules`, `DELETE /api/schedules/{id}`

## Self-learning memory (credit: ruflo)

Aiva's episodic memory is **inspired by [ruflo](https://github.com/ruvnet/ruflo)
by @ruvnet** — we borrowed its self-learning principles and reimplemented them
natively over SQLite (no external services):

- **Learning hooks** — every chat exchange (app + WS) is auto-stored
  as an `episode`; nothing depends on the model remembering to save.
- **Relevance-ranked recall** — the `recall` tool ranks memories by
  `token-overlap × recency-decay (30-day half-life) × behavioral score`
  (ruflo uses HNSW vector search; token overlap is plenty at personal scale).
- **Behavioral scoring** — approval cards in the app POST to
  `/api/chat/feedback`: approvals upgrade a pattern's score gradually
  (×1.15), dismissals downgrade instantly (×0.5) — ruflo's
  "upgrades require history, downgrades are instant" trust principle.
- **Deduped `remember`** — storing the same fact twice reinforces it
  instead of duplicating.

Memory kinds: `fact` (explicit), `episode` (auto), `pattern` (behavioral).
All of it lives in `workspace/aiva.db` next to the markdown persona files.
