package app.homeward.consent

import app.homeward.IntegrationTest
import app.homeward.TestDatabase
import app.homeward.security.LoginThrottle
import app.homeward.security.LookupThrottle
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Demo seed (fictional): Lincoln (Volunteer Alex, Lead Jordan), Eastside (Volunteer Riley, Lead Morgan),
// Westfield (Volunteer Casey). PIN 2468.
private const val LINCOLN = "d0000000-0000-0000-0000-000000000001"
private const val EASTSIDE = "d0000000-0000-0000-0000-000000000002"
private const val WESTFIELD = "d0000000-0000-0000-0000-000000000003"

@AutoConfigureMockMvc
class ConsentApiTest : IntegrationTest() {
    @Autowired lateinit var mvc: MockMvc

    @Autowired lateinit var throttle: LoginThrottle

    @Autowired lateinit var lookupThrottle: LookupThrottle

    @BeforeEach fun reset() {
        throttle.clear()
        lookupThrottle.clear()
    }

    private fun loggedIn(
        shelter: String,
        name: String,
    ) = MockHttpSession().also { s ->
        mvc
            .post("/api/login") {
                session = s
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"shelterId":"$shelter","name":"$name","pin":"2468"}"""
            }.andExpect { status { isOk() } }
    }

    private fun post(
        s: MockHttpSession,
        url: String,
        json: String = "{}",
    ) = mvc.post(url) {
        session = s
        with(csrf())
        contentType = MediaType.APPLICATION_JSON
        content = json
    }

    private fun person(
        s: MockHttpSession,
        name: String,
        extra: String = "",
    ): UUID {
        val id = UUID.randomUUID()
        post(s, "/api/persons", """{"id":"$id","preferredName":"$name"$extra}""").andExpect { status { isCreated() } }
        return id
    }

    private fun searchFor(
        s: MockHttpSession,
        searcher: UUID,
        name: String,
    ): UUID {
        val id = UUID.randomUUID()
        post(
            s,
            "/api/searches",
            """{"id":"$id","searcherPersonId":"$searcher","soughtNames":["$name"]}""",
        ).andExpect { status { isCreated() } }
        return id
    }

    // A search at Lincoln that found someone registered at Eastside, with a request sent: returns (searchId, requestId).
    private fun requested(
        alex: MockHttpSession,
        found: UUID,
        name: String,
        // An adult searcher: someone registered with only a name counts as a child (unknown age), and then a lead decides.
        searcher: UUID = person(alex, "Searcher ${UUID.randomUUID().toString().take(6)}", ""","approxAge":45"""),
    ): Pair<UUID, UUID> {
        val searchId = searchFor(alex, searcher, name)
        val body =
            post(alex, "/api/searches/$searchId/requests", """{"personId":"$found","message":"Your family is looking for you."}""")
                .andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        return searchId to UUID.fromString(body.substringAfter("\"requestId\":\"").substringBefore("\""))
    }

    private fun status(
        s: MockHttpSession,
        searchId: UUID,
    ) = mvc
        .get("/api/searches/$searchId/status") { session = s }
        .andExpect { status { isOk() } }
        .andReturn()
        .response.contentAsString

    private fun count(sql: String): Int =
        TestDatabase.asOwner { c ->
            c.createStatement().executeQuery(sql).use {
                it.next()
                it.getInt(1)
            }
        }

    @Test
    fun `no match, pending, declined, blocked and another desk all get the identical reply`() {
        val alex = loggedIn(LINCOLN, "Volunteer Alex")
        val riley = loggedIn(EASTSIDE, "Volunteer Riley")
        val noMatch = searchFor(alex, person(alex, "Lonely Searcher"), "Nobody Bythisname")
        val (pending, _) = requested(alex, person(riley, "Perpetua Hollins", ""","approxAge":50"""), "Perpetua Hollins")
        val (declined, declinedReq) = requested(alex, person(riley, "Desmond Ravel", ""","approxAge":50"""), "Desmond Ravel")
        post(riley, "/api/requests/$declinedReq/decision", """{"choice":"DECLINE"}""").andExpect { status { isNoContent() } }
        val blockedPerson = person(riley, "Bertram Okoye", ""","approxAge":50""")
        val (blocked, blockedReq) = requested(alex, blockedPerson, "Bertram Okoye")
        post(riley, "/api/requests/$blockedReq/decision", """{"choice":"CONNECT","contactPlan":"Call the Eastside desk"}""")
            .andExpect { status { isNoContent() } }
        val morgan = loggedIn(EASTSIDE, "Lead Morgan")
        post(morgan, "/api/lead/flags", """{"personId":"$blockedPerson","kind":"custody_hold"}""").andExpect { status { isCreated() } }

        val replies = listOf(noMatch, pending, declined, blocked).map { status(alex, it) }
        assertEquals(listOf("""{"type":"Active"}"""), replies.distinct(), "every hidden state must look the same")
        // Another desk asking about Lincoln's search gets the same reply, not 403 or 404.
        assertEquals("""{"type":"Active"}""", status(riley, pending))
    }

    @Test
    fun `Connect shows only the contact plan, is audited, and a flag added later takes it back`() {
        val alex = loggedIn(LINCOLN, "Volunteer Alex")
        val riley = loggedIn(EASTSIDE, "Volunteer Riley")
        val rosa = person(riley, "Rosamund Delacroix", ""","approxAge":80,"homeArea":"Seaview","phone":"555-0199"""")
        val (searchId, req) = requested(alex, rosa, "Rosamund Delacroix")
        // Volunteers at another desk can't answer for Rosa.
        post(alex, "/api/requests/$req/decision", """{"choice":"CONNECT","contactPlan":"x"}""").andExpect { status { isNotFound() } }
        post(riley, "/api/requests/$req/decision", """{"choice":"CONNECT"}""").andExpect { status { isBadRequest() } }
        post(riley, "/api/requests/$req/decision", """{"choice":"CONNECT","contactPlan":"Call the Eastside desk and ask for Rosa"}""")
            .andExpect { status { isNoContent() } }

        val reply = status(alex, searchId)
        assertEquals("""{"type":"Connected","contactPlan":"Call the Eastside desk and ask for Rosa"}""", reply)
        for (leak in listOf("Seaview", "555-0199", "shelter")) assertFalse(reply.contains(leak))
        assertEquals(1, count("SELECT count(*) FROM audit_log WHERE action = 'REVEALED_CONTACT' AND target = '$req'"))

        val morgan = loggedIn(EASTSIDE, "Lead Morgan")
        post(morgan, "/api/lead/flags", """{"personId":"$rosa","kind":"do_not_disclose"}""").andExpect { status { isCreated() } }
        assertEquals("""{"type":"Active"}""", status(alex, searchId))
    }

    @Test
    fun `a child is only connected by a lead with a guardian check and a note`() {
        val alex = loggedIn(LINCOLN, "Volunteer Alex")
        val riley = loggedIn(EASTSIDE, "Volunteer Riley")
        val mia = person(riley, "Mirabel Castellanos", ""","approxAge":9,"unaccompanied":true""")
        val (searchId, req) = requested(alex, mia, "Mirabel Castellanos")
        post(riley, "/api/requests/$req/decision", """{"choice":"CONNECT","contactPlan":"Call the Eastside desk"}""").andExpect {
            status { isConflict() }
            jsonPath("$.message") { value(ConsentService.NEEDS_LEAD) }
        }
        // "Safe" would also tell them the child is here: a lead decides that too.
        post(riley, "/api/requests/$req/decision", """{"choice":"SAFE"}""").andExpect { status { isConflict() } }
        // The lead on the ASKING side can't release a child at another shelter.
        val jordan = loggedIn(LINCOLN, "Lead Jordan")
        post(jordan, "/api/lead/requests/$req/release-minor", """{"guardianChecked":true,"note":"x","contactPlan":"x"}""")
            .andExpect { status { isNotFound() } }
        // A volunteer can't use the lead route at all.
        post(riley, "/api/lead/requests/$req/release-minor", """{"guardianChecked":true,"note":"x","contactPlan":"x"}""")
            .andExpect { status { isForbidden() } }
        val morgan = loggedIn(EASTSIDE, "Lead Morgan")
        mvc.get("/api/lead/overview") { session = morgan }.andExpect {
            status { isOk() }
            jsonPath("$.childrenWaiting[?(@.requestId == '$req')]") { exists() }
            jsonPath("$.unaccompaniedChildren[?(@.personId == '$mia')]") { exists() }
        }
        post(morgan, "/api/lead/requests/$req/release-minor", """{"guardianChecked":false,"note":"x","contactPlan":"x"}""")
            .andExpect { status { isBadRequest() } }
        post(morgan, "/api/lead/requests/$req/release-minor", """{"guardianChecked":true,"note":" ","contactPlan":"x"}""")
            .andExpect { status { isBadRequest() } }
        assertEquals("""{"type":"Active"}""", status(alex, searchId))
        post(
            morgan,
            "/api/lead/requests/$req/release-minor",
            """{"guardianChecked":true,"note":"Mirabel recognised her father; school list matches","contactPlan":"Father comes to the Eastside desk at 3 p.m."}""",
        ).andExpect { status { isNoContent() } }
        assertTrue(status(alex, searchId).contains("Father comes to the Eastside desk"))
        assertEquals(1, count("SELECT count(*) FROM audit_log WHERE action = 'MINOR_RELEASED' AND target = '$req'"))
    }

    @Test
    fun `findable means a volunteer can connect at once, but never past an answer or for someone who asked to be asked`() {
        val alex = loggedIn(LINCOLN, "Volunteer Alex")
        val riley = loggedIn(EASTSIDE, "Volunteer Riley")
        val casey = loggedIn(WESTFIELD, "Volunteer Casey")
        val (_, findableReq) =
            requested(
                alex,
                person(riley, "Fenwick Applegate", ""","approxAge":40,"visibility":"findable""""),
                "Fenwick Applegate",
            )
        val (_, askReq) = requested(alex, person(riley, "Ottilie Marchbanks", ""","approxAge":40"""), "Ottilie Marchbanks")
        post(casey, "/api/requests/$findableReq/connect-findable", """{"contactPlan":"x"}""").andExpect { status { isNotFound() } }
        post(alex, "/api/requests/$askReq/connect-findable", """{"contactPlan":"x"}""").andExpect { status { isConflict() } }
        post(alex, "/api/requests/$findableReq/connect-findable", """{"contactPlan":"Call the Eastside desk"}""").andExpect {
            status { isNoContent() }
        }
        // Already answered: a second decision never overwrites the first.
        post(riley, "/api/requests/$findableReq/decision", """{"choice":"DECLINE"}""").andExpect { status { isConflict() } }
        assertEquals(1, count("SELECT count(*) FROM contact_requests WHERE id = '$findableReq' AND state = 'connected'"))
    }

    @Test
    fun `a slip opens the holder's own answers at any desk, and wrong PINs get one reply and lock after 5`() {
        val alex = loggedIn(LINCOLN, "Volunteer Alex")
        val riley = loggedIn(EASTSIDE, "Volunteer Riley")
        val casey = loggedIn(WESTFIELD, "Volunteer Casey")
        val son = person(alex, "Lucius Wetherby", ""","approxAge":45""")
        val slip = post(alex, "/api/persons/$son/slip").andReturn().response.contentAsString
        val code = slip.substringAfter("\"code\":\"").substringBefore("\"")
        val pin = slip.substringAfter("\"pin\":\"").substringBefore("\"")
        val (searchId, req) = requested(alex, person(riley, "Henrietta Wetherby", ""","approxAge":70"""), "Henrietta Wetherby", son)
        post(riley, "/api/requests/$req/decision", """{"choice":"SAFE"}""").andExpect { status { isNoContent() } }

        // Westfield can't see Lincoln's search...
        assertEquals("""{"type":"Active"}""", status(casey, searchId))
        val wrong = post(casey, "/api/lookup", """{"code":"$code","pin":"0000"}""").andReturn().response.contentAsString
        val unknown = post(casey, "/api/lookup", """{"code":"HW-ZZZZ","pin":"$pin"}""").andReturn().response.contentAsString
        assertEquals(wrong, unknown, "a wrong PIN and an unknown code must get the same reply")
        // ...until the son's own slip is checked there (lower case and no prefix are fine).
        post(casey, "/api/lookup", """{"code":"${code.removePrefix("HW-").lowercase()}","pin":"$pin"}""").andExpect {
            status { isOk() }
            jsonPath("$.found") { value(true) }
            jsonPath("$.searches[0].status.type") { value("Safe") }
        }
        assertEquals("""{"type":"Safe"}""", status(casey, searchId))

        // 5 wrong PINs lock the slip; then even the right PIN gets the same "didn't match" reply.
        repeat(5) { post(riley, "/api/lookup", """{"code":"$code","pin":"9999"}""") }
        post(riley, "/api/lookup", """{"code":"$code","pin":"$pin"}""").andExpect { jsonPath("$.found") { value(false) } }
        assertEquals(1, count("SELECT count(*) FROM reunion_codes WHERE code = '$code' AND locked_until > now()"))
        assertTrue(count("SELECT count(*) FROM audit_log WHERE action = 'CODE_LOOKUP_FAILED' AND target = '$son'") >= 6)
        assertEquals(1, count("SELECT count(*) FROM audit_log WHERE action = 'CODE_LOOKUP' AND target = '$son'"))
    }

    @Test
    fun `after 10 failed code lookups a login is refused even with the right code and PIN, another login still works`() {
        val alex = loggedIn(LINCOLN, "Volunteer Alex")
        val casey = loggedIn(WESTFIELD, "Volunteer Casey")
        val p = person(alex, "Ophelia Brannock", ""","approxAge":50""")
        val slip = post(alex, "/api/persons/$p/slip").andReturn().response.contentAsString
        val code = slip.substringAfter("\"code\":\"").substringBefore("\"")
        val pin = slip.substringAfter("\"pin\":\"").substringBefore("\"")
        val good = """{"code":"$code","pin":"$pin"}"""
        // Guessing many different codes (each only once, so no single slip locks).
        val replies =
            (1..10).map {
                post(
                    casey,
                    "/api/lookup",
                    """{"code":"HW-ZZ${it + 1}2","pin":"0000"}""",
                ).andReturn().response.contentAsString
            }
        val refused = post(casey, "/api/lookup", good).andReturn().response
        assertEquals(200, refused.status)
        assertEquals(replies.first(), refused.contentAsString, "a refused lookup must look exactly like a wrong guess")
        post(casey, "/api/lookup", good).andExpect { jsonPath("$.found") { value(false) } }
        post(alex, "/api/lookup", good).andExpect { jsonPath("$.found") { value(true) } }
    }

    @Test
    fun `23 wrong PINs sent at the same time - only 5 are checked, then the slip is locked`() {
        val alex = loggedIn(LINCOLN, "Volunteer Alex")
        val p = person(alex, "Quentin Harrowgate", ""","approxAge":50""")
        val slip = post(alex, "/api/persons/$p/slip").andReturn().response.contentAsString
        val code = slip.substringAfter("\"code\":\"").substringBefore("\"")
        val pin = slip.substringAfter("\"pin\":\"").substringBefore("\"")
        val wrong = if (pin == "1111") "2222" else "1111"
        // Three logins (7-8 guesses each, under the per-login limit of 10), so the row lock is what's tested.
        val desks = listOf(alex, loggedIn(LINCOLN, "Lead Jordan"), loggedIn(EASTSIDE, "Volunteer Riley"))
        val pool =
            java.util.concurrent.Executors
                .newFixedThreadPool(23)
        val start = java.util.concurrent.CountDownLatch(1)
        val jobs =
            (1..23).map {
                pool.submit {
                    start.await()
                    post(desks[it % 3], "/api/lookup", """{"code":"$code","pin":"$wrong"}""")
                }
            }
        start.countDown()
        jobs.forEach { it.get() }
        pool.shutdown()
        assertEquals(1, count("SELECT count(*) FROM reunion_codes WHERE code = '$code' AND locked_until > now()"))
        // The 5th wrong PIN locked it and reset the count; the other 18 never reached the PIN check.
        // (Without the row lock, guesses run side by side and the count ends at 23 % 5 = 3.)
        assertEquals(1, count("SELECT count(*) FROM reunion_codes WHERE code = '$code' AND failed_attempts = 0"))
        // Locked: even the right PIN gets the same "didn't match".
        post(alex, "/api/lookup", """{"code":"$code","pin":"$pin"}""").andExpect { jsonPath("$.found") { value(false) } }
    }

    @Test
    fun `a lead can't use release-minor to connect an adult who never said yes`() {
        val alex = loggedIn(LINCOLN, "Volunteer Alex")
        val riley = loggedIn(EASTSIDE, "Volunteer Riley")
        val (searchId, req) = requested(alex, person(riley, "Augustin Vale", ""","approxAge":52"""), "Augustin Vale")
        val morgan = loggedIn(EASTSIDE, "Lead Morgan")
        post(morgan, "/api/lead/requests/$req/release-minor", """{"guardianChecked":true,"note":"x","contactPlan":"Meet at Eastside"}""")
            .andExpect { status { isConflict() } }
        assertEquals("""{"type":"Active"}""", status(alex, searchId))
    }

    @Test
    fun `the next slip check forgets the previous person, and a flagged searcher's message is never read out`() {
        val alex = loggedIn(LINCOLN, "Volunteer Alex")
        val riley = loggedIn(EASTSIDE, "Volunteer Riley")
        val casey = loggedIn(WESTFIELD, "Volunteer Casey")
        val wanted = person(riley, "Philippa Grange", ""","approxAge":33""")
        val slip = post(riley, "/api/persons/$wanted/slip").andReturn().response.contentAsString
        val code = slip.substringAfter("\"code\":\"").substringBefore("\"")
        val pin = slip.substringAfter("\"pin\":\"").substringBefore("\"")
        val searcher = person(alex, "Tobin Grange", ""","approxAge":36""")
        val (_, req) = requested(alex, wanted, "Philippa Grange", searcher)

        post(casey, "/api/lookup", """{"code":"$code","pin":"$pin"}""").andExpect { jsonPath("$.answersWaiting") { value(1) } }
        mvc.get("/api/answers") { session = casey }.andExpect { jsonPath("$[?(@.requestId == '$req')]") { exists() } }
        // The next person at Casey's desk mistypes their PIN: Philippa's requests are gone from this desk.
        post(casey, "/api/lookup", """{"code":"HW-ZZZZ","pin":"0000"}""")
        mvc.get("/api/answers") { session = casey }.andExpect { jsonPath("$[?(@.requestId == '$req')]") { doesNotExist() } }

        // A flag on the searcher: the request disappears from the found person's desk too.
        mvc.get("/api/answers") { session = riley }.andExpect { jsonPath("$[?(@.requestId == '$req')]") { exists() } }
        val jordan = loggedIn(LINCOLN, "Lead Jordan")
        post(jordan, "/api/lead/flags", """{"personId":"$searcher","kind":"suspected_abuser"}""").andExpect { status { isCreated() } }
        mvc.get("/api/answers") { session = riley }.andExpect { jsonPath("$[?(@.requestId == '$req')]") { doesNotExist() } }
    }

    // Everything a searcher-facing reply says: status code, content type and the exact body.
    private fun rawStatus(
        s: MockHttpSession,
        searchId: UUID,
    ) = mvc
        .get("/api/searches/$searchId/status") { session = s }
        .andReturn()
        .response
        .let { Triple(it.status, it.contentType, it.contentAsString) }

    @Test
    fun `no match, pending, declined and blocked get the same status code, content type and raw body`() {
        val alex = loggedIn(LINCOLN, "Volunteer Alex")
        val riley = loggedIn(EASTSIDE, "Volunteer Riley")
        val noMatch = searchFor(alex, person(alex, "Lonely Searcher"), "Nobody Bythisname")
        val (pending, _) = requested(alex, person(riley, "Perdita Fenwright", ""","approxAge":50"""), "Perdita Fenwright")
        val (declined, declinedReq) = requested(alex, person(riley, "Ignatius Moorcroft", ""","approxAge":50"""), "Ignatius Moorcroft")
        post(riley, "/api/requests/$declinedReq/decision", """{"choice":"DECLINE"}""").andExpect { status { isNoContent() } }
        val hiddenPerson = person(riley, "Thaddeus Quillfeather", ""","approxAge":50""")
        val (blocked, blockedReq) = requested(alex, hiddenPerson, "Thaddeus Quillfeather")
        post(riley, "/api/requests/$blockedReq/decision", """{"choice":"CONNECT","contactPlan":"Call the Eastside desk"}""")
            .andExpect { status { isNoContent() } }
        post(loggedIn(EASTSIDE, "Lead Morgan"), "/api/lead/flags", """{"personId":"$hiddenPerson","kind":"custody_hold"}""")
            .andExpect { status { isCreated() } }
        val replies = listOf(noMatch, pending, declined, blocked).map { rawStatus(alex, it) }
        assertEquals(1, replies.distinct().size, "hidden states differ: $replies")
        assertEquals(200, replies.first().first)
    }

    // All keys in a JSON text (no JSON library needed: every key is written as "name":).
    private fun keys(json: String) = Regex("\"([^\"]+)\"\\s*:").findAll(json).map { it.groupValues[1] }.toList()

    private val locationWords = listOf("shelter", "homearea", "phone", "address", "deskphone")

    private fun assertNoLocationKeys(
        json: String,
        what: String,
    ) {
        for (k in keys(json)) {
            assertFalse(locationWords.any { k.lowercase().contains(it) }, "$what has a location key \"$k\": $json")
        }
        // contactPlan is only ever given together with a Connected answer.
        assertTrue(keys(json).count { it == "contactPlan" } <= Regex("\"type\":\"Connected\"").findAll(json).count(), "$what: $json")
    }

    @Test
    fun `no searcher-facing reply has a location key anywhere, and contactPlan only comes with Connected`() {
        val alex = loggedIn(LINCOLN, "Volunteer Alex")
        val riley = loggedIn(EASTSIDE, "Volunteer Riley")
        val casey = loggedIn(WESTFIELD, "Volunteer Casey")
        val son = person(alex, "Barnaby Wetherspoon", ""","approxAge":45""")
        val slip = post(alex, "/api/persons/$son/slip").andReturn().response.contentAsString
        val code = slip.substringAfter("\"code\":\"").substringBefore("\"")
        val pin = slip.substringAfter("\"pin\":\"").substringBefore("\"")
        val located = ""","approxAge":60,"homeArea":"Seaview","phone":"555-0123""""
        val (connectedSearch, connectedReq) = requested(alex, person(riley, "Gwendolyn Ashcombe", located), "Gwendolyn Ashcombe", son)
        post(riley, "/api/requests/$connectedReq/decision", """{"choice":"CONNECT","contactPlan":"Call the Eastside desk"}""")
            .andExpect { status { isNoContent() } }
        val (safeSearch, safeReq) = requested(alex, person(riley, "Percival Pimblett", located), "Percival Pimblett", son)
        post(riley, "/api/requests/$safeReq/decision", """{"choice":"SAFE"}""").andExpect { status { isNoContent() } }
        val (pendingSearch, _) = requested(alex, person(riley, "Hortensia Bellweather", located), "Hortensia Bellweather", son)
        val noMatchSearch = searchFor(alex, son, "Nobody Bythisname")

        for ((label, id) in listOf(
            "connected" to connectedSearch,
            "safe" to safeSearch,
            "pending" to pendingSearch,
            "no match" to noMatchSearch,
        )) {
            assertNoLocationKeys(status(alex, id), "status of the $label search")
        }
        val found = post(casey, "/api/lookup", """{"code":"$code","pin":"$pin"}""").andReturn().response.contentAsString
        assertTrue(found.contains("\"found\":true") && found.contains("Call the Eastside desk"), found)
        assertNoLocationKeys(found, "lookup reply (found)")
        val missed = post(casey, "/api/lookup", """{"code":"$code","pin":"0000"}""").andReturn().response.contentAsString
        assertNoLocationKeys(missed, "lookup reply (not found)")
        for (leak in listOf("Seaview", "555-0123")) assertFalse(found.contains(leak) || missed.contains(leak))
    }

    @Test
    fun `a contact request and every slip check are audited with who did it`() {
        val alex = loggedIn(LINCOLN, "Volunteer Alex")
        val riley = loggedIn(EASTSIDE, "Volunteer Riley")
        val casey = loggedIn(WESTFIELD, "Volunteer Casey")
        val son = person(alex, "Rupert Hollingsworth", ""","approxAge":45""")
        val slip = post(alex, "/api/persons/$son/slip").andReturn().response.contentAsString
        val code = slip.substringAfter("\"code\":\"").substringBefore("\"")
        val pin = slip.substringAfter("\"pin\":\"").substringBefore("\"")
        val (_, req) = requested(alex, person(riley, "Clementine Haverford", ""","approxAge":60"""), "Clementine Haverford", son)

        fun audited(
            action: String,
            who: String,
            target: Any,
        ) = count(
            "SELECT count(*) FROM audit_log a JOIN staff s ON s.id = a.staff_id " +
                "WHERE a.action = '$action' AND a.target = '$target' AND s.display_name = '$who'",
        )
        assertEquals(1, audited("CONTACT_REQUEST", "Volunteer Alex", req), "contact request audit")

        post(casey, "/api/lookup", """{"code":"$code","pin":"0000"}""")
        assertEquals(1, audited("CODE_LOOKUP_FAILED", "Volunteer Casey", son), "failed slip check audit")
        post(casey, "/api/lookup", """{"code":"$code","pin":"$pin"}""")
        assertEquals(1, audited("CODE_LOOKUP", "Volunteer Casey", son), "successful slip check audit")
    }

    // Sends the same kind of request from several threads at once; returns the status codes.
    private fun inParallel(calls: List<() -> Int>): List<Int> {
        val pool =
            java.util.concurrent.Executors
                .newFixedThreadPool(calls.size)
        val start = java.util.concurrent.CountDownLatch(1)
        val jobs =
            calls.map { call ->
                pool.submit<Int> {
                    start.await()
                    call()
                }
            }
        start.countDown()
        val statuses = jobs.map { it.get() }
        pool.shutdown()
        return statuses
    }

    @Test
    fun `different answers sent at the same time to one pending request - exactly one wins, the rest are told it was answered`() {
        val alex = loggedIn(LINCOLN, "Volunteer Alex")
        val riley = loggedIn(EASTSIDE, "Volunteer Riley")
        val (searchId, req) = requested(alex, person(riley, "Ambrose Winterbourne", ""","approxAge":55"""), "Ambrose Winterbourne")
        val answers =
            listOf(
                """{"choice":"CONNECT","contactPlan":"Call the Eastside desk"}""",
                """{"choice":"DECLINE"}""",
                """{"choice":"SAFE"}""",
                """{"choice":"CONNECT","contactPlan":"Meet at the front door"}""",
            )
        val statuses =
            inParallel(
                answers.map { body ->
                    { post(loggedIn(EASTSIDE, "Volunteer Riley"), "/api/requests/$req/decision", body).andReturn().response.status }
                },
            )
        assertEquals(1, statuses.count { it == 204 }, "exactly one answer wins: $statuses")
        assertEquals(answers.size - 1, statuses.count { it == 409 }, "the others get 409: $statuses")
        assertEquals(1, count("SELECT count(*) FROM audit_log WHERE action = 'DECISION' AND target = '$req'"))
        // The searcher sees exactly the winner's answer, never a mix.
        val state =
            TestDatabase.asOwner { c ->
                c.createStatement().executeQuery("SELECT state FROM contact_requests WHERE id = '$req'").use {
                    it.next()
                    it.getString(1)
                }
            }
        val shown = status(alex, searchId)
        when (state) {
            "connected" -> assertTrue(shown.startsWith("""{"type":"Connected""""), shown)
            "safe_only" -> assertEquals("""{"type":"Safe"}""", shown)
            else -> assertEquals("""{"type":"Active"}""", shown)
        }
    }

    @Test
    fun `pressing Connect twice at the same time connects once and audits one decision`() {
        val alex = loggedIn(LINCOLN, "Volunteer Alex")
        val riley = loggedIn(EASTSIDE, "Volunteer Riley")
        val (_, req) = requested(alex, person(riley, "Leopold Thistlewood", ""","approxAge":55"""), "Leopold Thistlewood")
        val connect = """{"choice":"CONNECT","contactPlan":"Call the Eastside desk"}"""
        val statuses = inParallel(List(2) { { post(riley, "/api/requests/$req/decision", connect).andReturn().response.status } })
        assertEquals(listOf(204, 409), statuses.sorted(), "one Connect works, the other is told it was answered")
        assertEquals(1, count("SELECT count(*) FROM audit_log WHERE action = 'DECISION' AND target = '$req'"))
        assertEquals(1, count("SELECT count(*) FROM contact_requests WHERE id = '$req' AND state = 'connected'"))
    }

    @Test
    fun `protecting someone twice keeps one flag, and a flagged child is not offered for release`() {
        val alex = loggedIn(LINCOLN, "Volunteer Alex")
        val riley = loggedIn(EASTSIDE, "Volunteer Riley")
        val kid = person(riley, "Wren Albescu", ""","approxAge":7""")
        val (_, req) = requested(alex, kid, "Wren Albescu")
        val morgan = loggedIn(EASTSIDE, "Lead Morgan")
        mvc
            .get(
                "/api/lead/overview",
            ) { session = morgan }
            .andExpect { jsonPath("$.childrenWaiting[?(@.requestId == '$req')]") { exists() } }
        repeat(
            2,
        ) { post(morgan, "/api/lead/flags", """{"personId":"$kid","kind":"do_not_disclose"}""").andExpect { status { isCreated() } } }
        assertEquals(1, count("SELECT count(*) FROM safety_flags WHERE person_id = '$kid' AND cleared_at IS NULL"))
        mvc
            .get(
                "/api/lead/overview",
            ) { session = morgan }
            .andExpect { jsonPath("$.childrenWaiting[?(@.requestId == '$req')]") { doesNotExist() } }
    }
}
