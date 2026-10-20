#!/usr/bin/env bash
# Runs the database tests in a throwaway PostgreSQL container with the same version as Neon.
#
#   scripts/db-test.sh           apply db/migration/V*.sql in order, then run every step test
#                                (server/src/test/resources/db/NN_*.sql) against them
#   scripts/db-test.sh --draft   apply the full schema draft and run its 42 break-it checks
#
# A test passes when psql has no error and no output line contains "FAIL".
# Needs Docker (on this Mac: `colima start`). Nothing touches Neon.
set -euo pipefail
cd "$(dirname "$0")/.."

IMAGE="postgres:18.6"                       # Neon's version, checked in step 1
NAME="homeward-db-test-$$"
MIGRATIONS="server/src/main/resources/db/migration"
TESTS="server/src/test/resources/db"

cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

docker run -d --name "$NAME" -e POSTGRES_USER=owner -e POSTGRES_PASSWORD=test -e POSTGRES_DB=homeward "$IMAGE" >/dev/null

# The image starts a temporary server first, then restarts; wait until two checks in a row succeed.
ready=0
for _ in $(seq 1 120); do
  if docker exec "$NAME" psql -U owner -d homeward -tAc 'SELECT 1' >/dev/null 2>&1; then
    ready=$((ready + 1)); [ "$ready" -ge 2 ] && break
  else
    ready=0
  fi
  sleep 0.5
done
[ "$ready" -ge 2 ] || { echo "PostgreSQL did not start"; exit 1; }

run_sql() {  # run_sql <file> : apply one SQL file, stop at the first error
  docker exec -i "$NAME" psql -X -q -U owner -d homeward -v ON_ERROR_STOP=1 < "$1"
}

failed=0
run_test() {  # run_test <file> : run a test file, fail on a psql error or any FAIL line
  local out
  if ! out=$(run_sql "$1" 2>&1); then
    echo "$out"; echo "ERROR in $1"; failed=1; return
  fi
  local ok fails
  ok=$(grep -c "ok, " <<<"$out" || true)
  fails=$(grep -c "FAIL" <<<"$out" || true)
  echo "  $(basename "$1"): $ok passed, $fails failed"
  if [ "$fails" -gt 0 ]; then grep "FAIL" <<<"$out"; failed=1; fi
}

echo "PostgreSQL $(docker exec "$NAME" psql -U owner -d homeward -tAc 'SHOW server_version')"

if [ "${1:-}" = "--draft" ]; then
  run_sql server/src/main/resources/db/draft/full_schema_draft.sql
  echo "applied: full_schema_draft.sql"
  run_test "$TESTS/draft/full_schema_checks.sql"
else
  for f in $(ls "$MIGRATIONS"/V*__*.sql | sort -V); do
    run_sql "$f"; echo "applied: $(basename "$f")"
  done
  for f in $(ls "$TESTS"/[0-9][0-9]_*.sql 2>/dev/null | sort); do
    run_test "$f"
  done
fi

if [ "$failed" -ne 0 ]; then echo "RESULT: FAILED"; exit 1; fi
echo "RESULT: all passed"
