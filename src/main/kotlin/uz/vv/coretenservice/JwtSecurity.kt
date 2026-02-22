package uz.vv.coretenservice

import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.UnsupportedJwtException
import io.jsonwebtoken.security.Keys
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey
import kotlin.text.startsWith
import kotlin.text.substring

@Configuration
@ConfigurationProperties(prefix = "jwt")
class JwtProperties {

    var secret: String = ""

    var accessTokenExpirationMs: Long = 3600000
//    //  30 000 ms = 30 second for testing
//    var accessTokenExpirationMs: Long = 30000

    // 604 800 000 ms = 10 080 minute = 168 hour = 7 day
    var refreshTokenExpirationMs: Long = 604800000

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

        val errorResponse = ResponseVO(
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

@Component
class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {

        try {
            val jwt = extractJwtFromRequest(request)

            if (jwt != null && SecurityContextHolder.getContext().authentication == null) {

                if (jwtProvider.validateToken(jwt)) {
                    authenticateUser(jwt, request)
                } else {
                    log.warn("Invalid JWT token")
                }
            }

        } catch (e: Exception) {
            log.error("JWT Authentication error: ${e.message}", e)
        }

        try {
            filterChain.doFilter(request, response)
        } finally {
            TenantContext.clear()
//            SecurityContextHolder.clearContext()
        }
    }


    private fun extractJwtFromRequest(request: HttpServletRequest): String? {
        val bearer = request.getHeader("Authorization") ?: return null
        return if (bearer.startsWith("Bearer ")) bearer.substring(7) else null
    }


    private fun authenticateUser(jwt: String, request: HttpServletRequest) {

        val userId = jwtProvider.getUserIdFromToken(jwt) ?: return
        val phone = jwtProvider.getPhoneNumFromToken(jwt) ?: return
        val roles = jwtProvider.getRolesFromToken(jwt).orEmpty()
        val tenantId = jwtProvider.getTenantIdFromToken(jwt)
        val employeeId = jwtProvider.getEmployeeIdFromToken(jwt)

        val authorities = roles.map { SimpleGrantedAuthority(it) }

        val authToken = UsernamePasswordAuthenticationToken(
            phone,
            null,
            authorities
        )

        authToken.details = WebAuthenticationDetailsSource().buildDetails(request)

        SecurityContextHolder.getContext().authentication = authToken

        TenantContext.setUserId(userId)
        TenantContext.setTenantId(tenantId)
        TenantContext.setEmployeeId(employeeId)

        log.debug(
            "Authenticated user. phone={}, userId={}, tenantId={}, employeeId={}",
            phone, userId, tenantId, employeeId
        )
    }
}


@Component
class JwtProvider(private val jwtProperties: JwtProperties) {

    private val logger = LoggerFactory.getLogger(JwtProvider::class.java)

    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray(StandardCharsets.UTF_8))
    }

    fun generateAccessToken(
        userDetails: CustomUserDetails,
        currentTenantId: UUID?
    ): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtProperties.accessTokenExpirationMs)

        val claims = Jwts.claims()
            .subject(userDetails.userId.toString())
            .add("phoneNum", userDetails.username)
            .add("firstName", userDetails.firstName)
            .add("lastName", userDetails.lastName)
            .add("employeeId", userDetails.employeeId?.toString())
            .add("roles", userDetails.authorities.map { it.authority })
            .apply {
                currentTenantId?.let { add("tenantId", it.toString()) }
            }
            .build()

        return Jwts.builder()
            .claims(claims)
            .issuer(jwtProperties.issuer)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(secretKey, Jwts.SIG.HS256)
            .compact()
    }

    fun generateRefreshToken(userId: UUID): String { // TODO  REFRESH TOKEN GENERATE
        val now = Date()
        val expiryDate = Date(now.time + jwtProperties.refreshTokenExpirationMs)

        return Jwts.builder()
            .subject(userId.toString())
            .claim("type", "refresh")
            .issuer(jwtProperties.issuer)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(secretKey, Jwts.SIG.HS256)
            .compact()
    }

    fun getUserIdFromToken(token: String): UUID? {
        return try {
            val claims = getClaims(token)
            UUID.fromString(claims.subject)
        } catch (e: Exception) {
            logger.error("Failed to extract user ID from token", e)
            null
        }
    }

    fun getTenantIdFromToken(token: String): UUID? {
        return try {
            val claims = getClaims(token)
            claims["tenantId"]?.toString()?.let { UUID.fromString(it) }
        } catch (e: Exception) {
            logger.debug("No tenant ID in token or failed to parse", e)
            null
        }
    }

    fun getEmployeeIdFromToken(token: String): UUID? {
        return try {
            val claims = getClaims(token)
            claims["employeeId"]?.toString()?.let { UUID.fromString(it) }
        } catch (e: Exception) {
            logger.debug("No employee ID in token or failed to parse", e)
            null
        }
    }

    fun getPhoneNumFromToken(token: String): String? {
        return try {
            val claims = getClaims(token)
            claims["phoneNum"] as? String
        } catch (e: Exception) {
            logger.error("Failed to extract phone number from token", e)
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun getRolesFromToken(token: String): List<String> {
        return try {
            val claims = getClaims(token)
            claims["roles"] as? List<String> ?: emptyList()
        } catch (e: Exception) {
            logger.error("Failed to extract roles from token", e)
            emptyList()
        }
    }

    fun validateToken(token: String): Boolean {
        return try {
            Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
            true
        } catch (e: SecurityException) {
            logger.error("Invalid JWT signature: ${e.message}")
            false
        } catch (e: MalformedJwtException) {
            logger.error("Invalid JWT token: ${e.message}")
            false
        } catch (e: ExpiredJwtException) {
            logger.error("Expired JWT token: ${e.message}")
            false
        } catch (e: UnsupportedJwtException) {
            logger.error("Unsupported JWT token: ${e.message}")
            false
        } catch (e: IllegalArgumentException) {
            logger.error("JWT claims string is empty: ${e.message}")
            false
        } catch (e: Exception) {
            logger.error("Token validation failed: ${e.message}")
            false
        }
    }

    fun isRefreshToken(token: String): Boolean {
        return try {
            val claims = getClaims(token)
            claims["type"] == "refresh"
        } catch (e: Exception) {
            false
        }
    }

    private fun getClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}

