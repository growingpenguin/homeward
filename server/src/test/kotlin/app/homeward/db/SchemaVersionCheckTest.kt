package app.homeward.db

import app.homeward.IntegrationTest
import app.homeward.TestDatabase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SchemaVersionCheckTest : IntegrationTest() {
    @Autowired
    lateinit var check: SchemaVersionCheck

    @Test
    fun `the build expects the highest bundled migration and the database has it`() {
        assertEquals(2, check.expectedVersion)
        assertEquals(2, check.databaseVersion())
        check.verify()
    }

    @Test
    fun `the app refuses to run when the database is older than the code`() {
        // Pretend V2 was never applied, then put it back.
        TestDatabase.asOwner { it.createStatement().execute("UPDATE flyway_schema_history SET success = false WHERE version = '2'") }
        try {
            val e = assertFailsWith<IllegalStateException> { check.verify() }
            assertTrue(e.message!!.contains("scripts/db-migrate.sh migrate"))
        } finally {
            TestDatabase.asOwner { it.createStatement().execute("UPDATE flyway_schema_history SET success = true WHERE version = '2'") }
        }
    }
}
