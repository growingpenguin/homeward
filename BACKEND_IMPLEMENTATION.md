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

**Date:** 2026-10-20 · **Branch:** `feature/backend-deploy` → PR into `develop` → `release/0.1.0` → `main`

**Done**
- `server/Dockerfile`: multi-stage (`eclipse-temurin:21-jdk` builds the jar, `eclipse-temurin:21-jre` runs it as a non-root user), `-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k`. Tests aren't run inside the image build (they need Docker); they run before pushing. `.dockerignore` keeps the draft, the seed and the tests out. The plain (non-Boot) jar is disabled so only one jar exists.
- `render.yaml`: web service `homeward-api` from `server/`, Docker, free plan, region **ohio** (same AWS region as Neon, us-east-2), deploys **`main`** only, health check `/api/health`. `DB_USER=homeward_app`; `DB_URL` and `DB_PASSWORD` are `sync: false` (typed into Render, never in git).

**Local check of the image, run like Render** (`docker run --memory=512m`, against Neon as `homeward_app`, credentials through a temporary owner-only env file):

| Run | Result |
| --- | --- |
| First run | healthy after 1.3 s, **but `{"schemaVersion":0}`** |
| After the fix | first healthy response after 1.9 s: `{"status":"ok","schemaVersion":2}`; `Started HomewardApplicationKt in 1.469 seconds`; memory **187 MB / 512 MB** |

**Bug found and fixed:** `SchemaVersionCheck` was an `ApplicationRunner`, and Spring Boot **already accepts requests while runners are still running**. So for a moment the app served traffic before the schema check finished (and health said version 0). It now runs while the app starts (`@Lazy(false)` + `InitializingBean`), **before** the web server accepts any request: an older schema stops the app before it serves anything. Tests updated (`verify()`), `./gradlew build` green.

**Review:** `/code-review` (Codex still unavailable): no serious issues. One minor note outside this diff: in `scripts/db-migrate.sh`, `grep -v` with `pipefail` could fail a successful run if every output line were filtered; logged for after the deadline.

**Release:** `release/0.1.0` (version bumped to `0.1.0`) → PR into `main` → tag `v0.1.0` → merged back into `develop`.

## B2. Login and security

**Date:** 2026-10-20 · **Branch:** `feature/backend-security` → PR into `develop`

