package uz.vv.coretenservice

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.core.io.UrlResource
import org.springframework.web.multipart.MultipartFile
import java.nio.file.StandardCopyOption
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.security.core.userdetails.UsernameNotFoundException

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
    private val tenantSecurityService: TenantSecurityService
) {

    @Transactional(readOnly = true)
    fun getAllByBoardId(boardId: UUID): List<TaskState> {
        val board = boardService.getBoard(boardId)
        return taskStateRepo.findAllByBoardId(board.id!!)
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
        taskStateRepo.trash(state.id!!)
    }

    // detachState o'chirildi: board maydoni non-nullable, state boardsiz mavjud bo'la olmaydi

    private fun getBoard(boardId: UUID) =
        boardService.getBoard(boardId)

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
    private val passwordEncoder: PasswordEncoder
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
        if (repository.existsByPhoneNum(dto.phoneNum)) {
            throw DuplicateResourceException("User already exists with phone number ${dto.phoneNum}")
        }

        if (dto.password != dto.confirmPassword) {
            throw PasswordMismatchException("Passwords do not match")
        }

        val user = toEntity(dto)
        return mapper.toResponse(repository.saveAndRefresh(user))
    }

    override fun toEntity(dto: UserCreateDTO): User {
        return mapper.toEntity(
            dto,
            encodedPassword = passwordEncoder.encode(dto.password)
        )
    }

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

    // Parol va telefon raqamini yangilash — tranzaksiya ichida bajariladi
    @Transactional
    fun updateSecurity(id: UUID, dto: UserUpdateSecurity): UserResponse {
        val user = updateSecurityEntity(dto, getByIdOrThrow(id))
        val saved = repository.save(user)
        return mapper.toResponse(saved)
    }

    private fun updateSecurityEntity(dto: UserUpdateSecurity, entity: User): User = entity.apply {

        dto.phoneNum?.let { newPhone ->
            if (newPhone != phoneNum) {
                if (repository.existsByPhoneNum(newPhone)) {
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
    private val employeeService: EmployeeService
) : BaseServiceImpl<
        Tenant,
        TenantCreateDTO,
        TenantUpdateDTO,
        TenantResponseDTO,
        TenantMapper,
        TenantRepo
        >(repo, mapper) {


    override fun create(dto: TenantCreateDTO): TenantResponseDTO {
        validateNameUnique(dto.name)

        val entity = toEntity(dto).apply {
            maxUsers = subscriptionPlan.maxUsers
        }

        val saved = repository.save(entity)
        return mapper.toResponse(saved)
    }

    override fun toEntity(dto: TenantCreateDTO): Tenant =
        mapper.toEntity(dto)

    override fun update(id: UUID, dto: TenantUpdateDTO): TenantResponseDTO {
        val tenant = getByIdOrThrow(id)

        dto.name?.let { validateNameUnique(it, excludeId = id) }

        val updated = updateEntity(dto, tenant)

        if (dto.subscriptionPlan != null) {
            updated.maxUsers = updated.subscriptionPlan.maxUsers
        }

        validateSubscriptionLimits(updated)

        val saved = repository.saveAndRefresh(updated)
        return mapper.toResponse(saved)
    }

    override fun updateEntity(dto: TenantUpdateDTO, entity: Tenant): Tenant =
        entity.apply {
            dto.name?.let { name = it }
            dto.address?.let { address = it }
            dto.tagline?.let { tagline = it }
            dto.subscriptionPlan?.let { subscriptionPlan = it }
        }

    @Transactional(readOnly = true)
    fun getTenant(id: UUID): Tenant = getByIdOrThrow(id)

    override fun getByIdOrThrow(id: UUID): Tenant =
        repository.findByIdAndDeletedFalse(id)
            ?: throw TenantNotFoundException("Tenant not found with id: $id")

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

    // Xavfsizlik: getByIdOrThrow() ichida TenantContext orqali tenant tekshiruvi amalga oshiriladi
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
            dto.tenantIds?.let {
                tenants = tenantRepo.findAllById(it).toMutableSet()
            }
        }

    @Transactional(readOnly = true)
    fun getAllByTenantId(id: UUID): List<EmployeeResponseDTO> {
        val employees = repository.findActiveByTenantId(id)
        return mapper.toListResponse(employees)
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

    // Xavfsizlik: getByIdOrThrow() ichida tenant tekshiruvi bajariladi
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


    override fun create(dto: ProjectCreateDTO): ProjectResponseDTO {
        checkNameUniqueness(dto.name, dto.tenantId)
        val project = repository.saveAndRefresh(toEntity(dto))
        return mapper.toResponse(project)
    }

    override fun toEntity(dto: ProjectCreateDTO): Project =
        mapper.toEntity(dto, tenant = tenantService.getTenant(dto.tenantId))

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
    fun getAllByTenantId(id: UUID): List<ProjectResponseDTO> =
        mapper.toListResponse(repository.findAllByTenantIdAndDeletedFalse(id))

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

}

@Service
class BoardService(
    repo: BoardRepo,
    mapper: BoardMapper,
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
        return toEntity(dto)
            .apply { assignDefaultStates() }
            .let { repository.save(it) }
            .let { mapper.toResponse(it) }
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
        val board = repository.findByIdAndDeletedFalse(id)
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
        mapper.toListResponse(repository.findAllByProjectIdAndDeletedFalse(projectId))

    private fun checkNameUniqueness(name: String, projectId: UUID, excludeId: UUID? = null) {
        val exists = if (excludeId == null) {
            repository.existsByNameAndProjectIdAndDeletedFalse(name, projectId)
        } else repository.existsByNameAndProjectIdAndIdNotAndDeletedFalse(name, projectId, excludeId)

        if (exists) throw BoardAlreadyExistsException("Board with name '$name' already exists")
    }
}




@Service
class FileService(
    private val fileRepo: FileRepo,
    @Value("\${file.upload-dir:uploads}") private val uploadDir: String,
    @Value("\${file.max-size:10485760}") private val maxSize: Long
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_RETRIES = 10
    }

    init {
        val path = basePath()
        if (!Files.exists(path)) {
            Files.createDirectories(path)
            log.info("Upload directory created: {}", path.toAbsolutePath())
        }
    }

    @Transactional
    fun upload(file: MultipartFile): File {

        if (file.isEmpty) throw FileEmptyException("File is empty")
        if (file.size > maxSize)
            throw FileTooLargeException("File size exceeds allowed limit")

        val originalName = file.originalFilename ?: "unnamed"
        val type = resolveFileType(file.contentType)
        val keyName = generateUniqueKey()

        val targetPath = try {
            saveToDisk(file, keyName)
        } catch (ex: Exception) {
            throw FileUploadFailedException("Disk write failed: ${ex.message}")
        }

        return try {
            fileRepo.save(
                File(
                    type = type,
                    orgName = originalName,
                    keyName = keyName,
                    path = targetPath.toString(),
                    size = file.size.toInt()
                )
            )
        } catch (ex: Exception) {
            Files.deleteIfExists(targetPath)
            throw FileUploadFailedException("Database save failed: ${ex.message}")
        }
    }

    fun download(keyName: String): Resource {
        val file = getByKey(keyName)
        val path = Paths.get(file.path)

        val resource = UrlResource(path.toUri())
        if (!resource.exists() || !resource.isReadable) {
            throw FileNotFoundException("File not found or unreadable: $keyName")
        }
        return resource
    }

    fun getByKey(keyName: String): File =
        fileRepo.findByKeyName(keyName)
            ?: throw FileNotFoundException("File not found: $keyName")

    @Transactional(readOnly = true)
    fun getAllByIds(ids: List<UUID>): List<File> {
        if (ids.isEmpty()) return emptyList()

        val files = fileRepo.findAllById(ids)
        if (files.size != ids.size) {
            val missing = ids.toSet() - files.map { it.id }.toSet()
            throw FileNotFoundException("Files not found for: $missing")
        }
        return files
    }

    @Transactional
    fun delete(id: UUID) {
        val file = fileRepo.findById(id)
            .orElseThrow { FileNotFoundException("File not found: $id") }

        fileRepo.delete(file)

        try {
            Files.deleteIfExists(Paths.get(file.path))
        } catch (ex: Exception) {
            log.error("Disk delete error for file {}: {}", id, ex.message)
        }
    }

    @Transactional
    fun deleteByKeyName(keyName: String) {
        val file = fileRepo.findByKeyName(keyName)
            ?: throw FileNotFoundException("File not found: $keyName")

        fileRepo.delete(file)

        try {
            Files.deleteIfExists(Paths.get(file.path))
        } catch (ex: Exception) {
            log.error("Disk delete error for key {}: {}", keyName, ex.message)
        }
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
            if (!fileRepo.existsByKeyName(key)) return key
        }
        throw FileKeyGenerationException("Max key generation attempts exceeded")
    }

    private fun resolveFileType(contentType: String?): FileType =
        when {
            contentType == null -> throw InvalidFileTypeException()
            contentType.startsWith("image") -> FileType.IMAGE
            contentType.startsWith("video") -> FileType.VIDEO
            else -> FileType.DOCUMENT
        }
}



@Service
class TaskService(
    repo: TaskRepo,
    mapper: TaskMapper,
    private val boardService: BoardService,
    private val employeeService: EmployeeService,
    private val taskStateService: TaskStateService,
    private val fileService: FileService,
    private val tenantSecurityService: TenantSecurityService
) : BaseServiceImpl<
        Task,
        TaskCreateDTO,
        TaskUpdateDTO,
        TaskResponseDTO,
        TaskMapper,
        TaskRepo
        >(repo, mapper) {

    @Transactional
    override fun create(dto: TaskCreateDTO): TaskResponseDTO {
        val entity = toEntity(dto)
        return mapper.toResponse(repository.save(entity))
    }

    override fun toEntity(dto: TaskCreateDTO): Task {
        val board = boardService.getBoard(dto.boardId)
        val defaultState = taskStateService.getByCode(board.id!!, "NEW")
        val ownerId = TenantContext.getEmployeeIdOrThrow()
        val owner = employeeService.getEmployee(ownerId)

        val files = dto.fileIds
            .let { fileService.getAllByIds(it.toList()) }

        return Task(
            title = dto.title,
            description = dto.description,
            priority = dto.priority,
            dueDate = dto.dueDate,
            state = defaultState,
            board = board,
            owner = owner,
            files = files.toMutableSet()
        )
    }

    @Transactional
    override fun update(id: UUID, dto: TaskUpdateDTO): TaskResponseDTO {
        val entity = getByIdOrThrow(id)
        val updated = updateEntity(dto, entity)
        return mapper.toResponse(repository.save(updated))
    }

    override fun updateEntity(dto: TaskUpdateDTO, entity: Task): Task {

        dto.title?.let { entity.title = it }
        dto.description?.let { entity.description = it }
        dto.priority?.let { entity.priority = it }
        dto.dueDate?.let { entity.dueDate = it }

        dto.stateId?.let {
            val newState = taskStateService.getByIdOrThrow(it)
            if (newState.board.id != entity.board.id)
                throw TaskStateMismatchException("State does not belong to this task's board")
            entity.state = newState
        }

        dto.boardId?.let { newBoardId ->
            val newBoard = boardService.getBoard(newBoardId)

            if (newBoard.id != entity.board.id) {
                val defaultState = taskStateService.getByCode(newBoard.id!!, "NEW")
                entity.board = newBoard
                entity.state = defaultState
            }
        }

        dto.fileIds?.let {
            val files = fileService.getAllByIds(it.toList())
            entity.files = files.toMutableSet()
        }
        return entity
    }

    override fun getByIdOrThrow(id: UUID): Task {
        val task = repository.findByIdAndDeletedFalse(id)
            ?: throw TaskNotFoundException("Task not found with ID: $id")

        tenantSecurityService.validateEntityTenantAccess(
            task.board.project.tenant.id,
            "Task"
        )

        return task
    }

    // Employee biriktirilayotganda tenant tekshiruvi employeeService.getEmployee() ichida bajariladi
    // ya'ni employee joriy tenantga tegishli ekanligini getByIdOrThrow() kafolatlaydi
    @Transactional
    fun assignEmployee(taskId: UUID, employeeId: UUID): TaskResponseDTO {
        val task = getByIdOrThrow(taskId)
        // Employee'ni context orqali tenant tekshiruvidan o'tkazamiz
        val employee = employeeService.getEmployee(employeeId)

        if (task.assignees.any { it.id == employee.id }) {
            throw BadRequestException("Employee is already assigned to this task")
        }

        task.assignees.add(employee)

        return mapper.toResponse(repository.saveAndRefresh(task))
    }

    @Transactional
    fun unassignEmployee(taskId: UUID, employeeId: UUID): TaskResponseDTO {
        val task = getByIdOrThrow(taskId)

        val removed = task.assignees.removeIf { it.id == employeeId }
        if (!removed) throw EmployeeNotFoundException("Employee not assigned to task")

        return mapper.toResponse(repository.saveAndRefresh(task))
    }

    @Transactional
    fun changeState(taskId: UUID, newStateCode: String): TaskResponseDTO {
        val task = getByIdOrThrow(taskId)
        val boardId = task.board.id!!

        val newState = taskStateService.getByCode(boardId, newStateCode)

        task.state = newState

        return mapper.toResponse(repository.saveAndRefresh(task))
    }

    @Transactional(readOnly = true)
    fun getByBoardId(boardId: UUID): List<TaskResponseDTO> {
        val tasks = repository.findAllByBoardIdAndDeletedFalse(boardId)
        return mapper.toListResponse(tasks)
    }
}


@Service
class TenantSecurityService {

    private val logger = LoggerFactory.getLogger(TenantSecurityService::class.java)

    fun validateTenantAccess(requiredTenantId: UUID?) {
        if (requiredTenantId == null) {
            return // No tenant requirement
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
