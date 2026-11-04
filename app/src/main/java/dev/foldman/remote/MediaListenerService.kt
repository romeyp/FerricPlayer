package dev.foldman.remote

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import dev.foldman.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Two jobs in one service.
 *
 * The first is why it exists: Android only lets apps with an enabled notification listener
 * read other apps' media sessions, which is how Ferric follows YT Music and Apple Music.
 *
 * The second is the bridge session. Ferric drives the streaming sources through their own
 * sessions but owns none itself, so Bluetooth media buttons went to the source sessions:
 * pause reached the playing one, but once paused the system's button target was lost and
 * play, next and previous landed nowhere (each source app alone, Ferric closed, obeys the
 * same earbuds perfectly). This service therefore hosts a MediaSessionCompat of Ferric's own
 * (tag "FerricBridge") that stands as the button target and relays into the deck through
 * [FerricBridge], while the deck keeps the session's state and metadata mirrored to the live
 * well on every tick. The mirroring is the fix's heart: the bridge is always the freshest
 * session, so it is the one holding the buttons when everything is paused.
 *
 * No manifest receiver is wired for media buttons, deliberately: on this app's minSdk the
 * system delivers button events directly to an active flagged session's callback, and the
 * androidx MediaButtonReceiver pattern resolves its target service by intent action and
 * starts it as a foreground service, which a notification listener can never honour.
 */
class MediaListenerService : NotificationListenerService() {
    private var bridge: MediaSessionCompat? = null
    private var scope: CoroutineScope? = null
    private var collectJob: Job? = null

    /** What the session last rendered, so only real changes are republished to it. */
    private var bridgeActive = false
    private var appliedMetaKey: List<Any?>? = null
    private var appliedPlaying = false
    private var appliedCanSeek = false
    private var appliedPositionMs = -1L

    private val bridgeCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            FerricBridge.transport?.play()
        }

        override fun onPause() {
            FerricBridge.transport?.pause()
        }

        /** A stop from a button or the system panel means pause on this deck. */
        override fun onStop() {
            FerricBridge.transport?.pause()
        }

        override fun onSkipToNext() {
            FerricBridge.transport?.next()
        }

        override fun onSkipToPrevious() {
            FerricBridge.transport?.previous()
        }

        override fun onSeekTo(pos: Long) {
            FerricBridge.transport?.seekTo(pos)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (bridge != null) return
        val session = MediaSessionCompat(this, BRIDGE_TAG)
        session.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
        )
        session.setCallback(bridgeCallback, Handler(Looper.getMainLooper()))
        // Tapping Ferric's card in the system media panel opens the deck.
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session.setSessionActivity(openApp)
        bridge = session
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        collectJob = s.launch { FerricBridge.state.collect(::applyBridgeState) }
    }

    override fun onListenerDisconnected() {
        teardownBridge()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        teardownBridge()
        super.onDestroy()
    }

    private fun teardownBridge() {
        collectJob?.cancel()
        collectJob = null
        scope?.cancel()
        scope = null
        bridge?.let {
            it.setActive(false)
            it.release()
        }
        bridge = null
        bridgeActive = false
        appliedMetaKey = null
        appliedPositionMs = -1L
    }

    /** Renders one mirrored deck state into the session, skipping whatever has not changed. */
    private fun applyBridgeState(state: FerricBridge.State?) {
        val session = bridge ?: return
        if (state == null) {
            // The deck is gone, or the local source is selected: the bridge stands down so
            // local playback's own session (in PlaybackService) keeps the buttons, as before.
            if (bridgeActive) {
                session.setPlaybackState(
                    PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_NONE, 0, 0f)
                        .build(),
                )
                session.setMetadata(null)
                session.setActive(false)
                bridgeActive = false
                appliedMetaKey = null
                appliedPositionMs = -1L
            }
            return
        }
        if (!bridgeActive) {
            session.setActive(true)
            bridgeActive = true
        }
        val metaKey = listOf(state.sourceLabel, state.title, state.artist, state.album, state.durationMs, state.artwork)
        if (metaKey != appliedMetaKey) {
            appliedMetaKey = metaKey
            session.setMetadata(buildMetadata(state))
            appliedPositionMs = -1L // a new track always earns a fresh playback state too
        }
        if (state.isPlaying != appliedPlaying ||
            state.canSeek != appliedCanSeek ||
            abs(state.positionMs - appliedPositionMs) >= POSITION_REFRESH_MS
        ) {
            appliedPlaying = state.isPlaying
            appliedCanSeek = state.canSeek
            appliedPositionMs = state.positionMs
            session.setPlaybackState(buildPlaybackState(state))
        }
    }

    private fun buildMetadata(state: FerricBridge.State): MediaMetadataCompat {
        val b = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, state.title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, state.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, state.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, state.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, state.album)
            // The source's name rides as the description: the panel names Ferric as the
            // controller, this says which well is actually playing.
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, state.sourceLabel)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, state.durationMs)
        state.artwork?.let { art ->
            b.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
            b.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, art)
            b.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, art)
        }
        return b.build()
    }

    private fun buildPlaybackState(state: FerricBridge.State): PlaybackStateCompat {
        var actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_STOP
        // Seek is advertised only when the live source takes one, the deck's usual rule.
        if (state.canSeek) actions = actions or PlaybackStateCompat.ACTION_SEEK_TO
        return PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(
                if (state.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                state.positionMs,
                if (state.isPlaying) 1f else 0f,
                SystemClock.elapsedRealtime(),
            )
            .build()
    }

    companion object {
        private const val BRIDGE_TAG = "FerricBridge"
        /** How far the tape may travel before the session's position is refreshed. */
        private const val POSITION_REFRESH_MS = 500L
    }
}
