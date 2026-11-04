package dev.foldman.deck

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import dev.foldman.BuildConfig
import dev.foldman.apple.AppleAuthExpired
import dev.foldman.apple.AppleKitBackend
import dev.foldman.apple.AppleMusicApi
import dev.foldman.apple.ApplePlaylist
import dev.foldman.apple.MusicKit
import dev.foldman.playback.LocalBackend
import dev.foldman.playback.LocalLibrary
import dev.foldman.remote.FerricBridge
import dev.foldman.remote.RemoteBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Cassette label colours, taken from the album art when there is some. */
data class LabelPalette(val paper: Color, val stripe: Color, val stripe2: Color, val ink: Color) {
    companion object {
        val Default = LabelPalette(Color(0xFFF2EBDC), Color(0xFFE8642C), Color(0xFFF2B33D), Color(0xFF2A2522))
    }
}

/**
 * One streaming source's held tape, for the stack's dual deck: the last track that source
 * played, its label colours, and how far through it the tape sits. A source that has not
 * played anything this session has no tape: [hasTrack] stays false and its well shows a
 * blank cassette instead.
 */
data class ParkedTape(
    val title: String = "",
    val artist: String = "",
    val artwork: ImageBitmap? = null,
    val label: LabelPalette = LabelPalette.Default,
    val progress: Float = 0f,
    val hasTrack: Boolean = false,
)

