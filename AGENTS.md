# AGENTS.md: handoff to Codex (from Claude Code, 2026-10-20 ~3:45 PM)

**Read `CLAUDE.md` first: its rules apply to you unchanged** (manual mode: ask before every change; plan first; git-flow: feature/* → PR into develop, main only via release/hotfix; privacy invariants; fictional data only; never print or commit secrets; log every step in `*_IMPLEMENTATION.md`). Deadline: **today 10:00 PM**.

## Where things stand
- **Live:** frontend https://hilarious-cassata-a51a3c.netlify.app → backend https://homeward-api-fjrc.onrender.com → Neon (schema V5). Full reunion verified live in a fresh browser context.
- **`main`** = v0.3.3 (tags v0.1.0–v0.3.3). **`develop`** = main + README + AI notes (PR #29). Last PR #29.
- Tests: `scripts/db-test.sh` 141 checks · `cd server && ./gradlew build` 51 · `cd client && npm test` 26, `npm run lint` 0 errors.

## Sources (read in this order)
1. `CLAUDE.md` (rules, commands, invariants) · `README.md` (what it does, how to try it)
2. Design: `PROPOSAL.md`, `DATABASE.md`, `BACKEND.md`, `FRONTEND.md`
3. Plans and logs: `DATABASE_PLAN.md` / `DATABASE_IMPLEMENTATION.md`, `BACKEND_PLAN.md` / `BACKEND_IMPLEMENTATION.md`, `FRONTEND_PLAN.md` / `FRONTEND_IMPLEMENTATION.md` (every review table and fix is logged there)
4. Code: `server/src/main/resources/db/migration/V1–V5`, `server/src/main/kotlin/app/homeward/{security,person,match,consent,audit,db,web}`, `client/src/{pages,components,api,forms,hooks}`; tests in `server/src/test/` and `client/src/**/*.test.tsx`
5. `AI_NOTES.md` (append an entry for your session)

## Steps left (in order)
1. **DB step 6 + B6:** `server/src/test/resources/db/06_reunion.sql` (fictional Sarah Kim: registered at North High (a1) → her sister searches at City (a2) → match without location → request → she says yes → contact plan visible; same flow hidden / flagged / a child shows nothing) and one API test of the same flow (`server/src/test/kotlin/.../ReunionApiTest.kt`, reuse helpers in `ConsentApiTest`). Log in `DATABASE_IMPLEMENTATION.md` step 6 and `BACKEND_IMPLEMENTATION.md` B6.
2. **Deck** `Homeward_Design.pptx`: update to what was built (V1–V5, packages `match/`, `consent/`, live links). Builder script was in Claude's scratchpad (gone); edit the pptx directly.
3. **Release 1.0.0:** `release/1.0.0` from develop, bump `server/build.gradle.kts` and `client/package.json`, run all three test suites, PR → main, tag `v1.0.0`, PR back into develop.
4. Optional: printable blank paper form (FRONTEND.md "Kept" list).

## Gotchas
- Docker via Colima (`colima start`); JDK 21: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.
- Port 8080 on this Mac is shared with an SSH tunnel: use `API_TARGET='http://[::1]:8080' npm run dev` and `API_URL='http://[::1]:8080' npm run gen:api`.
- `.env.local` contains an unquoted `&`: don't `source` it; read it with Python (see `scripts/run-backend.sh`).
- Before trusting a new test, break the code once and see it fail (several first-draft tests passed for the wrong reason).
- A searcher registered with only a name counts as a child (unknown age), so "Connect" then needs a lead: give test searchers an age.

## Codex continuation (2026-10-20)
- DB step 6/B6 implemented and independently reviewed: **155 SQL checks / 52 backend tests / 26 frontend tests**, all passing. Both new tests were proven to fail under a deliberate mutation, then restored.
- Neon V5 app-role permission checks passed; Render and Netlify proxy health returned 200. No new browser run in Codex; the fresh-browser reunion above was performed by Claude.
- See the Step 6 and B6 implementation logs for evidence. Deck update and release 1.0.0 are the next handoff items; printable blank form and real-person usability sessions remain optional follow-up work.
