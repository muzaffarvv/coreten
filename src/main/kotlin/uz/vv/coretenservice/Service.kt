package uz.vv.coretenservice

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.core.io.UrlResource
import org.springframework.web.multipart.MultipartFile
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import java.nio.file.StandardCopyOption
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

private val logger = LoggerFactory.getLogger(TaskActionServiceImpl::class.java)


@Service
class RoleService(private val roleRepo: RoleRepo) {

    @Transactional
    fun createIfNotExist(name: String, code: String, permissions: Set<Permission>): Role =
        roleRepo.findByCode(code) ?: roleRepo.saveAndRefresh(
            Role(name = name, code = code, permissions = permissions.toMutableSet())
        )

    @Transactional(readOnly = true)
    fun getByCode(code: String): Role =
        roleRepo.findByCode(code)
            ?: throw RoleNotFoundException("Role not found with code: $code")
}

@Service
class PermissionService(private val permissionRepo: PermissionRepo) {

    @Transactional(readOnly = true)
    fun getByCode(code: String): Permission? = permissionRepo.findByCode(code)

    @Transactional
    fun createIfNotExist(name: String, code: String): Permission =
        getByCode(code) ?: permissionRepo.saveAndRefresh(
            Permission(name = name, code = code)
        )
}

@Service
class TaskStateService(
    private val taskStateRepo: TaskStateRepo,
    private val boardService: BoardService,
    private val taskRepo: TaskRepo,
    private val tenantSecurityService: TenantSecurityService
) {

    @Transactional(readOnly = true)
    fun getAllByBoardId(boardId: UUID): List<TaskStateDto> {
        val board = boardService.getBoard(boardId)
        val states = taskStateRepo.findAllByBoardId(board.id!!)
        return states.map { taskState -> taskState.toResponse() }
    }

    @Transactional(readOnly = true)
    fun getByCode(boardId: UUID, code: String): TaskState =
        taskStateRepo.findByBoardIdAndCode(boardId, code)
            ?: throw TaskStateNotFoundException("State not found with code: $code")

    @Transactional(readOnly = true)
    fun getByIdOrThrow(id: UUID): TaskState {
        val state = taskStateRepo.findByIdAndDeletedFalse(id)
            ?: throw TaskStateNotFoundException("State not found with id: $id")

        tenantSecurityService.validateEntityTenantAccess(
            state.board.project.tenant.id,
            "TaskState"
        )

        return state
    }

    @Transactional
    fun create(boardId: UUID, dto: TaskStateCreate): TaskStateDto {
        val board = getBoard(boardId)
        checkCodeUniqueness(boardId, dto.code)

        val state = TaskState(dto.code, dto.name, board)

        return taskStateRepo.saveAndRefresh(state).toResponse()
    }

    @Transactional
    fun copyState(stateId: UUID, toBoardId: UUID): TaskStateDto {
        val original = getByIdOrThrow(stateId)
        val targetBoard = getBoard(toBoardId)

        checkCodeUniqueness(toBoardId, original.code)

        val copied = TaskState(
            code = original.code,
            name = original.name,
            board = targetBoard
        )

        return taskStateRepo.saveAndRefresh(copied).toResponse()
    }

    @Transactional
    fun update(stateId: UUID, dto: TaskStateUpdate): TaskStateDto {
        val state = getByIdOrThrow(stateId)

        dto.code?.let { newCode ->
            if (newCode != state.code) {
                checkCodeUniqueness(state.board.id!!, newCode)
                state.code = newCode
            }
        }

        dto.name?.let { state.name = it }

        return taskStateRepo.saveAndRefresh(state).toResponse()
    }

    @Transactional
    fun delete(stateId: UUID) {
        val state = getByIdOrThrow(stateId)

        if (state.code == "NEW") {
            throw BadRequestException("Default 'NEW' state cannot be deleted")
        }

        val taskCount = taskRepo.countByStateIdAndDeletedFalse(stateId)
        if (taskCount > 0) throw BadRequestException("Cannot delete state. Move $taskCount tasks to another state first.")

        taskStateRepo.trash(state.id!!)
    }

    private fun getBoard(boardId: UUID) = boardService.getBoard(boardId)

    private fun checkCodeUniqueness(boardId: UUID, code: String) {
        if (taskStateRepo.existsByBoardIdAndCode(boardId, code))
            throw TaskStateAlreadyExistsException("State with code $code already exists")
    }
}

interface BaseService<CreateDto, UpdateDto, ResponseDto> {
    fun create(dto: CreateDto): ResponseDto
    fun update(id: UUID, dto: UpdateDto): ResponseDto
    fun getById(id: UUID): ResponseDto
    fun getAllList(): List<ResponseDto>
    fun delete(id: UUID)
}

