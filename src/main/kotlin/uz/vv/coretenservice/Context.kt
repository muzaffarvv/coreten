package uz.vv.coretenservice

import java.util.UUID

object TenantContext {

    private val currentTenantId: ThreadLocal<UUID?> = ThreadLocal()
    private val currentEmployeeId: ThreadLocal<UUID?> = ThreadLocal()
    private val currentUserId: ThreadLocal<UUID?> = ThreadLocal()

    fun setTenantId(tenantId: UUID?) {
        currentTenantId.set(tenantId)
    }

    fun getTenantId(): UUID {
        return currentTenantId.get()
            ?: throw TenantNotFoundException("Tenant context not set. Ensure JWT contains tenantId claim.")
    }

    fun getTenantIdOrNull(): UUID? {
        return currentTenantId.get()
    }

    fun setEmployeeId(employeeId: UUID?) {
        currentEmployeeId.set(employeeId)
    }

    fun getEmployeeId(): UUID? {
        return currentEmployeeId.get()
    }

    fun getEmployeeIdOrThrow(): UUID {
        return currentEmployeeId.get()
            ?: throw EmployeeNotFoundException("Employee context not set. Ensure user has employee record.")
    }

    fun setUserId(userId: UUID?) {
        currentUserId.set(userId)
    }

    fun getUserId(): UUID? {
        return currentUserId.get()
    }

    fun getUserIdOrThrow(): UUID {
        return currentUserId.get()
            ?: throw UserNotFoundException("User context not set. Ensure JWT contains userId claim.")
    }

    fun clear() {
        currentTenantId.remove()
        currentEmployeeId.remove()
        currentUserId.remove()
    }
}

