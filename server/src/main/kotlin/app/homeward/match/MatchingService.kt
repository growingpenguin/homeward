package app.homeward.match

import org.springframework.stereotype.Service
import java.util.UUID
import kotlin.math.min

// Puts small clues together (spelling, sound, nickname, age) into a score with plain-English reasons.
// It only SUGGESTS: a volunteer looks at the reasons, and the found person decides.
@Service
class MatchingService(
    private val searches: SearchRepo,
) {
    // Runs every sought name through find_candidates and stores the suggestions worth showing.
    // Safe to run again (e.g. "Check again" after more people were registered): a person is never
    // suggested twice for one search.
    fun run(search: StoredSearch) {
        val best = mutableMapOf<UUID, Pair<Double, Signals>>()
        for (name in search.soughtNames) {
            for (s in searches.signals(name, search.ageMin, search.ageMax)) {
                if (s.personId == search.searcherPersonId) continue // never suggest the searcher to themselves
                val score = score(s)
                if (score > (best[s.personId]?.first ?: -1.0)) best[s.personId] = score to s
            }
        }
        val strong =
            best
                .filterValues { it.first >= MIN_SCORE }
                .entries
                .sortedByDescending { it.value.first }
                .take(MAX_SUGGESTIONS)
        val names = searches.names(strong.map { it.key })
        for ((personId, value) in strong) {
            val name = names[personId] ?: continue // hidden or protected in the meantime
            searches.suggest(search.id, personId, value.first, reasons(value.second, name))
        }
    }

    companion object {
        const val MIN_SCORE = 0.35
        const val MAX_SUGGESTIONS = 10

        // 0..1. Spelling counts most; a sound-alike or nickname helps a misspelling; the right age adds a
        // little, the wrong age takes a little (ages are often guesses, so never much).
        fun score(s: Signals): Double {
            var score = 0.6 * s.nameSimilarity
            score += min(s.wordsSoundAlike, 2) * 0.15
            if (s.nicknameMatch) score += 0.2
            when (s.ageClose) {
                true -> score += 0.1
                false -> score -= 0.15
                null -> {}
            }
            return score.coerceIn(0.0, 1.0)
        }

        // What the volunteer reads on the card, in plain words.
        fun reasons(
            s: Signals,
            name: CandidateName,
        ): List<String> =
            buildList {
                when {
                    s.nameSimilarity >= 0.6 -> add("name spelled like ${name.preferredName}")
                    s.nameSimilarity >= 0.3 -> add("name spelled a bit like ${name.preferredName}")
                }
                if (s.wordsSoundAlike > 0) add("name sounds like ${name.preferredName}")
                if (s.nicknameMatch) add("a nickname matches ${name.preferredName}")
                when (s.ageClose) {
                    true -> add("age fits (about ${name.approxAge})")
                    false -> add("age doesn't fit (about ${name.approxAge})")
                    null -> {}
                }
                if (name.minor) add("a child: a lead decides before anything is shared")
            }
    }
}
