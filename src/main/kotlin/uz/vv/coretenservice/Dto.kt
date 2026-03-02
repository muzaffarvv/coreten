package uz.vv.coretenservice

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.validation.constraints.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID


data class UserCreateDTO(
    @field:NotBlank(message = "First name is required")
    @field:Size(min = 2, max = 72, message = "First name must be between 2 and 72 characters")
    @field:Pattern(regexp = "^[A-Za-zА-Яа-яЁё]+$", message = "Last name must contain only letters")
    val firstName: String,

    @field:Pattern(regexp = "^[A-Za-zА-Яа-яЁё]+$", message = "Last name must contain only letters")
    val lastName: String? = null,

    @field:NotBlank(message = "Phone number is required")
    @field:Pattern(
        regexp = "^998\\d{9}$|^\\+998\\d{9}$",
        message = "Please enter a valid phone number (e.g. 998901234567)"
    )
    val phoneNum: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, message = "Password must be at least 8 characters long")
    @field:Pattern(
        regexp = "^(?=.*[0-9])(?=.*[!@#$%&()\\-+]).*$",
        message = "Password must contain at least one digit and one special character"
    )
    val password: String,

    @field:NotBlank(message = "Please confirm your password")
    val confirmPassword: String
)

data class UserUpdate(
    @field:Size(min = 2, max = 72, message = "First name must be between 2 and 72 characters")
    @field:Pattern(regexp = "^[A-Za-zА-Яа-яЁё]+$", message = "Last name must contain only letters")
    val firstName: String?,

    @field:Pattern(regexp = "^[A-Za-zА-Яа-яЁё]+$", message = "Last name must contain only letters")
    val lastName: String?,
)

data class UserUpdateSecurity(
    @field:Pattern(
        regexp = "^998\\d{9}$|^\\+998\\d{9}$",
        message = "Please enter a valid phone number"
    )
    val phoneNum: String?,

    val oldPassword: String?,

    @field:ValidPasswordIfPresent
    val newPassword: String?,

    val confirmPassword: String?,
)

data class UserUpdateRoleDTO(
    @field:NotNull(message = "New position is required")
    @field:Pattern(regexp = "^[A-Z_]+$", message = "Role code must contain only uppercase letters and underscores")
    val roleCode: String
)

data class UserResponse(
    val id: UUID,
    val firstName: String,
    val lastName: String,
    val phoneNum: String,
    val roles: Set<RoleDto>
)


data class RoleDto(
    val name: String,
    val code: String,
    val permissions: Set<PermissionDto>
)

data class PermissionDto(
    val name: String,
    val code: String
)


data class TenantCreateDTO(
    @field:NotBlank(message = "Company name can't be empty")
    @field:Size(min = 2, max = 72, message = "The name is too short or too long")
    @field:Pattern(
        regexp = "^[A-Za-zА-Яа-яЁё]+(?: [A-Za-zА-Яа-яЁё]+)*$",
        message = "Tenant name must contain only letters"
    )
    val name: String,

    @field:Size(max = 72, message = "The address is too long")
    val address: String?,

    @field:Size(max = 150, message = "The tagline is too long")
    val tagline: String?,
)

data class TenantUpdateDTO(
    @field:Size(min = 2, max = 72, message = "The name is too short or too long")
    @field:Pattern(
        regexp = "^[A-Za-zА-Яа-яЁё]+(?: [A-Za-zА-Яа-яЁё]+)*$",
        message = "Tenant name must contain only letters"
    )
    val name: String?,

    @field:Size(max = 72, message = "The address is too long")
    val address: String?,

    @field:Size(max = 150, message = "The tagline is too long")
    val tagline: String?,
)

data class TenantResponseDTO(
    val id: UUID,
    val name: String,
    val address: String?,
    val tagline: String?,
    val subscriptionPlan: TenantPlan,
    val active: Boolean,
    val maxUsers: Int?
)

data class ChangePlanRequest(
    @field:NotNull(message = "New plan is required")
    @field:Pattern(regexp = "^[A-Za-z]+$", message = "New plan must contain only letters")
    val newPlan: TenantPlan
)


data class EmployeeCreateDTO(
    val userId: UUID,
    val tenantIds: Set<UUID>
)

data class EmployeeUpdateDTO(
    val active: Boolean? = null,
    val position: Position? = null,
)

