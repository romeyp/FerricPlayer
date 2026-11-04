package dev.foldman.remote

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The seam between the deck and its bridge media session (hosted by [MediaListenerService]).
 *
 * Ferric owns no playback of its own for the streaming sources, so before this bridge the
 * system handed Bluetooth media buttons to the source apps' sessions: pause reached the
 * playing session, but once it paused the media button target was lost and play, next and
 * previous landed nowhere. The bridge session stands in Ferric's place as the button target
 * and relays every command into the deck, while the deck keeps the session's state and
 * metadata mirrored to the live well, so the bridge is always the freshest session.
 *
 * Two halves, deliberately dumb: [DeckViewModel][dev.foldman.deck.DeckViewModel] installs a
 * [Transport] (its own key handlers, nothing parallel) and publishes a [State] on every tick;
 * the service's session forwards its callbacks into [transport] and renders [state]. Neither
 * half knows the other's internals.
 */
object FerricBridge {

    /** The deck's transport, called by the bridge session's callbacks. */
    interface Transport {
        fun play()
        fun pause()
        fun next()
        fun previous()
        fun seekTo(positionMs: Long)
    }

    /** Installed by the deck while it lives; null when there is no deck to drive. */
    @Volatile
    var transport: Transport? = null

    /**
     * What the bridge session mirrors: the live well's track and play state. A null published
     * state means the bridge stands down (the deck is gone, or the local source is selected
     * and its own session in the playback service is the button target instead).
     */
    data class State(
        /** The driving source's name (YT Music, Apple Music), shown as the description. */
        val sourceLabel: String,
        val title: String,
        val artist: String,
        val album: String,
        val artwork: Bitmap?,
        val durationMs: Long,
        val positionMs: Long,
        val isPlaying: Boolean,
        /** The bridge only advertises SEEK_TO when the live source accepts a seek. */
        val canSeek: Boolean,
    )

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    fun publish(state: State?) {
        _state.value = state
    }
}
