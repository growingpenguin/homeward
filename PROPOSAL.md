# Proposal: Homeward — a shelter-run reunification desk

> Fullstack Challenge 26F · Reuniting Separated Families
> Working name: **Homeward** (placeholder)

---

## 0. The challenge in simple words

**What happened:** A big hurricane is coming. About 400,000 people have to leave their homes in just two days. They leave any way they can: by car, by bus, even by rescue boat. They end up in many different places: shelters, hotels, relatives' houses.

**The problem:** In the rush, thousands of families got split up. For example:
- A child was at school when the buses left, so the child went on a different bus from the parents.
- A grandma in a nursing home was moved by a different organization, and her family doesn't know where she went.
- A husband stayed behind to help neighbors and was rescued two days later.

Now these people need to **find each other again**.

**Why it's hard:**
- 📵 **Many people have no phone.** It was lost, broken, left at home, or the battery is dead. They can't call anyone or look up numbers.
- 🪪 **Many people have no ID.** Their documents were left behind.
- 🙈 **Some people don't want to be found.** For example, a woman who escaped a violent partner, or an undocumented immigrant who is scared to give their real name.
- 📋 **Each shelter keeps its own list**, and some lists are only on paper.
- 🙋 **The good news:** every shelter has staff and volunteers who can help. But most of them aren't tech experts, and they are very busy and tired.

**What they're asking me to do:** Build a **website** (a frontend + a backend + a database, put online with a working link) that helps separated families find each other. I **don't** have to build everything. I pick the most important part, build it well, and explain in the README how the other parts would work.

**What they really want to see:**
1. That I **think about the people first**: who uses the app and what they actually need.
2. That I'm **realistic** about the situation (no phones, no ID, busy volunteers, paper lists).
3. That I **notice what can go wrong**, especially keeping people safe who don't want to be found.
4. That I **explain my choices and tradeoffs** (what I gave up and why).
5. **How I used AI tools** while building it (they want notes on this).

