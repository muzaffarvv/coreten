package uz.vv.coretenservice


import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService
) {

    /**
     * POST /auth/register
     * Register new user account
     */
    @PostMapping("/register")
    fun register(@Valid @RequestBody dto: UserCreateDTO): ResponseEntity<ResponseVO<AuthResponse>> {
        val authResponse = authService.register(dto)

        return ResponseEntity.status(HttpStatus.CREATED).body(
            ResponseVO(
                status = HttpStatus.CREATED.value(),
                errors = null,
                timestamp = Instant.now(),
                data = authResponse,
                source = "/auth/register"
            )
        )
    }

    /**
     * POST /auth/login
     * Authenticate user and return tokens
     */
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<ResponseVO<AuthResponse>> {
        val authResponse = authService.login(request)

        return ResponseEntity.ok(
            ResponseVO(
                status = HttpStatus.OK.value(),
                errors = null,
                timestamp = Instant.now(),
                data = authResponse,
                source = "/auth/login"
            )
        )
    }

    /**
     * POST /auth/refresh
     * Refresh access token using refresh token
     */
    @PostMapping("/refresh")
    fun refreshToken(@Valid @RequestBody request: RefreshTokenRequest): ResponseEntity<ResponseVO<AuthResponse>> {
        val authResponse = authService.refreshToken(request)

        return ResponseEntity.ok(
            ResponseVO(
                status = HttpStatus.OK.value(),
                errors = null,
                timestamp = Instant.now(),
                data = authResponse,
                source = "/auth/refresh"
            )
        )
    }

    /**
     * POST /auth/switch-tenant
     * Switch to different tenant (requires authentication)
     */
    @PostMapping("/switch-tenant")
    fun switchTenant(@Valid @RequestBody request: SwitchTenantRequest): ResponseEntity<ResponseVO<AuthResponse>> {
        val currentUserId = TenantContext.getUserIdOrThrow()
        val authResponse = authService.switchTenant(request, currentUserId)

        return ResponseEntity.ok(
            ResponseVO(
                status = HttpStatus.OK.value(),
                errors = null,
                timestamp = Instant.now(),
                data = authResponse,
                source = "/auth/switch-tenant"
            )
        )
    }

    /**
     * GET /auth/me
     * Get current authenticated user info
     */
    @GetMapping("/me")
    fun getCurrentUser(): ResponseEntity<ResponseVO<CurrentUserResponse>> {
        val userId = TenantContext.getUserIdOrThrow()
        val tenantId = TenantContext.getTenantIdOrNull()
        val employeeId = TenantContext.getEmployeeIdOrThrow()

        val response = CurrentUserResponse(
            userId = userId,
            currentTenantId = tenantId,
            currentEmployeeId = employeeId
        )

        return ResponseEntity.ok(
            ResponseVO(
                status = HttpStatus.OK.value(),
                errors = null,
                timestamp = Instant.now(),
                data = response,
                source = "/auth/me"
            )
        )
    }
}

/**
 * Response for /auth/me endpoint
 */
data class CurrentUserResponse(
    val userId: java.util.UUID?,
    val currentTenantId: java.util.UUID?,
    val currentEmployeeId: java.util.UUID?
)