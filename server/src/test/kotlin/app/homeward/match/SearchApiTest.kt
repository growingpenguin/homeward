package app.homeward.match

import app.homeward.IntegrationTest
import app.homeward.TestDatabase
import app.homeward.security.LoginThrottle
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

// Demo seed (fictional): Lincoln High Shelter (Volunteer Alex), Eastside Community Center (Volunteer Riley), PIN 2468.
private const val LINCOLN = "d0000000-0000-0000-0000-000000000001"
private const val EASTSIDE = "d0000000-0000-0000-0000-000000000002"

@AutoConfigureMockMvc
class SearchApiTest : IntegrationTest() {
    @Autowired lateinit var mvc: MockMvc

    @Autowired lateinit var throttle: LoginThrottle

    @BeforeEach fun reset() = throttle.clear()

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
        json: String,
    ) = mvc.post(url) {
        session = s
        with(csrf())
        contentType = MediaType.APPLICATION_JSON
        content = json
    }

    // Registers someone at the logged-in volunteer's desk and returns their id.
    private fun person(
        s: MockHttpSession,
        name: String,
        extra: String = "",
    ): UUID {
        val id = UUID.randomUUID()
        post(s, "/api/persons", """{"id":"$id","preferredName":"$name"$extra}""").andExpect { status { isCreated() } }
        return id
    }

    private fun search(
        s: MockHttpSession,
        searcher: UUID,
        names: String,
        ages: String = "",
    ) = post(s, "/api/searches", """{"id":"${UUID.randomUUID()}","searcherPersonId":"$searcher","soughtNames":[$names]$ages}""")

    private fun count(sql: String): Int =
        TestDatabase.asOwner { c ->
            c.createStatement().executeQuery(sql).use {
                it.next()
                it.getInt(1)
            }
        }

    @Test
    fun `a misspelled name finds someone at another shelter, with reasons and without where they are`() {
        val riley = loggedIn(EASTSIDE, "Volunteer Riley")
        val rosa = person(riley, "Rosalind Quartermaine", ""","approxAge":80,"homeArea":"Seaview","phone":"555-0177"""")
        val alex = loggedIn(LINCOLN, "Volunteer Alex")
        val son = person(alex, "Marcus Quartermaine", ""","approxAge":50""")
        val body =
            search(alex, son, "\"Rosalin Quartermain\"", ""","ageMin":75,"ageMax":85""")
                .andExpect {
                    status { isCreated() }
                    jsonPath("$.matches[0].personId") { value(rosa.toString()) }
                    jsonPath("$.matches[0].reasons[0]") { value("name spelled like Rosalind Quartermaine") }
                }.andReturn()
                .response.contentAsString
        // No location anywhere in the reply: not the shelter, home area, phone or desk.
        for (leak in listOf("Eastside", "Seaview", "555-0177", "555-0102", "shelter", "homeArea", "phone")) {
            assertFalse(body.contains(leak), "the search reply must not contain $leak: $body")
        }
        assertTrue(body.contains("age fits (about 80)"))
        assertEquals(
            1,
            count(
                "SELECT count(*) FROM audit_log WHERE action = 'SEARCH' AND target IN (SELECT id FROM searches WHERE searcher_person_id = '$son')",
            ),
        )
    }

    @Test
    fun `hidden people are never suggested, and the searcher isn't suggested to themselves`() {
        val riley = loggedIn(EASTSIDE, "Volunteer Riley")
        person(riley, "Theodora Blackwood", ""","visibility":"hidden"""")
        val alex = loggedIn(LINCOLN, "Volunteer Alex")
        val searcher = person(alex, "Theodora Blackwood")
        search(alex, searcher, "\"Theodora Blackwood\"").andExpect {
            status { isCreated() }
            jsonPath("$.matches.length()") { value(0) }
        }
    }

    @Test
    fun `check again finds someone registered after the search, never twice`() {
        val alex = loggedIn(LINCOLN, "Volunteer Alex")
        val searcher = person(alex, "Ingrid Solberg")
        val searchId =
            search(alex, searcher, "\"Henrik Solberg\"")
                .andExpect { jsonPath("$.matches.length()") { value(0) } }
                .andReturn()
                .response.contentAsString
                .substringAfter("\"searchId\":\"")
                .substringBefore("\"")
        val riley = loggedIn(EASTSIDE, "Volunteer Riley")
        val henrik =
            person(riley, "Henrik Solberg", ""","source":"paper","paperRef":"Sheet 9, line 3","recordedAt":"2026-10-19T12:00:00Z"""")
        repeat(2) {
            mvc.get("/api/searches/$searchId") { session = alex }.andExpect {
                status { isOk() }
                jsonPath("$.matches.length()") { value(1) }
                jsonPath("$.matches[0].personId") { value(henrik.toString()) }
            }
        }
        assertEquals(1, count("SELECT count(*) FROM match_candidates WHERE search_id = '$searchId'"))
        // Another desk can't open this search.
        mvc.get("/api/searches/$searchId") { session = riley }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `a contact request only goes to a suggested match, starts as pending and is audited`() {
        val riley = loggedIn(EASTSIDE, "Volunteer Riley")
        val wanted = person(riley, "Ottoline Pembridge", ""","approxAge":44""")
        val stranger = person(riley, "Zebulon Fairweather")
        val alex = loggedIn(LINCOLN, "Volunteer Alex")
        val searcher = person(alex, "Clem Pembridge")
        val searchId =
            search(alex, searcher, "\"Ottoline Pembridge\"")
                .andReturn()
                .response.contentAsString
                .substringAfter("\"searchId\":\"")
                .substringBefore("\"")
        post(alex, "/api/searches/$searchId/requests", """{"personId":"$stranger"}""").andExpect {
            status { isNotFound() }
            jsonPath("$.message") { value("That person isn't one of the suggested matches.") }
        }
        repeat(2) {
            post(alex, "/api/searches/$searchId/requests", """{"personId":"$wanted","message":"Your brother Clem is looking for you."}""")
                .andExpect { status { isCreated() } }
        }
        assertEquals(
            1,
            count("SELECT count(*) FROM contact_requests WHERE search_id = '$searchId' AND person_id = '$wanted' AND state = 'pending'"),
        )
        // Both sends are actions a volunteer took, so both are in the audit log (only ids, never the message).
        assertEquals(
            2,
            count(
                "SELECT count(*) FROM audit_log WHERE action = 'CONTACT_REQUEST' AND target IN (SELECT id FROM contact_requests WHERE search_id = '$searchId')",
            ),
        )
        mvc.get("/api/searches/$searchId") { session = alex }.andExpect { jsonPath("$.matches[0].requested") { value(true) } }
    }

    @Test
    fun `the person searching must be registered at this desk first`() {
        val riley = loggedIn(EASTSIDE, "Volunteer Riley")
        val elsewhere = person(riley, "Wanda Ostrowski")
        val alex = loggedIn(LINCOLN, "Volunteer Alex")
        search(alex, elsewhere, "\"Piotr Ostrowski\"").andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("Please register the person who is looking first.") }
        }
    }
}
