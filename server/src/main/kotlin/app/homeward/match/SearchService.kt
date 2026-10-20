package app.homeward.match

import app.homeward.audit.AuditService
import app.homeward.security.CurrentStaff
import app.homeward.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class SearchService(
    private val searches: SearchRepo,
    private val matching: MatchingService,
    private val audit: AuditService,
    private val currentStaff: CurrentStaff,
) {
    @Transactional
    fun start(r: StartSearchRequest): Pair<SearchResult, Boolean> {
        val staff = currentStaff.get()
        if (r.ageMin != null && r.ageMax != null && r.ageMin > r.ageMax) {
            throw ApiException(HttpStatus.BAD_REQUEST, "Please check the ages: the first should be the smaller one.")
        }
        // The person searching is registered at this desk first (they get a slip, too).
        if (searches.personShelter(r.searcherPersonId!!) != staff.shelterId) { // !!: validated as present
            throw ApiException(HttpStatus.BAD_REQUEST, "Please register the person who is looking first.")
        }
        val created = searches.insert(r, staff.shelterId, staff.staffId)
        val search = searches.find(r.id!!)!! // !!: id validated; the row exists after insert or conflict
        // Same id from another desk: never reveal or touch someone else's search.
        if (search.shelterId != staff.shelterId) {
            throw ApiException(HttpStatus.CONFLICT, "Something went wrong with this form. Please start it again.")
        }
        return result(search) to created
    }

    // "Check again": runs matching again, so someone registered since (e.g. a paper form typed in
    // tonight) can still be suggested for yesterday's search.
    @Transactional
    fun get(id: UUID): SearchResult = result(ownSearch(id))

    @Transactional
    fun request(
        searchId: UUID,
        body: ContactRequestIn,
    ): ContactRequestCreated {
        val staff = currentStaff.get()
        val search = ownSearch(searchId)
        val personId = body.personId!! // !!: validated as present
        // Only someone this search suggested and matching may still find (not hidden or protected since).
        if (!searches.isVisibleSuggestion(search.id, personId)) {
            throw ApiException(HttpStatus.NOT_FOUND, "That person isn't one of the suggested matches.")
        }
        val requestId = searches.request(search.id, personId, staff.staffId, body.message?.trim()?.ifEmpty { null })
        audit.log(staff, "CONTACT_REQUEST", requestId)
        return ContactRequestCreated(requestId)
    }

    private fun result(search: StoredSearch): SearchResult {
        matching.run(search)
        audit.log(currentStaff.get(), "SEARCH", search.id)
        return SearchResult(search.id, search.searcherPersonId, search.soughtNames, searches.cards(search.id))
    }

    // Volunteers only see searches made at their own desk; anything else looks like "not found".
    private fun ownSearch(id: UUID): StoredSearch {
        val search = searches.find(id)
        if (search == null || search.shelterId != currentStaff.get().shelterId) {
            throw ApiException(HttpStatus.NOT_FOUND, "Search not found.")
        }
        return search
    }
}
