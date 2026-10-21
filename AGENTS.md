# AGENTS.md: handoff to Codex (from Claude Code, 2026-10-20 ~3:45 PM)

**Read `CLAUDE.md` first: its rules apply to you unchanged** (manual mode: ask before every change; plan first; git-flow: feature/* → PR into develop, main only via release/hotfix; privacy invariants; fictional data only; never print or commit secrets; log every step in `*_IMPLEMENTATION.md`). Deadline: **today 10:00 PM**.

## Where things stand
- **Live:** frontend https://hilarious-cassata-a51a3c.netlify.app → backend https://homeward-api-fjrc.onrender.com → Neon (schema V5). Full reunion verified live in a fresh browser context.
- **`main`** = v0.3.3 (tags v0.1.0–v0.3.3).
- Tests: `scripts/db-test.sh` 155 checks · `cd server && ./gradlew build` 52 · `cd client && npm test` 26, `npm run lint` 0 errors.

## Sources (read in this order)
1. `CLAUDE.md` (rules, commands, invariants) · `README.md` (what it does, how to try it)
2. Design: `PROPOSAL.md`, `DATABASE/DATABASE.md`, `BACKEND/BACKEND.md`, `FRONTEND/FRONTEND.md`
3. Plans and logs: `DATABASE/DATABASE_PLAN.md` / `DATABASE/DATABASE_IMPLEMENTATION.md`, `BACKEND/BACKEND_PLAN.md` / `BACKEND/BACKEND_IMPLEMENTATION.md`, `FRONTEND/FRONTEND_PLAN.md` / `FRONTEND/FRONTEND_IMPLEMENTATION.md` (every review table and fix is logged there)
4. Code: `server/src/main/resources/db/migration/V1–V5`, `server/src/main/kotlin/app/homeward/{security,person,match,consent,audit,db,web}`, `client/src/{pages,components,api,forms,hooks}`; tests in `server/src/test/` and `client/src/**/*.test.tsx`
5. `AI_NOTES.md` (append an entry for your session)

## Gotchas
- Docker via Colima (`colima start`); JDK 21: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.
- Port 8080 on this Mac is shared with an SSH tunnel: use `API_TARGET='http://[::1]:8080' npm run dev` and `API_URL='http://[::1]:8080' npm run gen:api`.
- `.env.local` contains an unquoted `&`: don't `source` it; read it with Python (see `scripts/run-backend.sh`).
- Before trusting a new test, break the code once and see it fail (several first-draft tests passed for the wrong reason).
- A searcher registered with only a name counts as a child (unknown age), so "Connect" then needs a lead: give test searchers an age.

## Codex continuation (2026-10-20)
- DB step 6/B6 implemented and independently reviewed: **155 SQL checks / 52 backend tests / 26 frontend tests**, all passing. Both new tests were proven to fail under a deliberate mutation, then restored.
- Neon V5 app-role permission checks passed; Render and Netlify proxy health returned 200. No new browser run in Codex; the fresh-browser reunion above was performed by Claude.
- See the Step 6 and B6 implementation logs for evidence. Deck and README update merged in PR #31. Release 1.0.0 is prepared with passing checks; see the release PR/tag for final merge and deployment status. Printable blank form and real-person usability sessions remain follow-up work.
