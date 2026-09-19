package com.example.musicsm.domain.repository

import com.example.musicsm.domain.model.ListeningStats
import com.example.musicsm.domain.model.StatsRange
import kotlinx.coroutines.flow.Flow

/** Aggregated listening history, derived from the append-only play-event log. */
interface StatsRepository {
    /** Re-emits whenever a new play is recorded. */
    fun stats(range: StatsRange): Flow<ListeningStats>

    /** Wipes the play-event log (the "clear listening history" action in settings). */
    suspend fun clear()
}
