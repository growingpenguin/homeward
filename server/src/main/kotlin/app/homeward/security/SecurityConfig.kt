package app.homeward.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

// B0: only the health check is open; everything else is closed until login exists (B2).
@Configuration
class SecurityConfig {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .authorizeHttpRequests {
                // /error must stay open, or every failure (e.g. the database being down) turns into an empty 403.
                it.requestMatchers("/api/health", "/error").permitAll()
                it.anyRequest().denyAll()
            }.build()
}
