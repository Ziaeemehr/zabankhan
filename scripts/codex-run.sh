#!/usr/bin/env bash
# Run Codex on zabankhan with a hard effort cap.
# Usage: scripts/codex-run.sh [low|medium] "task prompt"
set -euo pipefail
effort="${1:-low}"; shift || true
case "$effort" in
  minimal|low|medium) ;;
  *) echo "codex-run: effort '$effort' not allowed (max medium); using medium" >&2; effort=medium ;;
esac
[ $# -ge 1 ] || { echo "usage: $0 [low|medium] \"prompt\"" >&2; exit 2; }
root="$(cd "$(dirname "$0")/.." && pwd)"
exec codex exec -m gpt-6-luna -c model_reasoning_effort="$effort" \
  --sandbox workspace-write --skip-git-repo-check -C "$root" "$*" < /dev/null
