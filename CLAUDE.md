# CLAUDE.md

Guidance for Claude (and other AI assistants) working in this repository.

## Working mode: manual only

- This project runs in **manual mode**, never auto mode.
- **Ask for permission before every change**: file edits, new or deleted files, commands that modify anything, commits, pushes and deploys. Show what will change and wait for a "yes".
- Read-only actions (reading files, listing, searching) don't need permission.

## Planning and implementation: plan mode first, always

- **Every implementation phase starts in plan mode.** Plan first, get the plan approved, then execute. Never start building a phase without an approved plan.
- **Order:** the design docs (`DATABASE.md`, `BACKEND.md`, `FRONTEND.md`) set the architecture. Building then goes **one feature at a time across all three layers** (database → backend → frontend, then test the whole flow): registration first, then safe matching, then consent and protection, then a complete reunion test.
- **Safety before search:** login, the "Ask me first" default and access rules are in place **before** any search is reachable.
- **Two files per layer:**
  - `DATABASE_PLAN.md`, `BACKEND_PLAN.md`, `FRONTEND_PLAN.md`: the approved plan for that layer, step by step.
  - `DATABASE_IMPLEMENTATION.md`, `BACKEND_IMPLEMENTATION.md`, `FRONTEND_IMPLEMENTATION.md`: a record of how each step was actually built: what was done, commands run, test results (with output), and anything that changed from the plan and why.
- Update the `_IMPLEMENTATION.md` file after each step, not at the end.

## What this project is

**Homeward** is a shelter-run family reunification app for the Fullstack Challenge 26F scenario: a hurricane evacuation of ~400,000 people, families separated across dozens of shelters, many people with **no phone, no ID**, and some who **do not want to be found**.

The app is operated by **shelter volunteers on behalf of survivors**. It covers: intake ("I'm here" / "I'm looking for someone") → fuzzy matching across shelters → consent-gated contact → reconnect. Read `PROPOSAL.md` for the full design, users, edge cases and scope, and `BACKEND.md`, `DATABASE.md` and `FRONTEND.md` for how each hard requirement maps to the backend, database and frontend, before making product decisions. The schema lives in the Flyway migrations in `server/src/main/resources/db/migration/` (`V1`–`V3`, built step by step; see `DATABASE_PLAN.md`); `db/draft/full_schema_draft.sql` is the complete tested draft they're split from.

## Privacy and safety invariants: never break these

These are the core of the product. Every change must keep them, and each one must have tests.

1. **Never return a person's location (shelter, address, phone) in search or match results, ever.** A location is only given through the contact plan of a `contact_request` that is `connected` ("findable" only means that "Connect" can be pressed without asking the person again).
2. **Identical responses for "no match", "hidden", "declined" and "pending".** Same status code, same body shape, same wording, and no timing or count differences you can easily avoid. Do not add fields like `matchCount` to searcher-facing responses.
3. **Default visibility is `ask`.** Never default to `findable`.
4. **`safety_flags` override everything**, including `findable`.
5. **Minors (`is_minor`) are only released with a lead/supervisor sign-off.** A name match alone never releases a minor.
6. **Do not collect** legal ID numbers, SSN, immigration/citizenship status, or photos. Do not make legal name required. Don't add these fields "for later".
7. **Audit-log every search and every reveal** (`audit_log`: who, what, when).
8. **The system suggests, people decide.** No automatic merges or reveals from matching scores.
9. **Seed/demo data is fictional only.** Never use real people's names or details.

If a requested change would break one of these, stop and flag it instead of implementing it.

## Users and UX rules

- Main user: a **non-technical, tired volunteer** at 2 a.m. Design for that.
- Intake should take **under 2 minutes**. Only `preferred_name` (name *or* nickname) is required; everything else is optional and forgiving (approximate ages, "unknown").
- Use **plain language** in the UI ("If someone comes looking for you, what should we do?"), not jargon ("visibility setting").
- Large tap targets, readable on a tablet, works with keyboard only. One main action per screen.
- Forms keep a local draft so a dropped connection never loses typed data.
- Keep UI strings in one place (i18n-ready); English first.

