package uz.vv.coretenservice


import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.util.UUID

/**
 * Custom UserDetails implementation with tenant support
 */
class CustomUserDetails(
    val userId: UUID,
    private val phoneNum: String,
    private val password: String,
    val firstName: String,
    val lastName: String,
    val employeeId: UUID?,
    val availableTenantIds: Set<UUID>,
    private val authorities: Set<GrantedAuthority>,
    private val enabled: Boolean = true,
    private val accountNonExpired: Boolean = true,
    private val credentialsNonExpired: Boolean = true,
    private val accountNonLocked: Boolean = true
) : UserDetails {

    companion object {
        /**
         * Build CustomUserDetails from User entity
         */
        fun from(user: User, employee: Employee? = null): CustomUserDetails {
            val authorities = buildAuthorities(user)

            return CustomUserDetails(
                userId = user.id!!,
                phoneNum = user.phoneNum,
                password = user.password,
                firstName = user.firstName,
                lastName = user.lastName,
                employeeId = employee?.id,
                availableTenantIds = employee?.tenants?.mapNotNull { it.id }?.toSet() ?: emptySet(),
                authorities = authorities,
                enabled = !user.deleted
            )
        }

        private fun buildAuthorities(user: User): Set<GrantedAuthority> {
            val authorities = mutableSetOf<GrantedAuthority>()

            user.roles.forEach { role ->
                // Add a role as authority (e.g., ROLE_ADMIN)
                authorities.add(SimpleGrantedAuthority("ROLE_${role.code}"))

                // Add permissions as authorities (e.g., PROJECT_CREATE)
                role.permissions.forEach { permission ->
                    authorities.add(SimpleGrantedAuthority(permission.code))
                }
            }

            return authorities
        }
    }

    override fun getAuthorities(): Collection<GrantedAuthority> = authorities

    override fun getPassword(): String = password

    override fun getUsername(): String = phoneNum

    override fun isAccountNonExpired(): Boolean = accountNonExpired

    override fun isAccountNonLocked(): Boolean = accountNonLocked

    override fun isCredentialsNonExpired(): Boolean = credentialsNonExpired

    override fun isEnabled(): Boolean = enabled

    fun hasAccessToTenant(tenantId: UUID): Boolean {
        return availableTenantIds.contains(tenantId)
    }

    fun getDefaultTenantId(): UUID? {
        return availableTenantIds.firstOrNull()
    }
}

@Service
class CustomUserDetailsService(
    private val userRepo: UserRepo,
    private val employeeRepo: EmployeeRepo
) : UserDetailsService {

    @Transactional(readOnly = true)
    override fun loadUserByUsername(phoneNum: String): UserDetails {
        val user = userRepo.findByPhoneNumAndDeletedFalse(phoneNum)
            ?: throw UsernameNotFoundException("User not found with phone number: $phoneNum")

        // Eagerly fetch roles and permissions
        user.roles.forEach { role ->
            role.permissions.size // Force initialization
        }

        // Try to find employee record (optional - user may not be an employee yet)
        val employee = try {
            employeeRepo.findAll()
                .firstOrNull { it.user.id == user.id && !it.deleted }
                ?.also { emp ->
                    emp.tenants.size // Force initialization of tenants
                }
        } catch (e: Exception) {
            null
        }

        return CustomUserDetails.from(user, employee)
    }

    @Transactional(readOnly = true)
    fun loadCustomUserByPhoneNum(phoneNum: String): CustomUserDetails {
        return loadUserByUsername(phoneNum) as CustomUserDetails
    }
}