abstract class BaseServiceImpl<
        E : BaseEntity,
        CreateDto,
        UpdateDto,
        ResponseDto,
        Mapper : BaseMapper<E, ResponseDto>,
        Repo : BaseRepo<E>
        >(
    protected val repository: Repo,
    protected val mapper: Mapper
) : BaseService<CreateDto, UpdateDto, ResponseDto> {

    protected abstract fun toEntity(dto: CreateDto): E
    protected abstract fun updateEntity(dto: UpdateDto, entity: E): E
    protected abstract fun getByIdOrThrow(id: UUID): E

    @Transactional
    override fun create(dto: CreateDto): ResponseDto {
        val entity = toEntity(dto)
        return mapper.toResponse(repository.saveAndRefresh(entity))
    }

    @Transactional(readOnly = true)
    override fun getById(id: UUID): ResponseDto =
        getByIdOrThrow(id).let(mapper::toResponse)

    @Transactional(readOnly = true)
    override fun getAllList(): List<ResponseDto> =
        repository.findAllNotDeleted().map(mapper::toResponse)

    @Transactional
    override fun delete(id: UUID) {
        val entity = getByIdOrThrow(id)
        repository.trash(entity.id!!)
    }
}


@Service
class UserService(
    repo: UserRepo,
    mapper: UserMapper,
    private val passwordEncoder: PasswordEncoder,
    private val roleService: RoleService
) : BaseServiceImpl<
        User,
        UserCreateDTO,
        UserUpdate,
        UserResponse,
        UserMapper,
        UserRepo
        >(repo, mapper) {

    @Transactional
    override fun create(dto: UserCreateDTO): UserResponse {
        if (repository.existsByPhoneNumAndDeletedFalse(dto.phoneNum)) {
            throw DuplicateResourceException("User already exists with phone number ${dto.phoneNum}")
        }

        if (dto.password != dto.confirmPassword) {
            throw PasswordMismatchException("Passwords do not match")
        }

        val user = toEntity(dto)
        user.roles.add(roleService.getByCode("PLATFORM_USER"))

        return mapper.toResponse(repository.saveAndRefresh(user))
    }

    override fun toEntity(dto: UserCreateDTO): User =
        mapper.toEntity(
            dto,
            encodedPassword = passwordEncoder.encode(dto.password)
        )

    @Transactional
    override fun update(id: UUID, dto: UserUpdate): UserResponse {
        val user = updateEntity(dto, getByIdOrThrow(id))
        val saved = repository.saveAndRefresh(user)
        return mapper.toResponse(saved)
    }

    override fun updateEntity(dto: UserUpdate, entity: User): User =
        entity.apply {
            dto.firstName?.let { firstName = it }
            dto.lastName?.let { lastName = it }
        }

    @Transactional
    fun updateSecurity(id: UUID, dto: UserUpdateSecurity): UserResponse {
        val user = updateSecurityEntity(dto, getByIdOrThrow(id))
        val saved = repository.saveAndRefresh(user)
        return mapper.toResponse(saved)
    }

    private fun updateSecurityEntity(dto: UserUpdateSecurity, entity: User): User = entity.apply {

        dto.phoneNum?.let { newPhone ->
            if (newPhone != phoneNum) {
                if (repository.existsByPhoneNumAndDeletedFalse(newPhone)) {
                    throw DuplicateResourceException(
                        "User already exists with phone number $newPhone"
                    )
                }
                phoneNum = newPhone
            }
        }

        val newPass = dto.newPassword ?: return@apply

        if (dto.oldPassword.isNullOrBlank() ||
            !passwordEncoder.matches(dto.oldPassword, password)
        ) {
            throw InvalidPasswordException("The old password was entered incorrectly")
        }

        if (newPass != dto.confirmPassword) {
            throw PasswordMismatchException("The new passwords did not match")
        }

        password = passwordEncoder.encode(newPass)
    }

    @Transactional
    fun updateUserRole(userId: UUID, dto: UserUpdateRoleDTO): UserResponse {
        val user = getByIdOrThrow(userId)

        val newRole = roleService.getByCode(dto.roleCode)

        user.roles.add(newRole)

        val saved = repository.saveAndRefresh(user)
        return mapper.toResponse(saved)
    }

    @Transactional(readOnly = true)
    fun getByPhoneNum(phoneNum: String): UserResponse {
        val user = repository.findByPhoneNumAndDeletedFalse(phoneNum)
            ?: throw UserNotFoundException("User not found with phone number: $phoneNum")
        return mapper.toResponse(user)
    }

    override fun getByIdOrThrow(id: UUID): User =
        repository.findByIdAndDeletedFalse(id) ?: throw UserNotFoundException("User not found with id: $id")
}


