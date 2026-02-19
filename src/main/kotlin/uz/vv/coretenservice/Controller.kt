package uz.vv.coretenservice


import jakarta.validation.Valid
import org.springframework.core.io.Resource
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody dto: UserCreateDTO): ResponseEntity<ResponseVO<AuthResponse>> {
        val authResponse = authService.register(dto)
        return created(authResponse, "/auth/register")
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<ResponseVO<AuthResponse>> {
        val authResponse = authService.login(request)
        return ok(authResponse, "/auth/login")
    }

    @PostMapping("/refresh")
    fun refreshToken(@Valid @RequestBody request: RefreshTokenRequest): ResponseEntity<ResponseVO<AuthResponse>> {
        val authResponse = authService.refreshToken(request)
        return ok(authResponse, "/auth/refresh")
    }

    @PostMapping("/switch-tenant")
    fun switchTenant(@Valid @RequestBody request: SwitchTenantRequest): ResponseEntity<ResponseVO<AuthResponse>> {
        val currentUserId = TenantContext.getUserIdOrThrow()
        val authResponse = authService.switchTenant(request, currentUserId)
        return ok(authResponse, "/auth/switch-tenant")
    }

    @GetMapping("/me")
    fun getCurrentUser(): ResponseEntity<ResponseVO<CurrentUserResponse>> {
        val userId = TenantContext.getUserIdOrThrow()
        val tenantId = TenantContext.getTenantIdOrNull()
        val employeeId = TenantContext.getEmployeeIdOrThrow()

        val response = CurrentUserResponse(
            userId = userId,
            currentTenantId = tenantId,
            currentEmployeeId = employeeId
        )

        return ok(response, "/auth/me")
    }
}


@RestController
@RequestMapping("/api/v1/users")
class UserController(private val userService: UserService) {

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    fun create(@Valid @RequestBody dto: UserCreateDTO): ResponseEntity<ResponseVO<UserResponse>> =
        created(userService.create(dto), "/api/v1/users")

    @GetMapping("/{id}")
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

    @GetMapping("/by-phone")
    fun getByPhone(@RequestParam phoneNum: String): ResponseEntity<ResponseVO<UserResponse>> =
        ok(userService.getByPhoneNum(phoneNum), "/api/v1/users/by-phone")

    @GetMapping
    fun getAll(): ResponseEntity<ResponseVO<List<UserResponse>>> = ok(userService.getAllList(), "/api/v1/users")

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        userService.delete(id)
        return noContent()
    }
}


@RestController
@RequestMapping("/api/v1/tenants")
class TenantController(private val tenantService: TenantService) {

    @PostMapping
//    @PreAuthorize("hasRole('PLATFORM_ADMIN')") TODO ROLE CHECK FOR EMPLOYEE
    fun create(@Valid @RequestBody dto: TenantCreateDTO): ResponseEntity<ResponseVO<TenantResponseDTO>> =
        created(tenantService.create(dto), "/api/v1/tenants")

    @GetMapping("/{id}")
//    @PreAuthorize("@tenantAuth.isAtLeast('EMPLOYEE')")
    fun getById(@PathVariable id: UUID): ResponseEntity<ResponseVO<TenantResponseDTO>> =
        ok(tenantService.getById(id), "/api/v1/tenants/$id")

    @PutMapping("/{id}")
//    @PreAuthorize("@tenantAuth.hasPosition('OWNER', 'ADMIN')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody dto: TenantUpdateDTO
    ): ResponseEntity<ResponseVO<TenantResponseDTO>> =
        ok(tenantService.update(id, dto), "/api/v1/tenants/$id")

    @DeleteMapping("/{id}")
//    @PreAuthorize("@tenantAuth.hasPosition('OWNER')")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        tenantService.delete(id)
        return noContent()
    }
}


@RestController
@RequestMapping("/api/v1/employees")
class EmployeeController(private val employeeService: EmployeeService) {

