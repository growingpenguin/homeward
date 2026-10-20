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

## F1. Login, home and the safety shell

**Branch:** `feature/frontend-login`

**Done:**
- `api/client.ts`: one `api()` for every call: same-origin cookies; on a write, the CSRF cookie's value in `X-XSRF-TOKEN` (calls `/api/csrf` first if there's no cookie, e.g. right after login); `X-Homeward-Activity: 1` only for a person's actions; `401 {locked}` → "locked" event, `401 {expired}` or a plain 401 (outside login/unlock and the startup check) → "expired" event.
- **Login** page: shelter list, name, PIN; plain sentences for missing details, wrong details and "too many tries"; PIN box emptied after every try.
- **Home**: the three big buttons, shown as "Coming next" until F2/F3; "Logged in as …" and **Log out**.
- `App`: the session comes from `/api/me`; "expired" or Log out forgets everything loaded and shows the login; logged in, everything sits inside `AppShell` (idle lock).
- Review items from F0 handled here:
  - **#2** (Netlify gives up after ~26 s while Render wakes): the login page wakes the backend (`/api/health`, shelters) while the volunteer types, and a login with no answer (network, 502–504) is tried again up to 3 times, 5 s apart; a wrong PIN is never retried.
  - **#3** (server locks while someone types a long form): typing tells the server once a minute (`stillHere` = `/api/me` with the activity header); when the server says locked, the app locks too.
  - **#4** (lock dropped saves waiting for the internet): `removeQueries()` instead of `clear()`, so paused saves are kept.
  - **#9** (generated response fields all optional): `Required<>` on response types in `api/types.ts`; nullable fields stay `| null`.
- Lock screen shows the backend's sentence for "too many tries" instead of "no connection". Lint warning in `useIdleLock` (`Date.now` during render) fixed.

**Tests:** `npm run lint` 0 errors (2 warnings left, in code F2 wires in) · `npm test` 2 files, **7 passed**:
login refuses missing details and shows the backend's wrong-login sentence, PIN box emptied, CSRF header and body sent · logged in shows the three actions · the server's `locked` replaces the whole app with the lock screen · the server's `expired` goes back to the login · only `stillHere` carries the activity header.

**Real run** (backend against Neon + `npm run dev`, Playwright browser, fictional Volunteer Alex, demo PIN):
```
GET  /api/me       401  (nobody logged in yet → login page)
GET  /api/health   200, GET /api/shelters 200 (3 demo shelters in the list)
POST /api/login    200  name typed as "volunteer alex" → "Logged in as Volunteer Alex", three buttons
reload /anything   → still logged in, back on Home
POST /api/logout   204  → login page
```
The 10-minute lock itself wasn't waited out by hand; the server side is tested in B2 (`AuthTest`), the client side by the test above.

