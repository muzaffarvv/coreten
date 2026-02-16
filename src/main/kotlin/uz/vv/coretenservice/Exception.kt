package uz.vv.coretenservice

import jakarta.servlet.http.HttpServletRequest
import java.time.Instant
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

sealed class BaseException(
    val errorCode: ErrorCode,
    override val message: String? = null
) : RuntimeException(message)

class UserNotFoundException(msg: String? = null) : BaseException(ErrorCode.USER_NOT_FOUND, msg)
class RoleNotFoundException(msg: String? = null) : BaseException(ErrorCode.ROLE_NOT_FOUND, msg)

class TenantNotFoundException(msg: String? = null) : BaseException(ErrorCode.TENANT_NOT_FOUND, msg)
class TenantAlreadyExistsException(msg: String? = null) : BaseException(ErrorCode.TENANT_ALREADY_EXISTS, msg)
class TenantSubscriptionLimitExceededException(msg: String? = null) : BaseException(ErrorCode.TENANT_SUBSCRIPTION_LIMIT_EXCEEDED, msg)

class EmployeeNotFoundException(msg: String? = null) : BaseException(ErrorCode.EMPLOYEE_NOT_FOUND, msg)

class ProjectNotFoundException(msg: String? = null) : BaseException(ErrorCode.PROJECT_NOT_FOUND, msg)
class ProjectAlreadyExistsException(msg: String? = null) : BaseException(ErrorCode.PROJECT_ALREADY_EXISTS, msg)

class BoardNotFoundException(msg: String? = null) : BaseException(ErrorCode.BOARD_NOT_FOUND, msg)
class BoardAlreadyExistsException(msg: String? = null) : BaseException(ErrorCode.BOARD_ALREADY_EXISTS, msg)

class TaskStateNotFoundException(msg: String? = null) : BaseException(ErrorCode.TASK_STATE_NOT_FOUND, msg)
class TaskStateAlreadyExistsException(msg: String? = null) : BaseException(ErrorCode.TASK_STATE_ALREADY_EXISTS, msg)

class TaskNotFoundException(msg: String? = null) : BaseException(ErrorCode.TASK_NOT_FOUND, msg)

class FileNotFoundException(msg: String? = null) : BaseException(ErrorCode.FILE_NOT_FOUND, msg)
class FileEmptyException(msg: String? = null) : BaseException(ErrorCode.FILE_EMPTY, msg)
class FileTooLargeException(msg: String? = null) : BaseException(ErrorCode.FILE_TOO_LARGE, msg)
class InvalidFileTypeException(msg: String? = null) : BaseException(ErrorCode.INVALID_FILE_TYPE, msg)
class FileUploadFailedException(msg: String? = null) : BaseException(ErrorCode.FILE_UPLOAD_FAILED, msg)
class FileKeyGenerationException(msg: String? = null) : BaseException(ErrorCode.FILE_KEY_GENERATION_FAILED, msg)


class InvalidPasswordException(msg: String? = null) : BaseException(ErrorCode.INVALID_PASSWORD, msg)
class PasswordMismatchException(msg: String? = null) : BaseException(ErrorCode.PASSWORDS_DO_NOT_MATCH, msg)
class DuplicateResourceException(msg: String? = null) : BaseException(ErrorCode.DUPLICATED_RECOURSE, msg)
class UnauthorizedException(msg: String? = null) : BaseException(ErrorCode.UNAUTHORIZED, msg)
class BadCredentialsException(msg: String? = null) : BaseException(ErrorCode.BAD_REQUEST, msg)



