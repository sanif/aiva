#!/usr/bin/env bash
# Convenience launcher: create venv if missing, install deps, run uvicorn.
set -euo pipefail

cd "$(dirname "$0")"

VENV=".venv"
if [ ! -d "$VENV" ]; then
  echo "Creating virtualenv in $VENV ..."
  python3 -m venv "$VENV"
  "$VENV/bin/pip" install --upgrade pip >/dev/null
  "$VENV/bin/pip" install -r requirements.txt
fi

# NOTE: do NOT `source .env` — it isn't shell syntax (values may contain
# spaces/quotes, e.g. LLM_SYSTEM_PROMPT). The app itself parses .env via
# pydantic-settings; we only need HOST/PORT here for the uvicorn command.
ENV_HOST=""
ENV_PORT=""
if [ -f ".env" ]; then
  ENV_HOST="$(sed -n 's/^HOST=//p' .env | tail -1 | tr -d '[:space:]')"
  ENV_PORT="$(sed -n 's/^PORT=//p' .env | tail -1 | tr -d '[:space:]')"
fi

HOST="${HOST:-${ENV_HOST:-0.0.0.0}}"
PORT="${PORT:-${ENV_PORT:-8420}}"

echo "Starting Aiva on http://${HOST}:${PORT} (docs at /docs)"
exec "$VENV/bin/uvicorn" app.main:app --host "$HOST" --port "$PORT"
