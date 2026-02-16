package uz.vv.coretenservice

import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class SecurityDataInitializer(
    private val roleService: RoleService,
    private val permissionService: PermissionService
) : CommandLineRunner {

    private val logger = LoggerFactory.getLogger(SecurityDataInitializer::class.java)

    override fun run(vararg args: String?) {
        logger.info("Initializing security data (roles and permissions)...")

        try {
            // Create Permissions
            val permissions = createPermissions()
            logger.info("Created ${permissions.size} permissions")

            // Create Roles with Permissions
            createRoles(permissions)
            logger.info("Security data initialization completed successfully")

        } catch (e: Exception) {
            logger.error("Failed to initialize security data", e)
        }
    }

    private fun createPermissions(): Map<String, Permission> {
        val permissionDefs = listOf(
            // User permissions
            "Read User" to "USER_READ",
            "Create User" to "USER_CREATE",
            "Update User" to "USER_UPDATE",
            "Delete User" to "USER_DELETE",

            // Tenant permissions
            "Read Tenant" to "TENANT_READ",
            "Create Tenant" to "TENANT_CREATE",
            "Update Tenant" to "TENANT_UPDATE",
            "Delete Tenant" to "TENANT_DELETE",
            "Manage Subscription" to "TENANT_MANAGE_SUBSCRIPTION",

            // Employee permissions
            "Read Employee" to "EMPLOYEE_READ",
            "Create Employee" to "EMPLOYEE_CREATE",
            "Update Employee" to "EMPLOYEE_UPDATE",
            "Delete Employee" to "EMPLOYEE_DELETE",
            "Assign Tenant" to "EMPLOYEE_ASSIGN_TENANT",

            // Project permissions
            "Read Project" to "PROJECT_READ",
            "Create Project" to "PROJECT_CREATE",
            "Update Project" to "PROJECT_UPDATE",
            "Delete Project" to "PROJECT_DELETE",

            // Board permissions
            "Read Board" to "BOARD_READ",
            "Create Board" to "BOARD_CREATE",
            "Update Board" to "BOARD_UPDATE",
            "Delete Board" to "BOARD_DELETE",

            // Task permissions
            "Read Task" to "TASK_READ",
            "Create Task" to "TASK_CREATE",
            "Update Task" to "TASK_UPDATE",
            "Delete Task" to "TASK_DELETE",
            "Assign Task" to "TASK_ASSIGN",
            "Change Task State" to "TASK_CHANGE_STATE",

            // File permissions
            "Upload File" to "FILE_UPLOAD",
            "Download File" to "FILE_DOWNLOAD",
            "Delete File" to "FILE_DELETE"
        )

        return permissionDefs.associate { (name, code) ->
            code to permissionService.createIfNotExist(name, code)
        }
    }

    private fun createRoles(permissions: Map<String, Permission>) {
        // USER Role - Basic access
        roleService.createIfNotExist(
            name = "User",
            code = "USER",
            permissions = setOf(
                permissions["PROJECT_READ"]!!,
                permissions["BOARD_READ"]!!,
                permissions["TASK_READ"]!!,
                permissions["TASK_CREATE"]!!,
                permissions["TASK_UPDATE"]!!,
                permissions["FILE_UPLOAD"]!!,
                permissions["FILE_DOWNLOAD"]!!
            )
        )

        // EMPLOYEE Role - Regular employee access
        roleService.createIfNotExist(
            name = "Employee",
            code = "EMPLOYEE",
            permissions = setOf(
                permissions["USER_READ"]!!,
                permissions["EMPLOYEE_READ"]!!,
                permissions["PROJECT_READ"]!!,
                permissions["BOARD_READ"]!!,
                permissions["TASK_READ"]!!,
                permissions["TASK_CREATE"]!!,
                permissions["TASK_UPDATE"]!!,
                permissions["TASK_ASSIGN"]!!,
                permissions["TASK_CHANGE_STATE"]!!,
                permissions["FILE_UPLOAD"]!!,
                permissions["FILE_DOWNLOAD"]!!,
                permissions["FILE_DELETE"]!!
            )
        )

        // TEAM_LEAD Role - Team management
        roleService.createIfNotExist(
            name = "Team Lead",
            code = "TEAM_LEAD",
            permissions = setOf(
                permissions["USER_READ"]!!,
                permissions["EMPLOYEE_READ"]!!,
                permissions["PROJECT_READ"]!!,
                permissions["PROJECT_CREATE"]!!,
                permissions["PROJECT_UPDATE"]!!,
                permissions["BOARD_READ"]!!,
                permissions["BOARD_CREATE"]!!,
                permissions["BOARD_UPDATE"]!!,
                permissions["TASK_READ"]!!,
                permissions["TASK_CREATE"]!!,
                permissions["TASK_UPDATE"]!!,
                permissions["TASK_DELETE"]!!,
                permissions["TASK_ASSIGN"]!!,
                permissions["TASK_CHANGE_STATE"]!!,
                permissions["FILE_UPLOAD"]!!,
                permissions["FILE_DOWNLOAD"]!!,
                permissions["FILE_DELETE"]!!
            )
        )

        // MANAGER Role - Project and team management
        roleService.createIfNotExist(
            name = "Manager",
            code = "MANAGER",
            permissions = setOf(
                permissions["USER_READ"]!!,
                permissions["EMPLOYEE_READ"]!!,
                permissions["EMPLOYEE_CREATE"]!!,
                permissions["EMPLOYEE_UPDATE"]!!,
                permissions["PROJECT_READ"]!!,
                permissions["PROJECT_CREATE"]!!,
                permissions["PROJECT_UPDATE"]!!,
                permissions["PROJECT_DELETE"]!!,
                permissions["BOARD_READ"]!!,
                permissions["BOARD_CREATE"]!!,
                permissions["BOARD_UPDATE"]!!,
                permissions["BOARD_DELETE"]!!,
                permissions["TASK_READ"]!!,
                permissions["TASK_CREATE"]!!,
                permissions["TASK_UPDATE"]!!,
                permissions["TASK_DELETE"]!!,
                permissions["TASK_ASSIGN"]!!,
                permissions["TASK_CHANGE_STATE"]!!,
                permissions["FILE_UPLOAD"]!!,
                permissions["FILE_DOWNLOAD"]!!,
                permissions["FILE_DELETE"]!!
            )
        )

        // ADMIN Role - Full tenant administration
        roleService.createIfNotExist(
            name = "Admin",
            code = "ADMIN",
            permissions = setOf(
                permissions["USER_READ"]!!,
                permissions["USER_CREATE"]!!,
                permissions["USER_UPDATE"]!!,
                permissions["USER_DELETE"]!!,
                permissions["TENANT_READ"]!!,
                permissions["TENANT_UPDATE"]!!,
                permissions["EMPLOYEE_READ"]!!,
                permissions["EMPLOYEE_CREATE"]!!,
                permissions["EMPLOYEE_UPDATE"]!!,
                permissions["EMPLOYEE_DELETE"]!!,
                permissions["EMPLOYEE_ASSIGN_TENANT"]!!,
                permissions["PROJECT_READ"]!!,
                permissions["PROJECT_CREATE"]!!,
                permissions["PROJECT_UPDATE"]!!,
                permissions["PROJECT_DELETE"]!!,
                permissions["BOARD_READ"]!!,
                permissions["BOARD_CREATE"]!!,
                permissions["BOARD_UPDATE"]!!,
                permissions["BOARD_DELETE"]!!,
                permissions["TASK_READ"]!!,
                permissions["TASK_CREATE"]!!,
                permissions["TASK_UPDATE"]!!,
                permissions["TASK_DELETE"]!!,
                permissions["TASK_ASSIGN"]!!,
                permissions["TASK_CHANGE_STATE"]!!,
                permissions["FILE_UPLOAD"]!!,
                permissions["FILE_DOWNLOAD"]!!,
                permissions["FILE_DELETE"]!!
            )
        )

        // OWNER Role - Complete system access
        roleService.createIfNotExist(
            name = "Owner",
            code = "OWNER",
            permissions = permissions.values.toSet() // All permissions
        )
    }
}