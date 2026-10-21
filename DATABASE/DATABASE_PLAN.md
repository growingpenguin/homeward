> Status: **approved** on 2026-10-20 (plan mode). Execution is logged step by step in [`DATABASE_IMPLEMENTATION.md`](DATABASE_IMPLEMENTATION.md).

# Database implementation plan (Homeward)

## Context

The design phase is finished: PROPOSAL.md, DATABASE.md, BACKEND.md, FRONTEND.md and the deck agree, and the full schema (`server/src/main/resources/db/migration/V1__init.sql`, 450 lines) passes 42 break-it checks on a local PostgreSQL 15. Nothing is built or deployed yet, and the folder isn't a git repo.

The user asked to start implementation with the database, following their 6 steps (Neon → basic tables → registration → safe matching → consent & protection → full reunion test), with a new rule now in CLAUDE.md: plan mode first, then execute, recording each layer in `<LAYER>_PLAN.md` + `<LAYER>_IMPLEMENTATION.md`.

Decisions taken with the user:
- **Scope:** this plan covers the **database side** of all 6 steps. When step 3 needs the backend and frontend (the registration form), those get their own plan-mode plans (`BACKEND_PLAN.md`, `FRONTEND_PLAN.md`) and are built as one vertical slice.
- **Migrations:** split the single `V1__init.sql` into one file per step (nothing is applied yet, so this is allowed). Same final schema.
- **Git:** local repo + public GitHub repo now, using the CLAUDE.md workflow (main / develop / `feature/*`, PRs into develop, Codex cross-check before each PR).

Manual mode still applies: every command that changes something is shown first and run only after a "yes".

## Prerequisites (found while planning)

