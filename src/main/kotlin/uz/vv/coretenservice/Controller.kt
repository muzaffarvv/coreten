package uz.vv.coretenservice

import jakarta.validation.Valid
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
) {

    @PostMapping("/register")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'SUPER_ADMIN')")
    fun register(@Valid @RequestBody dto: UserCreateDTO): ResponseEntity<ResponseVO<AuthResponse>> {
        val authResponse = authService.register(dto)
        return created(authResponse, "/api/v1/auth/register")
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<ResponseVO<AuthResponse>> {
        val authResponse = authService.login(request)
        return ok(authResponse, "/api/v1/auth/login")
    }

    @PostMapping("/refresh")
    fun refreshToken(@Valid @RequestBody request: RefreshTokenRequest): ResponseEntity<ResponseVO<AuthResponse>> {
        val authResponse = authService.refreshToken(request)
        return ok(authResponse, "/api/v1/auth/refresh")
    }

    @PostMapping("/switch-tenant")
    fun switchTenant(@Valid @RequestBody request: SwitchTenantRequest): ResponseEntity<ResponseVO<AuthResponse>> {
        val currentUserId = TenantContext.getUserIdOrThrow()
        val authResponse = authService.switchTenant(request, currentUserId)
        return ok(authResponse, "/api/v1/auth/switch-tenant")
    }

    @GetMapping("/me")
    fun getCurrent(): ResponseEntity<ResponseVO<UserInfo>> =
        ok(authService.getCurrent(), "/api/v1/auth/me")
}


@RestController
@RequestMapping("/users")
class UserController(private val userService: UserService) {

    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    fun create(@Valid @RequestBody dto: UserCreateDTO): ResponseEntity<ResponseVO<UserResponse>> =
        created(userService.create(dto), "/api/v1/users")

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_USER')")
    fun getById(@PathVariable id: UUID): ResponseEntity<ResponseVO<UserResponse>> =
        ok(userService.getById(id), "/api/v1/users/$id")

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody dto: UserUpdate
    ): ResponseEntity<ResponseVO<UserResponse>> =
        ok(userService.update(id, dto), "/api/v1/users/$id")

    @PutMapping("/{id}/security")
    fun updateSecurity(
        @PathVariable id: UUID,
        @Valid @RequestBody dto: UserUpdateSecurity
    ): ResponseEntity<ResponseVO<UserResponse>> =
        ok(userService.updateSecurity(id, dto), "/api/v1/users/$id/security")

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    fun updateRole(
        @PathVariable id: UUID,
        @RequestBody dto: UserUpdateRoleDTO
    ): UserResponse = userService.updateUserRole(id, dto)

    @GetMapping("/{phoneNum}/by-phone")
    @PreAuthorize("hasRole('PLATFORM_USER')")
    fun getByPhone(@PathVariable phoneNum: String): ResponseEntity<ResponseVO<UserResponse>> =
        ok(userService.getByPhoneNum(phoneNum), "/api/v1/users/$phoneNum/by-phone")

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_USER')")
    fun getAll(): ResponseEntity<ResponseVO<List<UserResponse>>> = ok(userService.getAllList(), "/api/v1/users")

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        userService.delete(id)
        return noContent()
    }
}


@RestController
@RequestMapping("/tenants")
class TenantController(private val tenantService: TenantService) {

    @PostMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'SUPER_ADMIN')")
    fun create(@Valid @RequestBody dto: TenantCreateDTO): ResponseEntity<ResponseVO<TenantResponseDTO>> =
        created(tenantService.create(dto), "/api/v1/tenants")

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'SUPER_ADMIN')")
    fun getById(@PathVariable id: UUID): ResponseEntity<ResponseVO<TenantResponseDTO>> =
        ok(tenantService.getById(id), "/api/v1/tenants/$id")

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'SUPER_ADMIN')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody dto: TenantUpdateDTO
    ): ResponseEntity<ResponseVO<TenantResponseDTO>> =
        ok(tenantService.update(id, dto), "/api/v1/tenants/$id")

    @PatchMapping("/{id}/plan")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'SUPER_ADMIN')")
    fun updatePlan(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ChangePlanRequest
    ): ResponseEntity<ResponseVO<TenantResponseDTO>> = ok(
        tenantService.changeSubscriptionPlan(id, request.newPlan),
        "/api/v1/tenants/$id/plan"
    )

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        tenantService.delete(id)
        return noContent()
    }
}


@RestController
@RequestMapping("/employees")
class EmployeeController(private val employeeService: EmployeeService) {