**Review** (`/code-review`; all 10 checked against the code and the backend's `security/` package, all real):

| # | Finding | Fix |
|---|---|---|
| 1 | `/api/me` answering `401 {locked}` (focus refetch, reload) showed the login instead of the lock screen | `getMe` returns `"locked"`; the app opens on the lock screen; no refetch on focus |
| 2 | Locking removed the `["me"]` session query too | Lock keeps `me` |
| 3 | A failed background refresh of `me` replaced the whole app with "no connection" | Only when there's no data yet |
| 4 | After the free server restarted, unlock got a plain 401 and said "wrong PIN" forever | A 401 without a sentence = session gone → login |
| 5 | Log out showed the login even when the server never got the request (session still alive for the next person) | Login page only after the server answers; otherwise "Couldn't log out… you are still logged in" |
| 6 | Logout didn't clear drafts or unsent saves (could post later under someone else) | `clearAllDrafts()` on Log out; unsent saves dropped when the session ends |
| 7 | A failed `/api/csrf` sent the write without a token → unexplained 403 | Reported as "no answer" (retried for login) |
| 8 | Typing the PIN on the lock screen counted as activity (re-locked, pinged the server) | Activity ignored while locked |
| 9 | PIN stayed in the box during retries and in the mutation cache after | Box emptied on submit, mutation reset when settled. Not fixed: a retry after a 504 that the server did count can add one more failure toward the 15-minute lockout (rare; noted) |
| 10 | Extra `/api/health` request on the login page | Removed: the shelter list wakes the backend |

**After the fixes:** `npm run lint` 0 errors · `npm test` **10 passed** (new: reload onto a locked server opens the lock screen and the right PIN opens the app · unlock after the server forgot the session goes to the login · a failed log out keeps you logged in and says so) · `npm run build` passed.
**Real run again** (Volunteer Riley, Eastside): `GET /api/me 401` → `POST /api/login 200` → Home → `GET /api/csrf 200` (login had cleared the cookie) → `POST /api/logout 204` → login page.

## F2. Register flow and slip

**Branch:** `feature/frontend-register`

**Done:**
- **Register** (`/register`): first "Are you copying paper forms?", then one question per screen with "Step n of N": (paper: sheet/line, date, slip code) → name and other names → age, "under 18?", and "is an adult from their family with them?" (only when they may be a child) → the three privacy cards (Ask me first selected) → optional extras → check and save. react-hook-form with the existing zod schema; `forms/PersonFields.tsx` holds each step's questions (shared with Edit).
- Every change goes into the draft (`useDraft`, per volunteer) with the record id made when the form opened; a save that failed for network/5xx is retried with the **same id**. Paper mode keeps paper mode and the date for the next form.
- **Slip** (`/persons/:id/slip`): "Print a slip" → code, PIN (shown once, `gcTime: 0` so it's dropped when the screen closes), shelter, desk phone, print CSS prints only the slip; a person who already has one shows "already has a slip". Links: fix a mistake, next person, home.
- **Edit** (`/persons/:id/edit`): the same questions on one page; always sends the privacy choice and "adult with them" (the backend refuses an edit without them).
- F0 review #7: the backend's limits (10 other names, 100/200/40/100 characters) checked in the form with plain sentences; blank other names are dropped instead of failing in zod's English.

**Changed from the plan:** `zodResolver` needs one cast (`personResolver` in `registerSchema.ts`): @hookform/resolvers 3 predates react-hook-form's input/output types, and version 5 needs a newer zod than the 3.23.8 FRONTEND.md was checked with.

**Tests:** `npm run lint` 0 errors · `npm test` 3 files, **14 passed** (new: only the name is required, Ask me first is sent by default, a 503 then success re-sends the same id · paper step refuses to go on without sheet/line and date · a reload keeps the draft with the same record id · the slip shows code and PIN after "Print a slip").

**Real run** (backend against Neon + `npm run dev`, Playwright, Volunteer Alex, fictional people):
```
POST /api/login 200 → Someone is here → "Tomas Brightwater", others "Tommy, T.B.", 67, 18 or over, Keep me out of searches, Harbor Point
POST /api/persons 201 → POST …/slip 201 → slip: Lincoln High Shelter, HW-DFP3, PIN ****, 555-0101
Fix a mistake → phone 555-0199 → PUT 200 → stored: otherNames [Tommy, T.B.], age 67, isMinor false, visibility hidden (kept), phone 555-0199, hasSlip true
Next person → paper mode → Next without details: both paper sentences shown → "Sheet 2, line 7", 2026-10-18, "Nell Okafor-Reyes" → POST 201
stored: source paper, paperRef "Sheet 2, line 7", recordedAt 2026-10-18T12:00:00Z → next form opens in paper mode with the date kept
```

**Review** (`/code-review`; all 10 checked against the code, all real):

| # | Finding | Fix |
|---|---|---|
| 1 | **Paper mode: the next form kept the saved person's record id** (the reset went into state, but the page closed before the draft was written), so the next person was answered with the first one and never stored. The real run above only checked the date and the step, so it missed this | `useDraft.reset()` writes the next form straight to storage; test: the next form has a new id and no name |
| 2 | A re-send after a lost reply, with changes made in between, got the first version back (200) and looked saved | The reply is compared with what was sent; if it differs, Edit opens with "already saved earlier, check the details"; test |
| 3 | "Is an adult from their family with them?" had **Yes** picked in advance | Nothing picked; required for a known child (marked under 18 or age under 18); optional when nothing is known about the age, so only the name stays required; test |
| 4 | "Not sure" (`undefined`) made the radio jump back to the starting value on Edit | "Not sure" is `null` |
| 5 | A hidden "adult with them" answer was still sent for an adult | Sent as false whenever they can't be a child (`unaccompaniedToSend`) |
| 6 | Drafts stayed when the session ended (only Log out cleared them) | Cleared whenever the session ends |
| 7 | A save that finished after leaving the page (e.g. back online) could jump to another screen | Callbacks on `mutate()`, so they only run while the page is open |
| 8 | Back to the slip after printing showed "Print a slip" again (→ 409) | The cached person is marked `hasSlip` |
| 9 | Check step didn't show the "adult with them" answer or the paper fields | Added |
| 10 | A new UUID made on every render; the id could change between retries if a draft had none | Initial form and id made once (`useState`) |

FRONTEND.md updated (the two schema lines).

**After the fixes:** `npm run lint` 0 errors · `npm test` **17 passed** · `npm run build` passed.
**Real run again** (paper mode, Volunteer Casey, Westfield): two forms in a row, "Ines Marlowe" and "Bao Castellanos" → `POST /api/persons` **201, 201** with **2 different ids**; the third form opens empty.

## F3. Search, Answers waiting, Check a code

**Branch:** `feature/frontend-search` (planned in plan mode with DB steps 4–5 and B4–B5).

**Done:**
- `api/types.ts`: search, consent and lead types from the regenerated schema. The searcher's answer is the union of the backend's three subtypes (`Active | Connected | Safe`), so `SearcherView`'s exhaustive `switch` breaks the build if a fourth ever appears.
- **Search** ("Someone is looking for family"): who is looking (name + "Is the person looking 18 or over?", because with no answer they count as a child and a lead decides) → registered with `POST /api/persons` → who they're looking for (names, age range, relationship, last place) → match cards with reasons and "a child: a lead decides", never a place; the screen says *"Don't say whether anyone is here"* → "Ask this person" with a message (with the reminder not to write where they are) → **always** ends with the searcher's slip. Draft kept, both ids made once (same id on every retry).
- **Answers waiting**: who is looking and why, the message to read out (`SayThis`), *Connect with …* (asks how they'll meet, then shows exactly what will be shared before "Yes, share this"), *Tell … I'm safe*, *Not now*, *Say no*; a child → "a lead decides", no Connect button. Home shows the count.
- **Check a code**: `CodeKeypad` + PIN (PIN box emptied) → the holder's name, "Turn the screen around" (one search at a time, each status fetched fresh) and "Answer now" for requests waiting for them; "Finished" / the last "Give the screen back" calls `POST /api/lookup/end`.
- F0 review #10: `SearcherView` takes `failed`, so a failed check says "Couldn't check right now" with Try again, never "Checking…" forever.

**Tests:** `npm run lint` 0 errors · `npm test` **21 passed** (4 new): one sentence for every hidden state, a failed check says so · search registers the searcher as an adult, cards show reasons and the warning, the request carries the chosen person and message · Connect shows what will be shared first; a child has no Connect · a miss gets the plain sentence and empties the PIN; a match shows the **fresh** answer (Safe, though the lookup had said Active) and ends the check.

**Real run** (backend against Neon + `npm run dev`, Playwright, fictional people, three shelters):
```
Eastside (Riley):  register "Esperanza Quill…", 78, Ask me first          POST /api/persons 201
Lincoln (Alex):    son "Mateo Quill…" (adult) looks for "Esperansa Quill…" 70–85
                   card: "Esperanza Quill… (about 78) | name spelled like … | name sounds like … | age fits (about 78)"  (no place)
                   request with message → 201, searcher's slip HW-…           POST /api/persons/:id/slip 201
Eastside (Riley):  Answers waiting: "Mateo … is looking for Esperanza …" | Say: "Your son Mateo is looking for you."
                   Connect → "Call the Eastside desk (555-0102) and ask for …" → POST /api/requests/:id/decision 204
Westfield (Casey): son's code + PIN → POST /api/lookup 200 → turn the screen → GET /api/searches/:id/status 200
                   "They want to connect. Here's how: | Call the Eastside desk (555-0102) and ask for Esperanza …"
```

**Review** (`/code-review`; each finding checked against the code and the backend):

| # | Finding | Fix |
|---|---|---|
| 1 | A slip check stayed open on the server (10 min) when the volunteer left via "Answer now", the lock or Home: the next person at the desk could see / answer the previous person's requests | Leaving the screen in any way calls `/api/lookup/end`; "Answer now" shows **only that person's** requests in place (`personId` added to the lookup reply) |
| 2 | The searcher's view sat inside the staff layout (volunteer's name, "Log out" reachable) | Drawn over the whole page (portal) and the app behind it is made `inert` |
| 3 | A child's card still had "Safe", "Not now", "Say no" | No answer buttons at all when a child is involved; test |
| 4 | The instruction "Don't say whether anyone is here" was inside the read-aloud quote | Instruction shown as an instruction; only "We'll ask, and let you know." is read out; test |
| 5 | Fixing the searcher's name after "Back" was silently ignored (same id) | After the first save it's an edit (`PUT`) |
| 6 | A search saved with a lost reply, then changed, got 409 with no way out | On that 409 a new search id is made and the search runs once more |
| 7 | The adult question could be skipped (→ counted as a child, every request waits for a lead) | Must be answered; test |
| 8 | The lock kept finished searches (names, match cards) in memory | Lock also removes finished mutations; only saves waiting for the internet stay |
| 9 | "Answer now" listed every request at the desk next to the slip holder's | Covered by #1 |
| 10 | If only the refresh after "Send the request" failed, the card looked failed | Saved request shows "Asked" at once; the refresh runs in the background |
| + | Age text written in the component; the PIN sent to `/api/lookup` was never checked by a test | Moved to `strings.ts`. **The new test caught a real bug:** the lookup sent an **empty PIN** (the box was emptied before the request read it; the earlier browser run passed by timing luck). Code and PIN are now passed to the request as values |

**After the fixes:** `npm run lint` 0 errors · `npm test` **21 passed**.

## F4. Lead page

**Branch:** `feature/frontend-lead` (on top of F3).

**Done:**
- **Lead page** (`/lead`, shown only when the login's role is lead or coordinator; the backend refuses `/api/lead/**` to anyone else): children waiting at this desk with the searcher's message, a guardian-check box, a note and how they'll meet; the Release button stays disabled until all three are there. Children without an adult here (link to their page). Active flags with *Lift this flag*.
- **On a person's page, for leads:** *Protect this person: never share anything* (a `do_not_disclose` flag) and *Reprint a lost slip* (old code stops working, the new PIN shown once).
- Home shows the lead page link to leads only.

**Tests:** `npm run lint` 0 errors · `npm test` **23 passed** (2 new): the lead link only for leads · Release disabled until guardian check + note + plan, then sends exactly those; flags can be lifted · `npm run build` passed.

**Real run, after the F3 fixes** (backend against Neon + `npm run dev`, Playwright, fictional people) — the child path, and the PIN fix confirmed in the browser:
```
Eastside (Riley):  "Pilar Montoya…", 9, no adult with her                            → saved
Lincoln (Alex):    father "Ramon Montoya…" (adult) searches 7–11
                   card: "Pilar Montoya… (about 9) | … | age fits (about 9) | a child: a lead decides before anything is shared"
                   request → 201, father's slip printed
Eastside (Riley):  Answers waiting: "Ramon … is looking for Pilar …" | "A child is involved, so a lead decides." — 0 buttons
Westfield (Casey): father's code + PIN → POST /api/lookup 200 → "Your request is saved. …" → POST /api/lookup/end 204
Eastside (Lead Morgan): Lead page → guardian checked + note + "Her father comes to the Eastside desk at 3 p.m."
                   → POST /api/lead/requests/:id/release-minor 204
Westfield (Casey): father's slip again → "They want to connect. Here's how: | Her father comes to the Eastside desk at 3 p.m."
                   (the app behind the searcher's screen: inert = true)
```
The run showed "a lead decides" twice on the card (a backend reason and a frontend line); the frontend line was removed.

**Review** (`/code-review`; each finding checked against the code and the backend):

| # | Finding | Fix |
|---|---|---|
| 1 | When the **child is the one searching**, the release never asked whether the adult here agreed | Required box "The person here agreed, in person (if they are the adult being asked)"; test |
| 2 | Releasing a child took one tap, without showing what will be shared | "Check what will be shared" shows the plan as it will be read, then "Yes, release"; nothing sent before; test |
| 3 | "Lift this flag" in one tap | Asks first, naming the flag and the person; test |
| 4 | "Reprint a lost slip" voided the old slip in one tap, without saying so | Says the old slip and PIN stop working and the new PIN is shown once; asks first; test |
| 5 | "Protect" offered again on a later visit, adding duplicate flags | Asks first; the backend keeps one active flag per person and kind (pressing again is harmless); backend test |
| 6 | After a release / lift, the Answers list stayed stale | Both refresh `answers` too |
| 7 | A child whose found person is flagged was offered for release, then refused with "ask a lead" (to a lead) | Left off the lead's list (nothing about them can be released); backend test |
| 8 | No test that volunteers never see the lead actions | Test |

**After the fixes:** `npm run lint` 0 errors · `npm test` **25 passed** · `npm run build` passed · backend `./gradlew build` **49 passed**.

## Hotfix 0.3.3. Live: the login page stuck on "Connecting" after Log out

**Found on the live site** (Netlify `hilarious-cassata-a51a3c.netlify.app` → Render), in a fresh browser context: after **Log out**, the login page showed "Connecting. Please wait…" forever with no shelters, though `GET /api/shelters` answered 200. Requests in order: `POST /api/logout 204` → `GET /api/me 401` → `GET /api/shelters 200`.

**Cause:** the click on "Log out" also counts as activity, so `stillHere` (`GET /api/me` with the activity header) went out at the same time. It came back 401 after the logout, which fired "session ended" a **second** time; that second round cancelled and removed the login page's shelter query while it was loading, leaving the page watching a removed query. It never showed locally because the two calls didn't cross there.

**Fix:** the handler does nothing when already logged out, and never cancels or removes the public shelter list.

**Test:** the fake backend can now answer slowly (`delayMs`); the shelters are still loading when the late 401 lands. **Old handler → `FAIL`**, fixed → passes. `npm test` **26 passed**, `npm run lint` 0 errors.

## Submission documentation refresh (2026-10-20)

**Branch:** `feature/release-docs`.

Updated `Homeward_Design.pptx` in place, preserving the 16-slide design. It now includes the live frontend/API links, V1–V5, the actual feature packages and classes, current test counts (155 SQL / 52 backend / 26 frontend), the implemented paper-entry/slip behavior, and explicit unfinished usability study, blank paper form, offline mode and retention job. Replaced emoji-only comparison cells with text because they rendered blank in LibreOffice.

README counts now match the reunion tests. Local commands use subshells so running one line does not break the next line's directory. Corrected the child-release desk description and narrowed the response-type claim to permit the consented contact plan.

**Verification:** PowerPoint OOXML validation against the original passed. Exported with LibreOffice and inspected all 16 rendered slides; re-rendered after replacing the missing comparison symbols. Application files are unchanged; the preceding validation remains 155 SQL checks, 52 backend tests and 26 frontend tests passing, frontend lint 0 errors (2 existing warnings), production build passing.

**Independent review:** GPT-6-astra found no material accuracy issues in the changed claims. Its two README suggestions (JDK 21 prerequisite and separate terminal for Vite) were applied. Latest rendered slides include the comparison-cell corrections; earlier montage images were stale.

## UI polish (after 1.0.0): less to think about, nothing oddly placed

**Why:** a review of every screen at tablet (820) and laptop (1280) width, by a Sonnet agent, from two angles: a child or exhausted person, and a designer who wants clean alignment. It found layout faults on every screen and three places where the screen said too much.

**Fixed** (`styles/tokens.css` rewritten around three tokens, `--gap`, `--column` and `--line`, plus small JSX changes):
- Big buttons: the label is centered instead of stuck to the top of the box.
- Radios and tick boxes sit beside their words, centered on one or two lines. Before, they sat centered **above** the words, because `.stack label` (higher specificity) overrode `.choice-card`.
- One spacing rhythm: browser default margins removed, every gap comes from `--gap`, and one heading scale.
- Buttons in one column are one width; confirm prompts are a row.
- Answer and match cards get inner spacing; nothing touches any more.
- The lock screen and code keypad are laid out properly (they had no CSS).
- Inputs, text boxes and buttons use the page font; the message box was monospace.
- Every `role="alert"` is red and bold, including login and unlock errors.
- The slip looks like a slip on screen too, with a large code and PIN.
- The top bar shares the content column's edges; the bottom has room, so the Netlify badge covers nothing.
- **Less to read:** the privacy question is shown once (it was the heading, a "Say:" box and the legend). A searcher's message is labelled "Read this to them:", not "Say:". The age step says "you" throughout.

**Checked:** measured at 820 px, the top bar text, content, button edges and card radios line up (edges 50 / 770 px; radio center = card center). `npm run lint` 0 errors · `npm test` 26 passed · `npm run build` passed.
