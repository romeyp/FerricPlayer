package dev.foldman.deck

import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

/** Where the tape in the deck comes from. */
enum class Source(val label: String, val packageName: String?) {
    LOCAL("Local folder", null),
    YT_MUSIC("YT Music", "com.google.android.apps.youtube.music"),
    APPLE_MUSIC("Apple Music", "com.apple.android.music"),
}

/**
 * What the mechanism is doing. PAUSED keeps the play key latched with the pinch roller lifted;
 * STOPPED releases every key. The reels animate from this.
 */
enum class Transport { STOPPED, PLAYING, PAUSED, REWINDING, FAST_FORWARDING }

/** The deck's repeat setting, mapped onto each backend's own constants. */
enum class RepeatMode { NONE, ALL, ONE }

/** The deck's orientation setting: follow the phone, or hold the deck portrait or landscape. */
enum class OrientationMode { AUTO, PORTRAIT, LANDSCAPE }

sealed interface SourceStatus {
    data object Ready : SourceStatus
    data object NeedsFolder : SourceStatus
    data class Scanning(val found: Int) : SourceStatus
    data object EmptyFolder : SourceStatus
    data object NeedsAccess : SourceStatus
    data object AppNotRunning : SourceStatus
    data object NotInstalled : SourceStatus
    data object NeedsDeveloperToken : SourceStatus
    data object NeedsAppleSignIn : SourceStatus
}

/** What the tape shelf knows about the user's Apple Music library (MusicKit sign-in + Apple Music API). */
data class AppleUi(
    /** MusicKit's auth AAR is in the build, so Foldman can sign in and browse. */
    val sdkAuth: Boolean = false,
    /** MusicKit's playback AAR is in the build, so Foldman streams in-app instead of driving the Apple Music app. */
    val sdkPlayback: Boolean = false,
    val hasDeveloperToken: Boolean = false,
    val signedIn: Boolean = false,
    val playlists: List<dev.foldman.apple.ApplePlaylist> = emptyList(),
    val openPlaylist: dev.foldman.apple.ApplePlaylist? = null,
    val tracks: List<dev.foldman.apple.AppleTrack> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null,
)

data class LocalTrack(
    val uri: Uri,
    val title: String,
    val artist: String?,
    val folder: String,
    val coverUri: Uri?,
)

/** A song in the play queue: a local file (id = index) or another app's queue item. */
data class QueueEntry(
    val id: Long,
    val title: String,
    val subtitle: String?,
    val artwork: Bitmap? = null,
    val artworkUri: Uri? = null,
)

data class NowPlaying(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artwork: Bitmap? = null,
    /** Changes whenever the artwork does, so the label palette is only worked out once per cover. */
    val artworkKey: Any? = null,
    val durationMs: Long = 0,
)

data class BackendState(
    val status: SourceStatus = SourceStatus.Ready,
    val nowPlaying: NowPlaying = NowPlaying(),
    val isPlaying: Boolean = false,
    val ended: Boolean = false,
    val trackIndex: Int = -1,
    val trackCount: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val queue: List<QueueEntry> = emptyList(),
    val currentQueueId: Long = -1,
    /** Shuffle and repeat, read from the source itself; the can* flags say whether the source
     * lets us set them, so the deck never lights a toggle it cannot honour. */
    val shuffleOn: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.NONE,
    val canShuffle: Boolean = false,
    val canRepeat: Boolean = false,
    /** Whether this source accepts a seek to an arbitrary position; the deck only turns its
     * progress lines into scrubbers for sources that say yes. */
    val canSeek: Boolean = false,
)

/** One tape source: the local Media3 player, or another app's media session. */
interface DeckBackend {
    val state: StateFlow<BackendState>
    fun positionMs(): Long
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun skipToNext()
    fun skipToPrevious()
    fun playQueueItem(id: Long)
    /** Shuffle and repeat commands. Backends that cannot honour them leave these as no-ops
     * and keep canShuffle / canRepeat false in their state. */
    fun setShuffle(on: Boolean) {}
    fun setRepeatMode(mode: RepeatMode) {}
    /** Re-checks permissions and sessions, e.g. when the app comes back to the foreground. */
    fun refresh() {}
    fun release()
}