    @PostMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'SUPER_ADMIN')")
    fun create(@Valid @RequestBody dto: EmployeeCreateDTO): ResponseEntity<ResponseVO<EmployeeResponseDTO>> =
        created(employeeService.create(dto), "/api/v1/employees")

    @GetMapping("/{id}")
    @PreAuthorize("@tenantAuth.isAtLeast('MANAGER')")
    fun getById(@PathVariable id: UUID): ResponseEntity<ResponseVO<EmployeeResponseDTO>> =
        ok(employeeService.getById(id), "/api/v1/employees/$id")

    @PutMapping("/{id}")
    @PreAuthorize("@tenantAuth.isAtLeast('ADMIN')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody dto: EmployeeUpdateDTO
    ): ResponseEntity<ResponseVO<EmployeeResponseDTO>> =
        ok(employeeService.update(id, dto), "/api/v1/employees/$id")

    @GetMapping("/tenant/{tenantId}")
    @PreAuthorize("@tenantAuth.isAtLeast('MANAGER')")
    fun getByTenant(@PathVariable tenantId: UUID): ResponseEntity<ResponseVO<List<EmployeeResponseDTO>>> =
        ok(employeeService.getAllByTenantId(tenantId), "/api/v1/employees/tenant/$tenantId")

    @GetMapping("/{id}/position")
    @PreAuthorize("@tenantAuth.isAtLeast('ADMIN')")
    fun getPosition(@PathVariable id: UUID): ResponseEntity<ResponseVO<Position>> =
        ok(employeeService.getPosition(id), "/api/v1/employees/$id/position")

    @PatchMapping("/{id}/position")
    @PreAuthorize("@tenantAuth.isAtLeast('ADMIN')")
    fun changePosition(
        @PathVariable id: UUID,
        @Valid @RequestBody dto: ChangePositionDTO
    ): ResponseEntity<ResponseVO<EmployeeResponseDTO>> =
        ok(employeeService.changePosition(id, dto), "/api/v1/employees/$id/position")

    @PreAuthorize("@tenantAuth.isAtLeast('ADMIN')")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        employeeService.delete(id)
        return noContent()
    }
}


@RestController
@RequestMapping("/projects")
class ProjectController(private val projectService: ProjectService) {

    @PostMapping
    @PreAuthorize("@tenantAuth.isAtLeast('ADMIN')")
    fun create(@Valid @RequestBody dto: ProjectCreateDTO): ResponseEntity<ResponseVO<ProjectResponseDTO>> =
        created(projectService.create(dto), "/api/v1/projects")

    @GetMapping("/{id}")
    @PreAuthorize("@tenantAuth.isAtLeast('EMPLOYEE')")
    fun getById(@PathVariable id: UUID): ResponseEntity<ResponseVO<ProjectResponseDTO>> =
        ok(projectService.getById(id), "/api/v1/projects/$id")

    @GetMapping("/tenant/{tenantId}")
    @PreAuthorize("@tenantAuth.isAtLeast('EMPLOYEE')")
    fun getByTenant(@PathVariable tenantId: UUID): ResponseEntity<ResponseVO<List<ProjectResponseDTO>>> =
        ok(projectService.getAllByTenantId(tenantId), "/api/v1/projects/tenant/$tenantId")

    @PutMapping("/{id}")
    @PreAuthorize("@tenantAuth.isAtLeast('ADMIN')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody dto: ProjectUpdateDTO
    ): ResponseEntity<ResponseVO<ProjectResponseDTO>> =
        ok(projectService.update(id, dto), "/api/v1/projects/$id")

    @DeleteMapping("/{id}")
    @PreAuthorize("@tenantAuth.isAtLeast('ADMIN')")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        projectService.delete(id)
        return noContent()
    }
}


@RestController
@RequestMapping("/boards")
class BoardController(private val boardService: BoardService) {

    @PostMapping
    @PreAuthorize("@tenantAuth.isAtLeast('MANAGER')")
    fun create(@Valid @RequestBody dto: BoardCreateDTO): ResponseEntity<ResponseVO<BoardResponseDTO>> =
        created(boardService.create(dto), "/api/v1/boards")

    @GetMapping("/{id}")
    @PreAuthorize("@tenantAuth.isAtLeast('EMPLOYEE')")
    fun getById(@PathVariable id: UUID): ResponseEntity<ResponseVO<BoardResponseDTO>> =
        ok(boardService.getById(id), "/api/v1/boards/$id")

