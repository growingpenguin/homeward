# AI usage notes

## 2026-10-19: Proposal, CLAUDE.md and database choice
- Asked: Claude (Claude Code) to draft a design proposal, a CLAUDE.md, a plain-language explanation of the challenge, and a database recommendation showing how it meets each requirement.
- Kept: the volunteer-operated design, consent-gated contact, the privacy invariants, PostgreSQL on Neon.
- Changed/rejected: Render's free Postgres was dropped because it expires after about 30 days. A `dmetaphone` on a full name turned out to compare mostly the first name, so names will be split into words for matching.
- Learned: run the example SQL instead of trusting it. A quick test on a local Postgres caught the `dmetaphone` limitation.

## 2026-10-19: Mapping the hard requirements to frontend, backend and DB
- Asked: Claude to show how each "why it's hard" point (no phone, no ID, not wanting to be found, paper lists, tired volunteers) is handled in the frontend, backend and database.
- Kept: the "mailbox, not phone book" model, pre-printed code sheets for paper intake, the blocked-searcher list, the "I'm safe" message without location, and the tested schema (now `server/src/main/resources/db/migration/V1__init.sql`).
- Changed/rejected: moved codes into their own `reunion_codes` table so paper forms can get a code before being typed in. Volunteers no longer see details of cross-shelter duplicates, because that could reveal a hidden person.
- Learned: the database can enforce the hardest rules (minors, safety flags, append-only audit), and I tested each one. "Identical replies" still has to be enforced and tested in the API.

## 2026-10-19: Choosing the stack (Kotlin + Spring Boot)
- Asked: Claude to compare backend options. I was considering Spring Boot or Kotlin; Claude explained that they aren't alternatives (Kotlin is the language, Spring Boot the framework) and recommended using both.
- Kept: PostgreSQL + Kotlin + Spring Boot + React/TypeScript. Netlify → Render → Neon. Spring JDBC instead of JPA so the matching SQL stays visible. A sealed `SearcherStatus` type with no location field.
- Changed/rejected: dropped Node + Express (the original plan). Moved the schema to Flyway's path (`server/src/main/resources/db/migration/V1__init.sql`). Kotlin + Ktor is the fallback if the JVM cold start on Render's free tier is too slow.
- Learned: the stack choice should be explained by the project's main risk (leaking a location), not by what's trendy. Measure the cold start on day 1.

## 2026-10-19: Why this frontend and backend
- Asked: Claude to explain, for the frontend and backend separately, why the chosen option is better for this problem and how it compares with the others.
- Kept: `BACKEND.md` (Kotlin + Spring Boot vs Java/Spring, Ktor, Node/Express, NestJS, FastAPI, Django, Go) and `FRONTEND.md` (web vs native app; React + Vite vs Next.js, SvelteKit, Vue, Angular, Thymeleaf/htmx). Each has a scorecard against the scenario's needs.
- Changed/rejected: comparisons are scored against the five hard parts of the scenario, not general popularity. The docs say honestly where other options win: Go/Ktor on cold start, Node on familiarity, SvelteKit/htmx on download size.
- Learned: "web app run by volunteers" is a bigger decision than which framework; a native app fails the "no phone" requirement outright.

## 2026-10-19: Simplifying BACKEND.md and a GPT (Codex) cross-check
- Asked: Claude to cut the backend comparison down to the top 3 options, add the code that enforces each constraint with comments, then have GPT (Codex) review BACKEND.md for errors and fit with the challenge.
- Kept: Kotlin + Spring Boot vs NestJS vs FastAPI; a "Requirements → code" section with one class per constraint; most of Codex's fixes (audit call missing its actor, safety flag re-checked before every reveal, shelter-scoped searches, the person's visibility choice actually saved, code lookup needs a logged-in volunteer, row lock on the 5-try PIN limit, approver taken from the login).
- Changed/rejected: Claude first picked Express and Ktor as the "top" rivals because they were convenient (lab default, our fallback); I pushed back and they became NestJS and FastAPI. "A location leak is a compile error" was an overclaim: a free-text "I'm safe" message could still hold a shelter name, so it became a fixed sentence. Claude used `csrf { it.spa() }` for Spring Security 6.4; Codex's second pass caught that it only exists in 7.0, so the plan moved to Spring Boot 4.
- Learned: a second model is good at finding places where the text promises more than the code does. Check each finding against the file before applying it.

