package app.homeward.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository

@Configuration
@EnableMethodSecurity // turns on @PreAuthorize
class SecurityConfig {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .authorizeHttpRequests {
                // /error stays open, or every failure would turn into an empty 403.
                it.requestMatchers("/api/health", "/error", "/api/shelters", "/api/csrf", "/api/login", "/api/openapi.json").permitAll()
                it.requestMatchers("/api/lead/**").hasRole("LEAD") // coordinators too, through the role hierarchy
                it.anyRequest().authenticated()
            }
            // CSRF token in a cookie, sent back by the React app in the X-XSRF-TOKEN header.
            .csrf { it.spa() }
            .securityContext { it.securityContextRepository(securityContextRepository()) }
            .sessionManagement { it.sessionFixation { f -> f.changeSessionId() } }
            .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() } // POST /api/logout does it
            .addFilterAfter(IdleLockFilter(), AuthorizationFilter::class.java)
            .build()

    @Bean
    fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()

    // coordinator > lead > volunteer
    @Bean
    fun roleHierarchy(): RoleHierarchy =
        RoleHierarchyImpl
            .withDefaultRolePrefix()
            .role("COORDINATOR")
            .implies("LEAD")
            .role("LEAD")
            .implies("VOLUNTEER")
            .build()

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    // Login is our own endpoint (shelter + name + PIN), so Spring's default in-memory user is never used.
    @Bean
    fun userDetailsService(): UserDetailsService = UserDetailsService { throw UsernameNotFoundException("not used") }
}
