package uz.vv.coretenservice

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

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