## Tech stack

- **Database:** PostgreSQL 18 on Neon with `pg_trgm`, `fuzzystrmatch` and `unaccent`; Flyway migrations in `server/src/main/resources/db/migration/` (`V1__base_tables.sql`, then `V2`/`V3` in later steps). Test with `scripts/db-test.sh` (Docker, PostgreSQL 18.6); apply with `scripts/db-migrate.sh migrate`
- **Backend:** Kotlin + Spring Boot (`server/`): Spring Web, Spring Security, Spring JDBC (**not** JPA/Hibernate), Bean Validation, Flyway, springdoc-openapi. Gradle Kotlin DSL
- **Frontend:** React + Vite + TypeScript (`client/`): React Hook Form + zod, TanStack Query
- **API contract:** OpenAPI from springdoc → TypeScript types generated with openapi-typescript into `client/src/api/schema.ts` (never edit by hand)
- **Tests:** JUnit 5 + Testcontainers (real Postgres) + MockMvc on the backend; Vitest on the frontend
- **Deploy:** Netlify (frontend, proxies `/api/*`) → Render (backend, Docker) → Neon (Postgres)

Why this stack: see `PROPOSAL.md` section 11, and `DATABASE.md`, `BACKEND.md`, `FRONTEND.md` for the full comparisons.

## Planned layout

```
client/                          React app (pages: Login, Home, Register, Search, CheckCode, Answers, Lead, Print)
  src/api/schema.ts              generated from OpenAPI, do not edit
  src/strings.ts                 all UI text (i18n-ready)
server/                          Kotlin + Spring Boot
  src/main/kotlin/app/homeward/
    (one package per feature; each holds its controller, DTOs, service and JDBC repo)
    web/                         shared web pieces: health, plain-sentence errors (ApiErrors)
    security/                    Spring Security config, PIN login, roles, idle lock
    person/                      registration, edit, slips (ReunionCodeService)
    audit/                       AuditService
    match/, consent/             later: MatchingService, VisibilityService, ConsentService; searcher-facing types in their own dto/searcher/
  src/main/resources/db/migration/   Flyway SQL migrations
  src/main/resources/db/seed/        fictional seed data
  src/test/kotlin/               JUnit + Testcontainers tests
docs/                            screenshots, paper form (planned)
PROPOSAL.md                      Design proposal
BACKEND.md, DATABASE.md, FRONTEND.md   Why each part was chosen and how it meets each requirement
AI_NOTES.md                      How AI tools were used (required by the challenge)
Homeward_Design.pptx             Design deck (database, backend and frontend)
README.md                        Submission README (overview, full picture, design, technical choices)
```

## Commands

```bash
# database (from the repo root; needs Docker: `colima start`)
scripts/db-test.sh           # migrations + demo seed + every SQL test on PostgreSQL 18.6 (Neon's version)
scripts/db-test.sh --draft   # the full schema draft and its checks
scripts/db-migrate.sh info   # which migrations are applied on Neon (read-only)
scripts/db-migrate.sh migrate  # apply pending migrations to Neon, as the owner (only after db-test passes)

# backend (needs JDK 21: export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home)
scripts/run-backend.sh       # run the API on :8080 against Neon, as homeward_app (from the repo root)
cd server && ./gradlew build # compile + ktlint + all tests (Testcontainers, PostgreSQL 18.6, app as homeward_app)
cd server && ./gradlew ktlintFormat  # fix formatting

# frontend (from client/)
npm install
npm run dev                  # Vite dev server, proxies /api to :8080
npm run gen:api              # regenerate src/api/schema.ts from the running backend's OpenAPI
npm test                     # Vitest
npm run lint                 # eslint + tsc --noEmit
npm run build                # production build for Netlify
```

Run the backend tests + lint and the frontend lint + tests before every commit.

## Code conventions

