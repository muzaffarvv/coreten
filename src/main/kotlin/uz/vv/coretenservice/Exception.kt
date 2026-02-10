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

class InvalidPasswordException(msg: String? = null) : BaseException(ErrorCode.INVALID_PASSWORD, msg)
class PasswordMismatchException(msg: String? = null) : BaseException(ErrorCode.PASSWORDS_DO_NOT_MATCH, msg)
class DuplicateResourceException(msg: String? = null) : BaseException(ErrorCode.DUPLICATED_RECOURSE, msg)

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