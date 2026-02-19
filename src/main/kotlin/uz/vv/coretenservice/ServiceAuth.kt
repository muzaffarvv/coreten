package uz.vv.coretenservice

import org.slf4j.LoggerFactory
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID


@Service
class AuthService(
    private val userRepo: UserRepo,
    private val employeeRepo: EmployeeRepo,
    private val roleService: RoleService,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider,
    private val jwtProperties: JwtProperties,
    private val customUserDetailsService: CustomUserDetailsService
) {

    private val logger = LoggerFactory.getLogger(AuthService::class.java)

    @Transactional
    fun register(dto: UserCreateDTO): AuthResponse {

        if (userRepo.existsByPhoneNum(dto.phoneNum)) {
            throw DuplicateResourceException("User already exists with phone number: ${dto.phoneNum}")
        }

        if (dto.password != dto.confirmPassword) {
            throw PasswordMismatchException("Passwords do not match")
        }

        val user = User(
            firstName = dto.firstName,
            lastName = dto.lastName ?: "",
            phoneNum = dto.phoneNum,
            password = passwordEncoder.encode(dto.password)
        )

        try {
            val userRole = roleService.getByCode("USER")
            user.roles.add(userRole)
        } catch (e: RoleNotFoundException) {
            logger.warn("Default USER role not found. User created without roles. $e.message")
        }

        val savedUser = userRepo.save(user)

        val userDetails = customUserDetailsService.loadCustomUserByPhoneNum(savedUser.phoneNum)

        return buildAuthResponse(userDetails, null)
    }

    @Transactional(readOnly = true)
    fun login(request: LoginRequest): AuthResponse {

        val userDetails = try {
            customUserDetailsService.loadCustomUserByPhoneNum(request.phoneNum)
        } catch (e: UsernameNotFoundException) {
            throw BadCredentialsException("Invalid phone number or password + $e.message")
        }

        if (!passwordEncoder.matches(request.password, userDetails.password)) {
            throw BadCredentialsException("Invalid phone number or password")
        }

        if (!userDetails.isEnabled) {
            throw BadCredentialsException("Account is disabled")
        }

        val defaultTenantId = userDetails.getDefaultTenantId()

        logger.info("User ${userDetails.username} logged in successfully with tenant: $defaultTenantId")

        return buildAuthResponse(userDetails, defaultTenantId)
    }

    @Transactional(readOnly = true)
    fun refreshToken(request: RefreshTokenRequest): AuthResponse {
        val refreshToken = request.refreshToken

        if (!jwtProvider.validateToken(refreshToken)) {
            throw BadCredentialsException("Invalid or expired refresh token")
        }

        if (!jwtProvider.isRefreshToken(refreshToken)) {
            throw BadCredentialsException("Provided token is not a refresh token")
        }

        val userId = jwtProvider.getUserIdFromToken(refreshToken)
            ?: throw BadCredentialsException("Invalid refresh token")

        val user = userRepo.findByIdAndDeletedFalse(userId)
            ?: throw UsernameNotFoundException("User not found")

        val userDetails = customUserDetailsService.loadCustomUserByPhoneNum(user.phoneNum)

        // Maintain the same tenant context if possible
        val currentTenantId = userDetails.getDefaultTenantId()

        logger.info("Token refreshed for user: ${user.phoneNum}")

        return buildAuthResponse(userDetails, currentTenantId)
    }

    @Transactional(readOnly = true)
    fun switchTenant(request: SwitchTenantRequest, currentUserId: UUID): AuthResponse {
        val targetTenantId = request.getTenantIdAsUUID()

        val user = userRepo.findByIdAndDeletedFalse(currentUserId)
            ?: throw UserNotFoundException("User not found")

        val userDetails = customUserDetailsService.loadCustomUserByPhoneNum(user.phoneNum)

        if (!userDetails.hasAccessToTenant(targetTenantId)) {
            throw UnauthorizedException("You don't have access to this tenant")
        }

        logger.info("User ${user.phoneNum} switched to tenant: $targetTenantId")

        return buildAuthResponse(userDetails, targetTenantId)
    }

    private fun buildAuthResponse(
        userDetails: CustomUserDetails,
        currentTenantId: UUID?
    ): AuthResponse {
        val accessToken = jwtProvider.generateAccessToken(userDetails, currentTenantId)
        val refreshToken = jwtProvider.generateRefreshToken(userDetails.userId)

        val tenantInfos = if (userDetails.employeeId != null) {
            val employee = employeeRepo.findByIdAndDeletedFalse(userDetails.employeeId)
            employee?.tenants?.map {
                TenantInfo(it.id!!, it.name)
            }?.toSet() ?: emptySet()
        } else emptySet()

        val userInfo = UserInfo(
            userId = userDetails.userId,
            phoneNum = userDetails.username,
            firstName = userDetails.firstName,
            lastName = userDetails.lastName,
            employeeId = userDetails.employeeId,
            currentTenantId = currentTenantId,
            availableTenants = tenantInfos,
            roles = userDetails.authorities.map { it.authority }
        )

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenType = jwtProperties.tokenType,
            expiresIn = jwtProperties.accessTokenExpirationMs,
            user = userInfo
        )
    }
}