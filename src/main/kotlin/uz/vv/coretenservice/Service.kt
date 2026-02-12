package uz.vv.coretenservice

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RoleService(private val roleRepo: RoleRepo) {

    @Transactional
    fun createIfNotExist(name: String, code: String, permissions: Set<Permission>): Role =
        roleRepo.findByCode(code) ?: roleRepo.saveAndRefresh(
            Role(name = name, code = code, permissions = permissions.toMutableSet())
        )

    fun getByCode(code: String): Role =
        roleRepo.findByCode(code)
            ?: throw RoleNotFoundException("Role not found with code: $code")
}

@Service
class PermissionService(private val permissionRepo: PermissionRepo) {

    fun getByCode(code: String): Permission? = permissionRepo.findByCode(code)

    @Transactional
    fun createIfNotExist(name: String, code: String): Permission =
        getByCode(code) ?: permissionRepo.saveAndRefresh(
            Permission(name = name, code = code)
        )
}


interface BaseService<CreateDto, UpdateDto, ResponseDto> {
    fun create(dto: CreateDto): ResponseDto
    fun update(id: UUID, dto: UpdateDto): ResponseDto
    fun getById(id: UUID): ResponseDto
    fun getAllList(): List<ResponseDto>
    fun getAll(pageable: Pageable): Page<ResponseDto>
    fun delete(id: UUID): Boolean
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
        mapper.toResponse(getByIdOrThrow(id))

    @Transactional(readOnly = true)
    override fun getAllList(): List<ResponseDto> =
        repository.findAllNotDeleted().map(mapper::toResponse)

    @Transactional(readOnly = true)
    override fun getAll(pageable: Pageable): Page<ResponseDto> =
        repository.findAllNotDeleted(pageable).map { mapper.toResponse(it) }

    @Transactional
    override fun delete(id: UUID): Boolean {
        getByIdOrThrow(id)
        return repository.trash(id)
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

    override fun create(dto: UserCreateDTO): UserResponse {
        if (repository.existsByPhoneNum(dto.phoneNum)) {
            throw DuplicateResourceException("Phone number already exists")
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
        val saved = repository.save(user)
        return mapper.toResponse(saved)
    }

    override fun updateEntity(dto: UserUpdate, entity: User): User =
        entity.apply {
            dto.firstName?.let { firstName = it }
            dto.lastName?.let { lastName = it }
        }

    fun updateSecurity(id: UUID, dto: UserUpdateSecurity): UserResponse {
        val user = updateSecurityEntity(dto, getByIdOrThrow(id))
        val saved = repository.save(user)
        return mapper.toResponse(saved)
    }

    private fun updateSecurityEntity(
        dto: UserUpdateSecurity,
        entity: User
    ): User = entity.apply {

        dto.phoneNum?.let { phoneNum = it }

        dto.newPassword?.let { newPass ->

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

        val saved = repository.save(updated)
        return mapper.toResponse(saved)
    }

    override fun updateEntity(dto: TenantUpdateDTO, entity: Tenant): Tenant =
        entity.apply {
            dto.name?.let { name = it }
            dto.address?.let { address = it }
            dto.tagline?.let { tagline = it }
            dto.subscriptionPlan?.let { subscriptionPlan = it }
        }

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
        val activeUsers = employeeService.countEmpTenantId(tenant.id!!)
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

    override fun getByIdOrThrow(id: UUID): Employee =
        repository.findByIdAndDeletedFalse(id)
            ?: throw EmployeeNotFoundException("Employee not found with id: $id")

    @Transactional(readOnly = true)
    fun countEmpTenantId(tenantId: UUID): Int =
        repository.countByTenantsIdAndDeletedFalse(tenantId)

    @Transactional
    fun changePosition(id: UUID, dto: ChangePositionDTO): EmployeeResponseDTO {
        val employee = getByIdOrThrow(id)
        employee.position = dto.position
        return mapper.toResponse(repository.save(employee))
    }
}

@Service
class TaskStateService(private val taskStateRepo: TaskStateRepo) {

    @Transactional
    fun create(board: Board, dto: TaskStateCreate): TaskStateDto {

        getByCode(board.id!!, dto.code)?.let {
            throw TaskStateNotFoundException("State with code ${dto.code} already exists in this board")
        }

        val state = TaskState(
            code = dto.code,
            name = dto.name,
            board = board
        )

        val saved = taskStateRepo.saveAndRefresh(state)
        return saved.toResponse()
    }

    @Transactional
    fun createDefaultStates(board: Board) {
        DefaultTaskStates.DEFAULT_STATES.forEach { dto ->
            createIfNotExist(board, dto.name, dto.code)
        }
    }

    fun getByCode(boardId: UUID, code: String): TaskState? =
        taskStateRepo.findByBoardIdAndCode(boardId, code)

    private fun createIfNotExist(board: Board, name: String, code: String): TaskState =
        getByCode(board.id!!, code) ?: taskStateRepo.saveAndRefresh(
            TaskState(
                name = name,
                code = code,
                board = board
            )
        )
}





