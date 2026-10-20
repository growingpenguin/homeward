package app.homeward.person

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
import org.springframework.test.web.servlet.put
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Demo seed (fictional): Lincoln High Shelter (Volunteer Alex), Eastside Community Center (Volunteer Riley), PIN 2468.
private const val LINCOLN = "d0000000-0000-0000-0000-000000000001"
private const val EASTSIDE = "d0000000-0000-0000-0000-000000000002"

@AutoConfigureMockMvc
class PersonApiTest : IntegrationTest() {
    @Autowired lateinit var mvc: MockMvc

    @Autowired lateinit var throttle: LoginThrottle

    @BeforeEach fun reset() = throttle.clear()

    private fun loggedIn(
        shelter: String = LINCOLN,
        name: String = "Volunteer Alex",
    ) = MockHttpSession().also { s ->
        mvc
            .post("/api/login") {
                session = s
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"shelterId":"$shelter","name":"$name","pin":"2468"}"""
            }.andExpect { status { isOk() } }
    }

    private fun register(
        s: MockHttpSession,
        json: String,
    ) = mvc.post("/api/persons") {
        session = s
        with(csrf())
        contentType = MediaType.APPLICATION_JSON
        content = json
    }

    private fun count(sql: String): Int =
        TestDatabase.asOwner { c ->
            c.createStatement().executeQuery(sql).use {
                it.next()
                it.getInt(1)
            }
        }

    @Test
    fun `register with only a name gets safe defaults, with shelter and volunteer from the login`() {
        val s = loggedIn()
        val id = UUID.randomUUID()
        register(s, """{"id":"$id","preferredName":"Sarah Kim"}""").andExpect {
            status { isCreated() }
            jsonPath("$.visibility") { value("ask") }
            jsonPath("$.treatedAsMinor") { value(true) } // unknown age counts as a child
            jsonPath("$.hasSlip") { value(false) }
        }
        assertEquals(
            1,
            count(
                "SELECT count(*) FROM persons WHERE id = '$id' AND shelter_id = '$LINCOLN' AND entered_by = 'd0000000-0000-0000-0000-0000000000a1'",
            ),
        )
    }

    @Test
    fun `a re-sent form (same id) returns the same person and stores nothing new`() {
        val s = loggedIn()
        val id = UUID.randomUUID()
        register(s, """{"id":"$id","preferredName":"Tom Baker"}""").andExpect { status { isCreated() } }
        register(s, """{"id":"$id","preferredName":"Tom Baker"}""").andExpect { status { isOk() } }
        assertEquals(1, count("SELECT count(*) FROM persons WHERE id = '$id'"))
    }

    @Test
    fun `two forms with the same id sent at the same time store one person and neither fails`() {
        val s = loggedIn()
        val id = UUID.randomUUID()
        val pool =
            java.util.concurrent.Executors
                .newFixedThreadPool(8)
        val start = java.util.concurrent.CountDownLatch(1)
        val jobs =
            (1..8).map {
                pool.submit<Int> {
                    start.await()
                    register(s, """{"id":"$id","preferredName":"Parallel Pennington"}""").andReturn().response.status
                }
            }
        start.countDown()
        val statuses = jobs.map { it.get() }
        pool.shutdown()
        assertTrue(statuses.all { it == 200 || it == 201 }, "statuses were $statuses")
        assertEquals(1, statuses.count { it == 201 }, "exactly one form creates the person: $statuses")
        assertEquals(1, count("SELECT count(*) FROM persons WHERE id = '$id'"))
    }

    @Test
    fun `an unknown field like ssn is refused, not silently dropped`() {
        register(loggedIn(), """{"id":"${UUID.randomUUID()}","preferredName":"X","ssn":"123-45-6789"}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("Something in this form isn't allowed. Please check it and try again.") }
        }
    }

    @Test
    fun `fields we never collect (immigration status, photo, date of birth) are refused with a plain sentence`() {
        for (field in listOf(""""immigrationStatus":"x"""", """"photo":"data:image/png;base64,AAAA"""", """"dateOfBirth":"1990-01-01"""")) {
            register(loggedIn(), """{"id":"${UUID.randomUUID()}","preferredName":"Edgar Fairweather",$field}""").andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("Something in this form isn't allowed. Please check it and try again.") }
            }
        }
        assertEquals(0, count("SELECT count(*) FROM persons WHERE preferred_name = 'Edgar Fairweather'"))
    }

    @Test
    fun `errors are plain sentences`() {
        val s = loggedIn()
        register(s, """{"id":"${UUID.randomUUID()}","preferredName":"   "}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("Please write a name or nickname.") }
        }
        register(
            s,
            """{"id":"${UUID.randomUUID()}","preferredName":"X","source":"paper","recordedAt":"2026-10-17T00:00:00Z"}""",
        ).andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("Write where it is on the paper, e.g. \"Sheet 4, line 12\".") }
        }
        register(s, """{"id":"${UUID.randomUUID()}","preferredName":"X","source":"paper","paperRef":"Sheet 1, line 2"}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("Write the date on the paper. If there isn't one, write the day the forms were collected.") }
        }
        register(s, """{"id":"${UUID.randomUUID()}","preferredName":"Mia","approxAge":9,"isMinor":false}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("This age is under 18, so this person counts as a child.") }
        }
    }

    @Test
    fun `paper registration keeps sheet, line and the paper's date`() {
        val id = UUID.randomUUID()
        register(
            loggedIn(),
            """{"id":"$id","preferredName":"Rosa Diaz","source":"paper","paperRef":"Sheet 4, line 12","recordedAt":"2026-10-17T00:00:00Z"}""",
        ).andExpect {
            status { isCreated() }
            jsonPath("$.source") { value("paper") }
            jsonPath("$.paperRef") { value("Sheet 4, line 12") }
            jsonPath("$.recordedAt") { value("2026-10-17T00:00:00Z") }
        }
    }

    @Test
    fun `a new slip is issued once, with a code and a 4-digit PIN stored only as a hash`() {
        val s = loggedIn()
        val id = UUID.randomUUID()
        register(
            s,
            """{"id":"$id","preferredName":"Ana Lee","visibility":"hidden"}""",
        ).andExpect { jsonPath("$.visibility") { value("hidden") } }
        val body =
            mvc
                .post("/api/persons/$id/slip") {
                    session = s
                    with(csrf())
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.code") { value(org.hamcrest.Matchers.matchesRegex("^HW-[2-9A-HJ-NP-Z]{4}$")) }
                    jsonPath("$.pin") { value(org.hamcrest.Matchers.matchesRegex("^[0-9]{4}$")) }
                    jsonPath("$.shelterName") { value("Lincoln High Shelter") }
                }.andReturn()
                .response.contentAsString
        val pin = Regex("\"pin\":\"(\\d{4})\"").find(body)!!.groupValues[1] // !!: asserted just above
        assertEquals(0, count("SELECT count(*) FROM reunion_codes WHERE person_id = '$id' AND pin_hash LIKE '%$pin%'"))
        assertEquals(1, count("SELECT count(*) FROM audit_log WHERE action = 'CODE_ISSUED' AND target = '$id'"))
        mvc
            .post("/api/persons/$id/slip") {
                session = s
                with(csrf())
            }.andExpect {
                status { isConflict() }
                jsonPath("$.message") { value("This person already has a slip. If it's lost, ask a lead to reprint it.") }
            }
    }

    @Test
    fun `a pre-printed slip is attached at registration only while it's free`() {
        TestDatabase.asOwner {
            it.createStatement().execute(
                "INSERT INTO reunion_codes (code, pin_hash) VALUES ('HW-3K7Q', '\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy') ON CONFLICT DO NOTHING",
            )
        }
        val s = loggedIn()
        val first = UUID.randomUUID()
        register(
            s,
            """{"id":"$first","preferredName":"Paper Person","source":"paper","paperRef":"S1 L1","recordedAt":"2026-10-17T00:00:00Z","slipCode":"HW-3K7Q"}""",
        ).andExpect { jsonPath("$.hasSlip") { value(true) } }
        val second = UUID.randomUUID()
        register(s, """{"id":"$second","preferredName":"Someone Else","slipCode":"HW-3K7Q"}""").andExpect { status { isConflict() } }
        assertEquals(0, count("SELECT count(*) FROM persons WHERE id = '$second'")) // the whole registration was rolled back
    }

    @Test
    fun `another shelter's person looks like 'not found'`() {
        val id = UUID.randomUUID()
        register(loggedIn(), """{"id":"$id","preferredName":"Grace Park"}""").andExpect { status { isCreated() } }
        val eastside = loggedIn(EASTSIDE, "Volunteer Riley")
        mvc.get("/api/persons/$id") { session = eastside }.andExpect { status { isNotFound() } }
        mvc
            .post("/api/persons/$id/slip") {
                session = eastside
                with(csrf())
            }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `an edit records who made it, from the login`() {
        val s = loggedIn()
        val id = UUID.randomUUID()
        register(s, """{"id":"$id","preferredName":"Sarah Kimm"}""")
        mvc
            .put("/api/persons/$id") {
                session = s
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"preferredName":"Sarah Kim","approxAge":29,"unaccompanied":false,"visibility":"ask"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.preferredName") { value("Sarah Kim") }
                jsonPath("$.treatedAsMinor") { value(false) }
            }
        assertEquals(
            1,
            count(
                "SELECT count(*) FROM person_history WHERE person_id = '$id' AND before->>'preferred_name' = 'Sarah Kimm' AND changed_by = 'd0000000-0000-0000-0000-0000000000a1'",
            ),
        )
    }

    @Test
    fun `an edit that leaves out the privacy choice is refused, never reset to the default`() {
        val s = loggedIn()
        val id = UUID.randomUUID()
        register(s, """{"id":"$id","preferredName":"Dana Ruiz","visibility":"hidden","unaccompanied":false}""")
        mvc
            .put("/api/persons/$id") {
                session = s
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"preferredName":"Dana Ruiz","unaccompanied":false}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("Please choose one of the three options.") }
            }
        assertEquals(1, count("SELECT count(*) FROM persons WHERE id = '$id' AND visibility = 'hidden'"))
    }

    @Test
    fun `a slip code typed in lower case attaches, and a re-send can add a slip code`() {
        TestDatabase.asOwner {
            it.createStatement().execute(
                "INSERT INTO reunion_codes (code, pin_hash) VALUES ('HW-5M8R', '\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy') ON CONFLICT DO NOTHING",
            )
        }
        val s = loggedIn()
        val id = UUID.randomUUID()
        register(s, """{"id":"$id","preferredName":"Late Slip","otherNames":[null,"  ","Lee"]}""").andExpect {
            status { isCreated() }
            jsonPath("$.otherNames.length()") { value(1) }
            jsonPath("$.hasSlip") { value(false) }
        }
        register(s, """{"id":"$id","preferredName":"Late Slip","slipCode":" hw-5m8r "}""").andExpect {
            status { isOk() }
            jsonPath("$.hasSlip") { value(true) }
        }
    }

    @Test
    fun `nothing works without logging in, and the OpenAPI description is public`() {
        register(MockHttpSession(), """{"id":"${UUID.randomUUID()}","preferredName":"X"}""").andExpect { status { isUnauthorized() } }
        val api =
            mvc
                .get("/api/openapi.json")
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        assertTrue(api.contains("RegisterPersonRequest") && !api.contains("\"ssn\""))
        // isMinor appears once per type, never also as a stray "minor" (from the Kotlin getter's name).
        assertTrue(api.contains("\"isMinor\"") && !api.contains("\"minor\""), "stray minor field in the OpenAPI description")
    }
}