data class DeckUi(
    val source: Source = Source.LOCAL,
    val transport: Transport = Transport.STOPPED,
    val status: SourceStatus = SourceStatus.NeedsFolder,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artwork: ImageBitmap? = null,
    val label: LabelPalette = LabelPalette.Default,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val trackIndex: Int = -1,
    val trackCount: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val tracks: List<LocalTrack> = emptyList(),
    val folderName: String? = null,
    val volume: Float = 0.5f,
    val queue: List<QueueEntry> = emptyList(),
    val currentQueueId: Long = -1,
    val shuffleOn: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.NONE,
    val canShuffle: Boolean = false,
    val canRepeat: Boolean = false,
    val canSeek: Boolean = false,
    /** The sleep timer's setting in minutes (0 = off) and the time left on it, in ms. */
    val sleepMinutes: Int = 0,
    val sleepRemainingMs: Long = 0,
    val orientationMode: OrientationMode = OrientationMode.AUTO,
    val keySounds: Boolean = true,
    val haptics: Boolean = true,
    val apple: AppleUi = AppleUi(),
    /** The streaming sources' held tapes, one per well of the stack's dual deck. */
    val parked: Map<Source, ParkedTape> = emptyMap(),
) {
    /** How far through the tape we are: the left reel empties into the right one as this grows. */
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

sealed interface DeckEvent {
    /** The tape ran out while winding: the mechanism thunks back to stop. */
    data object TapeEnd : DeckEvent
    /** STOP pressed while already stopped: the cassette door pops. */
    data object Eject : DeckEvent
}

class DeckViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("foldman", Context.MODE_PRIVATE)
    private val audio = app.getSystemService(AudioManager::class.java)
    private val library = LocalLibrary(app)
    private val local = LocalBackend(app).also { it.connect() }
    /** In-app Apple Music, when MusicKit's playback AAR is in the build; otherwise the Apple Music app is driven remotely. */
    private val appleKit = if (MusicKit.hasPlayback) AppleKitBackend(app) else null
    private val apple = MutableStateFlow(AppleUi(sdkAuth = MusicKit.hasAuth, sdkPlayback = appleKit != null))
    private var appleJob: Job? = null
    private val remotes = mapOf(
        Source.YT_MUSIC to RemoteBackend(app, Source.YT_MUSIC),
        Source.APPLE_MUSIC to RemoteBackend(app, Source.APPLE_MUSIC),
    )

    private val _ui = MutableStateFlow(DeckUi())
    val ui: StateFlow<DeckUi> = _ui.asStateFlow()
    private val _events = MutableSharedFlow<DeckEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<DeckEvent> = _events

    private var source = runCatching { Source.valueOf(prefs.getString(KEY_SOURCE, null) ?: "") }.getOrDefault(Source.LOCAL)
    private val backend: DeckBackend
        get() = backendFor(source)

    private fun backendFor(s: Source): DeckBackend = when (s) {
        Source.LOCAL -> local
        Source.APPLE_MUSIC -> appleKit ?: remotes.getValue(s)
        else -> remotes.getValue(s)
    }

    /**
     * One streaming source's held tape, accumulated as the deck runs. The live source's hold
     * follows the deck itself; a parked source's hold is read back from its own backend,
     * which keeps its session bound and its place while the other source plays. A hold is
     * only ever replaced by a newer track, never cleared: once a source has played, its
     * well keeps that tape for the rest of the session.
     */
    private class TapeHold {
        var title = ""
        var artist = ""
        var hasTrack = false
        var artBitmap: ImageBitmap? = null
        var artSource: Bitmap? = null
        var paletteKey: Any? = null
        var palette: LabelPalette = LabelPalette.Default
        var progress = 0f
        fun snapshot() = ParkedTape(title, artist, artBitmap, palette, progress, hasTrack)
    }

    private val holds = mapOf(
        Source.YT_MUSIC to TapeHold(),
        Source.APPLE_MUSIC to TapeHold(),
    )

    private var tracks: List<LocalTrack> = emptyList()
    private var folderName: String? = null
    private var scanJob: Job? = null

    /** STOP was pressed (or the tape ended): show STOPPED rather than PAUSED while not playing. */
    private var stopped = true
    /** Play was just pressed; keep the play key down while the player catches up. */
    private var playRequestedAt = 0L
    /** -1 rewinding, +1 fast-forwarding, 0 neither. */
    private var winding = 0
    private var windPosition = 0L
    private var windLastTick = 0L
    private var windLastSeek = 0L
    private var windWaitingForTrack = -1

    private var paletteKey: Any? = null
    private var label = LabelPalette.Default
    private var artBitmap: ImageBitmap? = null
    private var artSource: Any? = null

    /** The sleep timer: the minutes it was set for (0 = off) and its deadline on the elapsed
     * clock. The tick loop counts it down in real time, so it survives every view change. */
    private var sleepMinutes = 0
    private var sleepDeadlineMs = 0L
    private var sleepRemainingMs = 0L

    /**
     * The bridge session's hands on the deck: every call runs through the same entry points
     * the on-screen keys use, so a Bluetooth button and a piano key can never disagree about
     * what play, pause, next or previous means. A bridge seek is converted to a fraction so
     * it passes through [seekToFraction]'s canSeek gate like any scrubber's.
     */
    private val bridgeTransport = object : FerricBridge.Transport {
        override fun play() = this@DeckViewModel.play()
        override fun pause() = this@DeckViewModel.pause()
        override fun next() = this@DeckViewModel.next()
        override fun previous() = this@DeckViewModel.previous()
        override fun seekTo(positionMs: Long) {
            val duration = backend.state.value.nowPlaying.durationMs
            if (duration > 0) seekToFraction(positionMs.toFloat() / duration)
        }
    }

    init {
        FerricBridge.transport = bridgeTransport
        prefs.getString(KEY_FOLDER, null)?.let { loadFolder(Uri.parse(it), firstLoad = true) }
        syncAppleTokens()
        if (apple.value.signedIn) loadApplePlaylists()
        appleKit?.let { kit ->
            viewModelScope.launch {
                kit.messages.collect { m -> if (m != null) apple.value = apple.value.copy(message = m) }
            }
        }
        backend.refresh()
        // Bind the streaming sessions up front too, so each dual deck well can learn its
        // source's held tape before that source is ever selected.
        remotes.values.forEach { it.refresh() }
        viewModelScope.launch {
            while (true) {
                tick()
                delay(if (winding != 0) 33 else 100)
            }
        }
    }

    // region Keys

    fun play() {
        if (winding != 0) finishWinding()
        stopped = false
        playRequestedAt = SystemClock.elapsedRealtime()
        backend.play()
        tick()
    }

    /**
     * A plain pause, with none of the pause key's toggle: the latched half of [pauseKey] and
     * the media button bridge both run through here, so every kind of pause means the same
     * thing. Already paused (or never started) is a no-op rather than a state flip.
     */
    fun pause() {
        if (!backend.state.value.isPlaying && playRequestedAt == 0L) return
        stopped = false
        playRequestedAt = 0
        backend.pause()
        tick()
    }

    fun pauseKey() {
        when (_ui.value.transport) {
            Transport.PLAYING -> pause()
            Transport.PAUSED -> play()
            else -> return
        }
        tick()
    }

    fun stop() {
        when {
            winding != 0 -> finishWinding()
            _ui.value.transport == Transport.STOPPED -> _events.tryEmit(DeckEvent.Eject)
            else -> backend.pause()
        }
        stopped = true
        playRequestedAt = 0
        tick()
    }

    fun rewind() = startWinding(-1)
    fun fastForward() = startWinding(+1)

    /** AMS back: to the top of this song, or the previous one when already near its start. */
    fun previous() {
        if (winding != 0) finishWinding()
        if (backend.positionMs() > 3000 || !_ui.value.hasPrevious) backend.seekTo(0) else backend.skipToPrevious()
        tick()
    }

    fun next() {
        if (winding != 0) finishWinding()
        backend.skipToNext()
        tick()
    }

    private fun startWinding(direction: Int) {
        if (winding == direction) return
        if (_ui.value.status != SourceStatus.Ready) return
        if (winding == 0) {
            backend.pause()
            windPosition = backend.positionMs()
        }
        winding = direction
        windLastTick = SystemClock.elapsedRealtime()
        windWaitingForTrack = -1
        playRequestedAt = 0
        tick()
    }

    private fun finishWinding() {
        winding = 0
        windWaitingForTrack = -1
        backend.seekTo(windPosition)
    }

    private fun tapeEnd() {
        finishWinding()
        stopped = true
        _events.tryEmit(DeckEvent.TapeEnd)
    }

    /** Moves the tape at [WIND_SPEED]× while REW/FF is latched, crossing into neighbouring songs like a real tape. */
    private fun windStep(state: BackendState) {
        val now = SystemClock.elapsedRealtime()
        val dt = now - windLastTick
        windLastTick = now
        val duration = state.nowPlaying.durationMs
        if (windWaitingForTrack >= 0) {
            // Just rewound into the previous song: wait until we know how long it is, then start from its end.
            if (state.trackIndex != windWaitingForTrack || duration <= 0) return
            windWaitingForTrack = -1
            windPosition = duration
        }
        windPosition += winding * dt * WIND_SPEED
        val isLocal = source == Source.LOCAL
        when {
            windPosition <= 0 -> {
                windPosition = 0
                if (isLocal && state.hasPrevious) {
                    windWaitingForTrack = state.trackIndex - 1
                    backend.skipToPrevious()
                } else tapeEnd()
            }
            duration > 0 && windPosition >= duration -> {
                if (state.hasNext) {
                    windPosition = 0
                    backend.skipToNext()
                    if (isLocal) backend.pause() else tapeEnd()
                } else {
                    windPosition = duration
                    tapeEnd()
                }
            }
            // Other apps' sessions get fewer seeks; ours can take one per frame.
            isLocal || now - windLastSeek > 250 -> {
                backend.seekTo(windPosition)
                windLastSeek = now
            }
        }
    }

    // endregion

    // region Sources

    fun selectSource(newSource: Source) {
        if (newSource == source) return
        if (winding != 0) finishWinding()
        // One tape in the deck at a time: ejecting stops the old one.
        if (backend.state.value.isPlaying) backend.pause()
        source = newSource
        prefs.edit { putString(KEY_SOURCE, newSource.name) }
        stopped = !backend.state.value.isPlaying
        backend.refresh()
        tick()
    }

    fun setFolder(uri: Uri) {
        prefs.edit { putString(KEY_FOLDER, uri.toString()) }
        selectSource(Source.LOCAL)
        loadFolder(uri, firstLoad = false)
    }

    fun playTrack(index: Int) {
        selectSource(Source.LOCAL)
        if (winding != 0) finishWinding()
        stopped = false
        playRequestedAt = SystemClock.elapsedRealtime()
        local.playTrack(index)
    }

    private fun loadFolder(uri: Uri, firstLoad: Boolean) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            local.setLibraryStatus(SourceStatus.Scanning(0))
            val found = runCatching {
                library.scan(uri) { n -> viewModelScope.launch { local.setLibraryStatus(SourceStatus.Scanning(n)) } }
            }.getOrElse {
                local.setLibraryStatus(SourceStatus.NeedsFolder)
                return@launch
            }
            folderName = found.firstOrNull()?.folder ?: uri.lastPathSegment?.substringAfterLast(':')
            tracks = found
            local.setTracks(found, startIndex = if (firstLoad) null else 0)
            if (!firstLoad) stopped = true
            tick()
            val tagged = library.readTags(found)
            tracks = tagged
            local.setTracks(tagged)
            tick()
        }
    }

    fun refresh() {
        backend.refresh()
        tick()
    }

    fun openAccessSettings() {
        val app = getApplication<Application>()
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(intent) }
    }

    fun openSourceApp() {
        (remotes[source])?.openApp()
    }

    private var queueFor: List<LocalTrack>? = null
    private var queueCache: List<QueueEntry> = emptyList()

    private fun localQueue(): List<QueueEntry> {
        if (queueFor !== tracks) {
            queueFor = tracks
            queueCache = tracks.mapIndexed { i, t ->
                QueueEntry(i.toLong(), t.title, t.artist ?: t.folder, artworkUri = t.coverUri)
            }
        }
        return queueCache
    }

    fun playQueueItem(id: Long) {
        if (source == Source.LOCAL) return playTrack(id.toInt())
        if (winding != 0) finishWinding()
        stopped = false
        playRequestedAt = SystemClock.elapsedRealtime()
        backend.playQueueItem(id)
    }

    // region Shuffle and repeat

    /** Flips shuffle on the active source. The backend updates its own state optimistically,
     * so the tick below already shows the new setting; the next state read reconciles it. */
    fun toggleShuffle() {
        backend.setShuffle(!_ui.value.shuffleOn)
        tick()
    }

    /** Cycles repeat OFF to ALL to ONE to OFF on the active source, same optimistic path. */
    fun cycleRepeat() {
        backend.setRepeatMode(
            when (_ui.value.repeatMode) {
                RepeatMode.NONE -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.NONE
            },
        )
        tick()
    }

    // endregion

    // region Seek

    /**
     * Moves the tape to a fraction of the current track. Only sources that advertise seek
     * (see BackendState.canSeek) and tracks with a known length take it; anything else is a
     * no-op, so a scrubber on a source that cannot seek never promises what it cannot do.
     */
    fun seekToFraction(fraction: Float) {
        val duration = backend.state.value.nowPlaying.durationMs
        if (!backend.state.value.canSeek || duration <= 0) return
        if (winding != 0) finishWinding()
        backend.seekTo((fraction.coerceIn(0f, 1f) * duration).toLong())
        tick()
    }

    // endregion

    // region Sleep timer

    /** Cycles the sleep timer OFF to 15 to 30 to 45 to 60 minutes and back to OFF. */
    fun cycleSleepTimer() {
        setSleepMinutes(
            when (sleepMinutes) {
                0 -> 15
                15 -> 30
                30 -> 45
                45 -> 60
                else -> 0
            },
        )
    }

    /** Long press on SLEEP: the timer is cancelled at once. */
    fun cancelSleepTimer() = setSleepMinutes(0)

    private fun setSleepMinutes(minutes: Int) {
        sleepMinutes = minutes
        if (minutes <= 0) {
            sleepDeadlineMs = 0
            sleepRemainingMs = 0
        } else {
            sleepRemainingMs = minutes * 60_000L
            sleepDeadlineMs = SystemClock.elapsedRealtime() + sleepRemainingMs
        }
        tick()
    }

    /** The timer ran out: pause through the standard pause path and disarm. */
    private fun expireSleepTimer(state: BackendState) {
        sleepMinutes = 0
        sleepDeadlineMs = 0
        sleepRemainingMs = 0
        if (state.isPlaying) {
            stopped = false
            playRequestedAt = 0
            backend.pause()
        }
    }

    // endregion

    // region Orientation

    fun setOrientationMode(mode: OrientationMode) {
        prefs.edit { putString(KEY_ORIENTATION, mode.name) }
        tick()
    }

    private fun orientationMode(): OrientationMode =
        runCatching { OrientationMode.valueOf(prefs.getString(KEY_ORIENTATION, null) ?: "") }
            .getOrDefault(OrientationMode.AUTO)

    // endregion

    fun setKeySounds(on: Boolean) {
        prefs.edit { putBoolean(KEY_SOUNDS, on) }
        tick()
    }

    fun setHaptics(on: Boolean) {
        prefs.edit { putBoolean(KEY_HAPTICS, on) }
        tick()
    }

    // region Apple Music

    private val developerToken: String?
        get() = prefs.getString(KEY_APPLE_DEV, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.APPLE_DEVELOPER_TOKEN.takeIf { it.isNotBlank() }
    private val musicUserToken: String? get() = prefs.getString(KEY_APPLE_USER, null)

    private fun syncAppleTokens() {
        appleKit?.setTokens(developerToken, musicUserToken)
        apple.value = apple.value.copy(hasDeveloperToken = developerToken != null, signedIn = musicUserToken != null)
    }

    fun setAppleDeveloperToken(token: String) {
        prefs.edit { putString(KEY_APPLE_DEV, token.trim()) }
        syncAppleTokens()
        tick()
    }

    /** Apple's sign-in screen, or null when it can't be shown (no SDK or no developer token). */
    fun appleSignInIntent(): Intent? {
        val dev = developerToken ?: return null
        return MusicKit.signInIntent(getApplication(), dev).also {
            if (it == null) apple.value = apple.value.copy(message = "MusicKit couldn't start sign-in.")
        }
    }

    fun onAppleSignInResult(data: Intent?) {
        MusicKit.readSignInResult(getApplication(), data)
            .onSuccess { token ->
                prefs.edit { putString(KEY_APPLE_USER, token) }
                apple.value = apple.value.copy(message = null)
                syncAppleTokens()
                loadApplePlaylists()
            }
            .onFailure { e -> apple.value = apple.value.copy(message = "Sign-in didn't finish (${e.message}).") }
        tick()
    }

    fun signOutOfApple() {
        prefs.edit { remove(KEY_APPLE_USER) }
        apple.value = apple.value.copy(playlists = emptyList(), openPlaylist = null, tracks = emptyList())
        syncAppleTokens()
        tick()
    }

    fun loadApplePlaylists() = appleCall { api ->
        apple.value = apple.value.copy(playlists = api.playlists())
    }

    fun openApplePlaylist(playlist: ApplePlaylist) {
        apple.value = apple.value.copy(openPlaylist = playlist, tracks = emptyList())
        appleCall { api ->
            val tracks = api.tracks(playlist.id)
            if (apple.value.openPlaylist?.id == playlist.id) apple.value = apple.value.copy(tracks = tracks)
        }
    }

    fun closeApplePlaylist() {
        apple.value = apple.value.copy(openPlaylist = null, tracks = emptyList())
        tick()
    }

    /** Plays the open playlist from [trackIndex]: in-app with MusicKit, otherwise handed to the Apple Music app. */
    fun playApplePlaylist(trackIndex: Int = 0) {
        val ui = apple.value
        val playlist = ui.openPlaylist ?: return
        selectSource(Source.APPLE_MUSIC)
        val kit = appleKit
        if (kit == null) {
            openInAppleMusic(playlist)
            return
        }
        // Uploaded songs have no catalog id and can't be streamed; skip them, keeping the start song.
        val playable = ui.tracks.withIndex().filter { it.value.catalogId != null }
        if (playable.isEmpty()) {
            apple.value = ui.copy(message = "Nothing in this playlist can be streamed.")
            return
        }
        val start = playable.indexOfFirst { it.index >= trackIndex }.coerceAtLeast(0)
        if (winding != 0) finishWinding()
        stopped = false
        playRequestedAt = SystemClock.elapsedRealtime()
        kit.messages.value = null
        apple.value = apple.value.copy(message = null)
        kit.playSongs(playable.map { it.value.catalogId!! }, start)
        tick()
    }

    private fun openInAppleMusic(playlist: ApplePlaylist) {
        val url = playlist.globalId?.let { "https://music.apple.com/playlist/$it" }
            ?: "https://music.apple.com/library/playlist/${playlist.id}"
        val base = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        val app = getApplication<Application>()
        runCatching { app.startActivity(Intent(base).setPackage(Source.APPLE_MUSIC.packageName)) }
            .recoverCatching { app.startActivity(base) }
    }

    private fun appleCall(block: suspend (AppleMusicApi) -> Unit) {
        val dev = developerToken ?: return
        val user = musicUserToken ?: return
        appleJob?.cancel()
        appleJob = viewModelScope.launch {
            apple.value = apple.value.copy(loading = true, message = null)
            try {
                block(AppleMusicApi(dev, user))
            } catch (e: AppleAuthExpired) {
                prefs.edit { remove(KEY_APPLE_USER) }
                syncAppleTokens()
                apple.value = apple.value.copy(message = "Apple Music sign-in expired or the developer token was rejected. Sign in again.")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                apple.value = apple.value.copy(message = e.message ?: "Couldn't reach Apple Music.")
            } finally {
                apple.value = apple.value.copy(loading = false)
                tick()
            }
        }
    }

    // endregion

    fun setVolume(fraction: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (fraction.coerceIn(0f, 1f) * max).roundToInt()
        if (target != audio.getStreamVolume(AudioManager.STREAM_MUSIC)) {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
        _ui.value = _ui.value.copy(volume = fraction.coerceIn(0f, 1f))
    }

    // endregion

    private fun tick() {
        val state = backend.state.value
        val now = SystemClock.elapsedRealtime()
        // The sleep timer counts real time on the deck's own clock loop; when it runs out
        // the tape pauses and the timer disarms itself.
        if (sleepDeadlineMs != 0L) {
            if (now >= sleepDeadlineMs) expireSleepTimer(state)
            else sleepRemainingMs = sleepDeadlineMs - now
        }
        if (winding != 0) windStep(state)
        if (state.ended) stopped = true
        if (state.isPlaying && winding == 0) {
            stopped = false
            playRequestedAt = 0
        }
        val playPending = now - playRequestedAt < 1500
        val transport = when {
            winding < 0 -> Transport.REWINDING
            winding > 0 -> Transport.FAST_FORWARDING
            state.isPlaying || playPending -> Transport.PLAYING
            stopped -> Transport.STOPPED
            else -> Transport.PAUSED
        }
        val np = state.nowPlaying
        updateLabel(np)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
        val prev = _ui.value
        val posMs = if (winding != 0) windPosition else backend.positionMs()
        refreshHolds(np, posMs)
        _ui.value = DeckUi(
            source = source,
            transport = transport,
            status = state.status,
            title = np.title.orEmpty(),
            artist = np.artist.orEmpty(),
            album = np.album.orEmpty(),
            artwork = artBitmap,
            label = label,
            positionMs = posMs,
            durationMs = np.durationMs,
            trackIndex = state.trackIndex,
            trackCount = state.trackCount,
            hasNext = state.hasNext,
            hasPrevious = state.hasPrevious,
            tracks = tracks,
            folderName = folderName,
            // While the wheel is being turned our own value leads; otherwise follow the hardware keys.
            volume = if (kotlin.math.abs(prev.volume - volume) * max < 1f) prev.volume else volume,
            queue = if (source == Source.LOCAL) localQueue() else state.queue,
            currentQueueId = if (source == Source.LOCAL) state.trackIndex.toLong() else state.currentQueueId,
            shuffleOn = state.shuffleOn,
            repeatMode = state.repeatMode,
            canShuffle = state.canShuffle,
            canRepeat = state.canRepeat,
            canSeek = state.canSeek,
            sleepMinutes = sleepMinutes,
            sleepRemainingMs = sleepRemainingMs,
            orientationMode = orientationMode(),
            keySounds = prefs.getBoolean(KEY_SOUNDS, true),
            haptics = prefs.getBoolean(KEY_HAPTICS, true),
            apple = apple.value,
            parked = holds.mapValues { it.value.snapshot() },
        )
        publishBridge(state, np, posMs)
    }

    /**
     * Keeps the bridge media session mirroring the live well: the current source's track,
     * artwork and play state, refreshed on every tick (including when a source pauses under
     * its own earbud handling, which the backend state reports within a tick). Source
     * switches are followed automatically, since whatever the deck shows is what is mirrored.
     * The local source is never mirrored: its playback service runs a session of its own, and
     * the bridge stands down so local keeps exactly the button behaviour it always had.
     */
    private fun publishBridge(state: BackendState, np: NowPlaying, posMs: Long) {
        if (source == Source.LOCAL) {
            FerricBridge.publish(null)
            return
        }
        FerricBridge.publish(
            FerricBridge.State(
                sourceLabel = source.label,
                title = np.title.orEmpty(),
                artist = np.artist.orEmpty(),
                album = np.album.orEmpty(),
                artwork = np.artwork,
                durationMs = np.durationMs,
                positionMs = posMs,
                isPlaying = state.isPlaying,
                canSeek = state.canSeek,
            ),
        )
    }

    /**
     * Keeps the dual deck's held tapes current. The live source's hold follows the deck
     * itself; each parked streaming source's hold is read from its own backend, whose state
     * flow retains the last track it played and whose position keeps its place.
     */
    private fun refreshHolds(np: NowPlaying, posMs: Long) {
        if (source != Source.LOCAL) {
            val hold = holds.getValue(source)
            if (!np.title.isNullOrBlank()) {
                hold.title = np.title.orEmpty()
                hold.artist = np.artist.orEmpty()
                hold.hasTrack = true
            }
            hold.artBitmap = artBitmap
            hold.palette = label
            if (np.durationMs > 0) {
                hold.progress = (posMs.toFloat() / np.durationMs).coerceIn(0f, 1f)
            }
        }
        for (s in holds.keys) {
            if (s == source) continue
            val b = backendFor(s)
            val pnp = b.state.value.nowPlaying
            val title = pnp.title
            if (title.isNullOrBlank()) continue
            val hold = holds.getValue(s)
            hold.title = title
            hold.artist = pnp.artist.orEmpty()
            hold.hasTrack = true
            val bitmap = pnp.artwork
            if (bitmap != null) {
                if (hold.artSource !== bitmap) {
                    hold.artSource = bitmap
                    hold.artBitmap = bitmap.asImageBitmap()
                }
                refreshHoldPalette(hold, bitmap, pnp.artworkKey)
            } else if (hold.artSource != null) {
                hold.artSource = null
                hold.artBitmap = null
                hold.paletteKey = null
                hold.palette = LabelPalette.Default
            }
            if (pnp.durationMs > 0) {
                hold.progress = (b.positionMs().toFloat() / pnp.durationMs).coerceIn(0f, 1f)
            }
        }
    }

    /** The parked wells' label colours come off their own artwork, by the live deck's rule. */
    private fun refreshHoldPalette(hold: TapeHold, bitmap: Bitmap, key: Any?) {
        val k = key ?: bitmap
        if (k == hold.paletteKey) return
        hold.paletteKey = k
        viewModelScope.launch {
            val palette = withContext(Dispatchers.Default) { Palette.from(bitmap).maximumColorCount(16).generate() }
            if (hold.paletteKey != k) return@launch
            hold.palette = paletteFrom(palette)
            tick()
        }
    }

    /** The label palette rule, shared by the live deck and the parked wells. */
    private fun paletteFrom(palette: Palette): LabelPalette {
        val d = LabelPalette.Default
        return LabelPalette(
            paper = palette.lightMutedSwatch?.rgb?.let { blend(Color(it), d.paper, 0.75f) } ?: d.paper,
            stripe = (palette.vibrantSwatch ?: palette.dominantSwatch)?.rgb?.let(::Color) ?: d.stripe,
            stripe2 = (palette.lightVibrantSwatch ?: palette.mutedSwatch)?.rgb?.let(::Color) ?: d.stripe2,
            ink = palette.darkMutedSwatch?.rgb?.let { blend(Color(it), d.ink, 0.5f) } ?: d.ink,
        )
    }

    private fun updateLabel(np: NowPlaying) {
        val bitmap = np.artwork
        if (bitmap == null) {
            artBitmap = null
            artSource = null
            paletteKey = null
            label = LabelPalette.Default
            return
        }
        if (artSource !== bitmap) {
            artSource = bitmap
            artBitmap = bitmap.asImageBitmap()
        }
        val key = np.artworkKey ?: bitmap
        if (key == paletteKey) return
        paletteKey = key
        viewModelScope.launch {
            val palette = withContext(Dispatchers.Default) { Palette.from(bitmap).maximumColorCount(16).generate() }
            if (paletteKey != key) return@launch
            label = paletteFrom(palette)
            tick()
        }
    }

    override fun onCleared() {
        if (FerricBridge.transport === bridgeTransport) {
            FerricBridge.transport = null
            FerricBridge.publish(null)
        }
        local.release()
        remotes.values.forEach { it.release() }
        appleKit?.release()
    }

    companion object {
        private const val KEY_SOURCE = "source"
        private const val KEY_FOLDER = "folder"
        private const val KEY_SOUNDS = "key_sounds"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_APPLE_DEV = "apple_developer_token"
        private const val KEY_APPLE_USER = "apple_music_user_token"
        private const val KEY_ORIENTATION = "orientation_mode"
        /** Winding speed relative to play: a four-minute song winds through in about 20 s. */
        const val WIND_SPEED = 12L

        private fun blend(a: Color, b: Color, t: Float) = Color(
            red = a.red * (1 - t) + b.red * t,
            green = a.green * (1 - t) + b.green * t,
            blue = a.blue * (1 - t) + b.blue * t,
        )
    }
}
