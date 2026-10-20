package app.homeward

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import kotlin.test.assertEquals

class HomewardApplicationTests : IntegrationTest() {
    @Autowired
    lateinit var jdbc: JdbcClient

    @Test
    fun `the app connects as the least-privilege user, never as the owner`() {
        assertEquals("homeward_app", jdbc.sql("SELECT current_user").query(String::class.java).single())
    }
}