@Service
class TenantService(
    repo: TenantRepo,
    mapper: TenantMapper,
    private val employeeService: EmployeeService,
    @Lazy private val projectService: ProjectService, // @Lazy vaqtinchalik yechim / interface and impl
    private val tenantSecurityService: TenantSecurityService
) : BaseServiceImpl<
        Tenant,
        TenantCreateDTO,
        TenantUpdateDTO,
        TenantResponseDTO,
        TenantMapper,
        TenantRepo
        >(repo, mapper) {

    @Transactional
    override fun create(dto: TenantCreateDTO): TenantResponseDTO {
        validateNameUnique(dto.name)

        val entity = toEntity(dto).apply { maxUsers = subscriptionPlan.maxUsers }

        val saved = repository.saveAndRefresh(entity)
        return mapper.toResponse(saved)
    }

    override fun toEntity(dto: TenantCreateDTO): Tenant =
        mapper.toEntity(dto)

    @Transactional
    override fun update(id: UUID, dto: TenantUpdateDTO): TenantResponseDTO {
        val tenant = getByIdOrThrow(id)

        dto.name?.let { validateNameUnique(it, excludeId = id) }

        val updated = updateEntity(dto, tenant)

        validateSubscriptionLimits(updated)

        val saved = repository.saveAndRefresh(updated)
        return mapper.toResponse(saved)
    }

    override fun updateEntity(dto: TenantUpdateDTO, entity: Tenant): Tenant =
        entity.apply {
            dto.name?.let { name = it }
            dto.address?.let { address = it }
            dto.tagline?.let { tagline = it }
        }

    @Transactional
    fun changeSubscriptionPlan(tenantId: UUID, newPlan: TenantPlan): TenantResponseDTO {
        val tenant = getByIdOrThrow(tenantId)

        if (tenant.subscriptionPlan == newPlan) {
            return mapper.toResponse(tenant)
        }

        tenant.subscriptionPlan = newPlan
        tenant.maxUsers = newPlan.maxUsers

        validateSubscriptionLimits(tenant)

        val saved = repository.save(tenant)
        return mapper.toResponse(saved)
    }

    @Transactional(readOnly = true)
    fun getTenant(id: UUID): Tenant = getByIdOrThrow(id)

    override fun getByIdOrThrow(id: UUID): Tenant {
        tenantSecurityService.validateTenantAccess(id)
        return repository.findByIdAndDeletedFalse(id)
            ?: throw TenantNotFoundException("Tenant not found with id: $id")
    }

    private fun validateNameUnique(name: String, excludeId: UUID? = null) {
        val exists = if (excludeId == null)
            repository.existsByNameIgnoreCase(name)
        else
            repository.existsByNameIgnoreCaseAndIdNot(name, excludeId)

        if (exists)
            throw TenantAlreadyExistsException(
                "Tenant with name '$name' already exists"
            )
    }

    private fun validateSubscriptionLimits(tenant: Tenant) {
        val activeUsers = employeeService.countActiveByTenantId(tenant.id!!)
        val limit = tenant.subscriptionPlan.maxUsers

        if (activeUsers > limit) {
            throw TenantSubscriptionLimitExceededException(
                "Current active users ($activeUsers) exceed allowed limit ($limit) for plan ${tenant.subscriptionPlan}"
            )
        }
    }

    @Transactional
    override fun delete(id: UUID) {
        val tenant = getByIdOrThrow(id)

        projectService.getAllByTenantId(tenant.id!!).forEach {
            projectService.delete(it.id)
        }

        employeeService.getAllByTenantId(tenant.id!!).forEach {
            employeeService.delete(it.id)
        }
        repository.trash(tenant.id!!)
    }
}


