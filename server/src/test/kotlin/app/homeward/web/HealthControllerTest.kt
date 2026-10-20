package app.homeward.web

import app.homeward.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@AutoConfigureMockMvc
class HealthControllerTest : IntegrationTest() {
    @Autowired
    lateinit var mvc: MockMvc

    @Test
    fun `health is public and shows the schema version`() {
        mvc.get("/api/health").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ok") }
            jsonPath("$.schemaVersion") { value(2) }
        }
    }

    @Test
    fun `everything else needs a login`() {
        mvc.get("/api/persons").andExpect { status { isUnauthorized() } }
    }
}
