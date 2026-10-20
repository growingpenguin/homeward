package app.homeward.match

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// Thin: validate, then call SearchService (which takes the shelter and the volunteer from the login).
@RestController
class SearchController(
    private val service: SearchService,
) {
    // 201 when new; 200 with the same search when the browser re-sends the same form.
    @PostMapping("/api/searches")
    fun start(
        @Valid @RequestBody body: StartSearchRequest,
    ): ResponseEntity<SearchResult> {
        val (result, created) = service.start(body)
        return ResponseEntity.status(if (created) HttpStatus.CREATED else HttpStatus.OK).body(result)
    }

    @GetMapping("/api/searches/{id}")
    fun get(
        @PathVariable id: UUID,
    ) = service.get(id)

    @PostMapping("/api/searches/{id}/requests")
    fun request(
        @PathVariable id: UUID,
        @Valid @RequestBody body: ContactRequestIn,
    ): ResponseEntity<ContactRequestCreated> = ResponseEntity.status(HttpStatus.CREATED).body(service.request(id, body))
}
