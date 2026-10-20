package app.homeward.web

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

// Every error the frontend can show is a plain sentence a tired volunteer understands.
data class ErrorMessage(
    val message: String,
)

class ApiException(
    val status: HttpStatus,
    override val message: String,
) : RuntimeException(message)

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(ApiException::class)
    fun api(e: ApiException) = ResponseEntity.status(e.status).body(ErrorMessage(e.message))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalid(e: MethodArgumentNotValidException) =
        ResponseEntity.badRequest().body(
            ErrorMessage(
                e.bindingResult.allErrors
                    .firstOrNull()
                    ?.defaultMessage ?: "Please check the form.",
            ),
        )

    // Unknown fields (e.g. "ssn") or broken JSON are refused, never silently dropped.
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(e: HttpMessageNotReadableException) =
        ResponseEntity.badRequest().body(ErrorMessage("Something in this form isn't allowed. Please check it and try again."))

    // The database's own rules (CHECK constraints) as the same plain sentences the frontend uses.
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun database(e: DataIntegrityViolationException): ResponseEntity<ErrorMessage> {
        val text = e.mostSpecificCause.message.orEmpty()
        val message =
            CONSTRAINT_MESSAGES.entries.firstOrNull { text.contains("\"${it.key}\"") }?.value
                ?: "That couldn't be saved. Please check the form."
        return ResponseEntity.badRequest().body(ErrorMessage(message))
    }

    companion object {
        private val CONSTRAINT_MESSAGES =
            mapOf(
                "persons_preferred_name_check" to "Please write a name or nickname.",
                "persons_approx_age_check" to "Please check the age.",
                "persons_check" to "Write where it is on the paper, e.g. \"Sheet 4, line 12\".",
                "persons_check1" to "This age is under 18, so this person counts as a child.",
                "persons_check2" to "This age is 18 or over. Please check the age, or whether this person is under 18.",
            )
    }
}
