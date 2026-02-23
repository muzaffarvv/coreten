package uz.vv.coretenservice

import jakarta.persistence.EntityManager
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
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
    fun trash(id: UUID)
    fun findAllNotDeleted(): List<T>
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
    override fun trash(id: UUID) {
        findById(id).map { entity ->
            entity.deleted = true
            save(entity)
        }
    }

    override fun findAllNotDeleted(): List<T> = findAll(notDeleted)

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
    fun existsByPhoneNumAndDeletedFalse(phoneNum: String): Boolean
    fun findByPhoneNumAndDeletedFalse(phoneNum: String): User?
}

@Repository
interface EmployeeRepo : BaseRepo<Employee> {
    fun countByTenantsIdAndActiveTrueAndDeletedFalse(tenantId: UUID): Int
    fun findByUserIdAndDeletedFalse(userId: UUID): Employee?
    @Query("""
        SELECT DISTINCT e FROM Employee e
        JOIN FETCH e.tenants t
        WHERE t.id = :tenantId AND e.active = true AND e.deleted = false
    """)
    fun findActiveByTenantIdWithTenants(@Param("tenantId") tenantId: UUID): List<Employee>
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

    @Transactional
    @Modifying
    @Query("UPDATE Board b SET b.deleted = true WHERE b.project.id = :projectId")
    fun softDeleteByProjectId(@Param("projectId") projectId: UUID)

    // Solution to problem N+1
    @Query(
        """
        SELECT b FROM Board b
        LEFT JOIN FETCH b.states
        WHERE b.project.id = :projectId
        AND b.deleted = false
        """
    )
    fun findAllByProjectIdAndDeletedFalseWithStates(@Param("projectId") projectId: UUID): List<Board>

    @Query(
        """
        SELECT b FROM Board b
        LEFT JOIN FETCH b.states
        WHERE b.id = :id
        AND b.deleted = false
        """
    )
    fun findByIdAndDeletedFalseWithStates(@Param("id") id: UUID): Board?
}

@Repository
interface TaskRepo : BaseRepo<Task> {
    @Query("""
    select distinct t from Task t
    join t.assignees a
    join t.board b
    join b.project p
    where a.id = :employeeId
      and p.tenant.id = :tenantId
      and t.deleted = false
""")
    fun findAllAssignedTasks(
        @Param("employeeId") employeeId: UUID,
        @Param("tenantId") tenantId: UUID
    ): List<Task>
    @Query("""
        SELECT DISTINCT t FROM Task t
        LEFT JOIN FETCH t.assignees
        LEFT JOIN FETCH t.files
        WHERE t.board.id = :boardId AND t.deleted = false
    """)
    fun findAllByBoardIdWithAssigneesAndFiles(@Param("boardId") boardId: UUID): List<Task>
    fun findAllByStateIdAndDeletedFalse(stateId: UUID): List<Task>
    fun countByStateIdAndDeletedFalse(stateId: UUID): Long
    @Transactional
    @Modifying
    @Query("UPDATE Task t SET t.deleted = true WHERE t.board.id = :boardId")
    fun softDeleteByBoardId(@Param("boardId") boardId: UUID)
}

@Repository
interface TaskStateRepo : BaseRepo<TaskState> {
    @Transactional
    @Modifying
    @Query("UPDATE TaskState s SET s.deleted = true WHERE s.board.id = :boardId")
    fun softDeleteByBoardId(@Param("boardId") boardId: UUID)
    fun existsByBoardIdAndCode(boardId: UUID, code: String): Boolean
    fun findByBoardIdAndCode(boardId: UUID, code: String): TaskState?
    fun findAllByBoardId(boardId: UUID): List<TaskState>
}

@Repository
interface FileRepo : BaseRepo<File> {
    fun findByKeyNameAndDeletedFalse(keyName: String): File?
    fun existsByKeyNameAndDeletedFalse(keyName: String): Boolean
    fun findAllByIdInAndDeletedFalse(ids: List<UUID>): List<File>
}

@Repository
interface RoleRepo : BaseRepo<Role> {
    fun findByCode(code: String): Role?
}

@Repository
interface PermissionRepo : BaseRepo<Permission> {
    fun findByCode(code: String): Permission?
}

