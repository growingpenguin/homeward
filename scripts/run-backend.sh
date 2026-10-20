#!/usr/bin/env bash
# Runs the backend locally against Neon as the least-privilege app user (homeward_app).
# Reads HOMEWARD_APP_DATABASE_URL from the git-ignored .env.local and turns it into the
# DB_URL / DB_USER / DB_PASSWORD the app expects. Nothing secret is printed.
#
#   scripts/run-backend.sh            ./gradlew bootRun on http://localhost:8080
set -euo pipefail
cd "$(dirname "$0")/.."

eval "$(python3 - <<'PY'
import shlex, urllib.parse
env = dict(l.split("=", 1) for l in open(".env.local").read().splitlines() if "=" in l and not l.startswith("#"))
u = urllib.parse.urlparse(env["HOMEWARD_APP_DATABASE_URL"].strip().strip('"').strip("'"))
print("export DB_URL=" + shlex.quote(f"jdbc:postgresql://{u.hostname}:{u.port or 5432}{u.path}?sslmode=require"))
print("export DB_USER=" + shlex.quote(urllib.parse.unquote(u.username)))
print("export DB_PASSWORD=" + shlex.quote(urllib.parse.unquote(u.password)))
PY
)"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
cd server && exec ./gradlew bootRun --console=plain
