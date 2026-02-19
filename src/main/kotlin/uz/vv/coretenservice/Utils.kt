package uz.vv.coretenservice

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.LocalDate

object EmployeeCodeGenerator {
    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    private val RANDOM = SecureRandom()

    fun generate(length: Int = 10): String {
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)])
        }
        // result: "9A3D4XJ2"
        return sb.toString()
    }
}

object FileKeyGenerator {

    private const val CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val RANDOM = SecureRandom()

    fun generateFileKey(): String {

        val randomPart = buildString {
            repeat(6) {
                append(CHARS[RANDOM.nextInt(CHARS.length)])
            }
        }

        val now = LocalDate.now()
        val year = (now.year % 100).toString()
        val month = "%02d".format(now.monthValue)
        val day = "%02d".format(now.dayOfMonth)

        return "$year$randomPart$month$day"
    }
}


object DefaultTaskStates {
    val DEFAULT_STATES = listOf(
        TaskStateCreate("New", "NEW"),
        TaskStateCreate("In Progress", "IN_PROGRESS"),
        TaskStateCreate("Review", "REVIEW"),
        TaskStateCreate("Done", "DONE")
    )
}


@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [ValidPasswordIfPresentValidator::class])
annotation class ValidPasswordIfPresent(
    val message: String = "Invalid password format",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)


class ValidPasswordIfPresentValidator :
    ConstraintValidator<ValidPasswordIfPresent, String?> {

    private val pattern = Regex("^(?=.*[0-9])(?=.*[!@#$%&()\\-+]).{8,}$")

    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        if (value.isBlank()) return false
        return pattern.matches(value)
    }
}


@Component
class TenantAccessUtil(
    private val employeeService: EmployeeService
) {

    fun hasAnyPosition(vararg allowedPositions: Position): Boolean {
        val employeeId = TenantContext.getEmployeeId() ?: return false
        return try {
            val currentPosition = employeeService.getPosition(employeeId)
            allowedPositions.contains(currentPosition)
        } catch (e: Exception) {
            false
        }
    }

    fun validatePosition(vararg allowedPositions: Position) {
        if (!hasAnyPosition(*allowedPositions)) {
            throw UnauthorizedException("Positions authorized to perform this action: ${allowedPositions.joinToString()}")
        }
    }

    // Validate by hierarchy (e.g.: Manager and above)
    fun isAtLeast(minPosition: Position): Boolean {
        val employeeId = TenantContext.getEmployeeId() ?: return false
        val currentPosition = employeeService.getPosition(employeeId)
        // Check for enum order (OWNER = 0, ADMIN = 1...)
        return currentPosition.ordinal <= minPosition.ordinal
    }
}