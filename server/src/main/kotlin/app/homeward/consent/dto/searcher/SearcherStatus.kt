package app.homeward.consent.dto.searcher

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

// The ONLY type a searcher ever receives. It has no shelter, phone or address field,
// so a location can't be attached to it by accident.
// "type" is written into the JSON ("Active" / "Safe" / "Connected"), so the frontend can tell the
// three answers apart (Active and Safe have no other fields), and springdoc puts it in the OpenAPI types.
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = SearcherStatus.Active::class, name = "Active"),
    JsonSubTypes.Type(value = SearcherStatus.Safe::class, name = "Safe"),
    JsonSubTypes.Type(value = SearcherStatus.Connected::class, name = "Connected"),
)
sealed interface SearcherStatus {
    // Same reply for: no match, hidden, pending, declined, blocked, not allowed to see.
    // A searcher can't tell "not here" from "here but hiding".
    data object Active : SearcherStatus

    // The sought person chose "Tell them I'm safe".
    // A fixed sentence in the UI, not free text, so nobody can type a location into it.
    data object Safe : SearcherStatus

    // Only after the sought person said yes. The one answer that may say how to meet
    // (e.g. "call the Eastside desk"), because they agreed to it.
    data class Connected(
        val contactPlan: String,
    ) : SearcherStatus
}

// One search, as the searcher may see it: who they asked about (they typed it) and the answer.
data class SearchAnswer(
    val searchId: java.util.UUID,
    val soughtNames: List<String>,
    val status: SearcherStatus,
)
