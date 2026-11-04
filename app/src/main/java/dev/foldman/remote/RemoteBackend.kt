package dev.foldman.remote

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat
import dev.foldman.deck.BackendState
import dev.foldman.deck.DeckBackend
import dev.foldman.deck.NowPlaying
import dev.foldman.deck.QueueEntry
import dev.foldman.deck.RepeatMode
import dev.foldman.deck.Source
import dev.foldman.deck.SourceStatus
import dev.foldman.playback.Artwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Plays YT Music or Apple Music by driving that app's own media session: Foldman is the deck,
 * the streaming app is the tape. Needs notification access ([MediaListenerService]) to see the session.
 */
class RemoteBackend(private val context: Context, source: Source) : DeckBackend {
    private val pkg = requireNotNull(source.packageName)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, MediaListenerService::class.java)

    private val _state = MutableStateFlow(BackendState(status = SourceStatus.AppNotRunning))
    override val state: StateFlow<BackendState> = _state.asStateFlow()

    private var controller: MediaController? = null
    /**
     * The same session seen through the compat API, which is the public route to its shuffle
     * and repeat modes: the framework controller's SDK surface hides both. Kept beside the
     * framework controller and used for nothing else.
     */
    private var controllerCompat: MediaControllerCompat? = null
    private var watchingSessions = false
    private var artworkKey: String? = null
    private var artwork: Bitmap? = null
    private var artworkJob: Job? = null

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list -> bind(list.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) = publish()
        override fun onSessionDestroyed() {
            unbind()
            publish()
        }
    }

    /** Shuffle and repeat live on the compat side of the session; its mode changes republish. */
    private val compatCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) = publish()
        override fun onShuffleModeChanged(shuffleMode: Int) = publish()
        override fun onRepeatModeChanged(repeatMode: Int) = publish()
    }

    val hasAccess: Boolean
        get() = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    val isInstalled: Boolean
        get() = context.packageManager.getLaunchIntentForPackage(pkg) != null

    override fun refresh() {
        if (!hasAccess) {
            unbind()
            publish()
            return
        }
        try {
            if (!watchingSessions) {
                sessions.addOnActiveSessionsChangedListener(sessionsChanged, listenerComponent, handler)
                watchingSessions = true
            }
            bind(sessions.getActiveSessions(listenerComponent))
        } catch (_: SecurityException) {
            publish()
        }
    }

    private fun bind(active: List<MediaController>) {
        val match = active.firstOrNull { it.packageName == pkg }
        if (match?.sessionToken == controller?.sessionToken) {
            publish()
            return
        }
        unbind()
        controller = match?.also { it.registerCallback(callback, handler) }
        controllerCompat = match?.let { c ->
            runCatching {
                MediaControllerCompat(context, MediaSessionCompat.Token.fromToken(c.sessionToken))
                    .also { it.registerCallback(compatCallback, handler) }
            }.getOrNull()
        }
        publish()
    }

    private fun unbind() {
        controller?.unregisterCallback(callback)
        controller = null
        controllerCompat?.unregisterCallback(compatCallback)
        controllerCompat = null
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            _state.value = BackendState(
                status = when {
                    !hasAccess -> SourceStatus.NeedsAccess
                    !isInstalled -> SourceStatus.NotInstalled
                    else -> SourceStatus.AppNotRunning
                },
            )
            return
        }
        val meta = c.metadata
        val pb = c.playbackState
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val album = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM)
        updateArtwork(meta, "$title|$artist|$album")
        val actions = pb?.actions ?: 0L
        _state.value = BackendState(
            status = SourceStatus.Ready,
            nowPlaying = NowPlaying(
                title = title,
                artist = artist,
                album = album,
                artwork = artwork,
                artworkKey = artworkKey.takeIf { artwork != null },
                durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0L,
            ),
            isPlaying = pb?.state == PlaybackState.STATE_PLAYING || pb?.state == PlaybackState.STATE_BUFFERING,
            ended = false,
            hasNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            hasPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            queue = c.queue.orEmpty().take(200).map { q ->
                QueueEntry(
                    id = q.queueId,
                    title = q.description.title?.toString() ?: "",
                    subtitle = q.description.subtitle?.toString(),
                    artwork = q.description.iconBitmap,
                    artworkUri = q.description.iconUri,
                )
            },
            currentQueueId = pb?.activeQueueItemId ?: -1,
            // Shuffle and repeat come from the compat view of the same session. Until it
            // reports them, both read as off and unsettable rather than guessed.
            shuffleOn = compatShuffleMode() == PlaybackStateCompat.SHUFFLE_MODE_ALL ||
                compatShuffleMode() == PlaybackStateCompat.SHUFFLE_MODE_GROUP,
            repeatMode = when (compatRepeatMode()) {
                PlaybackStateCompat.REPEAT_MODE_ALL, PlaybackStateCompat.REPEAT_MODE_GROUP -> RepeatMode.ALL
                PlaybackStateCompat.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.NONE
            },
            canShuffle = compatActions() and PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE != 0L,
            canRepeat = compatActions() and PlaybackStateCompat.ACTION_SET_REPEAT_MODE != 0L,
            // Seek is honoured only when the session advertises it in its current actions;
            // a session that never offers ACTION_SEEK_TO keeps the deck's bars display only.
            canSeek = compatActions() and PlaybackStateCompat.ACTION_SEEK_TO != 0L,
        )
    }

    private fun compatShuffleMode(): Int =
        controllerCompat?.let { runCatching { it.shuffleMode }.getOrNull() } ?: PlaybackStateCompat.SHUFFLE_MODE_INVALID

    private fun compatRepeatMode(): Int =
        controllerCompat?.let { runCatching { it.repeatMode }.getOrNull() } ?: PlaybackStateCompat.REPEAT_MODE_INVALID

    private fun compatActions(): Long = controllerCompat?.playbackState?.actions ?: 0L

    private fun updateArtwork(meta: MediaMetadata?, key: String) {
        if (key == artworkKey && (artwork != null || artworkJob?.isActive == true)) return
        if (key != artworkKey) artwork = null
        artworkKey = key
        val bitmap = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (bitmap != null) {
            artwork = bitmap
            return
        }
        val uri = listOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
        ).firstNotNullOfOrNull { meta?.getString(it)?.takeIf(String::isNotBlank) } ?: return
        artworkJob?.cancel()
        artworkJob = scope.launch {
            val bmp = Artwork.load(context, Uri.parse(uri))
            if (artworkKey == key && bmp != null) {
                artwork = bmp
                publish()
            }
        }
    }

    override fun positionMs(): Long {
        val pb = controller?.playbackState ?: return 0L
        if (pb.position == PlaybackState.PLAYBACK_POSITION_UNKNOWN) return 0L
        if (pb.state != PlaybackState.STATE_PLAYING) return pb.position
        val elapsed = SystemClock.elapsedRealtime() - pb.lastPositionUpdateTime
        return pb.position + (elapsed * pb.playbackSpeed).toLong()
    }

    override fun play() {
        val c = controller
        if (c != null) {
            c.transportControls.play()
            return
        }
        // No session yet: ask the app to resume its last queue, then open it next to us if it doesn't.
        sendMediaButton(KeyEvent.KEYCODE_MEDIA_PLAY)
        handler.postDelayed({ if (controller == null) openApp() }, 2500)
    }

    override fun pause() { controller?.transportControls?.pause() }
    override fun seekTo(positionMs: Long) {
        // Seeks go through the compat view of the session, the same side that reports
        // whether seeking is offered at all; the framework controller is the fallback.
        val compat = controllerCompat
        if (compat != null) compat.transportControls.seekTo(positionMs)
        else controller?.transportControls?.seekTo(positionMs)
    }
    override fun playQueueItem(id: Long) { controller?.transportControls?.skipToQueueItem(id) }
    override fun skipToNext() { controller?.transportControls?.skipToNext() }
    override fun skipToPrevious() { controller?.transportControls?.skipToPrevious() }

    override fun setShuffle(on: Boolean) {
        val compat = controllerCompat ?: return
        compat.transportControls.setShuffleMode(
            if (on) PlaybackStateCompat.SHUFFLE_MODE_ALL else PlaybackStateCompat.SHUFFLE_MODE_NONE,
        )
        // Show the new setting at once; the session's next state report reconciles it.
        _state.value = _state.value.copy(shuffleOn = on)
    }

    override fun setRepeatMode(mode: RepeatMode) {
        val compat = controllerCompat ?: return
        compat.transportControls.setRepeatMode(
            when (mode) {
                RepeatMode.NONE -> PlaybackStateCompat.REPEAT_MODE_NONE
                RepeatMode.ALL -> PlaybackStateCompat.REPEAT_MODE_ALL
                RepeatMode.ONE -> PlaybackStateCompat.REPEAT_MODE_ONE
            },
        )
        _state.value = _state.value.copy(repeatMode = mode)
    }

    /** Opens the streaming app, beside Foldman when the screen is unfolded. */
    fun openApp() {
        val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        runCatching { context.startActivity(launch) }
    }

    private fun sendMediaButton(code: Int) {
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
                .setPackage(pkg)
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, code))
            runCatching { context.sendBroadcast(intent) }
        }
    }

    override fun release() {
        if (watchingSessions) sessions.removeOnActiveSessionsChangedListener(sessionsChanged)
        unbind()
        scope.cancel()
    }
}