data class ResponseVO<T> (
    val status: Int,
    val errors: Map<String, String>?,
    val timestamp: Instant,
    val data: T?,
    val source: String?
)

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BaseException::class)
    fun handleBaseException(ex: BaseException, request: HttpServletRequest): ResponseEntity<ResponseVO<Nothing>> {
        val status = getHttpStatus(ex.errorCode)
        val response = ResponseVO<Nothing>(
            status = status.value(),
            errors = mapOf("code" to ex.errorCode.name, "message" to (ex.message ?: "Error occurred")),
            timestamp = Instant.now(),
            data = null,
            source = request.requestURI
        )
        return ResponseEntity(response, status)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ResponseVO<Nothing>> {
        val errors = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "Invalid value") }

        val response = ResponseVO<Nothing>(
            status = HttpStatus.BAD_REQUEST.value(),
            errors = errors,
            timestamp = Instant.now(),
            data = null,
            source = request.requestURI
        )
        return ResponseEntity(response, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(Exception::class)
    fun handleAllExceptions(ex: Exception, request: HttpServletRequest): ResponseEntity<ResponseVO<Nothing>> {
        val response = ResponseVO<Nothing>(
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            errors = mapOf("error" to (ex.message ?: "Internal Server Error")),
            timestamp = Instant.now(),
            data = null,
            source = request.requestURI
        )
        return ResponseEntity(response, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    private fun getHttpStatus(errorCode: ErrorCode): HttpStatus {
        return when (errorCode) {
            ErrorCode.USER_NOT_FOUND,
            ErrorCode.ROLE_NOT_FOUND,
            ErrorCode.TENANT_NOT_FOUND -> HttpStatus.NOT_FOUND

            ErrorCode.INVALID_PASSWORD,
            ErrorCode.PASSWORDS_DO_NOT_MATCH -> HttpStatus.BAD_REQUEST

            ErrorCode.DUPLICATED_RECOURSE -> HttpStatus.CONFLICT

            else -> HttpStatus.INTERNAL_SERVER_ERROR
        }
    }
}


/**
 * ============================================================================
 * SUGGESTED ADDITIONS TO Exception.kt
 * ============================================================================
 *
 * Please add the following to your existing Exception.kt file:
 *
 * 1. Add to ErrorCode enum:
 *    ```kotlin
 *    UNAUTHORIZED_ACCESS(401),
 *    TOKEN_EXPIRED(402),
 *    INVALID_TOKEN(403),
 *    TENANT_ACCESS_DENIED(407)
 *    ```
 *
 * 2. Add new exception classes:
 *    ```kotlin
 *    class UnauthorizedException(msg: String? = null) : BaseException(ErrorCode.UNAUTHORIZED_ACCESS, msg)
 *    class TokenExpiredException(msg: String? = null) : BaseException(ErrorCode.TOKEN_EXPIRED, msg)
 *    class InvalidTokenException(msg: String? = null) : BaseException(ErrorCode.INVALID_TOKEN, msg)
 *    class TenantAccessDeniedException(msg: String? = null) : BaseException(ErrorCode.TENANT_ACCESS_DENIED, msg)
 *    ```
 *
 * 3. Update getHttpStatus() in GlobalExceptionHandler:
 *    ```kotlin
 *    private fun getHttpStatus(errorCode: ErrorCode): HttpStatus {
 *        return when (errorCode) {
 *            ErrorCode.USER_NOT_FOUND,
 *            ErrorCode.ROLE_NOT_FOUND,
 *            ErrorCode.TENANT_NOT_FOUND,
 *            ErrorCode.EMPLOYEE_NOT_FOUND,
 *            ErrorCode.PROJECT_NOT_FOUND,
 *            ErrorCode.BOARD_NOT_FOUND,
 *            ErrorCode.TASK_NOT_FOUND,
 *            ErrorCode.TASK_STATE_NOT_FOUND,
 *            ErrorCode.FILE_NOT_FOUND -> HttpStatus.NOT_FOUND
 *
 *            ErrorCode.INVALID_PASSWORD,
 *            ErrorCode.PASSWORDS_DO_NOT_MATCH,
 *            ErrorCode.BAD_REQUEST,
 *            ErrorCode.FILE_EMPTY,
 *            ErrorCode.FILE_TOO_LARGE,
 *            ErrorCode.INVALID_FILE_TYPE -> HttpStatus.BAD_REQUEST
 *
 *            ErrorCode.DUPLICATED_RECOURSE,
 *            ErrorCode.TENANT_ALREADY_EXISTS,
 *            ErrorCode.PROJECT_ALREADY_EXISTS,
 *            ErrorCode.BOARD_ALREADY_EXISTS,
 *            ErrorCode.TASK_STATE_ALREADY_EXISTS -> HttpStatus.CONFLICT
 *
 *            ErrorCode.UNAUTHORIZED,
 *            ErrorCode.UNAUTHORIZED_ACCESS,
 *            ErrorCode.TOKEN_EXPIRED,
 *            ErrorCode.INVALID_TOKEN,
 *            ErrorCode.TENANT_ACCESS_DENIED -> HttpStatus.UNAUTHORIZED
 *
 *            ErrorCode.TENANT_SUBSCRIPTION_LIMIT_EXCEEDED -> HttpStatus.FORBIDDEN
 *
 *            else -> HttpStatus.INTERNAL_SERVER_ERROR
 *        }
 *    }
 *    ```
 *
 * 4. Add handler for Spring Security exceptions in GlobalExceptionHandler:
 *    ```kotlin
 *    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException::class)
 *    fun handleBadCredentials(ex: BadCredentialsException, request: HttpServletRequest): ResponseEntity<ResponseVO<Nothing>> {
 *        val response = ResponseVO<Nothing>(
 *            status = HttpStatus.UNAUTHORIZED.value(),
 *            errors = mapOf("message" to (ex.message ?: "Invalid credentials")),
 *            timestamp = Instant.now(),
 *            data = null,
 *            source = request.requestURI
 *        )
 *        return ResponseEntity(response, HttpStatus.UNAUTHORIZED)
 *    }
 *
 *    @ExceptionHandler(org.springframework.security.access.AccessDeniedException::class)
 *    fun handleAccessDenied(ex: AccessDeniedException, request: HttpServletRequest): ResponseEntity<ResponseVO<Nothing>> {
 *        val response = ResponseVO<Nothing>(
 *            status = HttpStatus.FORBIDDEN.value(),
 *            errors = mapOf("message" to "Access denied"),
 *            timestamp = Instant.now(),
 *            data = null,
 *            source = request.requestURI
 *        )
 *        return ResponseEntity(response, HttpStatus.FORBIDDEN)
 *    }
 *    ```
 *
 * ============================================================================
 */

