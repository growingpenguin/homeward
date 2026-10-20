#!/usr/bin/env bash
# Applies the FICTIONAL demo seed (db/seed/demo_seed.sql) to Neon, as the owner, through the
# direct connection in .env.local. Re-runnable: a second run updates rows to match the file.
# The connection string goes to psql through an environment variable and is never printed.
set -euo pipefail
cd "$(dirname "$0")/.."

URL="$(python3 - <<'PY'
env = dict(l.split("=", 1) for l in open(".env.local").read().splitlines() if "=" in l and not l.startswith("#"))
print(env["DATABASE_URL_UNPOOLED"].strip().strip('"').strip("'"))
PY
)"
docker run --rm -i -e URL="$URL" postgres:18.6 sh -c 'psql "$URL" -X -q -v ON_ERROR_STOP=1' < server/src/main/resources/db/seed/demo_seed.sql
echo "demo seed applied"
