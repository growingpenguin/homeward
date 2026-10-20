# FRONTEND_IMPLEMENTATION.md

How each step of `FRONTEND_PLAN.md` was actually built: what was done, commands, test results, and what changed from the plan.

## F0. Skeleton and deploy setup

**Branch:** `feature/frontend-skeleton`

**Done:**
- `client/` from the Vite 8 React-TS template (made in the scratchpad when the FRONTEND.md code was type-checked), demo assets removed. `strict` TypeScript turned on.
- The 14 checked files from FRONTEND.md copied in unchanged, except three imports that now point to `api/types.ts`.
- `npm run gen:api` → `src/api/schema.ts` generated from the running backend's `/api/openapi.json` (10 paths, 9 schemas). `api/types.ts` gives the generated types short names; `SearcherStatus` stays hand-written there until B4 adds it to the backend.
- `vite.config.ts`: dev proxy `/api` → `localhost:8080` (override with `API_TARGET`), Vitest with jsdom and jest-dom.
- `client/netlify.toml`: build, `/api/*` proxied to Render (status 200, so cookies stay first-party), SPA fallback.
- `styles/tokens.css` (from FRONTEND.md), `styles/print.css` (slip), placeholder `App` with a smoke test.

**Versions:** vite 8.3.4, react 19.3.1, typescript 6.0.3, vitest 5.0.5, @testing-library/react 16.3.3, jsdom 30.1.3, react-router 7.18.4, @tanstack/react-query 5.104.5, react-hook-form 7.90.0, zod 3.23.8, oxlint 1.89.0.

**Changed from the plan:**
- **openapi-typescript is not a dependency.** 7.13.0 (the latest) only accepts TypeScript 5 as a peer and npm refuses it next to TypeScript 6. It only generates a file, so `gen:api` runs it through `npx` with its own TypeScript instead of forcing the install with `--legacy-peer-deps`.
- **Vitest 5**, not 3: Vitest 3 doesn't accept Vite 8 (the earlier ERESOLVE).
- **Lint is oxlint + `tsc`** (the template's linter), not ESLint.
- **Port 8080 on this Mac is shared with an SSH tunnel** (IPv4), so `localhost:8080` can reach the tunnel instead of the backend. Generated with `API_URL='http://[::1]:8080' npm run gen:api`; for dev, `API_TARGET='http://[::1]:8080' npm run dev`.
- `netlify.toml` uses `homeward-api.onrender.com` (the service name in `render.yaml`); to confirm once the Render service is live.

**Tests:**
```
npm run lint   → 0 errors, 3 warnings (in the reused hooks: useDraft setState in effect, useIdleLock Date.now in render, CodeKeypad exports a helper); fixed in F1 where the hooks are wired in
npm test       → 1 file, 1 test passed
npm run build  → built in 240ms, JS 222 kB (70 kB gzip)
```

**Noted for B4:** the generated `RegisterPersonRequest` also lists `minor` next to `isMinor` (springdoc reads the Kotlin `isMinor` getter as a second property). Harmless, but the request type should only show `isMinor`.

**Review** (`/code-review`; each finding checked against the code first). All 10 are in the reused FRONTEND.md files or deploy config, not the new skeleton:

| # | Finding | Result |
|---|---|---|
| 1 | Paper date sent as midnight UTC, so it reads as the day before in US time zones | **Fixed:** sent as noon UTC; test |
| 2 | Netlify's proxy gives up after about 26 s, and a sleeping Render service can take longer to wake, so the first login can fail | F1: wake the backend (`/api/health`) while the login page loads, and retry a login that got 502/504 |
| 3 | The client counts typing as activity, but the server only counts requests with `X-Homeward-Activity`: a long form with no requests gets locked on the server only | F1: send a small activity call while the volunteer is typing |
| 4 | Locking clears paused saves (`queryClient.clear()`) | F1: clear only queries, keep paused saves |
| 5 | `normalizeCode` cut real characters from codes starting with HW (`HWABC` → `ABC`) | **Fixed:** strip `HW` only from a 6-character `HW7K3P`; test |
| 6 | The slip-code rule needed `HW-`, but the keypad gives 4 characters | **Fixed:** both accepted, always sent as `HW-XXXX`; test |
| 7 | Client misses some backend limits (10 other names, phone 40, …); blank other-name row shows zod's English | F2, where the form is built |
| 8 | Skipped optional fields sent as `""` and stored as empty strings | **No change:** the backend stores blanks as NULL (`PersonRepo.kt` `blankToNull()` on home area, description, phone, paper ref) |
| 9 | Generated response fields are all optional (springdoc doesn't mark Kotlin non-null fields required) | F1: mark them required on the backend before the pages use them |
| 10 | `freshOnly` shows "Checking…" forever after an error | F3, with `SearcherView` |

FRONTEND.md updated for #5 and #6.

**After the fixes:** `npm run lint` 0 errors (same 3 warnings) · `npm test` 2 files, 4 tests passed · `npm run build` passed.

## F1–F4

_Not started._