@Service
class EmployeeService(
    private val userRepo: UserRepo,
    private val tenantRepo: TenantRepo,
    private val tenantSecurityService: TenantSecurityService,
    repo: EmployeeRepo,
    mapper: EmployeeMapper
) : BaseServiceImpl<
        Employee,
        EmployeeCreateDTO,
        EmployeeUpdateDTO,
        EmployeeResponseDTO,
        EmployeeMapper,
        EmployeeRepo
        >(repo, mapper) {

    override fun toEntity(dto: EmployeeCreateDTO): Employee {
        val user = userRepo.findByIdAndDeletedFalse(dto.userId)
            ?: throw UserNotFoundException("User not found with id: ${dto.userId}")

        val tenants = tenantRepo.findAllById(dto.tenantIds).toMutableSet()

        val code = "EMP-${EmployeeCodeGenerator.generate(8)}"

        return Employee(
            code = code,
            user = user,
            position = Position.INTERN,
            tenants = tenants
        )
    }

    @Transactional(readOnly = true)
    fun getPosition(id: UUID): Position = getByIdOrThrow(id).position

    @Transactional(readOnly = true)
    fun getEmployee(id: UUID): Employee = getByIdOrThrow(id)

    @Transactional
    override fun update(id: UUID, dto: EmployeeUpdateDTO): EmployeeResponseDTO =
        mapper.toResponse(repository.save(updateEntity(dto, getByIdOrThrow(id))))

    override fun updateEntity(dto: EmployeeUpdateDTO, entity: Employee): Employee =
        entity.apply {
            dto.active?.let { active = it }
            dto.position?.let { position = it }
        }

    @Transactional(readOnly = true)
    fun getAllByTenantId(id: UUID): List<EmployeeResponseDTO> {
        logger.info("Fetching all employees for Tenant ID: {}", id)

        try {
            tenantSecurityService.validateTenantAccess(id)
            val employees = repository.findActiveByTenantIdWithTenants(id)

            logger.info("Successfully retrieved {} employees for Tenant ID: {}", employees.size, id)
            return mapper.toListResponse(employees)

        } catch (e: AccessDeniedException) {
            logger.warn("Access denied for Tenant ID: {}. Reason: {}", id, e.message)
            throw e
        } catch (e: Exception) {
            logger.error("Error occurred while fetching employees for Tenant ID: {}", id, e)
            throw e
        }
    }

    override fun getByIdOrThrow(id: UUID): Employee {
        val employee = repository.findByIdAndDeletedFalse(id)
            ?: throw EmployeeNotFoundException("Employee not found with id: $id")

        val currentTenantId = TenantContext.getTenantIdOrNull()

        if (employee.tenants.none { it.id == currentTenantId }) {
            throw UnauthorizedException("Employee does not belong to current tenant")
        }

        return employee
    }

    @Transactional(readOnly = true)
    fun countActiveByTenantId(tenantId: UUID): Int =
        repository.countByTenantsIdAndActiveTrueAndDeletedFalse(tenantId)

    @Transactional
    fun changePosition(id: UUID, dto: ChangePositionDTO): EmployeeResponseDTO {
        val employee = getByIdOrThrow(id)
        employee.position = dto.position
        return mapper.toResponse(repository.save(employee))
    }
}


@Service
class ProjectService(
    repository: ProjectRepo,
    mapper: ProjectMapper,
    @Lazy private val boardService: BoardService,
    private val tenantService: TenantService,
    private val tenantSecurityService: TenantSecurityService
) : BaseServiceImpl<
        Project,
        ProjectCreateDTO,
        ProjectUpdateDTO,
        ProjectResponseDTO,
        ProjectMapper,
        ProjectRepo
        >(repository, mapper) {
    @Transactional
    override fun create(dto: ProjectCreateDTO): ProjectResponseDTO {
        checkNameUniqueness(dto.name, dto.tenantId)
        val project = repository.saveAndRefresh(toEntity(dto))
        return mapper.toResponse(project)
    }

    override fun toEntity(dto: ProjectCreateDTO): Project =
        mapper.toEntity(dto, tenant = tenantService.getTenant(dto.tenantId))

    @Transactional
    override fun update(id: UUID, dto: ProjectUpdateDTO): ProjectResponseDTO {
        val entity = getByIdOrThrow(id)
        val updated = updateEntity(dto, entity)

        checkNameUniqueness(
            updated.name,
            updated.tenant.id!!,
            excludeId = updated.id!!
        )

        return mapper.toResponse(repository.save(updated))
    }

    override fun updateEntity(dto: ProjectUpdateDTO, entity: Project): Project {
        entity.apply {
            dto.name?.let { name = it }
            dto.description?.let { description = it }
            dto.active?.let { active = it }
            return entity
        }
    }

    @Transactional(readOnly = true)
    fun getAllByTenantId(id: UUID): List<ProjectResponseDTO> {
        tenantSecurityService.validateTenantAccess(id)
        return mapper.toListResponse(repository.findAllByTenantIdAndDeletedFalse(id))
    }

    @Transactional(readOnly = true)
    fun getProject(id: UUID): Project = getByIdOrThrow(id)

    override fun getByIdOrThrow(id: UUID): Project {
        val project = repository.findByIdAndDeletedFalse(id)
            ?: throw ProjectNotFoundException("Project not found with id: $id")

        tenantSecurityService.validateEntityTenantAccess(
            project.tenant.id,
            "Project"
        )

        return project
    }

    private fun checkNameUniqueness(name: String, tenantId: UUID, excludeId: UUID? = null) {
        val exists = if (excludeId == null) {
            repository.existsByNameAndTenantIdAndDeletedFalse(name, tenantId)
        } else repository.existsByNameAndTenantIdAndIdNotAndDeletedFalse(name, tenantId, excludeId)

        if (exists) throw ProjectAlreadyExistsException("Project with name '$name' already exists")

    }

    @Transactional
    override fun delete(id: UUID) {
        val project = getByIdOrThrow(id)

        boardService.getAllByProject(project.id!!)
            .forEach { boardService.delete(it.id) }

        repository.trash(project.id!!)
    }
}


