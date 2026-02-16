package uz.vv.coretenservice

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import io.jsonwebtoken.*
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.*
import javax.crypto.SecretKey


import java.security.SecureRandom
import java.time.LocalDate

object EmployeeCodeGenerator {
    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    private val RANDOM = SecureRandom()

    fun generate(length: Int = 10): String {
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)])
        }
        // result: "9A3D4XJ2"
        return sb.toString()
    }
}

object FileKeyGenerator {

    private const val CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val RANDOM = SecureRandom()

    fun generateFileKey(): String {

        val randomPart = buildString {
            repeat(6) {
                append(CHARS[RANDOM.nextInt(CHARS.length)])
            }
        }

        val now = LocalDate.now()
        val year = (now.year % 100).toString()
        val month = "%02d".format(now.monthValue)
        val day = "%02d".format(now.dayOfMonth)

        return "$year$randomPart$month$day"
    }
}


object DefaultTaskStates {
    val DEFAULT_STATES = listOf(
        TaskStateCreate("New", "NEW"),
        TaskStateCreate("In Progress", "IN_PROGRESS"),
        TaskStateCreate("Review", "REVIEW"),
        TaskStateCreate("Done", "DONE")
    )
}


@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [ValidPasswordIfPresentValidator::class])
annotation class ValidPasswordIfPresent(
    val message: String = "Invalid password format",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)


class ValidPasswordIfPresentValidator :
    ConstraintValidator<ValidPasswordIfPresent, String?> {

    private val pattern = Regex("^(?=.*[0-9])(?=.*[!@#$%&()\\-+]).{8,}$")

    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        if (value.isBlank()) return false
        return pattern.matches(value)
    }
}

/**
 * ThreadLocal holder for current tenant context
 * This is populated from JWT token on each request
 */
object TenantContext {

    private val currentTenantId: ThreadLocal<UUID?> = ThreadLocal()
    private val currentEmployeeId: ThreadLocal<UUID?> = ThreadLocal()
    private val currentUserId: ThreadLocal<UUID?> = ThreadLocal()

    /**
     * Set current tenant ID from JWT
     */
    fun setTenantId(tenantId: UUID?) {
        currentTenantId.set(tenantId)
    }

    /**
     * Get current tenant ID
     * @throws IllegalStateException if tenant context is not set
     */
    fun getTenantId(): UUID {
        return currentTenantId.get()
            ?: throw IllegalStateException("Tenant context not set. Ensure JWT contains tenantId claim.")
    }

    /**
     * Get current tenant ID or null if not set
     */
    fun getTenantIdOrNull(): UUID? {
        return currentTenantId.get()
    }

    /**
     * Set current employee ID from JWT
     */
    fun setEmployeeId(employeeId: UUID?) {
        currentEmployeeId.set(employeeId)
    }

    /**
     * Get current employee ID
     */
    fun getEmployeeId(): UUID? {
        return currentEmployeeId.get()
    }

    /**
     * Get current employee ID or throw exception
     * @throws IllegalStateException if employee context is not set
     */
    fun getEmployeeIdOrThrow(): UUID {
        return currentEmployeeId.get()
            ?: throw IllegalStateException("Employee context not set. Ensure user has employee record.")
    }

    /**
     * Set current user ID from JWT
     */
    fun setUserId(userId: UUID?) {
        currentUserId.set(userId)
    }

    fun getUserId(): UUID? {
        return currentUserId.get()
    }

    /**
     * Get current user ID or throw exception
     */
    fun getUserIdOrThrow(): UUID {
        return currentUserId.get()
            ?: throw IllegalStateException("User context not set")
    }

    /**
     * Clear all context (call at end of request)
     */
    fun clear() {
        currentTenantId.remove()
        currentEmployeeId.remove()
        currentUserId.remove()
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
        val expiryDate = Date(now.time + jwtProperties.accessTokenExpiration)

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

    fun generateRefreshToken(userId: UUID): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtProperties.refreshTokenExpiration)

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