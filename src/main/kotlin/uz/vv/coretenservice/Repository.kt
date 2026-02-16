package uz.vv.coretenservice

import jakarta.persistence.EntityManager
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.support.JpaEntityInformation
import org.springframework.data.jpa.repository.support.SimpleJpaRepository
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@NoRepositoryBean
interface BaseRepo<T : BaseEntity> : JpaRepository<T, UUID>, JpaSpecificationExecutor<T> {
    fun findByIdAndDeletedFalse(id: UUID): T?
    fun trash(id: UUID): Boolean
    fun findAllNotDeleted(): List<T>
    fun findAllNotDeleted(pageable: Pageable): Page<T>
    fun saveAndRefresh(entity: T): T
}

class BaseRepoImpl<T : BaseEntity>(
    entityInformation: JpaEntityInformation<T, UUID>,
    private val entityManager: EntityManager
) : SimpleJpaRepository<T, UUID>(entityInformation, entityManager), BaseRepo<T> {

    private val notDeleted: Specification<T> = Specification { root, _, cb ->
        cb.equal(root.get<Boolean>("deleted"), false)
    }

    override fun findByIdAndDeletedFalse(id: UUID): T? =
        findOne(notDeleted.and { root, _, cb ->
            cb.equal(root.get<UUID>("id"), id)
        }).orElse(null)

    @Transactional
    override fun trash(id: UUID): Boolean = findById(id).map { entity ->
            entity.deleted = true
            save(entity)
            true
    }.orElse(false)

    override fun findAllNotDeleted(): List<T> = findAll(notDeleted)

    override fun findAllNotDeleted(pageable: Pageable): Page<T> = findAll(notDeleted, pageable)

    @Transactional
    override fun saveAndRefresh(entity: T): T {
        val saved = save(entity)
        entityManager.flush()
        entityManager.refresh(saved)
        return saved
    }
}

@Repository
interface UserRepo : BaseRepo<User> {
    fun findByPhoneNumAndDeletedFalse(phoneNum: String): User?
    fun existsByPhoneNum(phoneNum: String): Boolean
}

@Repository
interface EmployeeRepo : BaseRepo<Employee> {
    fun findByCodeAndDeletedFalse(code: String): Employee?
    fun countByTenantsIdAndDeletedFalse(tenantId: UUID): Int
    fun countByTenantsIdAndActiveTrueAndDeletedFalse(tenantId: UUID): Int
    // TODO employees by tenantId sql query
    @Query(""" 
           SELECT e FROM Employee e
           JOIN e.tenants t 
           WHERE t.id = :tenantId AND e.active = true
           """)
    fun findActiveByTenantId(@Param("tenantId") tenantId: UUID): List<Employee>

}

@Repository
interface TenantRepo : BaseRepo<Tenant> {
    fun existsByNameIgnoreCase(name: String): Boolean
    fun existsByNameIgnoreCaseAndIdNot(name: String, id: UUID): Boolean
}



@Repository
interface ProjectRepo : BaseRepo<Project> {

    fun existsByNameAndTenantIdAndDeletedFalse(name: String, tenantId: UUID): Boolean

    fun existsByNameAndTenantIdAndIdNotAndDeletedFalse(name: String, tenantId: UUID, id: UUID): Boolean

    fun findAllByTenantIdAndDeletedFalse(tenantId: UUID): List<Project>
}


@Repository
interface BoardRepo : BaseRepo<Board> {

    fun existsByNameAndProjectIdAndDeletedFalse(name: String, projectId: UUID): Boolean

    fun existsByNameAndProjectIdAndIdNotAndDeletedFalse(name: String, projectId: UUID, id: UUID): Boolean

    fun findAllByProjectIdAndDeletedFalse(projectId: UUID): List<Board>
}


@Repository
interface TaskRepo : BaseRepo<Task> {
    @Modifying
    @Query(value = "delete from task_files where file_id = :fileId", nativeQuery = true)
    fun deleteTaskFileRelations(@Param("fileId") fileId: UUID)
    fun findAllByBoardIdAndDeletedFalse(boardId: UUID): List<Task>
}




@Repository
interface FileRepo : BaseRepo<File> {
    fun findByKeyName(keyName: String): File?
    fun existsByKeyName(keyName: String): Boolean
}




@Repository
interface RoleRepo : BaseRepo<Role> {
    fun findByCode(code: String): Role?
}

@Repository
interface PermissionRepo : BaseRepo<Permission> {
    fun findByCode(code: String): Permission?
}

@Repository
interface TaskStateRepo : BaseRepo<TaskState> {
    fun existsByBoardIdAndCode(boardId: UUID, code: String): Boolean
    fun findByBoardIdAndCode(boardId: UUID, code: String): TaskState?
    fun findAllByBoardId(boardId: UUID): List<TaskState>
}