    @PostMapping
    fun create(@Valid @RequestBody dto: EmployeeCreateDTO): ResponseEntity<ResponseVO<EmployeeResponseDTO>> =
        created(employeeService.create(dto), "/api/v1/employees")

    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID): ResponseEntity<ResponseVO<EmployeeResponseDTO>> =
        ok(employeeService.getById(id), "/api/v1/employees/$id")

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody dto: EmployeeUpdateDTO
    ): ResponseEntity<ResponseVO<EmployeeResponseDTO>> =
        ok(employeeService.update(id, dto), "/api/v1/employees/$id")

    // Tenant bo'yicha employee'lar ro'yxati
    @GetMapping("/tenant/{tenantId}")
    fun getByTenant(@PathVariable tenantId: UUID): ResponseEntity<ResponseVO<List<EmployeeResponseDTO>>> =
        ok(employeeService.getAllByTenantId(tenantId), "/api/v1/employees/tenant/$tenantId")

    // Employee pozitsiyasini olish — context orqali tenant tekshiruvi bajariladi
    @GetMapping("/{id}/position")
    fun getPosition(@PathVariable id: UUID): ResponseEntity<ResponseVO<Position>> =
        ok(employeeService.getPosition(id), "/api/v1/employees/$id/position")

    // Pozitsiyani o'zgartirish
    @PatchMapping("/{id}/position")
    fun changePosition(
        @PathVariable id: UUID,
        @Valid @RequestBody dto: ChangePositionDTO
    ): ResponseEntity<ResponseVO<EmployeeResponseDTO>> =
        ok(employeeService.changePosition(id, dto), "/api/v1/employees/$id/position")

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        employeeService.delete(id)
        return noContent()
    }
}


@RestController
@RequestMapping("/api/v1/projects")
class ProjectController(private val projectService: ProjectService) {

    @PostMapping
    fun create(@Valid @RequestBody dto: ProjectCreateDTO): ResponseEntity<ResponseVO<ProjectResponseDTO>> =
        created(projectService.create(dto), "/api/v1/projects")

    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID): ResponseEntity<ResponseVO<ProjectResponseDTO>> =
        ok(projectService.getById(id), "/api/v1/projects/$id")

    @GetMapping("/tenant/{tenantId}")
    fun getByTenant(@PathVariable tenantId: UUID): ResponseEntity<ResponseVO<List<ProjectResponseDTO>>> =
        ok(projectService.getAllByTenantId(tenantId), "/api/v1/projects/tenant/$tenantId")

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody dto: ProjectUpdateDTO
    ): ResponseEntity<ResponseVO<ProjectResponseDTO>> =
        ok(projectService.update(id, dto), "/api/v1/projects/$id")

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        projectService.delete(id)
        return noContent()
    }
}


@RestController
@RequestMapping("/api/v1/boards")
class BoardController(private val boardService: BoardService) {

    @PostMapping
    fun create(@Valid @RequestBody dto: BoardCreateDTO): ResponseEntity<ResponseVO<BoardResponseDTO>> =
        created(boardService.create(dto), "/api/v1/boards")

    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID): ResponseEntity<ResponseVO<BoardResponseDTO>> =
        ok(boardService.getById(id), "/api/v1/boards/$id")

    @GetMapping("/project/{projectId}")
    fun getByProject(@PathVariable projectId: UUID): ResponseEntity<ResponseVO<List<BoardResponseDTO>>> =
        ok(boardService.getAllByProject(projectId), "/api/v1/boards/project/$projectId")

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody dto: BoardUpdateDTO
    ): ResponseEntity<ResponseVO<BoardResponseDTO>> =
        ok(boardService.update(id, dto), "/api/v1/boards/$id")

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        boardService.delete(id)
        return noContent()
    }
}


@RestController
@RequestMapping("/api/v1/tasks")
class TaskController(private val taskService: TaskService) {