| Needed | Status now | Action |
| --- | --- | --- |
| Git repo | none | Step 0 |
| GitHub (`gh`) | logged in as `growingpenguin` | Step 0 creates the repo |
| Docker | Colima installed but not running | `colima start` (I run it after a yes) |
| Neon account + project | unknown | **You** create it (I can't sign up for you); I give exact clicks |
| `psql` | `/opt/homebrew/opt/postgresql@15/bin/psql` | use as is |
| Java / Gradle | not installed | **not needed for this plan** (Flyway runs from its Docker image); needed later for the backend plan |

## Files

- **New:** `DATABASE_PLAN.md` (this plan, approved), `DATABASE_IMPLEMENTATION.md` (log filled in after each step: what was done, commands, test output, changes from plan), `.gitignore`, `.env.example`.
- **Migrations** (replace `V1__init.sql`): `V1__base_tables.sql`, `V3__safe_matching.sql`, `V4__consent_and_protection.sql` (V2 was taken by the backend plan's `V2__login_and_schema_version.sql`) in `server/src/main/resources/db/migration/`.
- **Tests:** `server/src/test/resources/db/` SQL test files (one per step, from the existing 42 checks) + `scripts/db-test.sh` (runs them in a throwaway Docker Postgres). The backend plan later reuses these files from JUnit + Testcontainers.
- **Seed:** `server/src/main/resources/db/seed/demo_seed.sql` (fictional only, never in a migration).
- **Synced:** DATABASE.md (file names, test list), CLAUDE.md (schema path), AI_NOTES.md (one entry per session).

## How the split works (same final schema)

| File | Step | Contents (moved unchanged from today's V1) |
| --- | --- | --- |
| `V1__base_tables.sql` | 2 | extensions, `has_text` / `all_have_text`, `shelters`, `staff`, `persons` (+ `treated_as_minor`, name trigger, indexes), `person_history` (+ trigger, REVOKE), `reunion_codes`, `searches`, `audit_log`, the two DB users and their grants for these tables |
| `V3__safe_matching.sql` | 4 | `safety_flags` (+ lock trigger), `search_candidates` view, `nicknames`, `find_candidates`, `match_candidates`, grants |
| `V4__consent_and_protection.sql` | 5 | `contact_requests` (+ guard), flag-blocks-reveals, no-reactivate, `searches_keep_searcher`, `persons_recheck_minor`, grants |

Each file grants its own permissions, so the app user works after every step. Proof that nothing changed: after V1→V3, all 42 existing checks must pass, and a schema dump must match today's single file (compared table by table, function by function).

## Steps

Every step: `feature/database-<step>` branch off develop → small commits → `scripts/db-test.sh` green → Codex review (`codex review --base develop`) → PR into develop → log the step in `DATABASE_IMPLEMENTATION.md`.

### Step 0. Repository
- `git init`, `.gitignore` (`.env`, `node_modules/`, `build/`, `.gradle/`, `.DS_Store`), first commit on `main` with the docs + deck, create `develop`.
- `gh repo create growingpenguin/homeward --public` (name can change at approval), push `main` and `develop`.
- Write `DATABASE_PLAN.md` (this plan) and an empty `DATABASE_IMPLEMENTATION.md`.

### Step 1. Create the PostgreSQL database (Neon)
- You: create a Neon project (I'll give the clicks), copy the **owner** connection string into a local `.env` (never committed; `.env.example` shows the names only).
- Check Neon's PostgreSQL version; `scripts/db-test.sh` uses **that** version's Docker image (closes the "re-test on Neon's version" open item).
- Connect with `psql` and confirm `pg_trgm`, `fuzzystrmatch`, `unaccent` are available.
- *(Connecting from Spring Boot happens in the backend plan.)*

### Step 2. Basic tables (`V1__base_tables.sql`)
- Write V1 from today's file; test file `01_base.sql`: defaults, CHECKs, no-ID columns, history trigger, BCrypt-only PINs, permissions.
- Run locally (Docker), then apply to Neon with the Flyway Docker image (so Spring Boot's Flyway later sees it as already applied).
- **On Neon:** check `homeward_app` / `homeward_maint` are **not** in `neon_superuser`; set their passwords by hand (outside git).

### Step 3. Registration (database side)
- Test `03_registration.sql`, run **as `homeward_app`**: insert a person, re-send the same UUID (ignored), attach a pre-printed slip, audit entry, edit → snapshot.
- Small fictional seed: 3 shelters, staff with BCrypt PINs.
- Then: **plan mode for the backend + frontend registration slice** (separate plans).

### Step 4. Safe matching (`V3__safe_matching.sql`)
- Test `04_matching.sql`: misspellings, sound-alike, nicknames, accents, one-sided ages, hidden adult and hidden child never found, view has no location, ranking.
- **Fix found in the setup review (Codex P2):** the 100-candidate cut in `find_candidates` currently ignores age, so with more than 100 near-identical names the only right-aged person could be dropped. Add the age signal to the ordering **before** the cut (unknown age = neutral), and a crowded-name test: 120 people named "Maria Garcia", only one aged 80, who must come back first for an age range of 75–85. Update DATABASE.md's excerpt to match.
- Seed grows to ~200 fictional people with messy names; time `find_candidates` on Neon (closes the "scale" open item).

### Step 5. Consent and protection (`V4__consent_and_protection.sql`)
- Test `05_protection.sql`: child rules (found and searching), flags (incl. after "Connect"), request can't move, searcher can't be swapped, age correction re-check, temp-table and fake-history attacks, audit append-only.

### Step 6. Full reunion test (database side)
- Test `06_reunion.sql` with fictional **Sarah Kim**: registered at North High School → her sister searches at City Community Center → match found without location → request → Sarah says yes → contact plan visible; and the same flow with Sarah hidden / flagged / a child shows nothing.
- Run all test files on Neon's version locally **and** the permission tests on Neon with the real `homeward_app` login.

## Verification

- `scripts/db-test.sh` exits 0 after every step (fails on any `FAIL` line or SQL error); final run = all 42 existing checks + the new step tests.
- Schema of V1+V2+V3 matches today's single V1 (tables, columns, constraints, functions, triggers, grants).
- On Neon: `flyway info` shows V1–V5 applied; role memberships checked; permission tests pass with the real app login.
- DATABASE.md SQL excerpts still match the migration files (the existing line-by-line check, now over V1–V5).
- Codex review before each PR; findings verified before fixing, as before.