**Done**
- Endpoints: `GET /api/shelters` (public: id + name only), `GET /api/csrf`, `POST /api/login` {shelterId, name, pin}, `GET /api/me`, `POST /api/unlock` {pin}, `POST /api/logout`.
- `SecurityConfig`: `csrf { spa() }` (XSRF-TOKEN cookie → `X-XSRF-TOKEN` header), session security context, session id changed on login, 401 (not a login page) for anonymous calls, role hierarchy coordinator > lead > volunteer, `@EnableMethodSecurity`, `/api/lead/**` needs LEAD. Spring's default in-memory user is replaced by a no-op.
- Login: name matched case-insensitively with spaces trimmed (covers the review note on V2's untrimmed unique index); BCrypt check; **the same answer and the same BCrypt work** for an unknown name and a wrong PIN (dummy hash).
- `LoginThrottle`: 5 wrong PINs for one shelter + name (or one unlock) → 429 for 15 minutes (in memory).
- `IdleLockFilter`: 10 minutes without a request marked `X-Homeward-Activity` → `401 {"locked":true}` until `/api/unlock`; background requests never refresh it.
- Session: 12 hours, cookie HttpOnly + SameSite=Lax, `Secure` via `COOKIE_SECURE` (`true` in render.yaml).
- `CurrentStaff`: the logged-in staff member for every later service.
- `scripts/db-seed.sh`: applied the fictional demo seed to Neon → `shelters: 3`, `staff: Lead Jordan, Lead Morgan, Lead Taylor, Volunteer Alex, Volunteer Casey, Volunteer Riley` (no coordinator).

**Tests:** `./gradlew build` → `BUILD SUCCESSFUL`, **17 tests, 0 failed**: shelters public with names only · login with a loosely typed name · unknown name and wrong PIN give identical answers · login without CSRF refused · 5 wrong PINs lock even the right PIN · nothing reachable without login · volunteer 403 / lead OK on a lead-only action · idle lock + wrong/right unlock · background requests don't refresh the idle timer, a person's action does · logout ends the session · **the real browser CSRF flow** (cookie value sent back in the header).

**Real run against Neon** (`scripts/run-backend.sh` + curl, as the browser will):
```
GET  /api/shelters → 3 shelters, ids and names only
POST /api/login (right PIN, name typed "volunteer alex") → 200 {"displayName":"Volunteer Alex","role":"volunteer",...}
GET  /api/me (with the cookie) → 200;  without it → 401
```

**Bug found by the real run:** `/api/csrf` returned `token.token` in JSON, but with `csrf.spa()` that value is **masked** and is rejected; the browser must send back the **cookie's** value. The first login attempt got 403 for that reason. `/api/csrf` now only sets the cookie and returns the names (`X-XSRF-TOKEN`, `XSRF-TOKEN`), and the new `CsrfFlowTest` proves the real flow. (In MockMvc, `token.headerName` reported `X-CSRF-TOKEN`, so the names are fixed constants, the ones `csrf.spa()` uses and the curl run accepted.)

**Smaller fixes on the way:** Kotlin needed `ResponseEntity<*>`; ktlint wanted `IdleLock.kt` renamed to `IdleLockFilter.kt` and one line shortened; an unneeded `!!` removed.

**Review (`/code-review`, Codex still unavailable):** 10 findings; 9 fixed, 1 deferred.

| Finding | Fix |
| --- | --- |
| Parallel requests could all pass the "locked?" check before any failure was counted (5-try limit bypassed) | The whole check → BCrypt → count runs under one lock per name (`LoginThrottle.guarded`) |
| After a lock expired, the count stayed at 5, so one typo locked again | An expired lock starts over from zero (new `LoginThrottleTest`) |
| The attempts map could grow forever with made-up names | Capped at 10,000 entries; expired/unlocked entries purged first |
| Login matched `lower(btrim(name))`, the unique index is `lower(name)`: two rows could match → 500 | Same expression as the index (`lower(display_name) = lower(btrim(:name))`) |
| `maxInactiveInterval` isn't a 12-hour shift: background requests keep it alive | `LOGIN_AT` in the session; after 12 hours every request gets `401 {"expired":true}` and the session ends (new test) |
| CSRF token not rotated at login | Login clears the XSRF cookie; the app fetches a new one |
| XSRF cookie not `Secure` behind Render's proxy | `server.forward-headers-strategy: native` |
| `db-seed.sh` put the owner URL on the docker command line (visible in `ps`) | Passed by name through the environment |
| `me`/`unlock` repeated the principal lookup | Use `CurrentStaff` |
| Anyone can keep a staff member locked out (no per-client limit) | **Deferred:** needs per-IP limits behind Render's proxy; after the deadline |

**Mistake while fixing (caught by the tests before any commit):** my first attempt registered a custom CSRF repository bean, which stopped the XSRF cookie being set (`CsrfFlowTest` failed). While reverting it, a text replacement merged `.securityContext { ... }` into a comment, silently switching it off; the same test failed again and the chain was rewritten cleanly. Final: `./gradlew build` → **19 tests, 0 failed**.

## B3. Registration API

**Date:** 2026-10-20 · **Branch:** `feature/backend-registration` → PR into `develop`

**Done**
- `POST /api/persons` (`RegisterPersonRequest`): only `preferredName` required; shelter and `entered_by` from the login; unknown JSON fields refused (`fail-on-unknown-properties: true`); a re-sent form (same browser-made `id`) returns **200** with the same person, a new one **201**; paper forms need sheet/line (database) **and** the paper's date (service); a pre-printed `slipCode` is attached in the same transaction, only while free (otherwise the whole registration is rolled back).
- `POST /api/persons/{id}/slip`: new code (`SecureRandom`, no 0/O/1/I, retries on a taken code) + 4-digit PIN shown **once**, BCrypt-stored; second slip → 409 "ask a lead to reprint"; audit `CODE_ISSUED`. Returns the shelter name and desk phone for printing.
- `GET` / `PUT /api/persons/{id}` (the Edit button): own shelter only, anything else → 404 "Person not found."; `updated_by` always from the login (covers the step 3 review note on history attribution).
- `ApiErrors`: validation, unreadable JSON and database CHECK violations → the same plain sentences the frontend uses.
- `AuditService`: inserts only `staff_id`, `action`, `target`.
- springdoc-openapi 3.1.1 → public `/api/openapi.json` (for the frontend's generated types).

**Changed from the plan:** edit is `PUT` (the frontend sends the whole form), not `PATCH`.

**Tests:** `./gradlew build` → **29 tests, 0 failed** (10 new): safe defaults with shelter/volunteer from the login · re-send stores nothing new · `ssn` field refused · plain-sentence errors (blank name, paper without sheet/line, paper without date, 9-year-old marked adult) · paper fields kept · slip once, PIN never stored in clear, audited, second slip 409 · pre-printed slip attached only while free, failed attach rolls back · another shelter's person = 404 · edit recorded with the editor from the login · no login = 401, OpenAPI public and without `ssn`.

**Test problem found and fixed:** the real CSRF flow test failed only when run after `AuthTest`. Narrowed down by running pairs of test classes: spring-security-test's `csrf()` helper swaps the app's CSRF token store for a test one inside the **shared** test context, after which no request gets a real cookie. Not an app bug (the real server sets the cookie). The test now runs in its own fresh context (`@DirtiesContext`).

**Real run against Neon** (`scripts/run-backend.sh` + curl with the real CSRF flow, as Volunteer Riley at Eastside):
```
login                                  → HTTP 200
register "Rosa Rodriguez", ~80, Seaview → HTTP 201  (fictional; she's the person in the PROPOSAL.md demo script)
same form again                        → HTTP 200 (same person, nothing new)
slip                                   → {"code":"HW-HR9K","pin":"****","shelterName":"Eastside Community Center","deskPhone":"555-0102"} HTTP 201
unknown field "ssn"                    → {"message":"Something in this form isn't allowed. Please check it and try again."} HTTP 400
```

**Review** (`/code-review`, Codex over its limit; each finding checked against the code before fixing):

| # | Finding | Result |
|---|---|---|
| 1 | Slip retry after a taken code ran in an aborted transaction (PostgreSQL rejects every statement after an error) | **Fixed:** `INSERT ... ON CONFLICT (code) DO NOTHING` + row count; only the one-per-person index still raises (→ 409) |
| 2 | An edit that left out `visibility` / `unaccompanied` reset them to the defaults (could turn "hidden" back into "ask") | **Fixed:** both `@NotNull` on edit; test: edit without the choice → 400, person stays hidden |
| 3 | Lower-case or spaced slip codes (`hw-5m8r`) refused | **Fixed:** case and spaces ignored, look-alike letters still refused; test |
| 4 | A re-send carrying a slip code didn't attach it when the first send had none | **Fixed:** attached when the person has no slip yet; test |
| 5 | `null` in `otherNames` crashed (500) | **Fixed:** nulls and blanks dropped; max 10 names, 100 characters each; test |
| 6 | Paper checks only in the database for sheet/line | **Fixed:** service also checks sheet/line and date, same plain sentences |
| 7 | Edit skipped the name length checks | **Fixed** |
| 8 | Two round trips (find, then update) on edit | Deferred: correct, only slower; fine at shelter scale |
| 9 | "Has an active slip" SQL written twice | Deferred: one place after B4 adds lost-slip handling |
| 10 | Packages are by feature (`person/`, `audit/`, `security/`), not the `web/service/repo` layout in CLAUDE.md | Docs: CLAUDE.md layout updated |

**After the fixes:** `./gradlew build` → **31 tests, 0 failed**; `scripts/db-test.sh` → all passed.

## B4. Matching API

**Branch:** `feature/backend-matching` · planned in plan mode with DB steps 4–5, B5 and F3–F4.

**Done** (`match/` package):
- `POST /api/searches` `{id, searcherPersonId, soughtNames, ageMin, ageMax, relationship, lastKnownPlace}`: the person searching is registered first with the existing `POST /api/persons` (same id rules, slip, privacy choice), and must be at this desk. Re-sent form (same id) → 200, new → 201. Matching runs, audit `SEARCH`.
- `GET /api/searches/{id}` ("Check again"): own desk only (else 404), runs matching again so someone registered later still gets suggested; never the same person twice (UNIQUE).
- `POST /api/searches/{id}/requests` `{personId, message}`: only a person this search suggested **and** matching may still find; marks the suggestion confirmed, saves the message on the search, makes a `pending` contact request (a re-send returns the same one), audit `CONTACT_REQUEST`.
- `MatchingService`: `0.6 × spelling + 0.15 per sound-alike word (max 2) + 0.2 nickname + 0.1 right age − 0.15 wrong age`, suggestions from 0.35, top 10; reasons in plain words ("name spelled like…", "name sounds like…", "a nickname matches…", "age fits (about 80)", "a child: a lead decides before anything is shared"). Never suggests the searcher to themselves.
- `MatchCard` has **no** shelter, home area, phone, description or paper fields, and cards are read **through** `search_candidates`, so someone hidden or protected after being suggested drops out.
- `isMinor` named once in the OpenAPI (`@get:`/`@param:JsonProperty`), the F0 note.

**Changed from the plan:** the searcher is registered with the existing person endpoint instead of inside the search request (one registration path, same rules).

**Tests:** `./gradlew build` → **36 tests, 0 failed** (5 new in `SearchApiTest`): a misspelling at another shelter is found with reasons, and the reply contains none of `Eastside`, `Seaview`, the phone, the desk phone, `shelter`, `homeArea`, `phone` · hidden people never suggested, nor the searcher themselves · "Check again" finds a paper form typed in later, once · another desk gets 404 · a request only to a suggested match, `pending`, stored once, both sends audited · the searcher must be registered at this desk · OpenAPI has `isMinor` and no stray `minor`.

## B5–B6

_Not started (re-planned in plan mode when reached)._