    @PostMapping
    fun create(@Valid @RequestBody dto: TaskCreateDTO): ResponseEntity<ResponseVO<TaskResponseDTO>> =
        created(taskService.create(dto), "/api/v1/tasks")

    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID): ResponseEntity<ResponseVO<TaskResponseDTO>> =
        ok(taskService.getById(id), "/api/v1/tasks/$id")

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody dto: TaskUpdateDTO
    ): ResponseEntity<ResponseVO<TaskResponseDTO>> =
        ok(taskService.update(id, dto), "/api/v1/tasks/$id")

    // Task holatini o'zgartirish
    @PatchMapping("/{id}/change-state")
    fun changeState(
        @PathVariable id: UUID,
        @RequestParam code: String
    ): ResponseEntity<ResponseVO<TaskResponseDTO>> =
        ok(taskService.changeState(id, code), "/api/v1/tasks/$id/change-state")

    // Employee biriktirish
    @PostMapping("/{taskId}/assignees/{employeeId}")
    fun assignEmployee(
        @PathVariable taskId: UUID,
        @PathVariable employeeId: UUID
    ): ResponseEntity<ResponseVO<TaskResponseDTO>> =
        ok(taskService.assignEmployee(taskId, employeeId), "/api/v1/tasks/$taskId/assignees/$employeeId")

    // Employee'ni olib tashlash
    @DeleteMapping("/{taskId}/assignees/{employeeId}")
    fun unassignEmployee(
        @PathVariable taskId: UUID,
        @PathVariable employeeId: UUID
    ): ResponseEntity<ResponseVO<TaskResponseDTO>> =
        ok(taskService.unassignEmployee(taskId, employeeId), "/api/v1/tasks/$taskId/assignees/$employeeId")

    @GetMapping("/board/{boardId}")
    fun getByBoard(@PathVariable boardId: UUID): ResponseEntity<ResponseVO<List<TaskResponseDTO>>> =
        ok(taskService.getByBoardId(boardId), "/api/v1/tasks/board/$boardId")

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        taskService.delete(id)
        return noContent()
    }
}


@RestController
@RequestMapping("/api/v1/task-states")
class TaskStateController(private val taskStateService: TaskStateService) {

    @PostMapping("/board/{boardId}")
    fun create(
        @PathVariable boardId: UUID,
        @Valid @RequestBody dto: TaskStateCreate
    ): ResponseEntity<ResponseVO<TaskStateDto>> =
        created(taskStateService.create(boardId, dto), "/api/v1/task-states/board/$boardId")

    @GetMapping("/board/{boardId}")
    fun getAllByBoard(@PathVariable boardId: UUID): ResponseEntity<ResponseVO<List<TaskState>>> =
        ok(taskStateService.getAllByBoardId(boardId), "/api/v1/task-states/board/$boardId")

    // TaskState'ni boshqa board'ga nusxalash
    @PostMapping("/{id}/copy-to/{toBoardId}")
    fun copyState(
        @PathVariable id: UUID,
        @PathVariable toBoardId: UUID
    ): ResponseEntity<ResponseVO<TaskStateDto>> =
        ok(taskStateService.copyState(id, toBoardId), "/api/v1/task-states/$id/copy-to/$toBoardId")

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody dto: TaskStateUpdate
    ): ResponseEntity<ResponseVO<TaskStateDto>> =
        ok(taskStateService.update(id, dto), "/api/v1/task-states/$id")

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        taskStateService.delete(id)
        return noContent()
    }
}


@RestController
@RequestMapping("/api/v1/files")
class FileController(private val fileService: FileService) {

    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(@RequestParam("file") file: MultipartFile): ResponseEntity<ResponseVO<File>> =
        created(fileService.upload(file), "/api/v1/files/upload")

    @GetMapping("/download/{keyName}")
    fun download(@PathVariable keyName: String): ResponseEntity<Resource> {
        val resource = fileService.download(keyName)
        val file = fileService.getByKey(keyName)

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${file.orgName}\"")
            .body(resource)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        fileService.delete(id)
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
