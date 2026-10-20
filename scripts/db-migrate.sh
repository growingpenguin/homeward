#!/usr/bin/env bash
# Runs Flyway against Neon with the owner's direct connection from .env.local.
#
#   scripts/db-migrate.sh info      show which migrations are applied (read-only)
#   scripts/db-migrate.sh migrate   apply pending migrations in db/migration/
#
# Credentials go to Flyway through a temporary file (owner-only, deleted at exit) and are never printed.
# Run scripts/db-test.sh first: nothing should reach Neon untested.
set -euo pipefail
cd "$(dirname "$0")/.."

CMD="${1:-info}"
case "$CMD" in info|migrate|validate) ;; *) echo "usage: $0 info|migrate|validate"; exit 1 ;; esac

FLYWAY_IMAGE="flyway/flyway:11.20.3"
ENVFILE="$(mktemp)"
trap 'rm -f "$ENVFILE"' EXIT
chmod 600 "$ENVFILE"

python3 - "$ENVFILE" <<'PY'
import sys, urllib.parse
env = dict(l.split("=", 1) for l in open(".env.local").read().splitlines() if "=" in l and not l.startswith("#"))
u = urllib.parse.urlparse(env["DATABASE_URL_UNPOOLED"].strip().strip('"').strip("'"))
with open(sys.argv[1], "w") as f:
    f.write(f"FLYWAY_URL=jdbc:postgresql://{u.hostname}:{u.port or 5432}{u.path}?sslmode=require\n")
    f.write(f"FLYWAY_USER={urllib.parse.unquote(u.username)}\n")
    f.write(f"FLYWAY_PASSWORD={urllib.parse.unquote(u.password)}\n")
PY

docker run --rm --env-file "$ENVFILE" \
  -v "$PWD/server/src/main/resources/db/migration:/flyway/sql:ro" \
  "$FLYWAY_IMAGE" -locations=filesystem:/flyway/sql "$CMD" 2>&1 | grep -v "rd.gt\|more recent version\|^-*$"
