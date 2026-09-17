package com.example.musicsm.wear

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the watch knows about the phone's playback. */
data class WearPlaybackState(
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val hasTrack: Boolean = false,
)

/**
 * Remote control for the phone. State arrives as a Data Layer item published by the phone's
 * `NowPlayingPublisher`; commands go back as messages handled by `WearBridgeService`.
 */
class WearPlayerViewModel(application: Application) : AndroidViewModel(application),
    DataClient.OnDataChangedListener {

    private val dataClient = Wearable.getDataClient(application)
    private val messageClient = Wearable.getMessageClient(application)
    private val nodeClient = Wearable.getNodeClient(application)

    private val _state = MutableStateFlow(WearPlaybackState())
    val state: StateFlow<WearPlaybackState> = _state.asStateFlow()

    /** Start listening and pull whatever the phone last published. */
    fun start() {
        dataClient.addListener(this)
        dataClient.dataItems.addOnSuccessListener { buffer ->
            buffer.firstOrNull { it.uri.path == WearContract.PATH_STATE }?.let(::apply)
            buffer.release()
        }
    }

    fun stop() {
        dataClient.removeListener(this)
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == WearContract.PATH_STATE
            ) {
                apply(event.dataItem)
            }
        }
        events.release()
    }

    fun playPause() {
        // Flip immediately so the button feels responsive; the phone confirms a moment later.
        _state.value = _state.value.copy(isPlaying = !_state.value.isPlaying)
        send(WearContract.CMD_PLAY_PAUSE)
    }

    fun next() = send(WearContract.CMD_NEXT)

    fun previous() = send(WearContract.CMD_PREVIOUS)

    /** Bring the phone app to the foreground on Now Playing. */
    fun openOnPhone() {
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse("musicsm://resume"))
        runCatching {
            RemoteActivityHelper(getApplication()).startRemoteActivity(intent)
        }
    }

    private fun send(command: String) {
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, WearContract.PATH_COMMAND, command.toByteArray())
            }
        }
    }

    private fun apply(item: DataItem) {
        val map = DataMapItem.fromDataItem(item).dataMap
        _state.value = WearPlaybackState(
            title = map.getString(WearContract.KEY_TITLE).orEmpty(),
            artist = map.getString(WearContract.KEY_ARTIST).orEmpty(),
            isPlaying = map.getBoolean(WearContract.KEY_PLAYING, false),
            hasTrack = map.getBoolean(WearContract.KEY_HAS_TRACK, false),
        )
    }
}
