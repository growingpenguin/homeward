package app.homeward.db

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

// The app never runs migrations (it doesn't have the owner's password), so at startup it checks
// that someone already ran them: an older schema would break the privacy rules the code relies on.
@Component
class SchemaVersionCheck(
    private val jdbc: JdbcClient,
) : ApplicationRunner {
    // The highest V<n>__*.sql bundled with this build.
    val expectedVersion: Int by lazy {
        PathMatchingResourcePatternResolver()
            .getResources("classpath:db/migration/V*__*.sql")
            .mapNotNull {
                Regex("""^V(\d+)__""")
                    .find(it.filename.orEmpty())
                    ?.groupValues
                    ?.get(1)
                    ?.toInt()
            }.maxOrNull() ?: 0
    }

    fun databaseVersion(): Int =
        jdbc
            // Migrations use whole-number versions only (V1, V2, ...); anything else is ignored, never crashes the cast.
            .sql("SELECT coalesce(max(version::int), 0) FROM flyway_schema_history WHERE success AND version ~ '^[0-9]+$'")
            .query(Int::class.java)
            .single()

    // The version seen at startup; /api/health reports this instead of querying the database
    // on every call (frequent health checks would keep Neon awake and use its free hours).
    // (Not `private set`: the Spring plugin makes this class open, and open properties can't have one.)
    @Volatile
    var checkedVersion: Int = 0

    override fun run(args: ApplicationArguments) {
        val actual = databaseVersion()
        checkedVersion = actual
        check(actual >= expectedVersion) {
            "Database schema is at version $actual but this build needs $expectedVersion. " +
                "Run scripts/db-migrate.sh migrate first."
        }
    }
}
