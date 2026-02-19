package uz.vv.coretenservice

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.slf4j.LoggerFactory
import java.time.Instant

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
class TaskStateMismatchException(msg: String? = null) : BaseException(ErrorCode.TASK_STATE_MISMATCH, msg)

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
class BadRequestException(msg: String? = null) : BaseException(ErrorCode.BAD_REQUEST, msg)
class BadCredentialsException(msg: String? = null) : BaseException(ErrorCode.BAD_CREDENTIALS, msg)



data class ResponseVO<T> (
    val status: Int,
    val errors: Map<String, String>?,
    val timestamp: Instant,
    val data: T?,
    val source: String?
)

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(BaseException::class)
    fun handleBaseException(ex: BaseException, request: HttpServletRequest): ResponseEntity<ResponseVO<Nothing>> {
        val status = getHttpStatus(ex.errorCode)
        logError(ex, request, status)

        val response = ResponseVO<Nothing>(
            status = status.value(),
            errors = mapOf(
                "code" to ex.errorCode.name,
                "message" to (ex.message ?: "An error occurred during the operation")
            ),
            timestamp = Instant.now(),
            data = null,
            source = request.requestURI
        )
        return ResponseEntity(response, status)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ResponseVO<Nothing>> {
        val errors = ex.bindingResult.fieldErrors.associate {
            it.field to (it.defaultMessage ?: "Invalid value provided")
        }

        val response = ResponseVO<Nothing>(
            status = HttpStatus.BAD_REQUEST.value(),
            errors = errors,
            timestamp = Instant.now(),
            data = null,
            source = request.requestURI
        )
        return ResponseEntity(response, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleReadableException(ex: HttpMessageNotReadableException, request: HttpServletRequest): ResponseEntity<ResponseVO<Nothing>> {
        val response = ResponseVO<Nothing>(
            status = HttpStatus.BAD_REQUEST.value(),
            errors = mapOf("error" to "The JSON request is malformed or has an invalid format"),
            timestamp = Instant.now(),
            data = null,
            source = request.requestURI
        )
        return ResponseEntity(response, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatchException(ex: MethodArgumentTypeMismatchException, request: HttpServletRequest): ResponseEntity<ResponseVO<Nothing>> {
        val response = ResponseVO<Nothing>(
            status = HttpStatus.BAD_REQUEST.value(),
            errors = mapOf(
                "parameter" to ex.name,
                "message" to "The parameter type is incorrect"
            ),
            timestamp = Instant.now(),
            data = null,
            source = request.requestURI
        )
        return ResponseEntity(response, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(ex: HttpRequestMethodNotSupportedException, request: HttpServletRequest): ResponseEntity<ResponseVO<Nothing>> {
        val response = ResponseVO<Nothing>(
            status = HttpStatus.METHOD_NOT_ALLOWED.value(),
            errors = mapOf("error" to "The HTTP method ${ex.method} is not supported for this API"),
            timestamp = Instant.now(),
            data = null,
            source = request.requestURI
        )
        return ResponseEntity(response, HttpStatus.METHOD_NOT_ALLOWED)
    }

    @ExceptionHandler(Exception::class)
    fun handleAllExceptions(ex: Exception, request: HttpServletRequest): ResponseEntity<ResponseVO<Nothing>> {
        logger.error("INTERNAL SERVER ERROR at ${request.requestURI}: ", ex)

        val response = ResponseVO<Nothing>(
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            errors = mapOf("error" to "An unexpected internal server error occurred. Please contact support."),
            timestamp = Instant.now(),
            data = null,
            source = request.requestURI
        )
        return ResponseEntity(response, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    private fun logError(ex: Exception, request: HttpServletRequest, status: HttpStatus) {
        if (status.is5xxServerError) {
            logger.error("Status: {} | URI: {} | Message: {}", status.value(), request.requestURI, ex.message)
        } else {
            logger.warn("Status: {} | URI: {} | Message: {}", status.value(), request.requestURI, ex.message)
        }
    }

    private fun getHttpStatus(errorCode: ErrorCode): HttpStatus {
        return when (errorCode) {
            ErrorCode.USER_NOT_FOUND,
            ErrorCode.ROLE_NOT_FOUND,
            ErrorCode.TENANT_NOT_FOUND,
            ErrorCode.EMPLOYEE_NOT_FOUND,
            ErrorCode.PROJECT_NOT_FOUND,
            ErrorCode.BOARD_NOT_FOUND,
            ErrorCode.TASK_STATE_NOT_FOUND,
            ErrorCode.TASK_NOT_FOUND,
            ErrorCode.FILE_NOT_FOUND -> HttpStatus.NOT_FOUND

            ErrorCode.TENANT_ALREADY_EXISTS,
            ErrorCode.PROJECT_ALREADY_EXISTS,
            ErrorCode.BOARD_ALREADY_EXISTS,
            ErrorCode.TASK_STATE_ALREADY_EXISTS,
            ErrorCode.DUPLICATED_RECOURSE -> HttpStatus.CONFLICT

            ErrorCode.INVALID_PASSWORD,
            ErrorCode.PASSWORDS_DO_NOT_MATCH,
            ErrorCode.FILE_EMPTY,
            ErrorCode.INVALID_FILE_TYPE,
            ErrorCode.BAD_CREDENTIALS,
            ErrorCode.BAD_REQUEST -> HttpStatus.BAD_REQUEST

            ErrorCode.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED
            ErrorCode.TENANT_SUBSCRIPTION_LIMIT_EXCEEDED -> HttpStatus.FORBIDDEN
            ErrorCode.FILE_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE

            else -> HttpStatus.INTERNAL_SERVER_ERROR
        }
    }
}