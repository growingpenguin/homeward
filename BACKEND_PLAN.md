> Status: **approved** on 2026-10-20 (plan mode). Execution is logged step by step in [`BACKEND_IMPLEMENTATION.md`](BACKEND_IMPLEMENTATION.md).

# Backend implementation plan (Homeward)

## Context

The database is on Neon at schema version 1 (basic tables, least-privilege users, tested on PostgreSQL 18.6). DB step 3 (registration, database side) is done and waiting for its Codex review (scheduled 04:33). Following CLAUDE.md, the backend now gets its own plan-mode plan, recorded in `BACKEND_PLAN.md` and logged step by step in `BACKEND_IMPLEMENTATION.md`.

Decisions taken with the user:
- **Scope:** the whole backend, staged B0–B6 to match the database steps. B0–B3 are planned in detail here; B4–B6 are outlined and get their own plan-mode refinement when reached.
- **The running app never holds the owner's database password.** Migrations run only through `scripts/db-migrate.sh` (already proven on Neon). The app connects as `homeward_app` and refuses to start if the database schema is older than the code expects. (Changes BACKEND.md, which said Flyway migrates on startup.)
- **Sessions in memory** for the MVP (a sleeping Render server means logging in again, close to the 10-minute idle lock anyway).
- **Deploy early (B1)** to measure the real JVM cold start, as PROPOSAL.md milestone 0 says.

Git-flow throughout: one `feature/backend-*` branch per step → small one-line commits → tests → `codex review --base develop` → PR into `develop`. Deploys go out as `release/*` → `main` (Render deploys `main`). Manual mode: commands that change things are shown first and run after a "yes".

## Versions (checked 2026-10-20)

Spring Boot **4.1.1** (Spring Security 7: `csrf { spa() }` exists) · Kotlin (Initializr default for Boot 4.1) · **JDK 21 LTS** (`brew install openjdk@21`, 21.0.12) · Gradle Kotlin DSL (wrapper) · PostgreSQL 18.6 (Neon) · Testcontainers `postgres:18.6` · springdoc-openapi 3.x (B3).

## Files

- **New:** `BACKEND_PLAN.md` (this plan), `BACKEND_IMPLEMENTATION.md` (log), `server/` Gradle project (`build.gradle.kts`, `settings.gradle.kts`, wrapper, `src/main/kotlin/app/homeward/...`), `server/Dockerfile`, `render.yaml`, `scripts/run-backend.sh`, `scripts/db-seed.sh`.
- **New migration:** `V2__login_and_schema_version.sql` (see B0/B2). The planned matching and protection migrations move to **V3 / V4** (not applied anywhere yet, so renumbering is safe); DATABASE_PLAN.md and DATABASE.md updated.
- **Kept as is:** `server/src/main/resources/db/{migration,draft,seed}`, `server/src/test/resources/db/`, `scripts/db-test.sh`, `scripts/db-migrate.sh`.
- **Synced:** BACKEND.md (migrations not on startup, in-memory sessions, versions), CLAUDE.md (real commands), DATABASE_PLAN.md (V2–V4 numbering), AI_NOTES.md.

## Steps

### B0. Toolchain and skeleton — `feature/backend-skeleton`
- Install JDK 21 (`brew install openjdk@21`); point Gradle at it.
- Generate the project with Spring Initializr (Kotlin, Gradle Kotlin DSL, Boot 4.1.1, Java 21, group `app.homeward`; deps: web, security, jdbc, validation, postgresql, testcontainers) into a temp folder, then copy into `server/` **without touching** the existing `db/` folders.
- `application.yml`: datasource from env (`DB_URL` = JDBC URL, `DB_USER=homeward_app`, `DB_PASSWORD`), `spring.main.lazy-initialization=true`, Flyway **off** at runtime.
- `V2__login_and_schema_version.sql`: `GRANT SELECT ON flyway_schema_history TO homeward_app` (so the app can read the schema version) and a unique login name per shelter: `UNIQUE (shelter_id, lower(display_name))` on `staff` (needed by B2 login). Tested with `scripts/db-test.sh`, applied with `scripts/db-migrate.sh`.
- `SchemaVersionCheck`: at startup, reads the highest successful version from `flyway_schema_history`; if lower than the highest `V*.sql` on the classpath, the app stops with "Run scripts/db-migrate.sh migrate".
- `GET /api/health` → `{"status":"ok","schemaVersion":2}` (public).
- ktlint Gradle plugin; `scripts/run-backend.sh` turns `.env.local` into `DB_*` and runs `./gradlew bootRun` (secrets never printed).
- Testcontainers on Colima (`DOCKER_HOST=unix://$HOME/.colima/default/docker.sock`, `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`). Test base class: start `postgres:18.6`, apply migrations + demo seed **as owner** with Flyway (test dependency only), give `homeward_app` a password, run the app **as `homeward_app`** — same as production.
- Tests: context loads as the app user; health 200; app refuses to start when the schema is behind.

