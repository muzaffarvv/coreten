package uz.vv.coretenservice

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.validation.constraints.*
import java.time.Instant
import java.util.UUID


data class UserCreateDTO(
    @field:NotBlank(message = "First name is required")
    @field:Size(min = 2, max = 72, message = "First name must be between 2 and 72 characters")
    val firstName: String,

    val lastName: String? = null,

    @field:NotBlank(message = "Phone number is required")
    @field:Pattern(
        regexp = "^998\\d{9}$|^\\+998\\d{9}$",
        message = "Please enter a valid phone number (e.g. 998901234567)"
    )
    val phoneNum: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 6, message = "Password must be at least 8 characters long")
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
    val firstName: String?,

    @field:NotBlank
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
    val name: String,

    @field:Size(max = 72, message = "The address is too long")
    val address: String?,

    @field:Size(max = 150, message = "The tagline is too long")
    val tagline: String?,

    @field:NotNull(message = "Please select a subscription plan")
    val subscriptionPlan: TenantPlan,
)

data class TenantUpdateDTO(
    @field:Size(min = 2, max = 72, message = "The name is too short or too long")
    val name: String?,

    @field:Size(max = 72, message = "The address is too long")
    val address: String?,

    @field:Size(max = 150, message = "The tagline is too long")
    val tagline: String?,

    val subscriptionPlan: TenantPlan?,

    val active: Boolean?,
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


data class CategoryCreateDTO(
    @field:NotBlank(message = "Category name is required")
    @field:Size(min = 2, max = 72, message = "The name is too short or too long")
    val name: String,

    @field:NotBlank(message = "Title is required")
    @field:Size(min = 2, max = 184, message = "The title is too short or too long")
    val title: String,

    val parentId: UUID?,

    @field:NotNull(message = "Tenant reference is required")
    val tenantId: UUID
)

data class CategoryUpdateDTO(
    @field:Size(min = 2, max = 72, message = "The name is too short or too long")
    val name: String?,

    @field:Size(min = 2, max = 184, message = "The title is too short or too long")
    val title: String?,

    val parentId: UUID?,
)

data class CategoryResponseDTO(
    val id: UUID,
    val name: String,
    val title: String,
    val parentId: UUID?,
    val tenantId: UUID,
    val isLast: Boolean
)


data class TaskCreateDTO(
    @field:NotBlank(message = "Title cannot be empty")
    @field:Size(max = 255, message = "The title is too long")
    val title: String,

    @field:NotBlank(message = "Please provide a description")
    val description: String,

    val priority: TaskPriority = TaskPriority.MEDIUM_LOW,

    @field:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy", timezone = "UTC")
    @field:Future(message = "The date must be in the future")
    val dueDate: Instant?,

    @field:NotNull(message = "Category selection is required")
    val categoryId: UUID,

    @field:NotNull(message = "Task owner is required")
    val ownerId: UUID,

    val fileIds: Set<UUID> = emptySet()
)

data class TaskUpdateDTO(
    @field:Size(max = 255, message = "The title is too long")
    val title: String?,

    val description: String?,

    val priority: TaskPriority?,

    @field:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy", timezone = "UTC")
    @field:Future(message = "The date must be in the future")
    val dueDate: Instant?,

    val stateId: UUID?,

    val categoryId: UUID?,

    val fileIds: Set<UUID>?
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
    val state: TaskStateDto,
    val files: Set<FileDto>
)


data class TaskStateDto(
    val code: String,
    val name: String

)

data class FileDto(
    val type: FileType,
    val orgName: String,
    val keyName: String,
)
