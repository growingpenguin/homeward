package app.homeward.consent

import app.homeward.IntegrationTest
import app.homeward.TestDatabase
import app.homeward.security.LoginThrottle
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// One journey through the public API, with separate logged-in sessions at three fictional desks.
// No database shortcuts set up people, searches, requests or decisions.
@AutoConfigureMockMvc
class ReunionApiTest : IntegrationTest() {
    @Autowired lateinit var mvc: MockMvc

    @Autowired lateinit var throttle: LoginThrottle

    private val json = JsonMapper.builder().build()

    private fun post(
        session: MockHttpSession,
        path: String,
        body: Map<String, Any?> = emptyMap(),
        expected: Int = 200,
    ): JsonNode {
        val response =
            mvc
                .post(path) {
                    this.session = session
                    with(csrf())
                    contentType = MediaType.APPLICATION_JSON
                    content = json.writeValueAsString(body)
                }.andExpect { status { isEqualTo(expected) } }
                .andReturn()
                .response
        return json.readTree(response.contentAsString.ifEmpty { "null" })
    }

    private fun login(
        desk: Int,
        name: String,
    ) = MockHttpSession().also {
        post(it, "/api/login", mapOf("shelterId" to "d0000000-0000-0000-0000-00000000000$desk", "name" to name, "pin" to "2468"))
    }

    private fun register(
        session: MockHttpSession,
        name: String,
        age: Int,
        visibility: String = "ask",
    ): String {
        val id = UUID.randomUUID().toString()
        post(
            session,
            "/api/persons",
            mapOf(
                "id" to id,
                "preferredName" to name,
                "approxAge" to age,
                "visibility" to visibility,
                "homeArea" to "Private test street",
                "phone" to "555-0198",
            ),
            201,
        )
        return id
    }

    private fun status(
        session: MockHttpSession,
        search: String,
    ): String =
        mvc
            .get("/api/searches/$search/status") { this.session = session }
            .andExpect { status { isOk() } }
            .andReturn()
            .response.contentAsString

