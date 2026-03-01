package uz.vv.coretenservice

import org.springframework.stereotype.Component


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
            subscriptionPlan = TenantPlan.FREE, // default
            active = true
        )
}


@Component
class EmployeeMapper() : BaseMapper<Employee, EmployeeResponseDTO> {

    override fun toResponse(entity: Employee): EmployeeResponseDTO =
        EmployeeResponseDTO(
            id = entity.id!!,
            code = entity.code,
            active = entity.active,
            position = entity.position,
            user = entity.user.id!!,
            tenants = entity.tenants.map { it.id!! }.toSet(),
            createdAt = entity.createdAt!!,
            updatedAt = entity.updatedAt!!
        )
}


@Component
class ProjectMapper : BaseMapper<Project, ProjectResponseDTO> {

    override fun toResponse(entity: Project): ProjectResponseDTO =
        ProjectResponseDTO(
            id = entity.id!!,
            name = entity.name,
            description = entity.description,
            active = entity.active,
            tenantId = entity.tenant.id!!
        )

    fun toEntity(dto: ProjectCreateDTO, tenant: Tenant): Project =
        Project(
            name = dto.name,
            description = dto.description,
            tenant = tenant
        )
}


@Component
class BoardMapper : BaseMapper<Board, BoardResponseDTO> {

    override fun toResponse(entity: Board): BoardResponseDTO =
        BoardResponseDTO(
            id = entity.id!!,
            name = entity.name,
            description = entity.description,
            active = entity.active,
            projectId = entity.project.id!!,
            states = entity.states.map { it.code }.toMutableList()
        )

    fun toEntity(
        dto: BoardCreateDTO,
        project: Project
    ): Board = Board(
            name = dto.name,
            description = dto.description,
            project = project,
            active = true,
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
            category = entity.board.id!!,
            owner = entity.owner.id!!,
            assignees = entity.assignees.map { it.id!! }.toSet(),
            state = entity.state.code,
            files = entity.files.map { it.toResponse() }.toSet()
        )
}

@Component
class TaskActionMapper {

    fun toResponse(entity: TaskAction, modifierName: String ): TaskActionResponse =
        TaskActionResponse(
            id = entity.id!!,
            taskId = entity.task.id!!,
            modifierId = entity.modifier.id!!,
            modifierName = modifierName,
            actionType = entity.actionType,
            oldValue = entity.oldValue,
            newValue = entity.newValue,
            comment = entity.comment,
            createdAt = entity.createdAt!!
        )

    fun createEntity(
        task: Task,
        modifier: Employee,
        type: TaskActionType,
        oldVal: String? = null,
        newVal: String? = null,
        comment: String? = null
    ): TaskAction = TaskAction(
        task = task,
        modifier = modifier,
        actionType = type,
        oldValue = oldVal,
        newValue = newVal,
        comment = comment
    )
}


fun TaskState.toResponse(): TaskStateDto =
    TaskStateDto(board.id!!, code, name)

fun File.toResponse(): FileDto =
    FileDto(type, orgName, keyName)