data class EmployeeResponseDTO(
    val id: UUID,
    val code: String,
    val active: Boolean,
    val position: Position,
    val user: UUID,
    val tenants: Set<UUID>,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class ChangePositionDTO(
    @field:NotNull(message = "Position is required")
    val position: Position
)



data class ProjectCreateDTO(

    @field:NotBlank(message = "Project name is required")
    @field:Size(min = 2, max = 72, message = "Project name must be between 2 and 72 characters")
    @field:Pattern(
        regexp = "^[A-Za-zА-Яа-яЁё]+(?: [A-Za-zА-Яа-яЁё]+)*$",
        message = "Project name must contain only letters"
    )
    val name: String,

    @field:Size(max = 320, message = "Description is too long")
    val description: String? = null,

    @field:NotNull(message = "Tenant ID is required")
    val tenantId: UUID,
)

data class ProjectUpdateDTO(

    @field:Size(min = 2, max = 72, message = "Project name must be between 2 and 72 characters")
    @field:Pattern(
        regexp = "^[A-Za-zА-Яа-яЁё]+(?: [A-Za-zА-Яа-яЁё]+)*$",
        message = "Project name must contain only letters"
    )
    val name: String?,

    @field:Size(max = 320, message = "Description is too long")
    val description: String?,

    val active: Boolean?
)

data class ProjectResponseDTO(
    val id: UUID,
    val name: String,
    val description: String?,
    val active: Boolean,
    val tenantId: UUID
)


data class BoardCreateDTO(

    @field:NotBlank(message = "Board name is required")
    @field:Pattern(
        regexp = "^[A-Za-zА-Яа-яЁё]+(?: [A-Za-zА-Яа-яЁё]+)*$",
        message = "Board name must contain only letters"
    )
    @field:Size(min = 2, max = 72)
    val name: String,

    @field:Size(max = 320, message = "Description is too long")
    val description: String? = null,

    @field:NotNull(message = "Project ID is required")
    val projectId: UUID,

    )

data class BoardUpdateDTO(

    @field:Size(min = 2, max = 72)
    @field:Pattern(
        regexp = "^[A-Za-zА-Яа-яЁё]+(?: [A-Za-zА-Яа-яЁё]+)*$",
        message = "Board name must contain only letters"
    )
    val name: String?,

    @field:Size(max = 320, message = "Description is too long")
    val description: String?,

    val active: Boolean?,

    )

data class BoardResponseDTO(
    val id: UUID,
    val name: String,
    val description: String?,
    val active: Boolean,
    val projectId: UUID,
    val states: MutableList<String> // codes
)


data class TaskCreateDTO(
    @field:NotBlank(message = "Title cannot be empty")
    @field:Size(max = 255, message = "The title is too long")
    val title: String,

    @field:NotBlank(message = "Please provide a description")
    val description: String,

    val priority: TaskPriority = TaskPriority.MEDIUM_LOW,

    @field:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy")
    @field:Future(message = "The date must be in the future")
    val dueDate: LocalDate?,

    @field:NotNull(message = "Board selection is required")
    val boardId: UUID,
)

data class TaskCreateWithFilesDTO(
    val boardId: UUID,
    val title: String,
    val description: String?,
    val priority: TaskPriority?,
    val dueDate: LocalDate?,
    val fileKeys: List<String>? = null
)

data class TaskUpdateDTO(
    @field:Size(max = 255, message = "The title is too long")
    val title: String?,

    val priority: TaskPriority?,

    @field:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy")
    @field:Future(message = "The date must be in the future")
    val dueDate: LocalDate?,

)

data class TaskResponseDTO(
    val id: UUID,
    val title: String,
    val description: String,
    val priority: TaskPriority,
    val dueDate: Instant?,
    val category: UUID,
    val owner: UUID,
    val assignees: Set<UUID>,
    val state: String, // code
    val files: Set<FileDto>
)

data class TaskActionResponse(
    val id: UUID?,
    val taskId: UUID,
    val modifierName: String,
    val modifierId: UUID,
    val actionType: TaskActionType,
    val oldValue: String?,
    val newValue: String?,
    val comment: String?,
    val createdAt: Instant?
)


data class TaskStateCreate(
    val name: String,
    val code: String
)

data class TaskStateUpdate(
    val name: String?,
    val code: String?
)

data class TaskStateDto(
    val boardId: UUID,
    val code: String,
    val name: String

)


data class FileDto(
    val type: FileType,
    val orgName: String,
    val keyName: String,
)


data class LoginRequest(
    @field:NotBlank(message = "Phone number is required")
    @field:Pattern(
        regexp = "^998\\d{9}$|^\\+998\\d{9}$",
        message = "Please enter a valid phone number (e.g. 998901234567)"
    )
    val phoneNum: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)

data class RefreshTokenRequest(
    @field:NotBlank(message = "Refresh token is required")
    val refreshToken: String
)

data class SwitchTenantRequest(
    @field:NotBlank(message = "Tenant ID is required")
    val tenantId: String
) {
    fun getTenantIdAsUUID(): UUID = UUID.fromString(tenantId)
}

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long, // milliseconds
    val user: UserInfo
)

data class UserInfo(
    val userId: UUID,
    val phoneNum: String,
    val firstName: String,
    val lastName: String,
    val employeeId: UUID?,
    val currentTenantId: UUID?,
    val availableTenants: Set<TenantInfo>,
    val roles: List<String>
)

data class TenantInfo(
    val tenantId: UUID,
    val tenantName: String
)