- **Kotlin:** use nullable types for anything a survivor may not know (`approxAge: Int?`, `phone: String?`); no `!!` without a comment explaining why. Prefer data classes and sealed types.
- **TypeScript:** `strict`. No `any` without a comment explaining why.
- Controllers stay thin. **What a searcher may see is decided in one place**, `VisibilityService`, and **consent decisions** (connect, safe, say no, child release) in `ConsentService`, so the privacy rules live in two known places. Do not filter location fields ad hoc inside controllers.
- **Searcher-facing responses use their own types** (a `dto/searcher/` package, e.g. `sealed interface SearcherStatus`) that have **no location fields**. Never reuse staff-internal DTOs for searcher-facing endpoints.
- Spring JDBC with **parameterized SQL only**, never string-concatenated queries. No JPA/Hibernate: matching SQL must stay visible.
- Schema changes go in a new Flyway migration (`V<n>__...sql`); never edit an applied migration. The app never migrates: test with `scripts/db-test.sh`, apply with `scripts/db-migrate.sh migrate`.
- Matching returns `MatchCandidate(score, reasons: List<String>)` where reasons are human-readable strings shown to volunteers.
- Keep the backend light for Render's free tier: no unused Spring starters, lazy initialization on.
- Secrets come from env vars (`DATABASE_URL`, `SESSION_SECRET`); never commit `.env`.
- Keep it simple: a small working app beats a large broken one. Don't add dependencies or features outside `PROPOSAL.md` scope without saying so.

## Testing priorities

1. Privacy invariants (one test per rule above, especially identical responses and no location leaks).
2. Matching: misspellings, nicknames, phonetic matches, missing fields, age estimates.
3. Minor release requires supervisor.
4. API validation and auth/role checks.

## Git workflow

### Branch structure

- **`main`**: the submission/release baseline; keep it stable. No direct commits or pushes. Update it only by merging a verified `develop` or `release/*`, so `main` always reflects something that passed review.
- **`develop`**: the integration branch where every feature comes together. Don't commit to it directly; merge via PR only, so every change hits a review gate.
- **`feature/*`**: one branch per unit of work, always branched off `develop`. Name it after the flow it builds (e.g. `feature/intake`, `feature/matching`, `feature/consent`, `feature/reunion-code`) so the branch's purpose is obvious.
- **`release/*`**: cut from `develop` only when freezing for submission; do final fixes and version tagging here, then merge into `main`.

### Workflow (fixed order)

1. Update `develop`, then branch `feature/<name>`.
2. Commit small and often, one line per message (what and why), because small commits are easy to review and to revert when one breaks.
3. Push `feature/*` to the remote.
4. Cross-check the code with GPT (Codex) before opening the PR (see below).
5. Open a PR into `develop`. Never PR directly into `main`, because `main` should only receive reviewed, integrated code.

### GPT (Codex) cross-check

Every code change gets a second opinion from a different model before it is merged, so one model's blind spots don't slip through.

```bash
# Review the whole branch against develop (codex review --base takes no custom instructions)
codex review --base develop

# A focused, read-only check with instructions (e.g. privacy invariants, matching, missing tests)
codex exec -s read-only "Do NOT modify files. Check the privacy invariants in CLAUDE.md, matching correctness, and missing tests."
```

- Verify each Codex finding against the code or a test before acting on it; don't apply suggestions blindly.
- If Claude and Codex disagree, note the disagreement and how it was resolved in the PR description and in `AI_NOTES.md`.

## AI usage notes (required by the challenge)

The challenge asks how we work with AI tools. After each meaningful AI-assisted session, append a short entry to `AI_NOTES.md`:

```
## YYYY-MM-DD: <topic>
- Asked: what I asked the AI to do
- Kept: what I used as-is
- Changed/rejected: what I fixed or threw away, and why
- Learned: anything worth remembering
```

Be honest about what the AI got wrong; that is part of what reviewers want to see.

## Deployment checklist

- Deploy early (milestone 0) and redeploy often.
- Test the live link in a **private browser window** before submitting.
- README must include: overview + deployed link + screenshots, the full picture, design (users, chosen part, edge cases), technical choices + biggest tradeoff, and demo login details.
