package uz.vv.coretenservice

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import java.time.Instant
import jakarta.servlet.FilterChain
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.web.filter.OncePerRequestFilter

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
class SecurityConfig(
    private val customUserDetailsService: CustomUserDetailsService,
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val passwordEncoder: PasswordEncoder,
    private val jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.disable() } // Configure CORS properly in production
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint(jwtAuthenticationEntryPoint)
            }
            .authorizeHttpRequests { auth ->
                auth
                    // Public endpoints
                    .requestMatchers(
                        "/auth/login",
                        "/auth/register",
                        "/auth/refresh",
                        "/error",
                        "/actuator/health" // If using Spring Actuator
                    ).permitAll()

                    .requestMatchers(
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                    ).permitAll()

                    // All other endpoints require authentication
                    .anyRequest().authenticated()
            }
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun authenticationProvider(): DaoAuthenticationProvider {
        val authProvider = DaoAuthenticationProvider()
        authProvider.setUserDetailsService(customUserDetailsService)
        authProvider.setPasswordEncoder(passwordEncoder)
        return authProvider
    }

    @Bean
    fun authenticationManager(authConfig: AuthenticationConfiguration): AuthenticationManager {
        return authConfig.authenticationManager
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder(12)
    }
}

@Component
class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider
) : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val jwt = extractJwtFromRequest(request)

            if (jwt != null && jwtProvider.validateToken(jwt)) {
                authenticateUser(jwt, request)
            }
        } catch (e: Exception) {
            logger.error("Cannot set user authentication: ${e.message}", e)
        }

        try {
            filterChain.doFilter(request, response)
        } finally {
            // CRITICAL: Clear tenant context after request
            TenantContext.clear()
        }
    }

    private fun extractJwtFromRequest(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization")

        return if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            bearerToken.substring(7)
        } else {
            null
        }
    }

    private fun authenticateUser(jwt: String, request: HttpServletRequest) {
        val userId = jwtProvider.getUserIdFromToken(jwt) ?: return
        val phoneNum = jwtProvider.getPhoneNumFromToken(jwt) ?: return
        val roles = jwtProvider.getRolesFromToken(jwt)
        val tenantId = jwtProvider.getTenantIdFromToken(jwt)
        val employeeId = jwtProvider.getEmployeeIdFromToken(jwt)

        // Build authorities from roles
        val authorities = roles.map { SimpleGrantedAuthority(it) }

        // Create an authentication token
        val authentication = UsernamePasswordAuthenticationToken(
            phoneNum,
            null,
            authorities
        )
        authentication.details = WebAuthenticationDetailsSource().buildDetails(request)

        // Set security context
        SecurityContextHolder.getContext().authentication = authentication

        // Set tenant context (ThreadLocal)
        TenantContext.setUserId(userId)
        TenantContext.setTenantId(tenantId)
        TenantContext.setEmployeeId(employeeId)

        logger.debug(
            "Authenticated user: {} (userId: {}, tenantId: {}, employeeId: {})",
            phoneNum,
            userId,
            tenantId,
            employeeId
        )
    }
}


@Configuration
@ConfigurationProperties(prefix = "jwt")
class JwtProperties {

    lateinit var secret: String

    var accessTokenExpiration: Long = 3600000

    var refreshTokenExpiration: Long = 604800000

    var issuer: String = "coreten-service"

    var tokenType: String = "Bearer"
}

@Component
class JwtAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper
) : AuthenticationEntryPoint {

    private val logger = LoggerFactory.getLogger(JwtAuthenticationEntryPoint::class.java)

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        logger.error("Unauthorized error: ${authException.message}")

        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.status = HttpServletResponse.SC_UNAUTHORIZED

        val errorResponse = ResponseVO<Nothing>(
            status = HttpServletResponse.SC_UNAUTHORIZED,
            errors = mapOf(
                "code" to "UNAUTHORIZED",
                "message" to (authException.message ?: "Unauthorized access - invalid or missing token")
            ),
            timestamp = Instant.now(),
            data = null,
            source = request.requestURI
        )

        objectMapper.writeValue(response.outputStream, errorResponse)
    }
}