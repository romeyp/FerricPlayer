package dev.foldman.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import dev.foldman.deck.DeckUi
import dev.foldman.deck.QueueEntry
import dev.foldman.deck.RepeatMode
import dev.foldman.deck.Source
import dev.foldman.deck.SourceStatus
import dev.foldman.deck.Transport
import dev.foldman.playback.Artwork

private val Title = TextStyle(color = Color(0xFFF4EFE9), fontSize = 15.sp, fontWeight = FontWeight.Medium)
private val Sub = TextStyle(color = Color(0xFFA79C92), fontSize = 12.sp)
private val Header = TextStyle(color = Color(0xFFF4EFE9), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

/** Smoked dark glass the library and now-playing sit behind on the unfolded screen. */
fun Modifier.glassPanel(corner: Dp = 18.dp): Modifier = drawWithCache {
    val r = CornerRadius(corner.toPx())
    val fill = Brush.verticalGradient(listOf(Color(0xFF2E2622), Color(0xFF1E1916)))
    onDrawBehind {
        drawRoundRect(Color.Black.copy(alpha = 0.5f), topLeft = Offset(0f, 3f), cornerRadius = r)
        drawRoundRect(fill, cornerRadius = r)
        drawRoundRect(Color.White.copy(alpha = 0.08f), cornerRadius = r, style = Stroke(1.5f))
    }
}

/** Library band: sources, then albums (folders) or the streaming app's state, then the queue. */
@Composable
fun LibraryBand(ui: DeckUi, actions: ShelfActions, onPlayQueue: (Long) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.glassPanel().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Scrolls if the panel is ever too short for every source.
        Column(Modifier.weight(0.3f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            Text("Sources", style = Header)
            Spacer(Modifier.height(6.dp))
            Source.entries.forEach { s ->
                val selected = s == ui.source
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Color.White.copy(alpha = 0.09f) else Color.Transparent)
                        .clickable { actions.selectSource(s) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SourceTile(s, Modifier.size(30.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.label, style = Title, maxLines = 1)
                        Text(if (selected) statusLine(ui) else "Tap to load", style = Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Column(Modifier.weight(0.36f).fillMaxHeight()) {
            when (ui.source) {
                Source.LOCAL -> Albums(ui, actions)
                Source.APPLE_MUSIC -> {
                    Text(ui.apple.openPlaylist?.let { "Playlist" } ?: "Playlists", style = Header)
                    Spacer(Modifier.height(8.dp))
                    AppleBrowser(ui.apple, actions.apple, fallback = { RemoteState(ui, actions, showHeader = false) })
                }
                else -> RemoteState(ui, actions)
            }
        }
        Column(Modifier.weight(0.34f).fillMaxHeight()) {
            Text("Queue", style = Header)
            Spacer(Modifier.height(8.dp))
            QueueList(ui, onPlayQueue)
        }
    }
}

private fun statusLine(ui: DeckUi): String = when (val s = ui.status) {
    SourceStatus.Ready -> if (ui.source == Source.LOCAL) "${ui.tracks.size} songs" else "Connected"
    SourceStatus.NeedsFolder -> "Choose a folder"
    is SourceStatus.Scanning -> "Scanning… ${s.found}"
    SourceStatus.EmptyFolder -> "No music found"
    SourceStatus.NeedsAccess -> "Needs notification access"
    SourceStatus.AppNotRunning -> "Not playing"
    SourceStatus.NotInstalled -> "Not installed"
    SourceStatus.NeedsDeveloperToken -> "Needs developer token"
    SourceStatus.NeedsAppleSignIn -> "Sign in to browse"
}

@Composable
private fun SourceTile(source: Source, modifier: Modifier) {
    val shell = DeckColors.shell(source)
    Box(
        modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Brush.linearGradient(listOf(androidx.compose.ui.graphics.lerp(shell, Color.White, 0.25f), shell))),
        contentAlignment = Alignment.Center,
    ) {
        // A tiny cassette window.
        Box(Modifier.fillMaxWidth(0.66f).height(9.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF1A1714)))
    }
}

@Composable
private fun Albums(ui: DeckUi, actions: ShelfActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Albums", style = Header, modifier = Modifier.weight(1f))
        Text(
            if (ui.status == SourceStatus.NeedsFolder) "Choose folder" else "Change folder",
            style = Sub.copy(color = DeckColors.Accent, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.clickable(onClick = actions.pickFolder).padding(4.dp),
        )
    }
    Spacer(Modifier.height(8.dp))
    when (val s = ui.status) {
        SourceStatus.NeedsFolder -> Text("Pick a folder of music on this phone. Covers come from the files or a cover.jpg beside them.", style = Sub)
        is SourceStatus.Scanning -> Text("Scanning… ${s.found} songs", style = Sub)
        SourceStatus.EmptyFolder -> Text("No music in that folder.", style = Sub)
        else -> {
            // Each folder is an album: its first track's index, cover and size.
            val albums = remember(ui.tracks) {
                ui.tracks.withIndex().groupBy { it.value.folder }.map { (folder, items) ->
                    Triple(folder, items.first(), items.size)
                }
            }
            LazyColumn {
                items(albums, key = { it.first }) { (folder, first, count) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { actions.playTrack(first.index) }
                            .padding(vertical = 5.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Thumb(null, first.value.coverUri, Modifier.size(38.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(folder, style = Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${first.value.artist ?: "Album"} · $count songs", style = Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteState(ui: DeckUi, actions: ShelfActions, showHeader: Boolean = true) {
    val app = ui.source.label
    if (showHeader) {
        Text(app, style = Header)
        Spacer(Modifier.height(8.dp))
    }
    val (text, button, action) = when (ui.status) {
        SourceStatus.NeedsAccess -> Triple(
            "Foldman plays $app through its media session, which Android only shares with apps that have notification access. If the switch is greyed out: App info → ⋮ → Allow restricted settings.",
            "Grant access", actions.openAccess,
        )
        SourceStatus.NotInstalled -> Triple("$app isn't installed.", "Get $app", { actions.installApp(ui.source) })
        SourceStatus.AppNotRunning -> Triple("Press PLAY to resume your last $app queue, or open it and pick something.", "Open $app", actions.openApp)
        else -> Triple("Choose playlists and albums in $app; the deck follows along with art, queue and transport.", "Open $app", actions.openApp)
    }
    Text(text, style = Sub.copy(lineHeight = 16.sp))
    Spacer(Modifier.height(10.dp))
    Text(
        button.uppercase(),
        style = Sub.copy(color = Color(0xFF1E1916), fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(DeckColors.Accent)
            .clickable(onClick = action)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun QueueList(ui: DeckUi, onPlay: (Long) -> Unit) {
    if (ui.queue.isEmpty()) {
        val empty = when {
            ui.source == Source.LOCAL -> "Nothing loaded"
            ui.source == Source.APPLE_MUSIC && ui.apple.sdkPlayback -> "Pick a playlist to load the queue"
            else -> "This app doesn't share its queue"
        }
        Text(empty, style = Sub)
        return
    }
    val list = rememberLazyListState()
    val currentIndex = ui.queue.indexOfFirst { it.id == ui.currentQueueId }
    LaunchedEffect(currentIndex) { if (currentIndex >= 0) list.animateScrollToItem(currentIndex) }
    LazyColumn(state = list) {
        itemsIndexed(ui.queue, key = { _, q -> q.id }) { _, q ->
            QueueRow(q, current = q.id == ui.currentQueueId, onClick = { onPlay(q.id) })
        }
    }
}

@Composable
private fun QueueRow(q: QueueEntry, current: Boolean, onClick: () -> Unit, showArt: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .drawWithCache {
                onDrawBehind {
                    drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, size.height), Offset(size.width, size.height), 1f)
                }
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showArt) {
            Thumb(q.artwork, q.artworkUri, Modifier.size(44.dp))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(q.title, style = Title.copy(color = if (current) DeckColors.Accent else Title.color), maxLines = 1, overflow = TextOverflow.Ellipsis)
            q.subtitle?.let { Text(it, style = Sub, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (current) Text("▶", style = Sub.copy(color = DeckColors.Accent))
    }
}

@Composable
fun UrlThumb(url: String?, modifier: Modifier) = Thumb(null, url?.let(Uri::parse), modifier)

/** Cover thumbnail from a bitmap the source handed us, or loaded (and cached) from a URI. */
@Composable
private fun Thumb(bitmap: Bitmap?, uri: Uri?, modifier: Modifier) {
    val context = LocalContext.current
    // produceState keeps its last value when the keys change, so always replace it: a reused row
    // would otherwise go on showing the previous song's cover.
    val image by produceState<ImageBitmap?>(bitmap?.asImageBitmap(), bitmap, uri) {
        value = bitmap?.asImageBitmap() ?: uri?.let { Artwork.thumbnail(context, it)?.asImageBitmap() }
    }
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF3A302A)), contentAlignment = Alignment.Center) {
        image?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            ?: Text("♪", style = Sub.copy(fontSize = 16.sp))
    }
}

/** Now-playing band: the cover big, what's up next, and liner notes. */
@Composable
fun NowPlayingBand(ui: DeckUi, onPlayQueue: (Long) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.glassPanel().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(ui.label.stripe, ui.label.stripe2))),
            contentAlignment = Alignment.Center,
        ) {
            ui.artwork?.let { Image(it, "Album art", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                ?: Text("A", style = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.85f)))
        }
        Column(Modifier.weight(0.5f).fillMaxHeight()) {
            Text("Up next", style = Header)
            Spacer(Modifier.height(6.dp))
            val i = ui.queue.indexOfFirst { it.id == ui.currentQueueId }
            val next = if (i >= 0) ui.queue.drop(i + 1).take(4) else emptyList()
            if (next.isEmpty()) Text("End of side A", style = Sub)
            next.forEach { q -> QueueRow(q, current = false, onClick = { onPlayQueue(q.id) }, showArt = true) }
        }
        Column(Modifier.weight(0.5f).fillMaxHeight()) {
            Text("Liner notes", style = Header)
            Spacer(Modifier.height(8.dp))
            Text(ui.title.ifBlank { "No tape playing" }, style = Title.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (ui.artist.isNotBlank()) Text(ui.artist, style = Sub.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (ui.album.isNotBlank()) Text(ui.album, style = Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            val details = buildList {
                add(ui.source.label)
                if (ui.trackCount > 0) add("Track ${ui.trackIndex + 1} of ${ui.trackCount}")
                add("${formatTime(ui.positionMs)} / ${formatTime(ui.durationMs)}")
            }
            details.forEach { Text(it, style = Sub) }
            Spacer(Modifier.weight(1f))
            // Tape-position hairline.
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.1f))) {
                Box(Modifier.fillMaxWidth(ui.progress).fillMaxHeight().background(DeckColors.Accent))
            }
        }
    }
}

/**
 * The deck's one progress bar, shared by the portrait panel, the landscape deck and the
 * stack's CD plate. On a source that advertises seek it is a real scrubber: a tap seeks at
 * once, a drag scrubs with the bar following the finger and [onDragTimeChange] reporting the
 * target position in ms (null when no drag is live) for surfaces that show an elapsed time,
 * and the seek commits on release. On a source that offers no seek, or a track whose length
 * is unknown, it renders exactly the bar it replaces: display only, no touch handling.
 * [touchHeight] is the invisible finger target around the visible [barHeight] line.
 */
@Composable
fun SeekScrubber(
    progress: Float,
    canSeek: Boolean,
    durationMs: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    barHeight: Dp = 3.dp,
    touchHeight: Dp = 20.dp,
    corner: Dp = 2.dp,
    barAlignment: Alignment = Alignment.Center,
    onDragTimeChange: (Long?) -> Unit = {},
) {
    val seekable = canSeek && durationMs > 0
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shown = dragFraction ?: progress
    val seek by rememberUpdatedState(onSeek)
    val dragTime by rememberUpdatedState(onDragTimeChange)
    Box(
        modifier
            .height(touchHeight)
            .semantics {
                contentDescription = "Progress"
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                if (seekable) setProgress { seek(it.coerceIn(0f, 1f)); true }
            }
            .then(
                if (seekable) {
                    Modifier.pointerInput(durationMs) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            fun fractionAt(x: Float) = (x / size.width).coerceIn(0f, 1f)
                            var fraction = fractionAt(down.position.x)
                            dragFraction = fraction
                            dragTime((fraction * durationMs).toLong())
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                // An up or a cancelled gesture both report the pointer released.
                                if (!change.pressed) break
                                fraction = fractionAt(change.position.x)
                                dragFraction = fraction
                                dragTime((fraction * durationMs).toLong())
                            }
                            seek(fraction)
                            dragFraction = null
                            dragTime(null)
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = barAlignment,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(corner))
                .background(Color.White.copy(alpha = 0.10f)),
        ) {
            Box(Modifier.fillMaxWidth(shown).fillMaxHeight().clip(RoundedCornerShape(corner)).background(DeckColors.Accent))
        }
    }
}

/**
 * The portrait deck's info panel: artwork thumbnail, title and artist, a progress readout with
 * elapsed and total times, and the round transport buttons. The progress bar is the shared
 * [SeekScrubber]: a real control on sources that advertise seek, a plain readout on the rest,
 * with the elapsed time following the finger while a drag is live. Pause is
 * folded into the play button, the way the reference deck does it, and it calls the same pauseKey
 * path the piano keys use so the transport behaves identically. On short windows the Cover deck
 * passes compact, which keeps the 1.1.1 padding, thumbnail and spacers so the panel does not
 * squeeze the cassette out of the middle zone. On tall windows the deck instead passes fill and
 * weights the panel into whatever height the cassette does not need: the artwork, type and
 * spacers grow a step, and a weighted spacer between the progress row and the buttons spreads
 * the extra height, so the buttons stay anchored at the bottom and the panel reads at the
 * reference deck's proportions.
 *
 * The panel is the Cover deck's terminal element: its recess surface runs flush to the bottom
 * edge of the window, behind the gesture bar. The bottom safe drawing inset is padded inside
 * the panel (after the recess draw, so the surface itself is not pushed up), and the bottom
 * content padding absorbs the deck column's old 10dp bottom padding, so the buttons keep
 * exactly the clearance above the screen edge they had before the panel went flush.
 *
 * Below the transport buttons sits the control strip: EQ (a shortcut to the equalizer app the
 * listener picked, where the phone's output EQ actually lives; long press picks a different
 * one), shuffle and repeat, all real commands against the active source. A hairline divides
 * the strip from the buttons; in fill mode a second
 * weighted spacer above the strip pins it to the panel's bottom edge, and the fill spacings
 * and paddings are trimmed by roughly the strip's height so the grown panel's minimum content
 * still fits the same windows it did before the strip arrived.
 */
@Composable
fun TrackInfoPanel(
    ui: DeckUi,
    onPrevious: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onQueue: () -> Unit,
    onSettings: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSeek: (Float) -> Unit,
    compact: Boolean = false,
    fill: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val playing = ui.transport == Transport.PLAYING
    // While a scrub drag is live, the elapsed readout shows where the finger is headed.
    var scrubTargetMs by remember { mutableStateOf<Long?>(null) }
    Column(
        modifier
            .recess()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(
                start = 16.dp, end = 16.dp,
                top = if (compact) 16.dp else if (fill) 14.dp else 24.dp,
                bottom = if (compact) 26.dp else if (fill) 20.dp else 34.dp,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(if (compact) 88.dp else if (fill) 124.dp else 104.dp)
                    .clip(RoundedCornerShape(if (compact) 10.dp else if (fill) 14.dp else 12.dp))
                    .background(Brush.linearGradient(listOf(ui.label.stripe, ui.label.stripe2))),
                contentAlignment = Alignment.Center,
            ) {
                ui.artwork?.let { Image(it, "Album art", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    ?: Text("♪", style = Sub.copy(fontSize = if (compact) 28.sp else if (fill) 36.sp else 32.sp, color = Color.White.copy(alpha = 0.85f)))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    ui.title.ifBlank { "NO TAPE" },
                    style = Title.copy(fontSize = if (fill) 23.sp else 21.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    ui.artist.ifBlank { statusLine(ui) },
                    style = Sub.copy(fontSize = if (fill) 18.sp else 17.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (ui.album.isNotBlank() && ui.artist.isNotBlank()) {
                    Text(ui.album, style = Sub.copy(fontSize = if (fill) 15.sp else 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(if (compact) 16.dp else if (fill) 12.dp else 20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(scrubTargetMs ?: ui.positionMs), style = Sub.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.width(8.dp))
            SeekScrubber(
                progress = ui.progress,
                canSeek = ui.canSeek,
                durationMs = ui.durationMs,
                onSeek = onSeek,
                modifier = Modifier.weight(1f),
                barHeight = 10.dp,
                touchHeight = 20.dp,
                corner = 5.dp,
                onDragTimeChange = { scrubTargetMs = it },
            )
            Spacer(Modifier.width(8.dp))
            Text(formatTime(ui.durationMs), style = Sub.copy(fontSize = 13.sp))
        }
        Spacer(Modifier.height(if (compact) 18.dp else if (fill) 12.dp else 22.dp))
        if (fill) Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundTransportButton("Settings", onSettings, roundSymbol = RoundSymbol.GEAR, diameter = 52.dp)
            Spacer(Modifier.width(12.dp))
            RoundTransportButton("Tapes and queue", onQueue, roundSymbol = RoundSymbol.LIST, diameter = 52.dp)
            Spacer(Modifier.weight(1f))
            RoundTransportButton("Previous", onPrevious, symbol = KeySymbol.PREV, diameter = 60.dp, enabled = ui.hasPrevious)
            Spacer(Modifier.width(14.dp))
            RoundTransportButton(
                if (playing) "Pause" else "Play",
                if (playing) onPause else onPlay,
                symbol = if (playing) KeySymbol.PAUSE else KeySymbol.PLAY,
                diameter = 76.dp, ring = true, active = playing,
            )
            Spacer(Modifier.width(14.dp))
            RoundTransportButton("Next", onNext, symbol = KeySymbol.NEXT, diameter = 60.dp, enabled = ui.hasNext)
        }
        // The control strip: EQ, shuffle, repeat. In fill mode the weighted spacer pins the
        // strip to the panel's bottom edge; otherwise it follows the buttons directly.
        if (fill) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
        Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EqualizerButton()
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                DeckStripButton(
                    description = "Shuffle",
                    onPress = onShuffle,
                    symbol = StripSymbol.SHUFFLE,
                    lit = ui.shuffleOn,
                    enabled = ui.canShuffle,
                    stateText = if (ui.shuffleOn) "On" else "Off",
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                DeckStripButton(
                    description = "Repeat",
                    onPress = onRepeat,
                    symbol = if (ui.repeatMode == RepeatMode.ONE) StripSymbol.REPEAT_ONE else StripSymbol.REPEAT,
                    lit = ui.repeatMode != RepeatMode.NONE,
                    enabled = ui.canRepeat,
                    stateText = when (ui.repeatMode) {
                        RepeatMode.NONE -> "Off"
                        RepeatMode.ALL -> "All"
                        RepeatMode.ONE -> "One"
                    },
                )
            }
        }
    }
}

/**
 * The EQ strip button and its chooser, shared by the portrait panel and the landscape rail.
 * A tap opens the remembered equalizer app (or the chooser when none was picked yet, or when
 * the picked one has gone away); a long press always opens the chooser. A phone with no
 * equalizer app at all gets a toast instead of a dialog.
 */
@Composable
fun EqualizerButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var eqChooserOpen by remember { mutableStateOf(false) }
    val openEqChooser: () -> Unit = {
        if (eqCandidates(context).isEmpty()) {
            Toast.makeText(context, "No equalizer app found", Toast.LENGTH_SHORT).show()
        } else {
            eqChooserOpen = true
        }
    }
    val onEqTap: () -> Unit = {
        val target = loadEqTarget(context)
        when {
            target == null -> openEqChooser()
            launchEqComponent(context, target) -> Unit
            else -> {
                clearEqTarget(context)
                openEqChooser()
            }
        }
    }
    DeckStripButton(
        description = "Equalizer",
        onPress = onEqTap,
        symbol = StripSymbol.EQ,
        modifier = modifier,
        onLongPress = openEqChooser,
        longPressHint = "Long press to choose a different equalizer app",
    )
    if (eqChooserOpen) {
        EqualizerChooser(
            candidates = remember { eqCandidates(context) },
            onPick = { candidate ->
                storeEqTarget(context, candidate.component)
                eqChooserOpen = false
                launchEqComponent(context, candidate.component)
            },
            onDismiss = { eqChooserOpen = false },
        )
    }
}

/** A candidate equalizer app for the chooser: where it lives, what it calls itself, its icon. */
private class EqCandidate(val component: ComponentName, val label: String, val icon: ImageBitmap?)

// The EQ shortcut's memory lives in the same SharedPreferences store the deck's other settings
// use (the view model's "foldman" file), under its own key.
private const val EQ_PREFS = "foldman"
private const val KEY_EQ_TARGET = "eq_target_component"

private fun loadEqTarget(context: Context): ComponentName? =
    context.getSharedPreferences(EQ_PREFS, Context.MODE_PRIVATE)
        .getString(KEY_EQ_TARGET, null)
        ?.let { ComponentName.unflattenFromString(it) }

private fun storeEqTarget(context: Context, component: ComponentName) {
    context.getSharedPreferences(EQ_PREFS, Context.MODE_PRIVATE)
        .edit { putString(KEY_EQ_TARGET, component.flattenToString()) }
}

private fun clearEqTarget(context: Context) {
    context.getSharedPreferences(EQ_PREFS, Context.MODE_PRIVATE)
        .edit { remove(KEY_EQ_TARGET) }
}

/**
 * Launches an app by its exact component; true when the phone took it. Ferric does no audio
 * processing of its own: the EQ button is a shortcut to whichever equalizer app the listener
 * chose, the app that actually sits on the phone's output and shapes every source the deck
 * drives. A failed launch never takes the deck down with it.
 */
private fun launchEqComponent(context: Context, component: ComponentName): Boolean =
    runCatching {
        context.startActivity(Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

/**
 * Asks the phone which equalizer apps it has, rather than guessing package names: activities
 * that answer the system's audio effect panel action, plus launchable apps whose name says
 * Equalizer (or whose package gives it away) and anything from Poweramp's maker. A Poweramp
 * equalizer leads the list when one is present; the rest sort by name.
 */
private fun eqCandidates(context: Context): List<EqCandidate> {
    val pm = context.packageManager
    val found = LinkedHashMap<String, EqCandidate>()
    fun add(info: ResolveInfo) {
        val component = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
        val key = component.flattenToString()
        if (key in found) return
        val label = info.loadLabel(pm).toString()
        val icon = runCatching { info.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        found[key] = EqCandidate(component, label, icon)
    }
    runCatching {
        pm.queryIntentActivities(Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL), 0)
    }.getOrDefault(emptyList()).forEach(::add)
    runCatching {
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
    }.getOrDefault(emptyList()).forEach { info ->
        val label = info.loadLabel(pm).toString()
        if (label.contains("qual", ignoreCase = true) || info.activityInfo.packageName.contains("maxmpz")) {
            add(info)
        }
    }
    fun EqCandidate.isPowerampEqualizer() =
        component.packageName.contains("maxmpz") && label.contains("qual", ignoreCase = true)
    return found.values.sortedWith(
        compareByDescending<EqCandidate> { it.isPowerampEqualizer() }.thenBy { it.label.lowercase() },
    )
}

/**
 * The equalizer chooser: one tap on a row remembers that app as the EQ button's target and
 * opens it. Styled on the deck's sheets: the same dark plate, the accent kept for the escape.
 */
@Composable
private fun EqualizerChooser(
    candidates: List<EqCandidate>,
    onPick: (EqCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1C1510))
                .padding(20.dp),
        ) {
            Text("Choose your equalizer", style = Header.copy(fontSize = 16.sp))
            Spacer(Modifier.height(6.dp))
            Text(
                "The EQ button opens the app you pick. Long press EQ any time to change it.",
                style = Sub.copy(lineHeight = 16.sp),
            )
            Spacer(Modifier.height(12.dp))
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                candidates.forEach { candidate ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(candidate) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        candidate.icon?.let { icon ->
                            Image(icon, null, Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
                        } ?: Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.08f)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(candidate.label, style = Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "NONE OF THESE",
                style = Sub.copy(color = DeckColors.Accent, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
                modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp),
            )
        }
    }
}
