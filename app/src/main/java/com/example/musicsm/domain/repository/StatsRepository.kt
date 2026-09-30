package com.example.musicsm.domain.repository

import com.example.musicsm.domain.model.ListeningStats
import com.example.musicsm.domain.model.SongPlayCount
import com.example.musicsm.domain.model.StatsRange
import kotlinx.coroutines.flow.Flow

/** Aggregated listening history, derived from the append-only play-event log. */
interface StatsRepository {
    /** Re-emits whenever a new play is recorded. */
    fun stats(range: StatsRange): Flow<ListeningStats>

    /**
     * Songs that were once on heavy rotation but have not been played for a long time.
     *
     * Read once rather than observed: this backs a Home shelf that should stay put while the
     * listener is looking at it, not re-order itself the moment one of its own tracks is played.
     */
    suspend fun forgottenFavorites(limit: Int): List<SongPlayCount>

    /** Wipes the play-event log (the "clear listening history" action in settings). */
    suspend fun clear()
}
