package app.homeward.person

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// Thin: validate, then call PersonService (which takes the shelter and the volunteer from the login).
@RestController
class PersonController(
    private val service: PersonService,
) {
    // 201 when new; 200 with the same record when the browser re-sends the same form.
    @PostMapping("/api/persons")
    fun register(
        @Valid @RequestBody body: RegisterPersonRequest,
    ): ResponseEntity<PersonView> {
        val result = service.register(body)
        return ResponseEntity.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK).body(result.person)
    }

    @GetMapping("/api/persons/{id}")
    fun get(
        @PathVariable id: UUID,
    ) = service.get(id)

    @PutMapping("/api/persons/{id}")
    fun edit(
        @PathVariable id: UUID,
        @Valid @RequestBody body: EditPersonRequest,
    ) = service.edit(id, body)

    @PostMapping("/api/persons/{id}/slip")
    fun slip(
        @PathVariable id: UUID,
    ): ResponseEntity<SlipResponse> = ResponseEntity.status(HttpStatus.CREATED).body(service.issueSlip(id))
}