@Service
class BoardService(
    repo: BoardRepo,
    mapper: BoardMapper,
    private val taskRepo: TaskRepo,
    private val taskStateRepo: TaskStateRepo,
    private val projectService: ProjectService,
    private val tenantSecurityService: TenantSecurityService
) : BaseServiceImpl<
        Board,
        BoardCreateDTO,
        BoardUpdateDTO,
        BoardResponseDTO,
        BoardMapper,
        BoardRepo
        >(repo, mapper) {

    @Transactional
    override fun create(dto: BoardCreateDTO): BoardResponseDTO {
        checkNameUniqueness(dto.name, dto.projectId)

        val board = toEntity(dto)
        board.assignDefaultStates()

        val savedBoard = repository.saveAndRefresh(board)
        return mapper.toResponse(savedBoard)
    }

    override fun toEntity(dto: BoardCreateDTO): Board =
        mapper.toEntity(dto, project = projectService.getProject(dto.projectId))

    @Transactional
    override fun update(id: UUID, dto: BoardUpdateDTO): BoardResponseDTO {
        val entity = getByIdOrThrow(id)
        val updated = updateEntity(dto, entity)

        checkNameUniqueness(
            updated.name,
            updated.project.id!!,
            excludeId = updated.id!!
        )

        return mapper.toResponse(repository.save(updated))
    }

    override fun updateEntity(dto: BoardUpdateDTO, entity: Board): Board =
        entity.apply {
            dto.name?.let { name = it }
            dto.description?.let { description = it }
            dto.active?.let { active = it }
        }

    @Transactional(readOnly = true)
    override fun getByIdOrThrow(id: UUID): Board {
        val board = repository.findByIdAndDeletedFalseWithStates(id)
            ?: throw BoardNotFoundException("Board not found with id $id")

        tenantSecurityService.validateEntityTenantAccess(
            board.project.tenant.id,
            "Board"
        )

        return board
    }

    @Transactional(readOnly = true)
    fun getBoard(id: UUID): Board = getByIdOrThrow(id)

    @Transactional(readOnly = true)
    fun getAllByProject(projectId: UUID): List<BoardResponseDTO> =
        mapper.toListResponse(repository.findAllByProjectIdAndDeletedFalseWithStates(projectId))

    private fun checkNameUniqueness(name: String, projectId: UUID, excludeId: UUID? = null) {
        val exists = if (excludeId == null) {
            repository.existsByNameAndProjectIdAndDeletedFalse(name, projectId)
        } else repository.existsByNameAndProjectIdAndIdNotAndDeletedFalse(name, projectId, excludeId)

        if (exists) throw BoardAlreadyExistsException("Board with name '$name' already exists")
    }

    @Transactional
    override fun delete(id: UUID) {
        val board = getByIdOrThrow(id)

        taskRepo.softDeleteByBoardId(id)
        taskStateRepo.softDeleteByBoardId(id)

        repository.trash(board.id!!)
    }
}

