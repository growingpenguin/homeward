# Backend choice: Kotlin + Spring Boot

## TL;DR

**Kotlin** (language) + **Spring Boot** (framework), with **Spring JDBC** for SQL and **Flyway** for migrations, deployed as a Docker image on **Render**.

This app's biggest risk is not speed or scale. It's **revealing where someone is** to a person they're hiding from. 

So the backend was chosen by one question:

> **Which stack makes the privacy rules hardest to break by accident?**

1. **Kotlin's type system removes location fields from what a searcher sees.** The searcher's reply is its own type with no shelter, phone or address field, so I can't send one by accident.
2. **Spring Security is proven code for the most dangerous part**: login, roles and sessions.
3. **Testcontainers lets me test the real database rules** (triggers, view, permissions), not a mock.

> Kotlin and Spring Boot are not alternatives. Kotlin is the **language**, Spring Boot is the **framework**.

---

## Options I compared

| Option | Good at | Weak for *this* problem |
| --- | --- | --- |
| **Kotlin + Spring Boot** ✅ | Null safety, sealed types, mature security, real-DB testing | Slow JVM cold start on free hosting; less familiar to the lab |
| NestJS (Node) | The most structured TypeScript backend, Spring-like modules and guards; widely used | TypeScript types disappear at runtime; login and roles are Passport + glue |
| Python + FastAPI | Fast to write; Pydantic `response_model` **filters undeclared fields out of responses at runtime** | That filter is a runtime check, not a compile error; auth and roles mostly built by hand |

**How they score on what this problem needs:**

| Need (from the scenario) | Kotlin + Spring | NestJS | FastAPI |
| --- | --- | --- | --- |
| Can't leak a location by accident | ✅ no location fields (compile-time) | 🟡 tests only | 🟡 runtime filter |
| Login, roles, idle lock without custom security code | ✅ Spring Security | 🟡 Passport + guards | 🟡 build most of it |
| Fast cold start on free hosting | ❌ | ✅ | ✅ |

Kotlin + Spring Boot wins the rows that protect people and loses the one about hosting. For an app where one mistake can put someone in danger, that's the right trade (see **Tradeoffs**).

---

## Requirements → code

The constraints come first, then the code that enforces each one. Writing these classes before the features means every feature is built on top of them, not patched afterwards.

| # | Constraint (from the scenario) | Enforced by |
| --- | --- | --- |
| 1 | Some people don't want to be found | `SearcherStatus`, `VisibilityService`, DB view + trigger |
| 2 | Children are never released without a lead | `ConsentService.releaseMinor`, DB trigger |
| 3 | Many people have no phone | `ReunionCodeService` (code + PIN) |
| 4 | Many people have no ID | `RegisterPersonRequest` (only a name is required) |
| 5 | Each shelter keeps its own list, some on paper | `PersonRepo.insert` (safe re-sends), paper fields |
| 6 | Volunteers are non-technical and exhausted | `SecurityConfig` (roles, idle lock), forgiving validation |
| 7 | Every search and reveal is traceable | `AuditService` |

### 1. "Some people don't want to be found"

