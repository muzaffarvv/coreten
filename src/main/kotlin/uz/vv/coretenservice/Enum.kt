package uz.vv.coretenservice

enum class TenantPlan {
    FREE,
    PRO,
    ENTERPRISE
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

    INVALID_PASSWORD(105),

    PASSWORDS_DO_NOT_MATCH(106),

    DUPLICATED_RECOURSE(101),

    BAD_REQUEST(400),

    UNAUTHORIZED(303)
}
