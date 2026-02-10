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

@Service
class TaskStateService(private val taskStateRepo: TaskStateRepo) {

    fun getByCode(code: String): TaskState? =
        taskStateRepo.findByCode(code)

    @Transactional
    fun createIfNotExist(name: String, code: String): TaskState =
        getByCode(code) ?: taskStateRepo.saveAndRefresh(
            TaskState(name = name, code = code)
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

    fun createUser(dto: UserCreateDTO): UserResponse {
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
        val user = repository.findByPhoneNumAndDeletedFalse(phoneNum) ?: throw UserNotFoundException("User not found with phone number: $phoneNum")
        return mapper.toResponse(user)
    }

    override fun getByIdOrThrow(id: UUID): User =
        repository.findByIdAndDeletedFalse(id) ?: throw UserNotFoundException("User not found with id: $id")

}


