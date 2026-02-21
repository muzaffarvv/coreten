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
import org.springframework.web.HttpMediaTypeNotSupportedException
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
class DuplicateResourceException(msg: String? = null) : BaseException(ErrorCode.DUPLICATED_RESOURCE, msg)

class UnauthorizedException(msg: String? = null) : BaseException(ErrorCode.UNAUTHORIZED, msg)
class BadRequestException(msg: String? = null) : BaseException(ErrorCode.BAD_REQUEST, msg)
class BadCredentialsException(msg: String? = null) : BaseException(ErrorCode.BAD_CREDENTIALS, msg)


data class ResponseVO<T>(
    val status: Int,
    val errors: Map<String, String>?,
    val timestamp: Instant,
    val data: T?,
    val source: String?
)

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleReadableException(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest
    ): ResponseEntity<ResponseVO<Nothing>> {
        val detail = ex.cause?.message ?: ""

        val errorMessage = when {
            detail.contains("Unexpected character") ->
                "Your JSON has a syntax error. Please check for extra commas or missing brackets."

            detail.contains("not a valid representation") ->
                "One of the values in your JSON is in the wrong format. Please check the data types."

            else -> "The request body is missing or the JSON structure is invalid. Please verify your input."
        }

        return buildResponse(HttpStatus.BAD_REQUEST, errorMessage, request)
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleMediaTypeNotSupported(
        ex: HttpMediaTypeNotSupportedException,
        request: HttpServletRequest
    ): ResponseEntity<ResponseVO<Nothing>> {
        val errorMessage = if (ex.contentType?.toString()?.contains("text/plain") == true) {
            "You are sending data as 'Text'. Please change it to 'JSON' in Postman (Body -> raw -> JSON)."
        } else {
            "This API only supports JSON. Your current format '${ex.contentType}' is not allowed."
        }
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, errorMessage, request)
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatchException(
        ex: MethodArgumentTypeMismatchException,
        request: HttpServletRequest
    ): ResponseEntity<ResponseVO<Nothing>> {
        val requiredType = ex.requiredType?.simpleName ?: "the correct type"
        val errorMessage = "The value '${ex.value}' is invalid for '${ex.name}'. It must be a $requiredType."

        return buildResponse(HttpStatus.BAD_REQUEST, errorMessage, request)
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(
        ex: HttpRequestMethodNotSupportedException,
        request: HttpServletRequest
    ): ResponseEntity<ResponseVO<Nothing>> {
        val supported = ex.supportedHttpMethods?.joinToString(", ") ?: "other methods"
        val errorMessage = "Method '${ex.method}' is not allowed for this URL. Please use $supported instead."

        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, errorMessage, request)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ResponseVO<Nothing>> {
        val errors = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "Invalid value") }

        val response = ResponseVO(
            status = HttpStatus.BAD_REQUEST.value(),
            errors = errors,
            timestamp = Instant.now(),
            data = null,
            source = request.requestURI
        )
        return ResponseEntity(response, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(BaseException::class)
    fun handleBaseException(ex: BaseException, request: HttpServletRequest): ResponseEntity<ResponseVO<Nothing>> {
        val status = getHttpStatus(ex.errorCode)
        logError(ex, request, status)

        val response = ResponseVO(
            status = status.value(),
            errors = mapOf("code" to ex.errorCode.name, "message" to (ex.message ?: "Operation failed")),
            timestamp = Instant.now(),
            data = null,
            source = request.requestURI
        )
        return ResponseEntity(response, status)
    }

    @ExceptionHandler(Exception::class)
    fun handleAllExceptions(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ResponseVO<Nothing>> {

        val status = HttpStatus.INTERNAL_SERVER_ERROR
        logError(ex, request, status)

        val errorMessage =
            "Something went wrong on our server. Please try again later or contact support."

        return buildResponse(status, errorMessage, request)
    }

    private fun buildResponse(
        status: HttpStatus,
        message: String,
        request: HttpServletRequest
    ): ResponseEntity<ResponseVO<Nothing>> {
        val response = ResponseVO(
            status = status.value(),
            errors = mapOf("error" to message),
            timestamp = Instant.now(),
            data = null,
            source = request.requestURI
        )
        return ResponseEntity(response, status)
    }

    private fun logError(ex: Exception, request: HttpServletRequest, status: HttpStatus) {
        val method = request.method
        val uri = request.requestURI
        val query = request.queryString?.let { "?$it" } ?: ""
        val fullPath = "$uri$query"

        if (status.is5xxServerError) {
            logger.error(
                "\n##################  UNHANDLED EXCEPTION  ##################\n" +
                        "status={} | method={} | uri={} | message={}\n" +
                        "###############################################################",
                status.value(),
                method,
                fullPath,
                ex.message,
                ex
            )
        } else {
            logger.warn(
                "\n HANDLED EXCEPTION | status={} | method={} | uri={} | message={}",
                status.value(),
                method,
                fullPath,
                ex.message
            )
        }
    }

    private fun getHttpStatus(errorCode: ErrorCode): HttpStatus = when (errorCode) {
        ErrorCode.USER_NOT_FOUND, ErrorCode.TENANT_NOT_FOUND, ErrorCode.FILE_NOT_FOUND -> HttpStatus.NOT_FOUND
        ErrorCode.TENANT_ALREADY_EXISTS, ErrorCode.DUPLICATED_RESOURCE -> HttpStatus.CONFLICT
        ErrorCode.BAD_REQUEST, ErrorCode.INVALID_PASSWORD -> HttpStatus.BAD_REQUEST
        ErrorCode.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED
        else -> HttpStatus.INTERNAL_SERVER_ERROR
    }
}