    @Test
    fun `Sarah and Mina reunite across three desks only after consent, and protections still win`() {
        throttle.clear()
        val alex = login(1, "Volunteer Alex")
        val riley = login(2, "Volunteer Riley")
        val casey = login(3, "Volunteer Casey")
        val morgan = login(2, "Lead Morgan")
        val sarah = register(riley, "Sarah Kim", 34)
        val mina = register(alex, "Mina Kim", 31)
        val slip = post(alex, "/api/persons/$mina/slip", expected = 201)
        val credentials = mapOf("code" to slip["code"].asString(), "pin" to slip["pin"].asString())
        val search = UUID.randomUUID().toString()
        val matches =
            post(
                alex,
                "/api/searches",
                mapOf("id" to search, "searcherPersonId" to mina, "soughtNames" to listOf("Sara Kim"), "ageMin" to 30, "ageMax" to 40),
                201,
            )["matches"]
        val card = matches.single { it["personId"].asString() == sarah }
        assertEquals(
            setOf("personId", "preferredName", "approxAge", "ageIsEstimate", "child", "score", "reasons", "requested"),
            card.propertyNames().toSet(),
        )
        assertTrue(card["reasons"].size() > 0)
        assertFalse(matches.toString().contains("Private test street"))
        assertFalse(matches.toString().contains("555-0198"))
        val request =
            post(
                alex,
                "/api/searches/$search/requests",
                mapOf(
                    "personId" to sarah,
                    "message" to "Sarah, your sister Mina is looking for you.",
                ),
                201,
            )["requestId"].asString()
        val active = """{"type":"Active"}"""
        assertEquals(active, status(alex, search))
        val before = post(casey, "/api/lookup", credentials)
        assertTrue(before["found"].asBoolean())
        assertEquals(active, before["searches"].single { it["searchId"].asString() == search }["status"].toString())
        post(casey, "/api/lookup/end", expected = 204)
        mvc.get("/api/answers") { session = riley }.andExpect {
            status { isOk() }
            jsonPath("$[?(@.requestId == '$request')]") { exists() }
        }
        val plan = "Call the Eastside desk and ask for Sarah"
        post(riley, "/api/requests/$request/decision", mapOf("choice" to "CONNECT", "contactPlan" to plan), 204)
        // Even after consent, an unrelated desk cannot bypass the slip verification.
        assertEquals(active, status(casey, search))
        val after = post(casey, "/api/lookup", credentials)
        val connected = after["searches"].single { it["searchId"].asString() == search }["status"]
        assertEquals(json.readTree("""{"type":"Connected","contactPlan":"$plan"}"""), connected)
        assertEquals(connected.toString(), status(alex, search))
        assertEquals(connected.toString(), status(casey, search))
        post(casey, "/api/lookup/end", expected = 204)
        assertEquals(active, status(casey, search))

        // Adding protection withdraws even the already-consented answer at every desk.
        post(morgan, "/api/lead/flags", mapOf("personId" to sarah, "kind" to "do_not_disclose"), 201)
        assertEquals(active, status(alex, search))
        val protected = post(casey, "/api/lookup", credentials)
        assertEquals(active, protected["searches"].single { it["searchId"].asString() == search }["status"].toString())
        val refreshed = post(alex, "/api/searches/$search/check")
        // Already-requested cards deliberately stay visible: disappearing would reveal the flag.
        assertTrue(refreshed["matches"].single { it["personId"].asString() == sarah }["requested"].asBoolean())
        val freshSearch = UUID.randomUUID().toString()
        val fresh =
            post(alex, "/api/searches", mapOf("id" to freshSearch, "searcherPersonId" to mina, "soughtNames" to listOf("Sarah Kim")), 201)
        assertFalse(fresh["matches"].any { it["personId"].asString() == sarah })

        // Hidden people never become a candidate; a guessed person ID cannot create the request.
        val hidden = register(riley, "Sarah Hiddenkim", 34, "hidden")
        val hiddenSearch = UUID.randomUUID().toString()
        val hiddenResult =
            post(
                alex,
                "/api/searches",
                mapOf(
                    "id" to hiddenSearch,
                    "searcherPersonId" to mina,
                    "soughtNames" to listOf("Sarah Hiddenkim"),
                ),
                201,
            )
        assertFalse(hiddenResult["matches"].any { it["personId"].asString() == hidden })
        post(alex, "/api/searches/$hiddenSearch/requests", mapOf("personId" to hidden), 404)
        assertEquals(active, status(alex, hiddenSearch))

        val child = register(riley, "Sarah Youngkim", 14)
        val childSearch = UUID.randomUUID().toString()
        post(alex, "/api/searches", mapOf("id" to childSearch, "searcherPersonId" to mina, "soughtNames" to listOf("Sarah Youngkim")), 201)
        val childRequest = post(alex, "/api/searches/$childSearch/requests", mapOf("personId" to child), 201)["requestId"].asString()
        post(riley, "/api/requests/$childRequest/decision", mapOf("choice" to "CONNECT", "contactPlan" to plan), 409)
        assertEquals(active, status(alex, childSearch))
        post(
            morgan,
            "/api/lead/requests/$childRequest/release-minor",
            mapOf(
                "guardianChecked" to true,
                "note" to "Guardian and school list checked",
                "contactPlan" to plan,
            ),
            204,
        )
        assertEquals(connected.toString(), status(alex, childSearch))

        // Observe actual persisted audit events; the test never writes audit rows itself.
        TestDatabase.asOwner { connection ->
            connection.prepareStatement("SELECT action FROM audit_log WHERE target = ?").use { statement ->
                statement.setObject(1, UUID.fromString(request))
                val actions = statement.executeQuery().use { rows -> buildSet { while (rows.next()) add(rows.getString(1)) } }
                assertTrue(actions.containsAll(setOf("CONTACT_REQUEST", "DECISION", "REVEALED_CONTACT")), actions.toString())
                statement.setObject(1, UUID.fromString(search))
                val searchActions = statement.executeQuery().use { rows -> buildSet { while (rows.next()) add(rows.getString(1)) } }
                assertTrue("SEARCH" in searchActions)
                statement.setObject(1, UUID.fromString(mina))
                val slipActions = statement.executeQuery().use { rows -> buildSet { while (rows.next()) add(rows.getString(1)) } }
                assertTrue(slipActions.containsAll(setOf("CODE_ISSUED", "CODE_LOOKUP")), slipActions.toString())
            }
        }
    }
}
