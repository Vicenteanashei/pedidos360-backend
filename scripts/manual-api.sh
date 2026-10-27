#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if [[ "${P360_NO_TOKEN:-}" == "true" ]]; then
  exec node "$script_dir/manual-api.mjs" "$@"
fi
read -r -s -p 'Pega el access token (no se mostrará): ' p360_manual_token
printf '\n' >&2
printf '%s' "$p360_manual_token" | node "$script_dir/manual-api.mjs" "$@"
unset p360_manual_token
