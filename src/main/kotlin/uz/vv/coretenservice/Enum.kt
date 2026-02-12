package uz.vv.coretenservice

enum class TenantPlan(val maxUsers: Int) {
    FREE(5),
    BASIC(25),
    PRO(50),
    BUSINESS(150),
    ENTERPRISE(999)
}

enum class Position {
    OWNER,
    ADMIN,
    MANAGER,
    TEAM_LEAD,
    EMPLOYEE,
    INTERN
}


enum class TaskPriority {
    LOW,
    MEDIUM_LOW,
    MEDIUM,
    MEDIUM_HIGH,
    HIGH,
    CRITICAL
}
// LOW < MEDIUM_LOW < MEDIUM < MEDIUM_HIGH < HIGH < CRITICAL

enum class FileType {
    DOCUMENT,
    PHOTO,
    VIDEO
}

enum class ErrorCode(code: Int) {

    USER_NOT_FOUND(101),

    ROLE_NOT_FOUND(101),

    TENANT_NOT_FOUND(204),
    TENANT_ALREADY_EXISTS(205),
    TENANT_SUBSCRIPTION_LIMIT_EXCEEDED(206),

    EMPLOYEE_NOT_FOUND(301),

    PROJECT_NOT_FOUND(304),

    TASK_STATE_NOT_FOUND(250),

    INVALID_PASSWORD(105),

    PASSWORDS_DO_NOT_MATCH(106),

    DUPLICATED_RECOURSE(101),

    BAD_REQUEST(400),

    UNAUTHORIZED(303)
}