**What I have to hand in:**
- A **live link** to the working app (it must open in a private browser window).
- A **public GitHub repo** with a README that has: an overview + screenshots, the full picture (what I built, what I didn't, and how it all fits), the design (users, my chosen part, edge cases), and my technical choices + biggest tradeoff.

> The test they suggest: *imagine a volunteer at a shelter at 2 a.m. with a clipboard and a line of worried families. Would my app work for them?*

---

## 1. The problem in one paragraph

About 400,000 people evacuated in two days. Families are now spread across dozens of shelters, hotels and relatives' homes. Many people have **no phone**, **no ID**, and **some do not want to be found**. Each shelter keeps its own records, some on paper. The one thing every shelter does have is **staff and volunteers**, most of them non-technical and exhausted. So the question is not "how does a survivor search an app?" It is **"how does a tired volunteer with a clipboard at 2 a.m. help someone find their family in under five minutes, without putting anyone in danger?"**

## 2. Who this is for

| User | Their moment | What they need |
|---|---|---|
| **Searcher** (a separated family member) | "My mom was moved from Seaview Nursing Home by a different agency. Where is she?" | To report who they're looking for **without a phone or ID**, and a reliable way to hear back. |
| **Sought person** (who may or may not want to be found) | "I'm safe. My kids are probably looking for me." *Or:* "I fled my husband. He must not find me." | To say "I'm here and I'm OK" and **control who finds out where they are**. |
| **Shelter volunteer** (main operator of the app) | 2 a.m., one laptop or tablet, a line of worried people, little training | A form that takes **under 2 minutes**, forgives missing info, and tells them clearly what to do next. |
| **Shelter lead / supervisor** | Handles the hard cases | To review possible matches, approve reunifications **involving children**, and act on safety flags. |
| **Regional coordinator** (stretch) | Sees across all shelters | To spot unaccompanied minors, overloaded shelters and duplicate records. |

**Key decision:** survivors do not have to operate the app themselves. **Volunteers use the app for them.** A person with no phone, no ID and no battery can still use the system by walking up to the help desk.

## 3. What I'm building, and why

The full problem has many parts (registration, transport, family tracing, medical, communication). I'm building the core of family tracing:

### ✅ The part I'm building: **Register → Match → Consent → Reconnect**

1. **Fast intake at the shelter desk.** A volunteer registers a person as *"I'm here"* (a presence record), *"I'm looking for someone"* (a search request), or both. Every field except a name-or-nickname is optional.
2. **Fuzzy matching across all shelters.** Matching that tolerates misspellings, nicknames, approximate ages and missing fields, and gives a short plain-language reason for each candidate ("name sounds like, age within 5 years").
3. **Consent-gated contact.** A match **never** shows the searcher where someone is. The sought person (through their shelter's staff) is asked: *"Maria R. is looking for you and left this message. Do you want to connect?"* Only after they say yes do both sides learn how to reach each other.
4. **A reunion code for people with no phone.** Each registered person gets a short printed slip with a **code + PIN** (e.g. `HW-7K3P` / `4821`). At any shelter, they can show it to a volunteer to check for messages.

### Why this part?

- **It's where people actually get reunited.** Without matching that works across shelters, records stay stuck where they were written.
- **It's where the hardest constraints show up:** no phones, no ID, people who don't want to be found, messy data. Solving them here shapes the whole system.
- **It's small enough to build well** in the challenge timeframe and show with a real demo.

## 4. The full picture: how the rest would work

```
 ┌───────────────────┐   ┌───────────────────┐   ┌──────────────────────┐
 │ Paper clipboards  │   │ Shelter desk app  │   │ Hotline / SMS (future)│
 │ (printed form)    │──▶│  (MVP)            │◀──│ call-center staff use │
 └───────────────────┘   │  intake · search  │   │ the same desk app     │
   typed in later        │  match review     │   └──────────────────────┘
                         └─────────┬─────────┘
                                   ▼
                     ┌───────────────────────────┐
                     │  Reunification API (MVP)   │
                     │  matching · consent ·      │
                     │  audit log · retention     │
                     └─────────┬─────────────────┘
                               ▼
                     ┌───────────────────────────┐
                     │  Postgres (MVP)            │
                     └─────────┬─────────────────┘
          ┌────────────────────┼─────────────────────────┐
          ▼                    ▼                         ▼
 ┌─────────────────┐ ┌──────────────────────┐ ┌─────────────────────────┐
 │ Agency imports  │ │ Coordinator dashboard│ │ Public "I'm safe" page   │
 │ (nursing homes, │ │ (unaccompanied minors│ │ (shows only "safe/not    │
 │ hospitals, bus  │ │  capacity, duplicates│ │  listed", never location)│
 │ manifests) CSV  │ │  (PARTIAL/STRETCH)   │ │  (NOT BUILT)             │
 │ (NOT BUILT)     │ └──────────────────────┘ └─────────────────────────┘
 └─────────────────┘
```

| Component | Status | How it would work |
|---|---|---|
| Shelter desk app (intake, search, match review, consent) | **MVP (what I'm building)** | Section 5 |
| Reunion code + PIN slips | **MVP** | Printable slip; lets someone check messages at *any* shelter without a phone |
| Paper-form fallback | **MVP (printable form + a "Typing from a paper form" switch on Register)** | Same fields as the app. Volunteers transcribe later; record is tagged `source: paper` with the date it was written |
| Agency / institution imports (nursing homes, schools, hospitals, bus manifests) | Not built | CSV upload to a staging table, then the same matching pipeline. School and nursing-home lists are the best leads for children and elderly people moved by other agencies |
| Offline mode | Not built (designed for) | PWA with a local IndexedDB queue; records get client-generated UUIDs so syncing later is idempotent. Conflicts are rare because records are mostly append-only |
| Hotline / SMS | Not built | Call-center staff use the same desk app. SMS only sends "you have a message, visit any shelter desk" — never a location |
| Public "I'm safe" lookup | Not built | Only for people who opted into "findable by anyone"; shows only "safe as of <date>" |
| Coordinator dashboard | Stretch | Counts per shelter, open searches for **unaccompanied minors** first, possible duplicate records |
| Data retention | Designed (database permission in place; purge job not built yet) | Records expire 90 days after the event closes; consent and audit logs are kept separately in a minimal form |

## 5. Core flows

### Flow A: "I'm here" (presence registration), target < 2 minutes
1. Volunteer taps **Register a person**.
2. Fills in: name *or* nickname ("what should we call you?"), approximate age, and optionally home area/street, phone and anything that helps recognise them. **No ID fields, no immigration status, no legal-name requirement.**
3. Asks one question in plain words: **"If someone comes looking for you, what should we do?"**
   - 🟢 *Tell them where I am* (findable)
   - 🟡 *Ask me first* (**default**)
   - 🔴 *Don't tell anyone I'm here* (hidden)
4. Prints a slip with **reunion code + PIN**.

### Flow B: "I'm looking for someone"
1. Volunteer records who they're looking for: name(s) and nicknames, age range, relationship, last known place (school, nursing home, street), and a short message ("Mom, I'm safe and looking for you. Love, Ana"). **The message never says where the searcher is**: it's read to the other person before they decide, so the screen reminds the volunteer, and the location is only shared through the contact plan after "Connect".
2. System shows **possible matches** with reasons. The volunteer **never sees the location of a non-findable person**.
3. Volunteer picks "Possible match → send contact request", or "No match yet → save search". Either way, the searcher is registered too (a name is enough) and gets a printed slip, so they can check back at any desk. Saved searches are re-run when new people are registered (designed; built right after the core loop, see BACKEND.md Open items).

### Flow C: Consent and reconnect
1. Contact request shows up in the sought person's shelter queue (and on their code lookup).
2. Staff read the message to them. They choose **Connect / Tell them I'm safe / Not now / Say no to this request**. "Tell them I'm safe" sends a fixed sentence with no location.
3. **Connect** → both shelters see each other's location and a contact plan (call the shelter desk phone, arrange transport).
   Someone who chose *"Tell them where I am"* has said yes in advance, so a volunteer can connect them without asking again (safety flags still apply; children always need a lead).
4. **Not now / Say no / hidden / no match** → the searcher always sees the **same message**: *"Your request is active. If we find a match and they choose to connect, we'll let you know here."* No response gives away whether a person exists or what they decided.

### Flow D: Children (minors < 18)
- A minor registered without a guardian is flagged **Unaccompanied** and shown first on the lead page and the Home badge.
- "Connect" needs a lead's sign-off whenever **either** person is a child: the one being looked for or the one searching.
- A hidden child doesn't show up in searches. A guardian looking for them goes through a lead, who can find and check them. Release always requires a **supervisor sign-off** with a verification checklist (knowledge questions, child recognises adult if age-appropriate, cross-check with school list). A matching name is not enough.
- Custody disputes / safety concerns → supervisor can place a **hold** that stops release and escalates.

## 6. Data model (draft; the final, tested version is in [`DATABASE.md`](DATABASE.md))

```
shelters        (id, name, area, desk_phone, created_at)
staff           (id, shelter_id, display_name, role[volunteer|lead|coordinator], pin_hash)
persons         (id UUID, shelter_id, preferred_name, other_names[], approx_age, age_is_estimate,
                 home_area, description, is_minor, guardian_person_id?, visibility[findable|ask|hidden],
                 reunion_code, pin_hash, source[app|paper|import], recorded_at, entered_by, status[present|moved|left])
searches        (id, searcher_person_id, sought_names[], sought_age_min/max, relationship,
                 last_known_place, message, status[open|matched|closed], created_at)
match_candidates(id, search_id, person_id, score, reasons JSONB, reviewed_by, decision)
contact_requests(id, search_id, person_id, state[pending|connected|safe_only|declined|blocked], decided_at)
safety_flags    (id, person_id?, search_id?, kind[do_not_disclose|suspected_abuser|custody_hold], note, created_by)
audit_log       (id, staff_id, action, target, at)  -- every search and every reveal
```

## 7. Matching

Runs inside Postgres using the `pg_trgm`, `fuzzystrmatch` and `unaccent` extensions (all available on Neon). The MVP uses names, nicknames and age; place and household come next:

| Signal | Technique | Why |
|---|---|---|
| Name similarity | trigram similarity + Double Metaphone ("Katherine" ≈ "Catherine", "Jon" ≈ "John") over preferred and other names | Spelling varies, especially when transcribed from paper or heard in a loud gym |
| Nicknames | small lookup table (Bill↔William, Liz↔Elizabeth…) | People give nicknames under stress |
| Age | overlap of ranges, with a tolerance for estimates | Volunteers often guess ages |
| Place *(planned)* | same home area / street / last-known institution | Strong signal when names are weak |
| Household *(planned)* | shared household members across records | A family is easier to match than one person |

The output is a **score + human-readable reasons**. **The system suggests, people decide.** No automatic merging or reveal happens without a staff decision.

## 8. Privacy and safety model (the non-negotiables)

1. **Location is never shown in search results**, for anyone. It's only shared through the contact plan after "Connect" (a person who chose "findable" has said yes in advance, so "Connect" can be pressed without asking them again).
2. **Identical responses** for "not registered", "hidden", "declined" and "pending". You cannot tell from the outside whether someone exists.
3. **Default is "Ask me first."** Being findable is opt-in.
4. **Collect the minimum.** No ID numbers, no immigration status, no SSN, no photos in the MVP. Nicknames are fine. Written into the UI: *"You don't need to give your legal name."*
5. **Every search and reveal is audit-logged** with the staff member who did it. Unusual patterns (one searcher looking for many unrelated people, repeated searches after a decline) raise a flag for the lead.
6. **Safety flags** (`do_not_disclose`, `suspected_abuser`) override everything, including "findable".
7. **Minors need a supervisor** to release.
8. **Data expires.** A retention job purges personal data after the recovery period.
9. **Staff access is scoped**: volunteers see their own shelter's people plus match *summaries*; leads see more; coordinators have the same rights as leads, and their own dashboard shows only counts.

## 9. Edge cases I'm designing for

| What could go wrong | How the design handles it |
|---|---|
| No phone, no contacts | Shelter desk is the contact point; printed reunion code + PIN works at any shelter |
| No ID | Nothing requires ID. Verification uses shared knowledge and the sought person's own consent |
| Gives a fake or partial name | Allowed; `preferred_name` + `other_names`. Matching also uses nicknames and age (place and household planned) |
| Abuser searching for a victim | No location until consent; identical "pending" response; audit log + pattern flags; `do_not_disclose` flag |
| Undocumented person afraid to register | No legal name, no ID, no status collected; plain-language privacy promise printed on the slip |
| Paper-only shelter | Printable form with the same fields; "Typing from a paper form" switch on Register; `source=paper` + original date kept |
| Same person registered twice (moved shelters) | The same ID is never saved twice; a person registered twice shows up as a match for a lead to check. Automatic merge suggestions are a stretch goal |
| Spelling differences / transcription errors | Phonetic + trigram matching; nicknames table |
| Child at school when buses left | Unaccompanied-minor flag, priority queue, supervisor-verified release; school lists via import (future) |
| Elderly parent moved by another agency | Matched by name and age now; the volunteer sees "last known place = Seaview Nursing Home" on the search. Place as an automatic signal and institution imports come later |
| Spouse rescued two days later | Saved searches are re-run on every new registration (designed, built right after the core loop) |
| Person can't read / doesn't speak English | Volunteer-operated; simple wording; i18n-ready strings (Spanish as first extra language, stretch) |
| Lost reunion code slip | Volunteer can look someone up by name + verification questions; code can be reissued |
| Volunteer makes a mistake | An Edit button fixes it, and every edit keeps a snapshot; nothing is revealed without a second, explicit consent step |
| Wi-Fi drops | MVP: the form keeps a local draft so nothing typed is lost. Full offline queue is designed but not built |
| Someone dies / sensitive news | Out of scope for the app; flagged to trained staff. The app never sends death notifications |

## 10. Assumptions

- Each shelter has **at least one internet-connected laptop or tablet** (connection may be unreliable) and a desk phone or radio.
- People staying at hotels or with relatives register at whichever shelter desk they reach; `shelter_id` is that desk.
- Staff accounts are created by a coordinator; volunteers log in with **shelter + name + short PIN** (no email needed).
- One region, one deployment, English first.
- Volunteers are trusted but fallible; abuse by staff is mitigated with audit logs and access limits rather than blocked outright.
- The "event" has a clear end date, which the retention policy uses.
- All demo data is **fictional**.

## 11. Technical plan

### Stack at a glance

| Layer | Choice |
|---|---|
| Database | **PostgreSQL** (Neon free tier) with `pg_trgm` + `fuzzystrmatch` + `unaccent` |
| Backend | **Kotlin + Spring Boot** (Spring Web, Spring Security, Spring JDBC, Flyway) |
| Frontend | **React + Vite + TypeScript** (React Hook Form + zod, TanStack Query) |
| API contract | OpenAPI (springdoc) → generated TypeScript types (openapi-typescript) |
| Tests | JUnit 5 + **Testcontainers** (real Postgres), MockMvc; Vitest on the frontend |
| Deploy | **Netlify** (frontend) → `/api/*` proxy → **Render** (backend, Docker) → **Neon** (database) |

```
Volunteer's tablet
      │
      ▼
Netlify: React app (loads instantly, even while the backend wakes up)
      │  /api/*  (proxied, so the browser sees one site and login cookies just work)
      ▼
Render: Kotlin + Spring Boot (Docker)
      │  Spring Security · visibility service · matching service · Flyway
      ▼
Neon: PostgreSQL (pg_trgm + fuzzystrmatch + unaccent, triggers, location-free view)
```

### Why this stack is the better option

The lab mostly uses React + Node. I kept React, but chose **Kotlin + Spring Boot** for the backend. This project's main risk isn't performance or scale; it's **leaking where someone is**. So I picked the stack that makes the privacy rules hardest to break by accident.

> Note: Kotlin and Spring Boot are not two alternatives. Kotlin is the **language**, Spring Boot is the **framework**. Using them together gets the strengths of both.

#### 1. Database: PostgreSQL
| Why it's better here | What it gives this project |
|---|---|
| **Fuzzy matching built in** | `pg_trgm` (similar spelling) and `fuzzystrmatch` (sounds alike) match "Rosa Rodrigues" to "Rosa Rodriguez" with no extra search service |
| **Safety rules inside the database** | Default `visibility = 'ask'`, a location-free view for searching, a trigger that blocks releasing a child without a lead, and an audit log the app can't edit. **Tested** on PostgreSQL 15 (Neon's newer version is re-tested before deploy) |
| **Relational data** | People ↔ searches ↔ matches ↔ consent are linked records, which is what a relational DB is for |

**Alternatives:**
- MongoDB: no joins, few built-in constraints, and fuzzy search needs an extra service.
- SQLite: free hosts wipe the file on redeploy.
- Firebase: no fuzzy search, security rules are easy to get wrong.

Details: [`DATABASE.md`](DATABASE.md).

#### 2. Backend: Kotlin + Spring Boot

**What Kotlin adds (the language):**
- **The type system removes location fields.** What a searcher can see is its own type, and that type has **no shelter, phone or address field**, so one can't be attached by accident. Only a "Connected" answer carries a contact plan, and only after a yes:
  ```kotlin
  sealed interface SearcherStatus {
      data object Active : SearcherStatus                      // the SAME reply for: no match, hidden,
                                                               // pending, not now, said no, blocked
      data object Safe : SearcherStatus                              // only if they chose "I'm safe" (fixed sentence)
      data class Connected(val contactPlan: String) : SearcherStatus // only after "Connect"
  }
  ```
- **Null safety fits a world without phones or ID.** `approxAge: Int?` and `phone: String?` make the code handle "unknown" every time, instead of crashing at 2 a.m.
- **Less code than Java.** Data classes and sealed types keep the backend short and easy for reviewers to read.

**What Spring Boot adds (the framework):**
- **Spring Security** handles PIN login, sessions, roles (volunteer < lead < coordinator) and the 10-minute idle lock. It's proven code I don't have to write myself, which matters most for the security-critical part.
- **Flyway** runs the tested SQL schema as migration `V1__init.sql`, unchanged.
- **Testcontainers** runs tests against a **real Postgres**, so the database's own safety rules (triggers, view, permissions) are tested as well as the Kotlin code.
- **Bean Validation + springdoc OpenAPI** give forgiving validation and an API description the frontend's types are generated from.
- **Spring JDBC, not JPA/Hibernate.** Matching is hand-written SQL (trigram + sound-alike), and an ORM would hide or fight it.

**Compared with the alternatives:**

| Option | Better at | Why I didn't pick it |
|---|---|---|
| Node + Express (lab default) | Familiar to reviewers, light, cheap to host | TypeScript types don't exist at runtime, so privacy relies on discipline + tests. Security, roles and migrations need many small libraries glued together |
| Java + Spring Boot | Most familiar enterprise stack | Same framework benefits, but more boilerplate and no null safety |
| Kotlin + Ktor | Lighter, faster cold start | Login, roles, validation and migrations would be built by hand: more security-critical code for me to get right. **My fallback if cold start becomes a problem** |

Details, including NestJS and FastAPI, plus a scorecard against the scenario's needs: [`BACKEND.md`](BACKEND.md).

#### 3. Frontend: React + Vite + TypeScript
| Why it's better here | What it gives this project |
|---|---|
| **Familiar to the lab** | Reviewers can read it easily |
| **React Hook Form + zod** | Step-by-step forms that forgive missing info, with drafts auto-saved in the browser |
| **TanStack Query** | Automatic retries when shelter Wi-Fi drops |
| **Types generated from the backend's OpenAPI** | Frontend and backend can't drift apart; the searcher screen's type has no location field |
| **Vite + PWA plugin** | A clear path to offline mode later |
| **Print CSS** | Slips, code sheets and paper forms print straight from the browser |

**Alternatives:**
- Next.js: server rendering isn't needed for a staff tool, and it adds a second server next to Spring.
- SvelteKit: lighter, but less familiar to reviewers.
- Server-rendered Thymeleaf/htmx: lightest, but a weaker fit for "a frontend that talks to a backend", and it loses the typed client.
- Native mobile app: survivors don't have phones, and shelter devices are mixed and borrowed. A web link works on anything and can print.

Details, including the library choices and a scorecard against the scenario's needs: [`FRONTEND.md`](FRONTEND.md).

#### 4. Deployment: Netlify + Render + Neon
- **Netlify** serves the React app, so the page **opens instantly** even while the backend is waking up. It shows "Connecting. Please wait" instead of a blank screen.
- Netlify's `/api/*` **proxy** makes the frontend and backend look like one site. Login cookies work without CORS problems.
- **Render** runs Spring Boot from a Docker image.
- **Neon** hosts Postgres. Render's free Postgres is deleted after about 30 days, too risky during review.

### The tradeoff of this stack (said honestly)

| Gave up | Why it's worth it | How I reduce the cost |
|---|---|---|
| **Slow cold start.** Render's free tier sleeps after ~15 min idle, and a JVM can take up to a minute to wake | One leaked location can put someone in danger; compile-time and database-level guarantees are worth a slow first load | Frontend on Netlify loads instantly with a "Connecting. Please wait" message; lazy initialization + small JVM heap; keep-alive ping during the review window |
| **Tight memory** (512 MB on the free tier) | Spring Boot fits with a tuned JVM | `-XX:MaxRAMPercentage`, no unused Spring starters |
| **Less familiar to lab reviewers** than Node | The code is short and readable Kotlin; the README explains the choice | Clear package layout, comments where the privacy rules live |
| **Two languages** (Kotlin + TypeScript) instead of one | Generated types from OpenAPI keep them in sync | One `make`/script to regenerate types |

### The biggest tradeoff

**Volunteer-mediated, not self-service.** Every search and every reveal goes through a human at a shelter desk. That makes it **slower** and dependent on stretched staff, and a survivor cannot search from a relative's phone at midnight. In exchange it **works for people with no phone, no ID and limited literacy**, it puts a human in front of every consent decision, and it makes stalking much harder. Given the scenario, I think that's the right trade. A public self-service layer can be added later on top of the same consent rules.

Other tradeoffs I'll name in the README:
- **Online-first** (offline designed, not built): simpler and more reliable to demo, but fragile if shelter internet drops.
- **Suggest, don't auto-match:** more staff clicks, but far fewer wrong reunifications.
- **Minimal data:** worse match accuracy than collecting photos/IDs, but safer and more trusted by people who are afraid.

## 12. Scope and milestones

| # | Milestone | Done when |
|---|---|---|
| 0 | Skeleton deployed | React page on Netlify calls `/api/health` on the Spring Boot service on Render, with Neon connected and Flyway applied. **Deploy on day 1** (also measures the real cold start) |
| 1 | Data model + seed | Migrations, 3 shelters, ~200 fictional people with messy names |
| 2 | Intake (Flow A) | Register a person in < 2 min, printable slip with code + PIN |
| 3 | Search + matching (Flow B) | Ranked candidates with reasons; tests for misspellings, nicknames, missing fields |
| 4 | Consent + reconnect (Flow C) | Contact request queue; identical responses tested |
| 5 | Minors + safety flags (Flow D) | Unaccompanied-minor priority, supervisor release, `do_not_disclose` |
| 6 | Paper fallback | Printable form + "Typing from a paper form" switch |
| 7 | README + screenshots + AI notes | Private-window test of the live link |
| — | Stretch | Coordinator dashboard, duplicate merge, Spanish, offline queue |

## 13. Demo script (how a reviewer will try it)

1. Log in as a volunteer at **Lincoln High Shelter** (demo PIN in README).
2. Register **"Ana Rodriguez"** looking for her mother **"Rosa Rodrigues, ~78, Seaview Nursing Home"**.
3. See the match **"Rosa Rodriguez, 80"** with reasons ("name sounds like…, age close"). Note that no shelter or location is shown.
4. Switch to the **Eastside** volunteer → see the contact request → Rosa chooses *Connect* → both desks see the plan. Ana can also check her answer at any desk with her slip.
5. Try searching for **"Daniel Price"** (registered as *hidden*) → you get the same "request is active" message as for a nonexistent person.
6. Register an **unaccompanied 9-year-old** → see the priority flag and the supervisor-only release.

## 14. Open questions / risks

- Is a desk-phone number per shelter enough as a "contact plan", or do we need in-app messaging between desks? (MVP: desk phone + note.)
- How should the system treat adults who lack capacity to consent (e.g. dementia)? (Not modelled yet; planned: a lead records the decision, the same way as for children, and it's logged.)
- What exactly should the retention period be? (MVP: configurable, default 90 days.)
