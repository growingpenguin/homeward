# Database implementation log (Homeward)

How each step of [`DATABASE_PLAN.md`](DATABASE_PLAN.md) was actually built: what was done, the commands run, test results, and anything that changed from the plan and why. Updated after each step, not at the end.

Branching follows git-flow: `main` (releases only) ← `develop` (integration, PRs only) ← `feature/*` (one per step).

---

## Step 0. Repository

**Date:** 2026-10-20 · **Branch:** `feature/project-setup` → PR into `develop`

- `git init -b main`, then an **empty** first commit on `main` (the usual git-flow start), then `develop` from `main`.
- All files (design docs, deck, schema draft, plans) went in through `feature/project-setup` and a PR into `develop`, so nothing was committed straight to `main` or `develop`.
- `.gitignore` keeps secrets (`.env`) and build output out of git.
- Public GitHub repo: `growingpenguin/homeward`.

**Codex review (`codex review --base develop`):** 2 real findings, both verified:
- P1: `connectFindable` could turn an earlier "no" into a Connect. Fixed in BACKEND.md: it only works on a request that is still pending, checked inside the UPDATE.
- P2: the 100-candidate cut in `find_candidates` ignores age. Added to DATABASE_PLAN.md step 4 (fixed when `find_candidates` moves into `V2__safe_matching.sql`, with a crowded-name test).
- Also found: the review command in CLAUDE.md didn't work (`--base` takes no instructions). Fixed.

**Changed from the plan:** the plan said "first commit on `main` with the docs". To follow git-flow strictly, `main` only got an empty commit; the docs arrive through a feature branch and a PR. `main` will receive them later through a release.

---

## Step 1. Create the PostgreSQL database (Neon)

**Date:** 2026-10-20 · **Branch:** `feature/database-neon` → PR into `develop`

**Done**
- Neon project `homeward` (`wispy-wave-25650609`, org `org-small-dust-05145668`), branch `production`.
- Neon CLI 8.8.0 (`npm i -g neon@latest`); the user ran `neon login` (browser).
- Neon agent skills (`neon`, `neon-postgres`, `neon-postgres-branches`) and the Neon MCP server (OAuth, URL only, pinned to the project) installed **for this project only**; both, plus `.neon`, are git-ignored.
- `neon link --org-id org-small-dust-05145668 --project-id wispy-wave-25650609 --branch production -y` → wrote `.neon` and pulled `DATABASE_URL` (pooled), `DATABASE_URL_UNPOOLED` (direct) and `NEON_BRANCH` into the git-ignored `.env.local`. `.env.example` lists the names only.

**Check (read-only, `psql` with the direct URL, secret never printed)**

| Check | Result |
| --- | --- |
| Server version | **PostgreSQL 18.6** |
| Connected as | `neondb_owner` / `neondb` |
| `fuzzystrmatch`, `pg_trgm`, `unaccent` | available (1.2, 1.6, 1.1), not installed yet |
| Tables in `public` | 0 (empty) |
| Owner can create roles | yes (needed by V1) |
| Owner is a member of | `neon_superuser` |

**Changed from the plan / what this means for the next steps**
- Neon runs **PostgreSQL 18.6**, but every test so far ran on 15. Step 2 runs all tests in a `postgres:18` Docker container before anything touches Neon.
- Migrations and `psql` use the **direct** URL (`DATABASE_URL_UNPOOLED`); the app will use the pooled one.
- The owner is in `neon_superuser`. Step 2 checks that `homeward_app` / `homeward_maint`, created with SQL by V1, are **not**.
- The pasted Neon setup instructions (`neon init -y`, `neon deploy`, `neon.ts`, `npm run build`) were adapted: there's no Node app at the repo root and only plain Postgres is needed, so the project was linked with `neon link` instead. Wiring Spring Boot to the database is in the backend plan.

## Step 2. Basic tables (`V1__base_tables.sql`)

**Date:** 2026-10-20 · **Branch:** `feature/database-base-tables` → PR into `develop`

### 2a. Local: split, test runner, tests (PostgreSQL 18.6 in Docker)

**Done**
- Started Docker (`colima start`) and pulled `postgres:18.6`, the **exact** version Neon runs; it has `pg_trgm`, `fuzzystrmatch` and `unaccent`.
- Moved `db/migration/V1__init.sql` → `db/draft/full_schema_draft.sql` (`git mv`, history kept). Flyway only reads `db/migration/`, so the draft can never run as a migration; it stays as the source for V2/V3 and as the reference for the final "nothing changed" comparison.
- `scripts/db-test.sh`: starts a throwaway `postgres:18.6` container, applies `db/migration/V*.sql` in version order, runs every `server/src/test/resources/db/NN_*.sql`, and fails on any SQL error or any `FAIL` line. `--draft` runs the full draft with its checks instead. The container is removed at the end.
- `V1__base_tables.sql` (198 lines): extensions, `has_text` / `all_have_text`, `shelters`, `staff`, `persons` (+ `treated_as_minor`, name trigger, indexes), `person_history` (+ trigger, `REVOKE`), `reunion_codes`, `searches`, `audit_log`, the two database users and the grants for **these** tables only. Every statement moved unchanged from the draft; only the section title "searches, matches, contact requests" became "searches".
- Tests: `00_helpers.sql` (the `expect_fail` helper + fictional shelters/staff) and `01_base.sql` (36 checks: defaults, re-sent id, no ID columns, matching columns, who counts as a child, bad input, codes, history, app-user and retention-user permissions, temp-table attack).

**Test results**

| Run | Command | Result |
| --- | --- | --- |
| Full draft on PostgreSQL 18.6 (version check) | `scripts/db-test.sh --draft` | `full_schema_checks.sql: 43 passed, 0 failed` → **the 15 → 18 version gap is closed** |
| V1 + step 2 tests, first run | `scripts/db-test.sh` | `01_base.sql: 35 passed, 1 failed` (`FAIL: matching columns jose perez`) |
| V1 + step 2 tests, after the fix | `scripts/db-test.sh` | `00_helpers.sql: 1 passed`, `01_base.sql: 36 passed, 0 failed`, `RESULT: all passed` |
| Does the runner catch failures? | a temporary test that prints `FAIL` | exit code non-zero ✅ (file removed afterwards) |

**Problems and fixes**
- The one failure was a **test bug**, not a schema bug: the test expected `name_words = ['jose','perez']`, but the trigger builds it with `SELECT DISTINCT`, which doesn't guarantee word order. The test now checks the content and the count, not the order.
- My first `git add` of the whole migration folder put `V1` into the "move the draft" commit. The commits were still local, so I reset and redid them with one concern each (draft move → runner → V1 → tests).

### 2b. Neon: apply V1 with Flyway, check the users

_In progress._

## Step 3. Registration (database side)

_Not started._

## Step 4. Safe matching (`V2__safe_matching.sql`)

_Not started._

## Step 5. Consent and protection (`V3__consent_and_protection.sql`)

_Not started._

## Step 6. Full reunion test (database side)

_Not started._
