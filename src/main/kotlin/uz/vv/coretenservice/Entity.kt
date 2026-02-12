package uz.vv.coretenservice

import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    var id: UUID? = null,

    @CreatedDate
    var createdAt: Instant? = null,

    @LastModifiedDate
    var updatedAt: Instant? = null,

    @CreatedBy
    var createdBy: UUID? = null,

    @LastModifiedBy
    var updatedBy: UUID? = null,

    @Column(nullable = false)
    @ColumnDefault(value = "false")
    var deleted: Boolean = false
)


@Entity
@Table(name = "users")
class User(

    @Column(nullable = false, length = 72)
    var firstName: String,

    @Column(nullable = false, length = 60)
    var lastName: String,

    @Column(nullable = false, unique = true, length = 32)
    var phoneNum: String,

    @Column(nullable = false)
    var password: String,

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_roles",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "role_id")]
    )
    var roles: MutableSet<Role> = mutableSetOf(),

    ) : BaseEntity()

@Entity
@Table(
    name = "employees",
    indexes = [Index(name = "idx_employee_user", columnList = "user_id")],
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["user_id"])
    ]
)
class Employee(

    @Column(nullable = false, unique = true, length = 20, updatable = false)
    var code: String,

    @Column(nullable = false)
    @ColumnDefault("true")
    var active: Boolean = true,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var position: Position,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "employee_tenant",
        joinColumns = [JoinColumn(name = "employee_id")],
        inverseJoinColumns = [JoinColumn(name = "tenant_id")]
    )
    var tenants: MutableSet<Tenant> = mutableSetOf()
) : BaseEntity()






@Entity
@Table(
    name = "tenants", indexes = [
        Index(name = "idx_tenant_name", columnList = "name"),
        Index(name = "idx_tenant_active", columnList = "active")
    ]
)
class Tenant(

    @Column(nullable = false, unique = true, length = 72)
    var name: String,

    @Column(length = 72)
    var address: String? = null,

    @Column
    var tagline: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var subscriptionPlan: TenantPlan,

    @Column(nullable = false)
    var active: Boolean = true,

    var maxUsers: Int? = null, // plan qarab userlar soni

) : BaseEntity()






@Entity
@Table(name = "projects")
class Project(

    @Column(nullable = false, length = 72)
    var name: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(nullable = false)
    var active: Boolean = true,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    var tenant: Tenant,

) : BaseEntity()






@Entity
@Table(name = "boards")
class Board(

    @Column(nullable = false, length = 72)
    var name: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    var project: Project,

    @Column(nullable = false)
    var active: Boolean = true,

    @OneToMany(mappedBy = "board", cascade = [CascadeType.ALL], orphanRemoval = true)
    var states: MutableList<TaskState> = mutableListOf()

) : BaseEntity()



@Entity
@Table(
    name = "task_states",
    uniqueConstraints = [UniqueConstraint(columnNames = ["board_id", "code"])]
)

class TaskState(

    @Column(nullable = false, length = 75)
    var code: String, // new, process, done

    @Column(nullable = false, length = 75)
    var name: String,

    @ManyToOne(optional = false)
    @JoinColumn(name = "board_id")
    var board: Board
) : BaseEntity()





@Entity
@Table(
    name = "tasks",
    indexes = [
        Index(name = "idx_task_tenant", columnList = "tenant_id"),
        Index(name = "idx_task_priority", columnList = "priority"),
        Index(name = "idx_task_due_date", columnList = "due_date"),
        Index(name = "idx_task_category", columnList = "category_id"),
        Index(name = "idx_task_state", columnList = "state_id")

    ]
)
class Task(

    @Column(nullable = false)
    var title: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    var description: String,

    @Version
    var version: Long? = null,

    var dueDate: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var priority: TaskPriority = TaskPriority.MEDIUM_LOW,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "state_id", nullable = false)
    var state: TaskState,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "board_id", nullable = false)
    var board: Board,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: Employee,

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "task_assignees",
        joinColumns = [JoinColumn(name = "task_id")],
        inverseJoinColumns = [JoinColumn(name = "employee_id")]
    )
    var assignees: MutableSet<Employee> = mutableSetOf(),

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "task_files",
        joinColumns = [JoinColumn(name = "task_id")],
        inverseJoinColumns = [JoinColumn(name = "file_id")]
    )
    var files: MutableSet<File> = mutableSetOf()

) : BaseEntity()




@Entity
@Table(name = "files")
class File(

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var type: FileType,

    @Column(name = "org_name", nullable = false)
    var orgName: String,

    @Column(name = "key_name", nullable = false, unique = true, length = 128)
    var keyName: String,

    @Column(nullable = false)
    var path: String,

    @Column(nullable = false)
    var size: Int

) : BaseEntity()






@Entity
@Table(name = "roles")
class Role(

    @Column(nullable = false, unique = true, length = 20)
    var code: String,

    @Column(nullable = false, length = 50)
    var name: String,

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "role_permissions",
        joinColumns = [JoinColumn(name = "role_id")],
        inverseJoinColumns = [JoinColumn(name = "permission_id")]
    )
    var permissions: MutableSet<Permission> = mutableSetOf()

) : BaseEntity()

@Entity
@Table(name = "permissions")
class Permission(

    @Column(nullable = false, unique = true, length = 75)
    var code: String,

    @Column(nullable = false, length = 75)
    var name: String,
) : BaseEntity()
