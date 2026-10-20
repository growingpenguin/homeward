package app.homeward.db

import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI

// What gets pasted into a hosting dashboard varies: "jdbc:postgresql://host/db?sslmode=require" (what the
// docs ask for), Neon's own "postgresql://user:pass@host/db?sslmode=require", or either with quotes, spaces,
// a line break or a stray "DB_URL=" in front. All of them become the JDBC address here, once.
// Any login inside a pasted URL is ignored: the app always connects as DB_USER / DB_PASSWORD
// (homeward_app), never as whoever the URL names (on Neon that's often the owner).
fun jdbcUrlFrom(raw: String): String {
    val text =
        raw
            .trim()
            .removePrefix("DB_URL=")
            .trim()
            .trim('"', '\'')
            .trim()
    if (text.startsWith("jdbc:postgresql://")) return text
    if (text.startsWith("postgresql://") || text.startsWith("postgres://")) {
        val uri = URI(text.replaceFirst("postgres://", "postgresql://"))
        val port = if (uri.port == -1) "" else ":${uri.port}"
        val query = uri.rawQuery?.let { "?$it" } ?: ""
        return "jdbc:postgresql://${uri.host}$port${uri.rawPath}$query"
    }
    // Never echo the value: if a password was pasted here by mistake, it must not end up in the logs.
    throw IllegalStateException("DB_URL isn't a database address. It should start with jdbc:postgresql:// (or postgresql://).")
}

@Configuration
class DatabaseUrl {
    @Bean
    fun jdbcConnectionDetails(properties: DataSourceProperties): JdbcConnectionDetails {
        val url = jdbcUrlFrom(properties.url ?: throw IllegalStateException("DB_URL is not set."))
        return object : JdbcConnectionDetails {
            override fun getJdbcUrl() = url

            override fun getUsername(): String = properties.username ?: "homeward_app"

            override fun getPassword(): String? = properties.password?.trim()
        }
    }
}