@Service
class TaskService(
    repo: TaskRepo,
    mapper: TaskMapper,
    private val boardService: BoardService,
    private val employeeService: EmployeeService,
    private val taskStateService: TaskStateService,
    private val fileRepo: FileRepo,
    private val tenantSecurityService: TenantSecurityService,
    private val taskActionService: TaskActionService
) : BaseServiceImpl<
        Task,
        TaskCreateDTO,
        TaskUpdateDTO,
        TaskResponseDTO,
        TaskMapper,
        TaskRepo
        >(repo, mapper) {

    private fun getCurrentEmployee() = employeeService.getEmployee(TenantContext.getEmployeeIdOrThrow())

    @Transactional
    override fun create(dto: TaskCreateDTO): TaskResponseDTO {
        val entity = toEntity(dto)
        val savedTask = repository.save(entity)

        taskActionService.log(
            savedTask,
            getCurrentEmployee(),
            TaskActionType.CREATED,
            null,
            savedTask.title
        )

        return mapper.toResponse(savedTask)
    }

    override fun toEntity(dto: TaskCreateDTO): Task =
        buildTask(
            dto.boardId,
            dto.title,
            dto.description,
            dto.priority,
            dto.dueDate
        )


    @Transactional
    override fun update(id: UUID, dto: TaskUpdateDTO): TaskResponseDTO {
        val entity = getByIdOrThrow(id)

        val oldTitle = entity.title

        val updated = updateEntity(dto, entity)

        val savedTask = repository.save(updated)

        if (oldTitle != savedTask.title) {
            taskActionService.log(
                task = savedTask,
                modifier = getCurrentEmployee(),
                type = TaskActionType.UPDATED,
                oldValue = oldTitle,
                newValue = savedTask.title,
                comment = "Vazifa tahrirlandi"
            )
        }

        return mapper.toResponse(savedTask)
    }

    override fun updateEntity(dto: TaskUpdateDTO, entity: Task): Task {
        val newInstantDate = dto.dueDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()

        dto.title?.takeIf { it != entity.title }?.let { entity.title = it }
        dto.priority?.takeIf { it != entity.priority }?.let { entity.priority = it }
        if (newInstantDate != entity.dueDate) {
            entity.dueDate = newInstantDate
        }

        return entity
    }

    @Transactional
    fun createWithFiles(dto: TaskCreateWithFilesDTO): TaskResponseDTO {
        val task = buildTask(
            dto.boardId,
            dto.title,
            dto.description,
            dto.priority,
            dto.dueDate
        )

        if (!dto.fileKeys.isNullOrEmpty()) {
            val files = fileRepo.findAllByKeyNameInAndDeletedFalse(dto.fileKeys)
            files.forEach { file ->
                file.task = task
                task.files.add(file)
            }
        }

        val savedTask = repository.save(task)

        taskActionService.log(
            savedTask,
            getCurrentEmployee(),
            TaskActionType.CREATED,
            null,
            "Vazifa fayllar bilan yaratildi"
        )

        return mapper.toResponse(savedTask)
    }

    @Transactional
    fun updateWithFiles(taskId: UUID, dto: TaskUpdateDTO, fileKeys: List<String>?): TaskResponseDTO {
        val entity = getByIdOrThrow(taskId)

        val updated = updateEntity(dto, entity)

        if (!fileKeys.isNullOrEmpty()) {
            val newFiles = fileRepo.findAllByKeyNameInAndDeletedFalse(fileKeys)

            val existingKeys = updated.files.map { it.keyName }.toSet()
            newFiles.forEach { file ->
                if (!existingKeys.contains(file.keyName)) {
                    updated.files.add(file)
                }
            }
        }

        val savedTask = repository.save(updated)

        return mapper.toResponse(savedTask)
    }

    override fun getByIdOrThrow(id: UUID): Task {
        val task = repository.findTaskByIdAndDeletedFalse(id)
            ?: throw TaskNotFoundException("Task not found with ID: $id")

        tenantSecurityService.validateEntityTenantAccess(
            task.board.project.tenant.id,
            "Task"
        )
        return task
    }

    @Transactional
    fun assignEmployee(taskId: UUID, employeeId: UUID): TaskResponseDTO {
        logger.info("Attempting to assign employee {} to task {}", employeeId, taskId)

        val task = getByIdOrThrow(taskId)
        val employee = employeeService.getEmployee(employeeId)

        if (task.assignees.any { it.id == employeeId }) {
            logger.warn("Assign failed: Employee {} already assigned to task {}", employeeId, taskId)
            throw BadRequestException("Employee is already assigned to this task")
        }

        task.assignees.add(employee)
        val savedTask = repository.saveAndRefresh(task)

        taskActionService.log(
            savedTask,
            getCurrentEmployee(),
            TaskActionType.ASSIGNED,
            null,
            employeeId.toString(),
            "Assigned new employee"
        )

        logger.info("Successfully assigned employee {} to task {}", employeeId, taskId)
        return mapper.toResponse(savedTask)
    }

    @Transactional
    fun unassignEmployee(taskId: UUID, employeeId: UUID): TaskResponseDTO {
        logger.info("Attempting to unassign employee {} from task {}", employeeId, taskId)

        val task = getByIdOrThrow(taskId)

        val removed = task.assignees.removeIf { it.id == employeeId }
        if (!removed) {
            logger.error("Unassign failed: Employee {} was not found in task {} assignees", employeeId, taskId)
            throw EmployeeNotFoundException("Employee not assigned to task")
        }

        val savedTask = repository.saveAndRefresh(task)

        taskActionService.log(
            savedTask,
            getCurrentEmployee(),
            TaskActionType.UNASSIGNED,
            employeeId.toString(),
            null
        )

        logger.info("Successfully unassigned employee {} from task {}", employeeId, taskId)
        return mapper.toResponse(savedTask)
    }

    @Transactional
    fun changeState(taskId: UUID, newStateCode: String): TaskResponseDTO {
        val task = getByIdOrThrow(taskId)
        val oldState = task.state.name
        logger.info("Changing task {} state from {} to {}", taskId, oldState, newStateCode)

        val boardId = task.board.id!!
        val newState = taskStateService.getByCode(boardId, newStateCode)

        task.state = newState
        val savedTask = repository.saveAndRefresh(task)

        taskActionService.log(
            savedTask,
            getCurrentEmployee(),
            TaskActionType.TASK_STATE_CHANGED,
            oldState,
            newState.name
        )

        logger.info("Task {} state successfully updated to {}", taskId, newState.name)
        return mapper.toResponse(savedTask)
    }

    @Transactional(readOnly = true)
    fun getByBoardId(boardId: UUID): List<TaskResponseDTO> {
        val board = boardService.getBoard(boardId)
        val tasks = repository.findAllByBoardIdWithAssigneesAndFiles(board.id!!)
        return mapper.toListResponse(tasks)
    }

    @Transactional(readOnly = true)
    fun getByState(stateId: UUID): List<TaskResponseDTO> {
        taskStateService.getByIdOrThrow(stateId)
        val tasks = repository.findAllByStateIdAndDeletedFalse(stateId)
        return mapper.toListResponse(tasks)
    }

    @Transactional(readOnly = true)
    fun getMyTasks(): List<TaskResponseDTO> {
        val employeeId = TenantContext.getEmployeeIdOrThrow()
        val tenantId = TenantContext.getTenantId()

        val tasks = repository.findAllAssignedTasks(employeeId, tenantId)
        return mapper.toListResponse(tasks)
    }

    @Transactional
    override fun delete(id: UUID) {
        val task = getByIdOrThrow(id)

        taskActionService.log(
            task,
            getCurrentEmployee(),
            TaskActionType.DELETED,
            task.title,
            null
        )

        repository.trash(task.id!!)
    }

    private fun buildTask(
        boardId: UUID,
        title: String,
        description: String?,
        priority: TaskPriority?,
        dueDate: LocalDate?
    ): Task {
        val board = boardService.getBoard(boardId)
        val defaultState = taskStateService.getByCode(board.id!!, "NEW")
        val owner = getCurrentEmployee()
        val instant = dueDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()

        return Task(
            title = title,
            description = description ?: "",
            priority = priority ?: TaskPriority.MEDIUM_LOW,
            dueDate = instant,
            state = defaultState,
            board = board,
            owner = owner
        )
    }
}