    @GetMapping("/project/{projectId}")
    @PreAuthorize("@tenantAuth.isAtLeast('EMPLOYEE')")
    fun getByProject(@PathVariable projectId: UUID): ResponseEntity<ResponseVO<List<BoardResponseDTO>>> =
        ok(boardService.getAllByProject(projectId), "/api/v1/boards/project/$projectId")

    @PutMapping("/{id}")
    @PreAuthorize("@tenantAuth.isAtLeast('MANAGER')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody dto: BoardUpdateDTO
    ): ResponseEntity<ResponseVO<BoardResponseDTO>> =
        ok(boardService.update(id, dto), "/api/v1/boards/$id")

    @DeleteMapping("/{id}")
    @PreAuthorize("@tenantAuth.isAtLeast('MANAGER')")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        boardService.delete(id)
        return noContent()
    }
}


@RestController
@RequestMapping("/tasks")
class TaskController(
    private val taskService: TaskService,
    private val taskActionService: TaskActionService
) {

    @PostMapping
    @PreAuthorize("@tenantAuth.isAtLeast('TEAM_LEAD')")
    fun create(@Valid @RequestBody dto: TaskCreateDTO): ResponseEntity<ResponseVO<TaskResponseDTO>> =
        created(taskService.create(dto), "/api/v1/tasks")

    @PostMapping("/with-files")
    @PreAuthorize("@tenantAuth.isAtLeast('TEAM_LEAD')")
    fun createWithFiles(
        @Valid @RequestBody dto: TaskCreateWithFilesDTO
    ): ResponseEntity<ResponseVO<TaskResponseDTO>> =
        created(taskService.createWithFiles(dto), "/api/v1/tasks/with-files")

    @GetMapping("/{id}")
    @PreAuthorize("@tenantAuth.isAtLeast('EMPLOYEE')")
    fun getById(@PathVariable id: UUID): ResponseEntity<ResponseVO<TaskResponseDTO>> =
        ok(taskService.getById(id), "/api/v1/tasks/$id")

    @PutMapping("/{id}")
    @PreAuthorize("@tenantAuth.isAtLeast('TEAM_LEAD')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody dto: TaskUpdateDTO
    ): ResponseEntity<ResponseVO<TaskResponseDTO>> =
        ok(taskService.update(id, dto), "/api/v1/tasks/$id")

    @PutMapping("/{id}/files")
    @PreAuthorize("@tenantAuth.isAtLeast('TEAM_LEAD')")
    fun updateWithFiles(
        @PathVariable id: UUID,
        @Valid @RequestBody dto: TaskUpdateDTO,
        @RequestParam(required = false) fileKeys: List<String>?
    ): ResponseEntity<ResponseVO<TaskResponseDTO>> =
        ok(taskService.updateWithFiles(id, dto, fileKeys), "/api/v1/tasks/$id/files")

    @PatchMapping("/{id}/change-state")
    @PreAuthorize("@tenantAuth.isAtLeast('TEAM_LEAD')")
    fun changeState(
        @PathVariable id: UUID,
        @RequestParam code: String
    ): ResponseEntity<ResponseVO<TaskResponseDTO>> =
        ok(taskService.changeState(id, code), "/api/v1/tasks/$id/change-state")

    @PostMapping("/assignees")
    @PreAuthorize("@tenantAuth.isAtLeast('TEAM_LEAD')")
    fun assignEmployee(
        @RequestParam taskId: UUID,
        @RequestParam employeeId: UUID
    ): ResponseEntity<ResponseVO<TaskResponseDTO>> =
        ok(taskService.assignEmployee(taskId, employeeId), "/api/v1/tasks/assignees")

    @DeleteMapping("/assignees")
    @PreAuthorize("@tenantAuth.isAtLeast('TEAM_LEAD')")
    fun unassignEmployee(
        @RequestParam taskId: UUID,
        @RequestParam employeeId: UUID
    ): ResponseEntity<ResponseVO<TaskResponseDTO>> =
        ok(taskService.unassignEmployee(taskId, employeeId), "/api/v1/tasks/assignees")

    @GetMapping("/board/{boardId}")
    @PreAuthorize("@tenantAuth.isAtLeast('EMPLOYEE')")
    fun getByBoard(@PathVariable boardId: UUID): ResponseEntity<ResponseVO<List<TaskResponseDTO>>> =
        ok(taskService.getByBoardId(boardId), "/api/v1/tasks/board/$boardId")

    @GetMapping("/state/{stateId}")
    @PreAuthorize("@tenantAuth.isAtLeast('EMPLOYEE')")
    fun getByState(@PathVariable stateId: UUID): ResponseEntity<ResponseVO<List<TaskResponseDTO>>> =
        ok(taskService.getByState(stateId), "/api/v1/tasks/state/$stateId")

    @GetMapping("/my")
    @PreAuthorize("@tenantAuth.isAtLeast('EMPLOYEE')")
    fun getMyTasks(): ResponseEntity<ResponseVO<List<TaskResponseDTO>>> =
        ok(taskService.getMyTasks(), "/api/v1/tasks/my-tasks")

    @GetMapping("/actions/{taskId}")
    fun getActionsByTaskId(@PathVariable taskId: UUID): ResponseEntity<List<TaskActionResponse>> {
        val actions = taskActionService.getAllByTaskId(taskId)
        return ResponseEntity.ok(actions)
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@tenantAuth.isAtLeast('TEAM_LEAD')")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        taskService.delete(id)
        return noContent()
    }
}


