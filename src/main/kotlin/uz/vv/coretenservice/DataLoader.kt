package uz.vv.coretenservice

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class SecurityDataInitializer(
    private val roleService: RoleService,
    private val permissionService: PermissionService,
    private val userRepo: UserRepo,
    private val passwordEncoder: PasswordEncoderConfig,

    @Value("\${system.super-admin.id}")
    private val systemSuperAdminId: String,

    @Value("\${system.super-admin.phone}")
    private val adminPhone: String,

    @Value("\${system.super-admin.password}")
    private val adminPassword: String,

    @Value("\${system.super-admin.first-name}")
    private val adminFirstName: String,

    @Value("\${system.super-admin.last-name}")
    private val adminLastName: String
) : CommandLineRunner {

    private val logger = LoggerFactory.getLogger(SecurityDataInitializer::class.java)

    override fun run(vararg args: String?) {
        logger.info("Initializing SYSTEM security data...")

        setupSystemSecurityContext()

        try {
            val permissions = createSystemPermissions()
            createSystemRoles(permissions)

            createSuperAdminUser()

            logger.info("System security data initialization completed successfully")
        } catch (e: Exception) {
            logger.error("Failed to initialize system security data", e)
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    private fun setupSystemSecurityContext() {
        val systemUser = CustomUserDetails(
            userId = UUID.fromString(systemSuperAdminId),
            phoneNum = "system",
            password = "",
            firstName = "System",
            lastName = "Initializer",
            employeeId = null,
            availableTenantIds = emptySet(),
            authorities = emptySet()
        )
        val auth = UsernamePasswordAuthenticationToken(systemUser, null, systemUser.authorities)
        SecurityContextHolder.getContext().authentication = auth
    }

    private fun createSuperAdminUser() {
        if (userRepo.findByPhoneNumAndDeletedFalse(adminPhone) == null) {
            val superRole = roleService.getByCode("SUPER_ADMIN")

            val admin = User(
                firstName = adminFirstName,
                lastName = adminLastName,
                phoneNum = adminPhone,
                password = passwordEncoder.passwordEncoder().encode(adminPassword),
                roles = mutableSetOf(superRole)
            )
            userRepo.save(admin)
            logger.info("Super Admin user created with phone: $adminPhone")
        }
    }

    private fun createSystemPermissions(): Map<String, Permission> {

        val permissionDefs = listOf(
            "Read User" to "USER_READ",
            "Create User" to "USER_CREATE",
            "Update User" to "USER_UPDATE",
            "Delete User" to "USER_DELETE",

            "Read Tenant" to "TENANT_READ",
            "Create Tenant" to "TENANT_CREATE",
            "Update Tenant" to "TENANT_UPDATE",
            "Delete Tenant" to "TENANT_DELETE",
            "Manage Subscription" to "TENANT_MANAGE_SUBSCRIPTION"
        )

        return permissionDefs.associate { (name, code) ->
            code to permissionService.createIfNotExist(name, code)
        }
    }

    private fun createSystemRoles(permissions: Map<String, Permission>) {

        roleService.createIfNotExist(
            name = "Platform User",
            code = "PLATFORM_USER",
            permissions = setOf(
                permissions["USER_READ"]!!,
                permissions["TENANT_READ"]!!
            )
        )

        roleService.createIfNotExist(
            name = "Platform Admin",
            code = "PLATFORM_ADMIN",
            permissions = setOf(
                permissions["USER_READ"]!!,
                permissions["USER_CREATE"]!!,
                permissions["USER_UPDATE"]!!,
                permissions["USER_DELETE"]!!,

                permissions["TENANT_READ"]!!,
                permissions["TENANT_CREATE"]!!,
                permissions["TENANT_UPDATE"]!!,
                permissions["TENANT_DELETE"]!!,
                permissions["TENANT_MANAGE_SUBSCRIPTION"]!!
            )
        )

        roleService.createIfNotExist(
            name = "Super Admin",
            code = "SUPER_ADMIN",
            permissions = permissions.values.toSet()
        )
    }
}
