package com.example.musicsm.navigation

import com.example.musicsm.domain.share.SharedPlaylist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands a decoded shared playlist from the deep link to the import screen.
 *
 * The payload is a couple of kilobytes, which is far too much to push through a navigation
 * argument, so it is parked here instead and the route stays argument-free. App-scoped because a
 * cold start from a QR scan handles the intent before the destination exists.
 */
@Singleton
class SharedPlaylistBus @Inject constructor() {

    private val _pending = MutableStateFlow<SharedPlaylist?>(null)
    val pending: StateFlow<SharedPlaylist?> = _pending.asStateFlow()

    fun offer(playlist: SharedPlaylist) {
        _pending.value = playlist
    }

    fun clear() {
        _pending.value = null
    }
}