@RestController
@RequestMapping("/task-states")
class TaskStateController(private val taskStateService: TaskStateService) {

    @PostMapping("/board/{boardId}")
    @PreAuthorize("@tenantAuth.isAtLeast('MANAGER')")
    fun create(
        @PathVariable boardId: UUID,
        @Valid @RequestBody dto: TaskStateCreate
    ): ResponseEntity<ResponseVO<TaskStateDto>> =
        created(taskStateService.create(boardId, dto), "/api/v1/task-states/board/$boardId")

    @GetMapping("/board/{boardId}")
    @PreAuthorize("@tenantAuth.isAtLeast('EMPLOYEE')")
    fun getAllByBoard(@PathVariable boardId: UUID): ResponseEntity<ResponseVO<List<TaskStateDto>>> =
        ok(taskStateService.getAllByBoardId(boardId), "/api/v1/task-states/board/$boardId")

    @PostMapping("/{id}/copy-to/{toBoardId}")
    @PreAuthorize("@tenantAuth.isAtLeast('MANAGER')")
    fun copyState(
        @PathVariable id: UUID,
        @PathVariable toBoardId: UUID
    ): ResponseEntity<ResponseVO<TaskStateDto>> =
        ok(taskStateService.copyState(id, toBoardId), "/api/v1/task-states/$id/copy-to/$toBoardId")

    @PutMapping("/{id}")
    @PreAuthorize("@tenantAuth.isAtLeast('MANAGER')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody dto: TaskStateUpdate
    ): ResponseEntity<ResponseVO<TaskStateDto>> =
        ok(taskStateService.update(id, dto), "/api/v1/task-states/$id")

    @DeleteMapping("/{id}")
    @PreAuthorize("@tenantAuth.isAtLeast('MANAGER')")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        taskStateService.delete(id)
        return noContent()
    }
}


@RestController
@RequestMapping("/files")
class FileController(private val fileService: FileService) {

    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @PreAuthorize("@tenantAuth.isAtLeast('TEAM_LEAD')")
    fun upload(@RequestParam("file") file: MultipartFile): ResponseEntity<ResponseVO<FileDto>> =
        created(fileService.upload(file), "/api/v1/files/upload")

    @GetMapping("/download/{keyName}")
    @PreAuthorize("@tenantAuth.isAtLeast('EMPLOYEE')")
    fun download(@PathVariable keyName: String): ResponseEntity<Resource> {
        val resource = fileService.download(keyName)
        val file = fileService.getByKey(keyName)

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${file.orgName}\"")
            .body(resource)
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@tenantAuth.isAtLeast('TEAM_LEAD')")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        fileService.delete(id)
        return noContent()
    }

    @DeleteMapping("/key/{keyName}")
    @PreAuthorize("@tenantAuth.isAtLeast('TEAM_LEAD')")
    fun deleteByKey(@PathVariable keyName: String): ResponseEntity<Void> {
        fileService.deleteByKeyName(keyName)  // softly delete
        return noContent()
    }
}


private fun <T> created(data: T, source: String): ResponseEntity<ResponseVO<T>> =
    ResponseEntity.status(HttpStatus.CREATED).body(
        ResponseVO(
            status = HttpStatus.CREATED.value(),
            errors = null,
            timestamp = Instant.now(),
            data = data,
            source = source
        )
    )

private fun <T> ok(data: T, source: String): ResponseEntity<ResponseVO<T>> =
    ResponseEntity.ok(
        ResponseVO(
            status = HttpStatus.OK.value(),
            errors = null,
            timestamp = Instant.now(),
            data = data,
            source = source
        )
    )

private fun noContent(): ResponseEntity<Void> =
    ResponseEntity.noContent().build()
