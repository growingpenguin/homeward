package app.homeward.db

import app.homeward.IntegrationTest
import app.homeward.TestDatabase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// The newest migration in the source tree, so these tests don't need editing for every new V<n>.
val newestMigration: Int =
    File("src/main/resources/db/migration")
        .list()!!
        .mapNotNull {
            Regex("""^V(\d+)__.*\.sql$""")
                .find(it)
                ?.groupValues
                ?.get(1)
                ?.toInt()
        }.max()

class SchemaVersionCheckTest : IntegrationTest() {
    @Autowired
    lateinit var check: SchemaVersionCheck

    @Test
    fun `the build expects the highest bundled migration and the database has it`() {
        assertEquals(newestMigration, check.expectedVersion)
        assertEquals(newestMigration, check.databaseVersion())
        check.verify()
    }

    @Test
    fun `the app refuses to run when the database is older than the code`() {
        // Pretend the newest migration was never applied, then put it back.
        TestDatabase.asOwner {
            it.createStatement().execute(
                "UPDATE flyway_schema_history SET success = false WHERE version = '$newestMigration'",
            )
        }
        try {
            val e = assertFailsWith<IllegalStateException> { check.verify() }
            assertTrue(e.message!!.contains("scripts/db-migrate.sh migrate"))
        } finally {
            TestDatabase.asOwner {
                it.createStatement().execute(
                    "UPDATE flyway_schema_history SET success = true WHERE version = '$newestMigration'",
                )
            }
        }
    }
}