interface FileService {
    fun upload(file: MultipartFile): FileDto
    fun download(keyName: String): Resource
    fun getByKey(keyName: String): File
    fun getAllByIds(ids: List<UUID>): List<File>
    fun delete(id: UUID)
    fun deleteByKeyName(keyName: String)
}

@Service
class FileServiceImpl(
    private val fileRepo: FileRepo,
    @Value("\${file.upload-dir:uploads}")
    private val uploadDir: String,
    @Value("\${file.max-size:10485760}")
    private val maxSize: Long
) : FileService {

    companion object {
        private const val MAX_RETRIES = 10
    }

    init {
        val path = basePath()
        if (!Files.exists(path)) {
            Files.createDirectories(path)
            logger.info("Upload directory created: {}", path.toAbsolutePath())
        }
    }

    @Transactional
    override fun upload(file: MultipartFile): FileDto {
        if (file.isEmpty) throw FileEmptyException("File is empty")
        if (file.size > maxSize) throw FileTooLargeException("File size exceeds limit")

        val originalName = file.originalFilename ?: "unnamed"
        val extension = getExtension(originalName)
        val keyName = "${generateUniqueKey()}$extension"
        val contentType = file.contentType

        val targetPath = try {
            saveToDisk(file, keyName)
        } catch (ex: Exception) {
            logger.error("Disk write failed: ${ex.message}")
            throw FileUploadFailedException("Disk write failed")
        }

        return try {
            val fileEntity = saveFileToDb(originalName, contentType, keyName, targetPath, file.size)
            fileEntity.toResponse()
        } catch (ex: Exception) {
            Files.deleteIfExists(targetPath)
            logger.error("Database save failed: ${ex.message}")
            throw FileUploadFailedException("Database save failed")
        }
    }

    @Transactional
    fun saveFileToDb(orgName: String, contentType: String?, key: String, path: Path, size: Long): File {
        val type = resolveFileType(contentType, orgName)
        return fileRepo.save(
            File(
                type = type,
                orgName = orgName,
                keyName = key,
                path = path.toString(),
                size = size.toInt()
            )
        )
    }

    override fun download(keyName: String): Resource {
        val file = getByKey(keyName)
        val path = Paths.get(file.path)

        val resource = UrlResource(path.toUri())
        if (!resource.exists() || !resource.isReadable) {
            throw FileNotFoundException("File not found or unreadable: $keyName")
        }
        return resource
    }

    override fun getByKey(keyName: String): File =
        fileRepo.findByKeyNameAndDeletedFalse(keyName)
            ?: throw FileNotFoundException("File not found: $keyName")

    @Transactional(readOnly = true)
    override fun getAllByIds(ids: List<UUID>): List<File> {
        if (ids.isEmpty()) return emptyList()

        val files = fileRepo.findAllByIdInAndDeletedFalse(ids)

        if (files.size != ids.size) {
            val missing = ids.toSet() - files.map { it.id }.toSet()
            throw FileNotFoundException("Files not found for: $missing")
        }

        return files
    }

    @Transactional
    override fun delete(id: UUID) {
        val file = fileRepo.findById(id)
            .orElseThrow { FileNotFoundException("File not found: $id") }

        file.deleted = true
        fileRepo.save(file)

        logger.info("File marked as deleted with id: {}", id)
    }

    @Transactional
    override fun deleteByKeyName(keyName: String) {
        val file = fileRepo.findByKeyNameAndDeletedFalse(keyName)
            ?: throw FileNotFoundException("File not found: $keyName")

        file.deleted = true
        fileRepo.save(file)

        logger.info("File marked as deleted with keyName: {}", keyName)
    }

    private fun saveToDisk(file: MultipartFile, keyName: String): Path {
        val target = basePath().resolve(keyName)
        file.inputStream.use { input ->
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
        }
        return target
    }

    private fun basePath(): Path {
        val path = Paths.get(uploadDir)
        return if (path.isAbsolute) path
        else Paths.get(System.getProperty("user.home")).resolve(uploadDir)
    }

    private fun generateUniqueKey(): String {
        repeat(MAX_RETRIES) {
            val key = FileKeyGenerator.generateFileKey()
            if (!fileRepo.existsByKeyNameAndDeletedFalse(key)) return key
        }
        throw FileKeyGenerationException("Max key generation attempts exceeded")
    }

    private fun getExtension(fileName: String): String {
        val lastDotIndex = fileName.lastIndexOf('.')
        return if (lastDotIndex > 0) fileName.substring(lastDotIndex) else ""
    }

    private fun resolveFileType(contentType: String?, fileName: String): FileType {
        val ct = contentType?.lowercase() ?: ""
        val ext = fileName.lowercase()

        return when {
            ct.startsWith("image") || listOf(
                ".jpg",
                ".jpeg",
                ".png",
                ".webp",
                ".gif"
            ).any { ext.endsWith(it) } -> FileType.IMAGE

            ct.startsWith("video") || listOf(".mp4", ".mov", ".avi").any { ext.endsWith(it) } -> FileType.VIDEO
            else -> FileType.DOCUMENT
        }
    }
}

