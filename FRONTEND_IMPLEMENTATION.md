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

## F1–F4

_Not started._
