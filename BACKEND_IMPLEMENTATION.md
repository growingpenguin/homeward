# Backend implementation log (Homeward)

How each step of [`BACKEND_PLAN.md`](BACKEND_PLAN.md) was actually built: what was done, the commands run, test results, and anything that changed from the plan and why. Updated after each step, not at the end.

Branching follows git-flow: `main` (releases only, deployed to Render) ← `develop` (integration, PRs only) ← `feature/backend-*` (one per step); deploys go through `release/*`.

---

## B0. Toolchain and skeleton

**Date:** 2026-10-20 · **Branch:** `feature/backend-skeleton` → PR into `develop`

**Done**
- `brew install openjdk@21` → `openjdk version "21.0.12.1"`.
- Spring Initializr (Kotlin, Gradle Kotlin DSL, **Spring Boot 4.1.1**, Java 21, group `app.homeward`; web, security, jdbc, validation, postgresql, testcontainers) generated into a temp folder, copied into `server/` with `rsync --ignore-existing` (the existing `db/` folders untouched; `HELP.md` and `application.properties` left out). It brought **Kotlin 2.3.21**, Jackson 3 and Gradle 9.8.1. First build as generated: `BUILD SUCCESSFUL in 45s` (Testcontainers already worked on Colima). Committed unchanged first, then changed.
- `V2__login_and_schema_version.sql`: lets `homeward_app` **read** Flyway's history table (only if it exists, so plain `psql` test runs still pass) and makes login names unique per shelter (`UNIQUE (shelter_id, lower(display_name))`). Test `10_login.sql` (3 checks). Applied to Neon: `Successfully applied 1 migration ... now at version v2`.
- `application.yml`: datasource from `DB_URL` / `DB_USER` (default `homeward_app`) / `DB_PASSWORD`, lazy initialization, small pool, **Flyway off**, `PORT` from Render.
- `SchemaVersionCheck`: at startup compares the highest successful version in `flyway_schema_history` with the highest `V*.sql` in the build; stops with "Run scripts/db-migrate.sh migrate first" if the database is older.
- `GET /api/health` → `{"status":"ok","schemaVersion":2}`, public. `SecurityConfig`: only `/api/health` open, everything else denied until login (B2).
- Gradle: ktlint plugin 14.2.0, Flyway as a **test-only** dependency, Colima socket for Testcontainers, `db/draft` and `db/seed` kept out of the app jar.
- Tests: `TestDatabase` starts `postgres:18.6`, applies the migrations (and the demo seed, when present) **as the owner** with Flyway, gives `homeward_app` a password; the app then runs **as `homeward_app`**, like production.
- `scripts/run-backend.sh`: turns `HOMEWARD_APP_DATABASE_URL` from `.env.local` into `DB_*` (never printed) and runs `./gradlew bootRun`.
- Docs synced: BACKEND.md (the app never migrates, sessions in memory, versions), DATABASE.md, DATABASE_PLAN.md (matching/protection renumbered to V3/V4), CLAUDE.md (real commands).

**Test results**
```
scripts/db-test.sh  →  01_base.sql: 36 passed · 10_login.sql: 3 passed · RESULT: all passed
./gradlew build     →  BUILD SUCCESSFUL, 5 tests, 0 failed:
  the app connects as the least-privilege user, never as the owner
  health is public and shows the schema version
  everything else is closed until login exists
  the build expects the highest bundled migration and the database has it
  the app refuses to run when the database is older than the code
```

**Real run against Neon** (`scripts/run-backend.sh`):
```
Started HomewardApplicationKt in 0.668 seconds (process running for 0.783)
GET /api/health   → {"status":"ok","schemaVersion":2}  [HTTP 200, 0.198s]
GET /api/persons  → HTTP 403
```

**Problems and fixes**
- `10_login.sql` first used `assert_true`, and `TestDatabase` first required `demo_seed.sql`; both live on the database step 3 branch, which isn't merged into `develop` yet. The test now uses inline `CASE` checks (avoids a merge conflict), and the seed is loaded only when the file exists. B2's login tests need the seed, so step 3 must be merged first.
- Two test files weren't written on the first try because their folders didn't exist (`mkdir -p` was missing); added and rerun.
- The scheduled Codex review of DB step 3 ran at the wrong time (I computed the wait after 04:33 had passed) and Codex is still over its limit; it's re-run once Codex is available.

**Changed from the plan**
- `HomewardApplication.kt` and the generated tests were reformatted by `ktlintFormat` (tabs → spaces, ktlint's default style).
- The generated `TestcontainersConfiguration` / `TestHomewardApplication` (which used `postgres:latest` and connected as the owner) were replaced by `TestDatabase` + `IntegrationTest`.

**Review (2026-10-20, ~11:00).** Codex was over its limit and the user set a 10:00 PM deadline, so (agreed with the user) B0 was reviewed with Claude Code's `/code-review`; Codex runs afterwards as a post-deadline check. Database step 3 was reviewed and merged first (PR #4), and `develop` was merged into this branch (clean). 9 findings:

| Finding | Decision |
| --- | --- |
| `/error` was denied, so any failure (e.g. Neon down during a health check) became an empty 403 | **Fixed:** `/error` permitted |
| `run-backend.sh`: `eval "$(python3 ...)"` hid a Python failure, so the app could start without (or with someone else's) DB settings | **Fixed:** assignment first, so `set -e` stops it |
| `/api/health` queried the database on every call (keeps Neon awake, uses free hours) | **Fixed:** reports the version checked at startup |
| `version::int` would crash on a dotted version (`V3_1`) | **Fixed:** only whole-number versions count (the project uses V1, V2, ... only) |
| Demo seed loaded only "if present", by relative path | **Fixed:** now required, with a clear error; new test checks 6 demo staff and **no** coordinator |
| V2's grant is skipped in plain `psql` runs, so `db-test.sh` doesn't test it | **Covered by the Gradle tests:** they apply migrations with Flyway, and the app reading the version at startup *is* the test |
| Unique login name doesn't trim spaces | **Backend:** B2 trims the login name; V2 is already on Neon |
| Unused validation starter | **Kept:** B3 uses it today |
| Duplicated `.env.local` parser in two scripts | **After the deadline** |

Result: `./gradlew build` → `BUILD SUCCESSFUL`, 6 tests, 0 failed; `scripts/db-test.sh` → all passed. Compile hiccup on the way: `private set` isn't allowed on an open property (the Spring plugin opens `@Component` classes), so the setter is public.

## B1. First deploy (skeleton)

_Not started._

## B2. Login and security

_Not started._

## B3. Registration API

_Not started._

## B4–B6

_Not started (re-planned in plan mode when reached)._