interface TaskActionService {

    fun getAllByTaskId(taskId: UUID): List<TaskActionResponse>

    fun log(
        task: Task,
        modifier: Employee,
        type: TaskActionType,
        oldValue: String? = null,
        newValue: String? = null,
        comment: String? = null
    )
}

@Service
class TaskActionServiceImpl(
    private val taskActionRepo: TaskActionRepo,
    private val taskActionMapper: TaskActionMapper
) : TaskActionService {

    @Transactional(readOnly = true)
    override fun getAllByTaskId(taskId: UUID): List<TaskActionResponse> {
        val actions = taskActionRepo.findAllByTaskId(taskId)

        return actions.map { entity ->
            val fullName = entity.modifier.user.let {
                "${it.firstName} ${it.lastName}".trim()
            }

            taskActionMapper.toResponse(entity, fullName)
        }
    }

    @Transactional()
    override fun log(
        task: Task,
        modifier: Employee,
        type: TaskActionType,
        oldValue: String?,
        newValue: String?,
        comment: String?
    ) {
        try {
            val action = taskActionMapper.createEntity(
                task = task,
                modifier = modifier,
                type = type,
                oldVal = oldValue,
                newVal = newValue,
                comment = comment
            )
            taskActionRepo.save(action)
        } catch (ex: Exception) {
            logger.warn("Failed to log task action [taskId=${task.id}, type=$type]: ${ex.message}")
        }
    }
}

@Service
class TenantSecurityService {

    private val logger = LoggerFactory.getLogger(TenantSecurityService::class.java)

    fun validateTenantAccess(requiredTenantId: UUID?) {
        if (requiredTenantId == null) {
            return
        }

        val currentTenantId = TenantContext.getTenantIdOrNull()

        if (currentTenantId == null) {
            logger.warn("Tenant access denied: No tenant context set")
            throw UnauthorizedException("Tenant context not set. Please select a tenant.")
        }

        if (currentTenantId != requiredTenantId) {
            logger.warn(
                "Tenant access denied: Required $requiredTenantId, but current is $currentTenantId"
            )
            throw UnauthorizedException(
                "Access denied. This resource belongs to a different tenant."
            )
        }
    }

    fun hasAccessToTenant(tenantId: UUID?): Boolean {
        if (tenantId == null) return true

        val currentTenantId = TenantContext.getTenantIdOrNull()
        return currentTenantId == tenantId
    }

    fun requireTenantContext(): UUID {
        return TenantContext.getTenantId()
    }

    fun validateEntityTenantAccess(entityTenantId: UUID?, entityType: String = "Resource") {
        try {
            validateTenantAccess(entityTenantId)
        } catch (e: UnauthorizedException) {
            throw UnauthorizedException("$entityType access denied: belongs to different tenant ${e.message}")
        }
    }
}
