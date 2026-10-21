# FRONTEND_PLAN.md

Approved in plan mode on 2026-10-20. Progress is logged in `FRONTEND_IMPLEMENTATION.md`.

## Plan

### Context

The backend can now log volunteers in (B2) and register people with printed slips (B3, in review), tested against Neon. The registration slice needs its frontend half before the first working feature can ship (release 0.2.0). The deadline is **10:00 PM today**; the user approved one approval per milestone, `/code-review` instead of Codex until Codex is back, and git-flow throughout. Following CLAUDE.md, the frontend gets its own plan (`FRONTEND_PLAN.md`) and log (`FRONTEND_IMPLEMENTATION.md`).

The design is already in FRONTEND.md, and its 14 code files were type-checked earlier (still in the scratchpad: `strings.ts`, `BigButton`, `SayThis`, `VisibilityChoice`, `SearcherView`, `CodeKeypad`, `ConnectionBanner`, `AppShell`, `registerSchema`, `toRequest`, `useDraft`, `useIdleLock`, `queryClient`, `http`). This plan **reuses them as they are** instead of rewriting.

### Versions (checked 2026-10-20)

Vite 8 + React 19 (`npm create vite@latest -- --template react-ts`), TypeScript strict, react-router, `@tanstack/react-query` 5, `react-hook-form` 7, **zod 3.23.8** (the version the FRONTEND.md code was checked with), `openapi-typescript` (types from the backend's `/api/openapi.json`), Vitest + Testing Library. Node 26 / npm 11 are installed.

### Steps (git-flow: one `feature/frontend-*` branch each → tests → `/code-review` → PR into develop)

### F0. Skeleton and deploy setup — `feature/frontend-skeleton`
- `client/` from the Vite React-TS template; strict TS; ESLint as generated.
- Copy the 14 checked files into `client/src/`; `npm run gen:api` → `src/api/schema.ts` from the running backend's `/api/openapi.json` (replaces the hand-written stand-in; files adjusted only where the real types differ, logged).
- `vite.config.ts` dev proxy `/api` → `http://localhost:8080`.
- `client/netlify.toml`: build `npm run build`, publish `dist`, redirect `/api/*` → `https://<render-url>/api/:splat` (200, proxied, so cookies are same-site), SPA fallback `/* → /index.html`.
- `styles/tokens.css` + `print.css` from FRONTEND.md.
- Tests: Vitest runs; `npm run build` passes.

### F1. Login, home and the safety shell — `feature/frontend-login`
- `api/client.ts`: `fetch` wrapper — `credentials: 'same-origin'`, reads the `XSRF-TOKEN` cookie into `X-XSRF-TOKEN` on writes, calls `/api/csrf` first and again after login, adds `X-Homeward-Activity: 1` only on user-triggered calls, maps `401 {locked}` → lock screen and `401 {expired}` / plain 401 → login.
- Pages: **Login** (shelter dropdown from `/api/shelters`, name, PIN, plain errors incl. "Too many tries"), **Home** (3 big buttons; Search / Check a code shown as "coming next" until F3), **Lock screen** via `AppShell` + `useIdleLock` (unlock through `/api/unlock`), logout.
- `ConnectionBanner` ("Connecting. Please wait" while the free backend wakes).
- Tests: login form errors; lock screen replaces the app; activity header only on user actions.

### F2. Register flow and slip — `feature/frontend-register`
- **Register** (one question per step: name → age (+ "Is an adult with them?") → privacy cards → optional extras (home area, anything that helps recognise them, phone) → check & save); **paper mode** switch before the first form (adds the sheet/line + date + slip-code step and keeps the date between forms); `useDraft` keyed by the logged-in volunteer; `toRegisterRequest` keeps the same id across retries.
- After save: **Print slip** page (`POST /api/persons/{id}/slip`, code + PIN shown once, desk phone, print CSS); pre-printed slip shows "already has a slip".
- **Edit** a saved person (`PUT /api/persons/{id}`).
- Tests: only the name is required; paper step needs sheet/line + date; re-submit after a network error re-sends the same id; slip shows the PIN once.
- **Release 0.2.0** (develop → `release/0.2.0` → main): first working feature live on Netlify + Render.

### F3–F4 (outline; planned in plan mode together with DB steps 4–5 and B4–B5)
- **F3:** Search (match cards with reasons, never a location; searcher's slip every time), Answers waiting (Connect / Safe / Not now / Say no; findable = yes in advance), Check a code (`CodeKeypad` + PIN), `SearcherView`.
- **F4:** Lead page (children, flags, lost slips), README screenshots, private-window test.

### You (once, ~3 minutes)

- Tell me the Render URL when the service is live (needed for `netlify.toml`).
- Netlify: **Add new site → Import from Git → `growingpenguin/homeward`**, base directory `client`, branch **`main`** (Netlify reads the rest from `netlify.toml`). I'll say when `main` has release 0.2.0.

### Files

- New: `FRONTEND_PLAN.md`, `FRONTEND_IMPLEMENTATION.md`, `client/` (Vite project), `client/netlify.toml`.
- Synced: CLAUDE.md commands (real `npm` scripts), FRONTEND.md (only where real code differs from the design snippets), AI_NOTES.md (end of day).

### Verification

- Every step: `npm run lint`, `npm test`, `npm run build` green; backend `./gradlew build` and `scripts/db-test.sh` stay green.
- F1–F2 by hand against the real backend (`scripts/run-backend.sh` + `npm run dev`): log in as Volunteer Alex, register a fictional person in normal and paper mode, print the slip, reload mid-form (draft kept), wait for the lock screen (shortened in dev only), log out.
- After release 0.2.0: the same on the live Netlify link in a **private window**.
- Codex unavailable → `/code-review` before each PR; findings verified before fixing; everything logged in FRONTEND_IMPLEMENTATION.md.