- **`SearcherStatus`** is the only answer a searcher can ever get back. I gave it no shelter, phone or address field on purpose, and "they're safe" is a fixed sentence, not free text, so nobody can type a location into it.
- It has just three possible answers: "still looking", "they said they're safe", and "connected". Only "connected" carries a contact plan, and only after the sought person said yes.
- "No match", "hidden", "pending", "declined" and "blocked" all become the same "still looking" answer, so nobody can guess that the person is actually here.
- **`VisibilityService`** is the one place that decides which answer a searcher gets. Before revealing anything, it checks that the volunteer may see this search (it was made at their desk, **or** the searcher's own code + PIN were just checked, so the slip works at any desk) and that the person has no safety flag *right now*, even if they said "Connect" earlier. Every reveal is written to the audit log.
- The database backs this up: matching reads from a view with no location in it, and a trigger blocks connecting anyone with a safety flag.

```kotlin
// consent/dto/searcher/SearcherStatus.kt
// The ONLY type a searcher ever receives. It has no shelter, phone or address field,
// so a location can't be attached to it by accident.
// "type" is written into the JSON ("Active" / "Safe" / "Connected"), so the frontend can tell the
// three answers apart (Active and Safe have no other fields), and springdoc puts it in the OpenAPI types.
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = SearcherStatus.Active::class, name = "Active"),
    JsonSubTypes.Type(value = SearcherStatus.Safe::class, name = "Safe"),
    JsonSubTypes.Type(value = SearcherStatus.Connected::class, name = "Connected"),
)
sealed interface SearcherStatus {
    // Same reply for: no match, hidden, pending, declined, blocked.
    // A searcher can't tell "not here" from "here but hiding".
    data object Active : SearcherStatus

    // The sought person chose "Tell them I'm safe".
    // A fixed sentence in the UI, not free text, so nobody can type a location into it.
    data object Safe : SearcherStatus

    // Only after the sought person pressed "Connect". This is the one answer
    // that may say how to meet (e.g. "call the Eastside desk"), because they agreed to it.
    data class Connected(val contactPlan: String) : SearcherStatus
}
```

```kotlin
// consent/VisibilityService.kt
// The single place that decides what a searcher may see.
// Controllers never filter location fields themselves; they call this.
@Service
class VisibilityService(
    private val searches: SearchRepo,
    private val requests: ContactRequestRepo,
    private val safety: SafetyFlagRepo,
    private val audit: AuditService,
) {
    // verifiedPersonId: set only when the searcher's own code + PIN were just checked
    // (LookupResult.Ok from ReunionCodeService), so their slip works at ANY desk.
    @Transactional
    fun statusForSearcher(searchId: UUID, staff: StaffUser, verifiedPersonId: UUID? = null): SearcherStatus {
        val search = searches.find(searchId) ?: return SearcherStatus.Active
        // Allowed at the desk where the search was made, or anywhere with the searcher's own slip.
        val allowed = search.shelterId == staff.shelterId ||
            (verifiedPersonId != null && search.searcherPersonId == verifiedPersonId)
        if (!allowed) return SearcherStatus.Active              // same reply as everything else

        // If several people matched: CONNECTED beats SAFE_ONLY beats everything else.
        val request = requests.bestDecisionFor(searchId)
            ?: return SearcherStatus.Active                     // no match looks like everything else

        // Safety flags beat everything, even a "Connect" given earlier:
        // a flag on the person, on this search, or on the searcher (same rule as the DB trigger).
        if (safety.isFlagged(personId = request.personId, searchId = searchId)) return SearcherStatus.Active

        // `when` must cover every state: adding a new state later forces
        // the developer to decide what the searcher sees.
        return when (request.state) {
            RequestState.CONNECTED -> {
                audit.log(staff, AuditAction.REVEALED_CONTACT, request.id)
                SearcherStatus.Connected(
                    checkNotNull(request.contactPlan) { "Connected request ${request.id} has no contact plan" }
                )
            }
            RequestState.SAFE_ONLY -> {
                audit.log(staff, AuditAction.REVEALED_SAFE, request.id)
                SearcherStatus.Safe
            }
            RequestState.PENDING,
            RequestState.DECLINED,
            RequestState.BLOCKED -> SearcherStatus.Active      // hidden states collapse into one reply
        }
    }
}
```

### 2. Children are never released without a lead

- **`ConsentService`** handles the moment two people are actually connected.
- For a child, `releaseMinor` only runs if the logged-in user is a lead or coordinator. A regular volunteer gets "not allowed" (`403`).
- A matching name alone is never enough to hand over a child; a lead has to confirm they checked the guardian and write a short note on how. This applies when **either** person is a child (the one searching or the one found), because "Connect" shows both sides how to reach each other.
- The approver is taken from whoever is logged in, not passed in by the caller, so nobody can sign off in someone else's name.
- Someone who chose *"Tell them where I am"* has said yes in advance, so `connectFindable` lets a volunteer connect them straight away. Only a volunteer at one of the two desks involved can press it, only while the request is still waiting (it never overrides a "no" or "safe only"), it still goes through the same safety checks, and it never works when a child is involved.
- The database has a trigger with the same rule, so even if the Kotlin check had a bug, the child still wouldn't be released. If someone's age is later corrected so they count as a child, every "Connect" involving them without a lead's sign-off goes back to `pending` for a lead to approve.
- Every release is written to the audit log, so we always know who approved it.

```kotlin
// consent/ConsentService.kt
@Service
class ConsentService(
    private val requests: ContactRequestRepo,
    private val currentStaff: CurrentStaff,                     // reads the logged-in user from Spring Security
    private val audit: AuditService,
) {
    // Only a lead or coordinator can release a minor; a volunteer gets 403. This is enforced by the
    // /api/lead/** route in SecurityConfig, not by @PreAuthorize.
    // The DB trigger checks the same rule (and safety flags), so a bug here still can't release a child.
    @Transactional
    fun releaseMinor(requestId: UUID, approval: MinorReleaseApproval) {
        val lead = currentStaff.get()                           // from the login, never from the request body
        require(approval.guardianChecked) { "Please confirm you checked the guardian first." }
        require(approval.note.isNotBlank()) { "Please write a short note on how you checked." }

        // Everything the DB trigger checks is passed through, so both layers see the same facts.
        requests.markConnected(
            requestId,
            approvedBy = lead.id,
            guardianChecked = approval.guardianChecked,
            note = approval.note,
            contactPlan = approval.contactPlan,
        )
        audit.log(lead, AuditAction.MINOR_RELEASED, requestId)
    }

    // "Tell them where I am" = consent given in advance: a volunteer can connect without
    // finding the person first. Children never go this way (the DB trigger needs a lead),
    // and safety flags still block it.
    @Transactional
    fun connectFindable(requestId: UUID, contactPlan: String) {
        val staff = currentStaff.get()
        val request = requests.get(requestId)
        // Only the desk where the search was made, or where the found person is registered.
        // Anyone else gets 404, the same as for a request that doesn't exist.
        if (staff.shelterId != request.searchShelterId && staff.shelterId != request.personShelterId) {
            throw notFound()
        }
        check(request.personVisibility == Visibility.FINDABLE) { "This person asked to be asked first." }
        require(contactPlan.isNotBlank()) { "Please write how they will meet." }

        // Advance consent only covers a request that is still waiting. If the person already said
        // "no" or "safe only", only they can change that. The check is inside the UPDATE itself
        // (... WHERE state = 'pending'), so a decision made a second earlier can't be overwritten.
        val changed = requests.markConnectedIfPending(requestId, approvedBy = staff.id, contactPlan = contactPlan)
        check(changed) { "This person has already answered. Please ask them again." }
        audit.log(staff, AuditAction.DECISION, requestId)
    }
}

// What the lead fills in before releasing a child.
data class MinorReleaseApproval(
    val guardianChecked: Boolean,                               // e.g. the child recognised them, details match
    val note: String,                                           // how they checked, in plain words
    val contactPlan: String,                                    // how they'll meet, e.g. "Dad comes to the Westfield desk at 3 p.m."
)
```

### 3. "Many people have no phone"

- **`ReunionCodeService`** lets someone with no phone check for messages using a printed slip with a code and a PIN.
- They can walk up to any shelter's desk, and a volunteer types in the code and PIN for them.
- I store the PIN hashed (BCrypt), so even someone who sees the database can't read it.
- After 5 wrong PINs the code locks for 15 minutes, so nobody can just guess their way in. The row is locked while it's checked, so even many guesses sent at the same time are counted one by one.
- A wrong code, a locked code and a wrong PIN get the same reply and take about the same time, and the codes skip look-alike characters (0/O, 1/I) so they're easy to read aloud or copy by hand.

```kotlin
// person/ReunionCodeService.kt
// A survivor with no phone shows a printed slip (code + PIN) at any shelter.
@Service
class ReunionCodeService(
    private val codes: ReunionCodeRepo,
    private val encoder: PasswordEncoder,                       // BCrypt: PINs are never stored in plain text
    private val audit: AuditService,
) {
    // Always called by a logged-in volunteer at the desk, never by the public.
    @Transactional
    fun lookup(code: String, pin: String, staff: StaffUser): LookupResult {
        // SELECT ... FOR UPDATE: locks this code's row until the transaction ends,
        // so guesses sent at the same time can't all slip past the 5-try limit.
        // Only working codes: WHERE voided_at IS NULL AND person_id IS NOT NULL,
        // so an old slip or a blank pre-printed slip never works.
        val rc = codes.findForUpdate(code.uppercase().trim())

        if (rc == null || rc.isLocked()) {
            encoder.matches(pin, DUMMY_HASH)                    // same BCrypt work, so timing gives nothing away
            audit.log(staff, AuditAction.CODE_LOOKUP_FAILED, rc?.personId)
            return LookupResult.Invalid                         // same reply as a wrong PIN
        }

        if (!encoder.matches(pin, rc.pinHash)) {
            codes.recordFailure(rc.code, maxAttempts = 5, lockFor = Duration.ofMinutes(15))
            audit.log(staff, AuditAction.CODE_LOOKUP_FAILED, rc.personId)
            return LookupResult.Invalid                         // no exception, so the failure count is saved
        }

        codes.resetFailures(rc.code)
        audit.log(staff, AuditAction.CODE_LOOKUP, rc.personId)
        return LookupResult.Ok(rc.personId)
    }

    companion object {
        // No 0/O or 1/I, so a code read aloud or handwritten isn't misread.
        const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

        // A real BCrypt hash of a throwaway PIN, used only to spend the same time on a miss.
        private val DUMMY_HASH: String = BCryptPasswordEncoder().encode("000000")
    }
}
```

A lost slip is reissued by a lead or coordinator only (the `/api/lead/**` route; the lead does it from the person's slip page, `/persons/:id/slip`). There is no lookup by name. With no ID, the lead checks the person with questions only they'd know (family names, home street), and the old code is voided in the same transaction.

### 4. "Many people have no ID"

- **`RegisterPersonRequest`** is what the volunteer fills in at the desk. The only required field is a name or nickname.
- Everything else (age, home area, phone) can be left empty. Empty means "unknown", so an unknown age is saved as empty, not as `0` (which would mean a baby).
- I left out fields for ID, date of birth, SSN, immigration status and photos on purpose. If we never collect them, they can never leak.
- New people start as "ask me first" (`ASK`), never "anyone can find me".
- **`MatchingService`** puts small clues together (a similar name, a nickname, a close age; place comes later) and shows the volunteer why each person might be a match. It only suggests; a person decides.

```kotlin
// person/PersonDtos.kt
data class RegisterPersonRequest(
    val id: UUID,                                               // made by the browser; a re-send uses the same id

    @field:NotBlank
    @field:Size(max = 100)
    val preferredName: String,                                  // the ONLY required field: a name or nickname

    val otherNames: List<String> = emptyList(),                 // nicknames, maiden names, spellings
    @field:Min(0) @field:Max(120)
    val approxAge: Int? = null,                                 // null = unknown (0 is a real age: a baby)
    val ageIsEstimate: Boolean = true,
    val isMinor: Boolean? = null,                               // null = not sure: a known age decides; with no age, a child until someone marks them as an adult
    val unaccompanied: Boolean = false,                         // a child with no adult: goes to the top of the lead's list
    val homeArea: String? = null,
    val description: String? = null,                            // "red jacket, walks with a cane"
    val phone: String? = null,
    val visibility: Visibility = Visibility.ASK,                // safe default, also enforced in the DB

    // Copying from paper (see FRONTEND.md, "paper mode")
    val source: Source = Source.APP,                            // APP or PAPER (IMPORT is for agency lists)
    val paperRef: String? = null,                               // "Sheet 4, line 12"; required when source = PAPER
    val recordedAt: Instant? = null,                            // the date written on the paper
    @field:Pattern(regexp = "^HW-[2-9A-HJ-NP-Z]{4}$")
    val slipCode: String? = null,                               // a pre-printed slip to attach to this person

    // Deliberately NO fields for: legal ID, date of birth, SSN, immigration status, photo.
)
```

`MatchingService` combines these weak clues into a score with plain-English reasons, and **only suggests**:

```kotlin
// Shown to the volunteer, e.g. reasons = ["name sounds like Maria Garcia", "age fits (about 80)"]
// (match/SearchDtos.kt; no shelter, phone or home area on it)
data class MatchCard(val personId: UUID, val preferredName: String, val score: Double, val reasons: List<String> /* ... */)
```

### 5. "Each shelter keeps its own list, some on paper"

- **`PersonRepo`** saves people into one shared database for every shelter, so a search at one shelter can find someone at another.
- The browser makes the record's ID before sending it. If the Wi-Fi drops and the form gets sent twice, the second one is ignored (`ON CONFLICT DO NOTHING`), so the same person is never saved twice.
- Paper records keep where they came from (`source`, `paperRef` like "Sheet 4, line 12") and when they were written down, not just when they were typed in.
- That way a volunteer can always go back to the original paper sheet if something looks wrong.
- A paper entry typed in tonight can still match a search from yesterday: a volunteer presses **Check again** on the saved search (`POST /api/searches/{id}/check`). Automatic re-runs when a person is saved are not built (planned).

```kotlin
// person/PersonRepo.kt
@Repository
class PersonRepo(private val jdbc: JdbcClient) {

    // The browser creates the UUID, so slow Wi-Fi or a double click
    // re-sends the same id and never creates a second person.
    fun insert(p: NewPerson): Boolean =
        jdbc.sql(
            """
            INSERT INTO persons (id, shelter_id, preferred_name, approx_age, visibility,
                                 source, paper_ref, recorded_at, entered_by)
            VALUES (:id, :shelterId, :name, :age, :visibility,
                    :source, :paperRef, :recordedAt, :enteredBy)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )
            .param("id", p.id)
            .param("shelterId", p.shelterId)
            .param("name", p.preferredName)
            .param("age", p.approxAge)
            .param("visibility", p.visibility.dbValue)           // 'findable', 'ask' or 'hidden': the person's choice is never lost
            .param("source", p.source.dbValue)                   // 'app', 'paper' or 'import' (same as PROPOSAL.md)
            .param("paperRef", p.paperRef)                       // e.g. "Sheet 4, line 12"
            .param("recordedAt", p.recordedAt)                   // when it was written on paper, not typed
            .param("enteredBy", p.enteredBy)                     // the logged-in volunteer who typed it
            .update() == 1
    // The other optional fields (other names, description, phone, minor flags) are saved
    // the same way in the same transaction; left out here to keep the example short.
}
```

### 6. "Volunteers are non-technical and exhausted"

- **`SecurityConfig`** sets up login and decides who can do what. I use Spring Security here instead of writing my own login code, because that's the easiest place to make a dangerous mistake.
- There are three roles: volunteer, lead and coordinator (a role hierarchy: COORDINATOR > LEAD > VOLUNTEER). Everything under `/api/lead/**` needs LEAD, so lead-only actions are blocked for regular volunteers by the route, not by `@PreAuthorize`.
- A login lasts a whole shift (12 hours), so tired volunteers don't keep getting kicked out. Sessions live in memory for the MVP: when the free Render server sleeps, volunteers log in again (close to the 10-minute idle lock anyway).
- But if there are no clicks or typing for 10 minutes, it asks for the PIN again, because laptops get left on desks. Background refreshes don't count as activity.
- Even the code + PIN lookup needs a volunteer to be logged in, so every lookup is tied to a real person at a real desk.
- Only the name is required on forms, and error messages are plain sentences, so a volunteer at 2 a.m. never gets stuck on a form.

```kotlin
// security/SecurityConfig.kt
@Configuration
@EnableMethodSecurity                                           // turns on @PreAuthorize
class SecurityConfig {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .authorizeHttpRequests {
                it.requestMatchers("/api/health", "/error", "/api/shelters", "/api/csrf", "/api/login", "/api/openapi.json").permitAll()  // only these work without a login
                it.requestMatchers("/api/lead/**").hasRole("LEAD")  // coordinators too, through the role hierarchy
                it.anyRequest().authenticated()                 // includes /api/lookup (code + PIN)
            }
            .csrf { it.spa() }                                  // CSRF token the React app sends back on every change
            .sessionManagement { it.sessionFixation().newSession() }
            .addFilterAfter(IdleLockFilter(idleAfter = Duration.ofMinutes(10)),
                            SecurityContextHolderFilter::class.java)  // laptops get left on desks
            .build()

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
```

- The session lasts a shift (`server.servlet.session.timeout: 12h`), in an HTTP-only, `SameSite` cookie, with CSRF protection.
- `PUT /api/persons/{id}` saves the "before" version into `person_history`, so a mistake can be undone.

### 7. Every search and reveal is traceable

- **`AuditService`** writes down every search, every contact request, every decision, every code lookup and every reveal: who did it, what they did and when.
- I call it inside the same service method that does the action (and in the same transaction), not from the controllers, and tests check that each of those actions leaves a log entry.
- The app's database user can only add to the log. It can't change or delete anything, so nobody can cover their tracks.
- This is meant to let a lead spot something odd, like one person searching for lots of different people (pattern flags and a coordinator dashboard are not built (planned); the log is the record for now).
- There's also no "export everyone" button. Coordinators have the same rights as leads; a counts-only coordinator dashboard is not built (planned).

```kotlin
// audit/AuditService.kt
// Called from the service method that does the action, in the same transaction.
// The app's DB user can only INSERT into audit_log: no edits, no deletes.
@Service
class AuditService(private val jdbc: JdbcClient) {
    // actor is always a logged-in staff member; targetId is null when nothing was found (e.g. a wrong code).
    // Never logs a PIN, a message text or a location.
    fun log(actor: StaffUser, action: AuditAction, targetId: UUID?) {
        // No `at` column: the database sets the time, and the app user isn't allowed to.
        jdbc.sql("INSERT INTO audit_log (staff_id, action, target) VALUES (:staff, :action, :target)")
            .param("staff", actor.id)
            .param("action", action.name)
            .param("target", targetId)
            .update()
    }
}
```

---

## Data access: Spring JDBC, not JPA/Hibernate

Matching is hand-written SQL (`similarity()`, `dmetaphone()`). Spring JDBC keeps it visible and reviewable, with parameterized queries only. JPA would hide that SQL, and lazy loading could pull in data (like a shelter) I didn't mean to load and send.

**Versions:** Spring Boot 4.1.1 / Spring Security 7 (needed for the one-line `csrf { it.spa() }`; `JdbcClient` needs 3.2+), Kotlin 2.3.21, JDK 21, and the `kotlin("plugin.spring")` plugin, so Spring can wrap Kotlin's final classes for `@Transactional`.

**Migrations:** the running app **never** migrates and never holds the owner's password. Migrations (`V1__base_tables.sql` to `V5__request_message.sql`) run only through `scripts/db-migrate.sh`, as the owner. At startup, the app reads the schema version (as `homeward_app`) and refuses to start if the database is older than the code. Schema changes are new files; applied migrations are never edited.

---

## Backend layout

```
server/src/main/kotlin/app/homeward/       one package per feature (controller, DTOs, service, JDBC repo)
  security/             SecurityConfig, PIN login, roles, idle lock
  person/               registration, edit, slips (ReunionCodeService, PersonDtos)
  match/                MatchingService, SearchController, SearchService, SearchRepo
  consent/              VisibilityService, ConsentService, LeadService, lead and answers endpoints
  consent/dto/searcher/ searcher-facing types (no location fields, ever)
  audit/                AuditService
  db/                   database URL parsing, schema version check
  web/                  shared pieces: health, plain-sentence errors (ApiErrors)
server/src/main/resources/db/migration/   Flyway migrations (V1__base_tables.sql to V5__request_message.sql)
server/src/test/kotlin/                   JUnit 5 + Testcontainers + MockMvc
```

## Tests that make the privacy rules real

1. The searcher status response is **identical** for no match, hidden, pending, declined and blocked.
2. No searcher-facing JSON contains a shelter, phone or home area, except the contact plan after "Connect".
3. New people default to `ASK` (API and DB), and a chosen `HIDDEN` is actually saved.
4. A safety flag beats `FINDABLE`, and a flag added *after* "Connect" hides the person again.
5. Releasing a child without a lead, a guardian check or a note fails in the **service** and in the **DB trigger**.
6. 5 wrong PINs lock the code, even when 23 guesses arrive at the same time.
7. Re-sending a registration with the same UUID doesn't duplicate.
8. A volunteer gets `403` on lead-only endpoints, and can't read another shelter's search, unless the searcher's own code + PIN were just checked at their desk.
9. `connectFindable` from a shelter that isn't one of the two desks involved gets `404`; with a child on either side it's refused; on a request the person already answered ("no" or "safe only") it's refused and the answer stays.
10. Every reveal and every code lookup (right or wrong) leaves an audit entry.

---

## Open items (some built, some not built (planned))

- **Data retention:** not built (planned); the `homeward_maint` role exists. A scheduled job would delete personal data 90 days after the event closes (configurable). A separate maintenance user does the deleting, so the app's user still can't touch the audit log.
- **Re-running searches (automatic):** not built (planned). Today only the manual **Check again** exists (`POST /api/searches/{id}/check`; `GET /api/searches/{id}` only reads). The same person is never suggested twice for one search. Planned: after a new person is saved, matching runs again for open searches.
- **Issuing codes (built):** codes are made with `SecureRandom`, checked to be unique, and the PIN is shown only once on the printed slip.
- **Paper forms (built):** each typed-in paper form is sent like a normal registration, with `source = paper`, `paperRef` and `recordedAt`. No separate batch API is needed (see FRONTEND.md).
- **Adults who can't decide for themselves** (e.g. from a nursing home), not built (planned): a lead would record the decision, the same way as for children.

---

## Tradeoffs I'm accepting

| Gave up | How I reduce the cost |
| --- | --- |
| **Slow cold start**: Render's free tier sleeps after ~15 min; a JVM can take up to a minute to wake | Netlify frontend loads instantly and shows "Connecting. Please wait"; lazy initialization; **measured on day 1** |
| **Memory** (512 MB on the free tier) | `-XX:MaxRAMPercentage=75`, only the Spring starters actually used |
| **Less familiar to lab reviewers** than Node | Short, commented Kotlin; the README explains the choice |

**Fallback:** if the cold start is unacceptable on day 1, switch to **Kotlin + Ktor**. The language, sealed types, SQL and schema stay the same; only the framework changes.
