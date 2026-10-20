package app.homeward.db

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DatabaseUrlTest {
    private val jdbc = "jdbc:postgresql://ep-example-123.us-east-2.aws.neon.tech/neondb?sslmode=require"

    @Test
    fun `the forms people paste all become the JDBC address`() {
        for (pasted in listOf(
            jdbc,
            "  $jdbc\n",
            "\"$jdbc\"",
            "DB_URL=$jdbc",
            "postgresql://neondb_owner:secret@ep-example-123.us-east-2.aws.neon.tech/neondb?sslmode=require",
            "postgres://u:p@ep-example-123.us-east-2.aws.neon.tech/neondb?sslmode=require",
        )) {
            assertEquals(jdbc, jdbcUrlFrom(pasted), "pasted: $pasted")
        }
        assertEquals("jdbc:postgresql://localhost:5432/homeward", jdbcUrlFrom("postgresql://localhost:5432/homeward"))
    }

    @Test
    fun `anything else fails with a clear message that never repeats the value`() {
        val e = assertFailsWith<IllegalStateException> { jdbcUrlFrom("npg_SuperSecretPassword") }
        assertFalse(e.message!!.contains("SuperSecret"))
    }
}
