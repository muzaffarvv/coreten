package uz.vv.coretenservice

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext


import java.security.SecureRandom

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

object DefaultTaskStates {
    val DEFAULT_STATES = listOf(
        PermissionDto("New", "NEW"),
        PermissionDto("In Progress", "IN_PROGRESS"),
        PermissionDto("Review", "REVIEW"),
        PermissionDto("Done", "DONE")
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
