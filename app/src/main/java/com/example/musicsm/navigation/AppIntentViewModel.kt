package com.example.musicsm.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.data.prefs.AppPreferences
import com.example.musicsm.domain.share.PlaylistShareCodec
import com.example.musicsm.ui.search.SearchRequestBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Root-level plumbing for [AppIntent] handling that doesn't belong to the player. */
@HiltViewModel
class AppIntentViewModel @Inject constructor(
    private val searchRequests: SearchRequestBus,
    private val sharedPlaylists: SharedPlaylistBus,
    preferences: AppPreferences,
) : ViewModel() {

    /** Whether the bottom bar may minimise while content scrolls (a Settings toggle). */
    val minimizeBarOnScroll: StateFlow<Boolean> = preferences.minimizeBarOnScroll
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferences.minimizeBarOnScrollNow)

    /** Pre-fill the Search tab with [query] (the search runs automatically). */
    fun prefillSearch(query: String) {
        if (query.isNotBlank()) searchRequests.request(query)
    }

    /**
     * Decode a shared-playlist payload and park it for the import screen.
     *
     * @return false when the link is corrupt or truncated, so the caller can say so rather than
     * opening an empty screen.
     */
    fun offerSharedPlaylist(payload: String): Boolean {
        val playlist = PlaylistShareCodec.decode(payload) ?: return false
        sharedPlaylists.offer(playlist)
        return true
    }
}
