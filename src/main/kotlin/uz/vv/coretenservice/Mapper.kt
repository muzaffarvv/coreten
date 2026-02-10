package uz.vv.coretenservice

import org.springframework.stereotype.Component

/**

fun User.toResponse(): UserResponse = UserResponse(
    id = this.id!!,
    firstName = this.firstName,
    lastName = this.lastName,
    phoneNum = this.phoneNum,
    roles = this.roles.map { it.toDto() }.toSet()
)

fun Role.toDto(): RoleDto = RoleDto(
    name = this.name,
    code = this.code,
    permissions = this.permissions.map { it.toDto() }.toSet()
)
fun Permission.toDto(): PermissionDto = PermissionDto(
    name = this.name,
    code = this.code
)



fun Tenant.toResponse(): TenantResponseDTO = TenantResponseDTO(
    id = this.id!!,
    name = this.name,
    address = this.address,
    tagline = this.tagline,
    subscriptionPlan = this.subscriptionPlan,
    active = this.active,
    maxUsers = this.maxUsers
)



fun Category.toResponse(): CategoryResponseDTO = CategoryResponseDTO(
    id = this.id!!,
    name = this.name,
    title = this.title,
    parentId = this.parent?.id,
    tenantId = this.tenant.id!!,
    isLast = this.isLast
)



fun Task.toResponse(): TaskResponseDTO = TaskResponseDTO(
    id = this.id!!,
    title = this.title,
    description = this.description,
    priority = this.priority,
    dueDate = this.dueDate,
    category = this.category.id!!,
    owner = this.owner.id!!,
    assignees = this.assignees.map { it.id!! }.toSet(),
    state = this.state.toDto(),
    files = this.files.map { it.toDto() }.toSet()
)

fun TaskState.toDto(): TaskStateDto = TaskStateDto(
    code = this.code,
    name = this.name
)
fun File.toDto(): FileDto = FileDto(
    type = this.type,
    orgName = this.orgName,
    keyName = this.keyName
)

*/

interface BaseMapper<E, R> {

    fun toResponse(entity: E): R

    fun toListResponse(entities: Collection<E>): List<R> =
        entities.map(::toResponse)
}

@Component
class UserMapper : BaseMapper<User, UserResponse> {

    override fun toResponse(entity: User): UserResponse =
        UserResponse(
            id = entity.id!!,
            firstName = entity.firstName,
            lastName = entity.lastName,
            phoneNum = entity.phoneNum,
            roles = entity.roles.map { it.toResponse() }.toSet()
        )

    fun toEntity(dto: UserCreateDTO, encodedPassword: String): User =
        User(
            firstName = dto.firstName,
            lastName = dto.lastName ?: "",
            phoneNum = dto.phoneNum,
            password = encodedPassword
        )
}

fun Role.toResponse(): RoleDto =
    RoleDto(
        name = name,
        code = code,
        permissions = permissions.map { it.toResponse() }.toSet()
    )

fun Permission.toResponse(): PermissionDto =
    PermissionDto(
        name = name,
        code = code
    )

@Component
class TenantMapper : BaseMapper<Tenant, TenantResponseDTO> {

    override fun toResponse(entity: Tenant): TenantResponseDTO =
        TenantResponseDTO(
            id = entity.id!!,
            name = entity.name,
            address = entity.address,
            tagline = entity.tagline,
            subscriptionPlan = entity.subscriptionPlan,
            active = entity.active,
            maxUsers = entity.maxUsers
        )

    fun toEntity(dto: TenantCreateDTO): Tenant =
        Tenant(
            name = dto.name,
            address = dto.address,
            tagline = dto.tagline,
            subscriptionPlan = dto.subscriptionPlan
        )
}

@Component
class CategoryMapper : BaseMapper<Category, CategoryResponseDTO> {

    override fun toResponse(entity: Category): CategoryResponseDTO =
        CategoryResponseDTO(
            id = entity.id!!,
            name = entity.name,
            title = entity.title,
            parentId = entity.parent?.id,
            tenantId = entity.tenant.id!!,
            isLast = entity.isLast
        )
}

@Component
class TaskMapper : BaseMapper<Task, TaskResponseDTO> {

    override fun toResponse(entity: Task): TaskResponseDTO =
        TaskResponseDTO(
            id = entity.id!!,
            title = entity.title,
            description = entity.description,
            priority = entity.priority,
            dueDate = entity.dueDate,
            category = entity.category.id!!,
            owner = entity.owner.id!!,
            assignees = entity.assignees.map { it.id!! }.toSet(),
            state = entity.state.toResponse(),
            files = entity.files.map { it.toResponse() }.toSet()
        )
}

fun TaskState.toResponse(): TaskStateDto =
    TaskStateDto(code, name)

fun File.toResponse(): FileDto =
    FileDto(type, orgName, keyName)





