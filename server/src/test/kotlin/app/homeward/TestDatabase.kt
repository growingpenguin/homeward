package app.homeward

import org.flywaydb.core.Flyway
import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.sql.DriverManager

// One real PostgreSQL 18.6 (Neon's version) for all tests, set up the way production is:
// migrations and the demo seed run as the owner, and the app connects as homeward_app.
object TestDatabase {
    const val APP_PASSWORD = "test-app-password"

    val container: PostgreSQLContainer by lazy {
        PostgreSQLContainer(DockerImageName.parse("postgres:18.6")).apply {
            start()
            migrateAsOwner(this)
        }
    }

    private fun migrateAsOwner(db: PostgreSQLContainer) {
        Flyway
            .configure()
            .dataSource(db.jdbcUrl, db.username, db.password)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        DriverManager.getConnection(db.jdbcUrl, db.username, db.password).use { conn ->
            conn.createStatement().use { st ->
                // Gradle runs tests from server/; fail loudly if the seed can't be found.
                val seed = File("src/main/resources/db/seed/demo_seed.sql")
                check(seed.exists()) { "Demo seed not found at ${seed.absolutePath} (run tests from server/ with Gradle)" }
                st.execute(seed.readText())
                st.execute("ALTER ROLE homeward_app PASSWORD '$APP_PASSWORD'")
            }
        }
    }

    // Points the app at the container as homeward_app (never as the owner).
    fun registerAppDataSource(registry: DynamicPropertyRegistry) {
        registry.add("spring.datasource.url") { container.jdbcUrl }
        registry.add("spring.datasource.username") { "homeward_app" }
        registry.add("spring.datasource.password") { APP_PASSWORD }
    }

    // Owner connection for tests that need to look behind the app's back (or break things on purpose).
    fun <T> asOwner(block: (java.sql.Connection) -> T): T =
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use(block)
}