## 2026-10-19: DATABASE.md
- Asked: Claude to write DATABASE.md in the same shape as BACKEND.md (top 3 options, requirements → SQL with plain-language bullets, tests, tradeoffs).
- Kept: PostgreSQL vs MySQL vs MongoDB; Neon over Supabase and Render; the view + trigger + insert-only audit design; `find_candidates` returning signals, not decisions.
- Changed/rejected: the schema had to change from the PROPOSAL draft to match BACKEND.md (codes in their own table, `contact_plan`, `approved_by`, `guardian_checked`, `searches.shelter_id`). Every SQL block was run on a throwaway local Postgres 15 and each rule was attacked (16 checks, all passed) before it went into the doc.
- Learned: "Rodrigez" and "Rodriguez" don't sound alike to `dmetaphone` (RTRJ vs RTRK), but trigram similarity still matches them, so the two techniques cover each other's gaps. Unknown age must count as "child", never "adult".

## 2026-10-19: GPT (Codex) cross-check of DATABASE.md
- Asked: Codex to review DATABASE.md and V1__init.sql for SQL errors, rules the SQL doesn't really enforce, and fit with the challenge; then Claude to apply the fixes and re-test.
- Kept: no syntax errors were found, and every doc excerpt matched V1. Fixes applied after Claude reproduced each bug on the local database: a 9-year-old marked "adult" no longer counts as an adult; a connected request can't be moved to another person (it could, and kept the lead's approval); a flag added after "Connect" now switches the request to `blocked`; hidden people (children too) never appear in matching; whitespace-only names/notes are refused; the app can't fake history, backdate the audit log or rewrite a flag; `unaccent` for "José"; an index for every matching branch; BCrypt-only PIN columns.
- Changed/rejected: Codex said a hidden adult can be connected "without consent"; I kept that possible, because "hidden" means "not in searches", not "unreachable", and the doc now says the database can't prove a human said yes. Doc claims were narrowed: the database enforces writes and rules; the backend enforces who reads what.
- Learned: on Neon, users created in the console get `neon_superuser`, which would silently undo the audit-log protection; create them with SQL. A local test passing doesn't prove the deployed permissions, so they get re-tested with the real credentials.
- Final pass: Codex's last review found 5 more real problems, and each was reproduced before fixing: a temporary table named `safety_flags` could hide the real flags from the trigger (it did connect a flagged request), the history function could be reused to fake history, a lifted flag could be switched back on, BACKEND.md's audit insert wrote a column the app isn't allowed to write, and the 100-candidate cut ignored sound and nickname matches. All fixed, 4 new attack tests added, and Codex's confirmation pass answered "all confirmed".

## 2026-10-19: FRONTEND.md, focused on usability for non-experts
- Asked: Claude to restructure FRONTEND.md like BACKEND.md, with fewer features and good HCI, because the users are tired non-experts; then a GPT (Codex) cross-check.
- Kept: 5 main screens with one main action each; batch entry merged into Register as a "copying from paper forms" mode; a usability-rules table (recognition over recall, Hick, Fitts, error prevention, honest status, plain language, forgiveness); React + Vite vs Next.js vs SvelteKit; every snippet type-checked with TypeScript strict and run on sample inputs.
- Changed/rejected: Codex's first review (28 points) showed the doc promised more than it did: a pre-printed slip "worked" before registration (the backend rejects that), the "child without an adult" badge had no question feeding it, drafts weren't tied to a volunteer, the lock could be skipped, and "findable" did nothing. I decided "Tell them where I am" means consent given in advance (BACKEND.md `connectFindable`; children still need a lead). Later passes caught smaller real bugs, each reproduced before fixing: tapping H then W on the code keys erased both, JavaScript turns "2026-02-30" into March 2, an old "Connected" could show while offline, and a reload gave the form a new ID (risking duplicates).
- Learned: for non-expert users, honest wording is a safety feature: "Keep me out of searches" is truer than "Nobody will find you". Each fix round found smaller bugs, so the cross-check is worth repeating until it comes back clean. The last two fixes (PIN error handling, keeping the record ID in drafts) are type-checked but still need a Codex confirmation, because Codex hit its usage limit.

