# Open-source readiness audit

Date: 2026-06-07. Scope: every file tracked in git on `main`, plus the git
history itself. Result: ready to publish, with three flagged items at the
bottom.

## What was checked and how

| Check | Method | Result |
|---|---|---|
| Personal names, emails, handles | `grep -iE "sanif|@gmail|priya"` over `git ls-files` | clean (defaults use the placeholder "Alex") |
| Secrets and tokens | grep for `sk-`, `ghp_`, `AKIA`, long hex over tracked files | clean (one false positive: a gradle commit hash URL in `gradlew`) |
| Env and credential files | `git ls-files` for `.env`, `*.db`, `local.properties` | none tracked; `.gitignore` covers all of them |
| Private LAN addresses | grep for `10.0.0.*` | clean; demo data uses documentation-style `192.168.x.x` |
| User data directories | `git ls-tree -r main` for `workspace/`, `docs/`, `design/` | not present in the tree or in history |
| History leakage | history squashed to a single root commit (`main`); the pre-publication chain exists only on the local `private-history` branch | clean |
| Hardcoded auth | API token comes only from `.env` (`change-me-aiva-token` placeholder in the example); web dashboard never embeds it | clean |
| Command execution | the action API and the agent's `run_action` tool accept only ids from `actions.yaml`; shell actions run fixed strings, no interpolation | by design |
| Dependency hygiene | requirements are pinned to floors, no git/url deps; Android uses Maven Central/Google only | clean |
| License | MIT, `LICENSE` at repo root, "Aiva contributors" | present |
| Tests | backend 53, android 20, all passing post-scrub | green |

## Changes made for publication

- Android package renamed from a personal namespace to `com.aiva.console`
  (this changes the applicationId: the app installs fresh and on-device
  settings reset once).
- All default/demo strings de-personalized (greeting, mock chat, agenda).
- `workspace/` (persona, memory, database) fully untracked; the backend
  creates it with generic templates on first start.
- `docs/` and `design/` untracked (internal design material and specs).
- Git history rewritten to a single public root commit.

## Flagged items (decide before pushing)

1. `screenshots/home.jpeg` shows the real owner name in the greeting.
   The token and URL in `settings.jpeg` are redacted, the name is not.
   Replace the screenshot (toggle demo mode and re-shoot) or accept it.
2. Git author name/email on the root commit is the repo owner's normal
   identity. This is standard attribution; change it before committing
   further if you want a pseudonym (`git config user.name/email`).
3. The local branch `private-history` still contains everything from before
   the rewrite, including design files and personal strings. It exists as a
   backup. Never push it; delete it with
   `git branch -D private-history` once you are confident.
