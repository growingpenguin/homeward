package app.homeward.security

import app.homeward.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

// Demo seed (fictional): Lincoln High Shelter has "Volunteer Alex" and "Lead Jordan", PIN 2468.
private const val LINCOLN = "d0000000-0000-0000-0000-000000000001"

// A lead-only endpoint that exists only in tests, to check roles.
@RestController
class LeadOnlyTestController {
    @GetMapping("/api/test/lead-only")
    @PreAuthorize("hasRole('LEAD')")
    fun leadOnly() = mapOf("ok" to true)
}

@AutoConfigureMockMvc
class AuthTest : IntegrationTest() {
    @Autowired lateinit var mvc: MockMvc

    @Autowired lateinit var throttle: LoginThrottle

    @BeforeEach fun reset() = throttle.clear()

    private fun login(
        name: String,
        pin: String,
        session: MockHttpSession = MockHttpSession(),
    ) = mvc.post("/api/login") {
        this.session = session
        with(csrf())
        contentType = MediaType.APPLICATION_JSON
        content = """{"shelterId":"$LINCOLN","name":"$name","pin":"$pin"}"""
    }

    @Test
    fun `shelters are public, ids and names only`() {
        mvc.get("/api/shelters").andExpect {
            status { isOk() }
            jsonPath("$[?(@.name == 'Lincoln High Shelter')]") { exists() }
            jsonPath("$[0].desk_phone") { doesNotExist() }
        }
    }

    @Test
    fun `a volunteer logs in with shelter, name and PIN, typed loosely`() {
        val session = MockHttpSession()
        login("  volunteer alex ", "2468", session).andExpect {
            status { isOk() }
            jsonPath("$.displayName") { value("Volunteer Alex") }
            jsonPath("$.role") { value("volunteer") }
        }
        mvc.get("/api/me") { this.session = session }.andExpect { jsonPath("$.displayName") { value("Volunteer Alex") } }
    }

    @Test
    fun `an unknown name and a wrong PIN get exactly the same answer`() {
        val wrongPin =
            login("Volunteer Alex", "0000")
                .andExpect { status { isUnauthorized() } }
                .andReturn()
                .response.contentAsString
        val unknownName =
            login("Nobody Here", "2468")
                .andExpect { status { isUnauthorized() } }
                .andReturn()
                .response.contentAsString
        assertEquals(wrongPin, unknownName)
    }

    @Test
    fun `login without a CSRF token is refused`() {
        mvc
            .post("/api/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"shelterId":"$LINCOLN","name":"Volunteer Alex","pin":"2468"}"""
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `five wrong PINs lock the name, even for the right PIN`() {
        repeat(4) { login("Volunteer Alex", "0000").andExpect { status { isUnauthorized() } } }
        login("Volunteer Alex", "0000").andExpect { status { isTooManyRequests() } }
        login("Volunteer Alex", "2468").andExpect { status { isTooManyRequests() } }
    }

    @Test
    fun `nothing is reachable without logging in`() {
        mvc.get("/api/me").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a volunteer gets 403 on lead-only actions, a lead gets through`() {
        val volunteer = MockHttpSession().also { login("Volunteer Alex", "2468", it) }
        mvc.get("/api/test/lead-only") { session = volunteer }.andExpect { status { isForbidden() } }
        val lead = MockHttpSession().also { login("Lead Jordan", "2468", it) }
        mvc.get("/api/test/lead-only") { session = lead }.andExpect { status { isOk() } }
    }

    @Test
    fun `after 10 idle minutes the screen locks until the PIN is entered again`() {
        val session = MockHttpSession().also { login("Volunteer Alex", "2468", it) }
        session.setAttribute(IdleLockFilter.LAST_ACTIVITY, Instant.now().minus(Duration.ofMinutes(11)))
        mvc.get("/api/me") { this.session = session }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.locked") { value(true) }
        }
        mvc
            .post("/api/unlock") {
                this.session = session
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"pin":"0000"}"""
            }.andExpect { status { isUnauthorized() } }
        mvc
            .post("/api/unlock") {
                this.session = session
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"pin":"2468"}"""
            }.andExpect { status { isOk() } }
        mvc.get("/api/me") { this.session = session }.andExpect { status { isOk() } }
    }

    @Test
    fun `background requests don't keep the screen unlocked, a person's action does`() {
        val session = MockHttpSession().also { login("Volunteer Alex", "2468", it) }
        val nineMinutesAgo = Instant.now().minus(Duration.ofMinutes(9))
        session.setAttribute(IdleLockFilter.LAST_ACTIVITY, nineMinutesAgo)
        mvc.get("/api/me") { this.session = session } // background: no activity header
        assertEquals(nineMinutesAgo, session.getAttribute(IdleLockFilter.LAST_ACTIVITY))
        mvc.get("/api/me") {
            this.session = session
            header(IdleLockFilter.ACTIVITY_HEADER, "1")
        }
        assert((session.getAttribute(IdleLockFilter.LAST_ACTIVITY) as Instant).isAfter(nineMinutesAgo))
    }

    @Test
    fun `a shift ends 12 hours after login, even with requests coming in`() {
        val session = MockHttpSession().also { login("Volunteer Alex", "2468", it) }
        session.setAttribute(IdleLockFilter.LOGIN_AT, Instant.now().minus(Duration.ofHours(13)))
        session.setAttribute(IdleLockFilter.LAST_ACTIVITY, Instant.now())
        mvc.get("/api/me") { this.session = session }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.expired") { value(true) }
        }
    }

    @Test
    fun `logout ends the session`() {
        val session = MockHttpSession().also { login("Volunteer Alex", "2468", it) }
        mvc.post("/api/logout") {
            this.session = session
            with(csrf())
        }
        mvc.get("/api/me") { this.session = session }.andExpect { status { isUnauthorized() } }
    }
}

// The real browser flow, without MockMvc's csrf() shortcut: GET /api/csrf, then send the cookie's value back.
@AutoConfigureMockMvc
class CsrfFlowTest : IntegrationTest() {
    @Autowired lateinit var mvc: MockMvc

    @Autowired lateinit var throttle: LoginThrottle

    @Test
    fun `login works with the XSRF-TOKEN cookie value sent back in the header`() {
        throttle.clear()
        val first =
            mvc
                .get("/api/csrf")
                .andExpect { jsonPath("$.headerName") { value("X-XSRF-TOKEN") } }
                .andReturn()
                .response
        val cookie = first.getCookie("XSRF-TOKEN")!! // !!: the endpoint exists to set it
        mvc
            .post("/api/login") {
                cookie(cookie)
                header("X-XSRF-TOKEN", cookie.value)
                contentType = MediaType.APPLICATION_JSON
                content = """{"shelterId":"$LINCOLN","name":"Volunteer Alex","pin":"2468"}"""
            }.andExpect { status { isOk() } }
    }
}
