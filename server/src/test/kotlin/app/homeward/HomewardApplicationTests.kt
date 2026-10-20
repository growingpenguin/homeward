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

    @Test
    fun `the fictional demo seed is loaded, with no coordinator account`() {
        assertEquals(6, jdbc.sql("SELECT count(*) FROM staff WHERE id::text LIKE 'd0000000-%'").query(Int::class.java).single())
        assertEquals(0, jdbc.sql("SELECT count(*) FROM staff WHERE role = 'coordinator'").query(Int::class.java).single())
    }
}