### B1. First deploy (skeleton) — `release/0.1.0`
- `server/Dockerfile` (multi-stage: `eclipse-temurin:21-jdk` build → `eclipse-temurin:21-jre`, `-XX:MaxRAMPercentage=75`).
- `render.yaml` (web service from `server/`, Docker, health check `/api/health`, free plan, auto-deploy `main`; DB env vars `sync: false`, so they're typed into Render, never into git).
- **You:** create a Render account, connect the GitHub repo, create the service from `render.yaml`, paste `DB_URL` / `DB_USER` / `DB_PASSWORD` (I give the exact values' names; the values come from your `.env.local`).
- Release: `release/0.1.0` from `develop` → PR into `main` → tag `v0.1.0` → merge back into `develop`.
- Measure: `curl /api/health` warm, and after ~15 min idle (cold start). If the cold start is much over a minute, decide on the Ktor fallback (BACKEND.md) before going further.

### B2. Login and security — `feature/backend-security`
- `POST /api/login` {shelterId, name, pin} (BCrypt check against `staff.pin_hash`), `POST /api/logout`, `GET /api/me`, `GET /api/shelters` (public: id + name only, for the login dropdown), `GET /api/csrf`.
- Spring Security: session cookie HttpOnly + SameSite=Lax (+ Secure on Render), 12-hour session, `csrf { spa() }`, role hierarchy coordinator > lead > volunteer, `@EnableMethodSecurity`, session fixation protection.
- Login throttling: 5 wrong PINs for one shelter + name → locked 15 minutes (in memory); same message for unknown name and wrong PIN.
- `IdleLockFilter`: 10 minutes without a **user** action (requests the frontend marks with an `X-Homeward-Activity` header; background refreshes don't count) → `401 {"locked": true}`; `POST /api/unlock` {pin} re-checks the PIN.
- `CurrentStaff` (staff id, shelter id, role from the session), used by every later service.
- `scripts/db-seed.sh`: applies `demo_seed.sql` to Neon (owner, direct URL), so demo login works on Render.
- Tests (MockMvc + Testcontainers): login ok / wrong PIN / unknown name same answer / lockout after 5; CSRF required on writes; volunteer gets 403 on lead-only; idle lock + unlock; logout ends the session.

### B3. Registration API — `feature/backend-registration` (backend half of the registration slice)
- `POST /api/persons` (`RegisterPersonRequest` from BACKEND.md: browser-made `id`, only `preferredName` required, `source`, `paperRef`, `recordedAt`, `slipCode`). Shelter and `entered_by` come from the login, never the body. Unknown JSON fields are **rejected** (so an `ssn` field can't sneak in). Same `id` again → `200` with the existing record, nothing new.
- `POST /api/persons/{id}/slip`: attaches a pre-printed `slipCode` (only if free and working) **or** issues a new code (`SecureRandom`, no 0/O/1/I, retry on collision) with a 4-digit PIN shown **once** in the response; BCrypt-stored; audit `CODE_ISSUED`. If the person already has a working slip → `409` "Ask a lead to reprint" (so a retry never makes a second code).
- `GET /api/persons/{id}` and `PATCH /api/persons/{id}` (edit; `updated_by` from the login): own shelter only, otherwise `404`.
- Errors: Bean Validation + database CHECK errors → plain sentences (ProblemDetail), matching the frontend's wording.
- springdoc-openapi → `/api/openapi.json` for the frontend's generated types.
- Tests: register, re-send, paper fields, hidden choice kept, slip issued once, pre-printed slip, cross-shelter 404, unknown field rejected, plain-sentence errors, all as the app user.
- **Then:** plan mode for the frontend registration screens (`FRONTEND_PLAN.md`), release `0.2.0` with the first working feature.

### B4–B6 (outline; each re-planned in plan mode when reached)
- **B4 Matching** (after DB step 4): searches API, `MatchingService` (signals → score + reasons), searcher registered + slip every time, `SearcherStatus` with `type` discriminator.
- **B5 Consent and protection** (after DB step 5): `VisibilityService`, `ConsentService` (`connectFindable` only on pending, desk check, child rules), `ReunionCodeService` lookup (FOR UPDATE, dummy hash, same reply), flags, lead endpoints, audit everywhere.
- **B6 Full reunion** (with the frontend): the Sarah Kim flow end to end through the API, then on Render.

## Verification

- Every step: `./gradlew build` (ktlint + all tests on Testcontainers `postgres:18.6`, app connected as `homeward_app`) and `scripts/db-test.sh` stay green.
- B0: app refuses to start with an old schema; `/api/health` shows the schema version.
- B1: live `curl https://<render-url>/api/health` → 200; cold-start time recorded in BACKEND_IMPLEMENTATION.md.
- B2–B3: MockMvc tests listed above; a manual run against Neon with `scripts/run-backend.sh` (login as a demo volunteer, register a fictional person, then check the row and audit entry with `psql`).
- Codex review before every PR; each finding verified before fixing. BACKEND_IMPLEMENTATION.md updated after every step with commands, outputs and changes from the plan.