## 2026-10-19: Final cross-check of all docs and the deck
- Asked: Claude to check that PROPOSAL.md, DATABASE.md, BACKEND.md, FRONTEND.md and the deck agree and solve the challenge, then a final GPT (Codex) cross-check, applying fixes after each round.
- Kept: Claude's own sync check found the slip didn't work at another desk (the backend only allowed the search's own shelter) and that PROPOSAL.md's demo script showed the location it said was hidden. Codex then found 11 more, all verified: the frontend read a `type` field the Kotlin answer never sent; a child who was *searching* could be connected without a lead; anyone could press "connect findable"; searchers with no match got no slip. A last pass found 7 more, including that a search's searcher could be swapped after a "yes" and that the example message ("I'm at Lincoln High shelter") revealed a location before consent.
- Changed/rejected: every database fix was re-tested by trying to break it (now 29 checks in DATABASE.md, all passing); "Built" became "MVP" in PROPOSAL.md because nothing is built yet; claims that the database or the screen "can't" leak were narrowed to what's actually enforced.
- Learned: the dangerous bugs were all at the seams between documents (frontend vs backend field names, rules checked for one side of a connection but not the other). Reviewing each document alone missed them; checking them together found them. My editor also once saved an old copy over a new file, so I check file contents after editing outside the editor.

## 2026-10-20: Building it (database → backend → frontend), live by the evening
- Asked: Claude (Claude Code) to build the plan one slice at a time across all three layers (registration, then matching, then consent), with plan mode before each phase, git-flow PRs, and a review before every merge. Codex hit its usage limit in the morning, so Claude Code's `/code-review` replaced it until the evening.
- Kept: the migrations split from the tested draft (V1–V5), the Spring Boot API with a sealed `SearcherStatus`, the React screens from FRONTEND.md, and the logs in `*_IMPLEMENTATION.md`.
- Changed/rejected: each review found real bugs that the passing tests had missed. All were fixed and each fix tested; tests were checked by temporarily breaking the code to see them fail:
  - the app's DB user could promote a volunteer to "lead" and so release a child;
  - parallel PIN guesses got past the 5-try limit, because the lookup had no transaction;
  - a lead could "release" an adult who never said yes;
  - in paper mode, the next form reused the saved person's id, so the next person was never stored;
  - the slip check sent an empty PIN (the box was cleared before the request read it).
- Also wrong: some first-draft tests passed for the wrong reason. A crowded-name test passed even with the bug, because the right person's id happened to sort first; checks of the form `SELECT … FROM f() WHERE …` print nothing (so "pass") when the row is missing. Both were rewritten and proven to fail on the bug.
- Going live found two problems no local run did: Render rejected a pasted `DB_URL` (the backend now accepts the usual pasted forms and always logs in as the app user), and a late 401 right after "Log out" left the login page stuck (it only happens with real network timing; the fix has a test that recreates the race).
- Learned: a green test suite says little until each test has been seen to fail. And the live deploy is its own test environment: run the whole flow there in a private window, not only locally.

## 2026-10-20: Codex handoff and the complete reunion tests
- Asked: continue Claude's handoff, preserve the project rules, finish DB step 6/B6, update the deck and prepare the final release.
- Kept: a rollback-only SQL journey and a three-desk API journey, with exact response fields, consent, slip proof, revocation, hidden people, children and audits. All 155 SQL checks, 52 backend tests and 26 frontend tests pass. Neon permission checks denied all six attempted no-row writes without changing live data.
- Changed/rejected: Codex initially expected a requested card to disappear after a flag; that would expose the change. The test now respects retained cards and checks fresh-search exclusion plus Active status. No production code changed. Both new tests were deliberately broken once and observed failing before restoration.
- Review: Claude CLI was at its session limit; a separate GPT-6-astra reviewer found no actionable issues in the tests. That review was read-only, and the test runs were performed by the implementing agent.
- Learned: a plausible negative assertion can contradict an intentional privacy guarantee. Read the earlier review decisions before treating a mismatch as a bug.
