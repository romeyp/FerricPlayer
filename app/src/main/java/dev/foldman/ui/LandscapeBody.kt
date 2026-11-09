package dev.foldman.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foldman.deck.DeckUi
import dev.foldman.deck.RepeatMode
import dev.foldman.deck.Source
import dev.foldman.deck.Transport

/**
 * The landscape deck as a personal player body, reproducing the approved mockup's
 * composition: the machine fills the whole window edge to edge, dark brushed metal
 * with a screw in each corner, and it runs full screen, the system bars hidden
 * while it is shown, like the stack and the expanded tape. A top band carries the
 * wordmark and, at its right, the counter and the pilot lamp. The
 * middle band is the door at the left and the pod at the right: the door is a solid
 * panel with a smoked window cut into it, and behind the glass the cassette is
 * rendered wider than the glass at its true ratio, so the window crops it to the
 * label and the reels, the way a real door does; the body print under the window
 * (AUTO REVERSE, the orange stripe, the tape types) is sculpture, printed on the
 * door, not controls. The pod carries the working furniture in the mockup's order:
 * source lamps, the real volume slider, two rows of rectangular pod keys (EQ,
 * shuffle, repeat, then source, settings, sleep) and the phones jack as sculpture
 * at its foot. The five piano keys sit in a row along the bottom edge, over the
 * HOLD REW / FF TO WIND line, and a thin bezel strip at the very bottom edge
 * carries what is playing and the seek hairline. Everything the slim landscape
 * deck offered stays reachable here: tapes, settings, sleep, source, seek and
 * volume. There is no expand here: the body already is the door forward view.
 */
@Composable
fun LandscapeDeck(
    ui: DeckUi,
    anim: DeckAnimations,
    deck: DeckControls,
    onTapes: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier,
    tabletop: Boolean = false,
) {
    val playing = ui.transport == Transport.PLAYING
    var scrubTargetMs by remember { mutableStateOf<Long?>(null) }
    // In tabletop the plate is the top half of the window: the system's top and side
    // insets are honoured, the bottom edge belongs to the library below.
    val insetModifier = if (tabletop) {
        Modifier.windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        )
    } else {
        Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
    }
    // Hold-to-wind steps for the REW and FF keys, computed from the live position at
    // the moment each step fires; the seek re-checks seek support, and when the
    // source cannot seek the keys are told so and a hold does nothing at all.
    val posNow by rememberUpdatedState(ui.positionMs)
    val durNow by rememberUpdatedState(ui.durationMs)
    val windStep: (Long) -> Unit = { delta ->
        val duration = durNow
        if (duration > 0) {
            deck.seek(((posNow + delta).coerceIn(0L, duration)).toFloat() / duration.toFloat())
        }
    }
    // Full screen while this body is shown, asserted by the deck itself with the
    // same call the screen's posture rule uses, so the bars stay down even when
    // something (a rotation settling, a sheet window) has raised them again after
    // the posture changed. Restoring them on exit is the posture rule's job, in
    // FoldmanScreen, exactly as it is for the stack and the expanded tape: this
    // deck deliberately does not raise them itself, so moving on to those views
    // keeps them down.
    val view = LocalView.current
    DisposableEffect(view) {
        view.showSystemBars(false)
        onDispose { }
    }
    Box(modifier.darkBrushed()) {
        BoxWithConstraints(Modifier.fillMaxSize().then(insetModifier)) {
            // The mockup's bands, as shares of the body above the bezel strip: the
            // top band and the piano keys keep their mockup shares, and the middle
            // band takes a larger one than the drawing's 57 percent, the extra
            // height being what lets the door's glass show the whole label and
            // both reels of a true ratio cassette.
            val bezelH = 30.dp
            val a = maxHeight - bezelH
            val topH = a * 0.155f
            val midH = a * 0.605f
            val keysH = a * 0.144f
            val gapTop = a * 0.034f
            val gapMid = a * 0.031f
            val windH = a - topH - midH - keysH - gapTop - gapMid
            val side = maxWidth * 0.034f
            Column(Modifier.fillMaxSize()) {
                // The top band: wordmark at the left, counter and pilot lamp
                // at the right.
                Row(
                    Modifier.fillMaxWidth().height(topH).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Wordmark("FERRIC", 22.sp)
                        Text(
                            "STEREO CASSETTE PLAYER",
                            style = EngravedCaps.copy(fontSize = 7.5.sp, color = Color(0xFF83868D)),
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    BodyCounter(ui.positionMs)
                    Spacer(Modifier.width(14.dp))
                    PilotDot(playing = playing, paused = ui.transport == Transport.PAUSED)
                }
                Spacer(Modifier.height(gapTop))
                // The middle band: the door at the left, the pod at the right,
                // inset from the body's edges the way the mockup insets them.
                BoxWithConstraints(Modifier.fillMaxWidth().height(midH)) {
                    val doorW = maxWidth * 0.66f
                    val podW = maxWidth * 0.225f
                    val innerGap = maxWidth - side * 2 - doorW - podW
                    Row(Modifier.fillMaxSize()) {
                        Spacer(Modifier.width(side))
                        LandscapeDoor(ui, anim, Modifier.width(doorW).fillMaxHeight())
                        Spacer(Modifier.width(innerGap))
                        LandscapePod(ui, deck, onTapes, onSettings, Modifier.width(podW).fillMaxHeight())
                        Spacer(Modifier.width(side))
                    }
                }
                Spacer(Modifier.height(gapMid))
                // The five piano keys along the bottom edge, PLAY wider and
                // latched with its orange line lit while the tape plays.
                Row(
                    Modifier.fillMaxWidth().height(keysH).padding(horizontal = side),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BodyKey(
                        "REW", KeySymbol.REW, deck.rewind, Modifier.weight(1f),
                        latched = ui.transport == Transport.REWINDING, height = keysH,
                        windDirection = -1, canWind = ui.canSeek, onWindStep = windStep,
                    )
                    BodyKey(
                        "PREV", KeySymbol.PREV, deck.previous, Modifier.weight(1f),
                        enabled = ui.hasPrevious, height = keysH,
                    )
                    BodyKey(
                        if (playing) "PAUSE" else "PLAY",
                        if (playing) KeySymbol.PAUSE else KeySymbol.PLAY,
                        if (playing) deck.pause else deck.play,
                        Modifier.weight(1.35f),
                        latched = playing, accent = true, height = keysH,
                    )
                    BodyKey(
                        "NEXT", KeySymbol.NEXT, deck.next, Modifier.weight(1f),
                        enabled = ui.hasNext, height = keysH,
                    )
                    BodyKey(
                        "FF", KeySymbol.FF, deck.fastForward, Modifier.weight(1f),
                        latched = ui.transport == Transport.FAST_FORWARDING, height = keysH,
                        windDirection = 1, canWind = ui.canSeek, onWindStep = windStep,
                    )
                }
                // The wind line, centred under the keys at the body's bottom.
                Box(Modifier.fillMaxWidth().height(windH), contentAlignment = Alignment.Center) {
                    Text(
                        "HOLD REW / FF TO WIND",
                        style = EngravedCaps.copy(fontSize = 6.5.sp, color = Color(0xFF5D6066)),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
                // The bezel strip at the very bottom edge: what is playing at the
                // left, where the tape sits at the right (following the scrubber
                // while it is being dragged), and the seek hairline as its bottom
                // line.
                Row(
                    Modifier.fillMaxWidth().height(16.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val headline = if (ui.artist.isBlank()) ui.title else "${ui.title} · ${ui.artist}"
                    Text(
                        headline,
                        Modifier.weight(1f),
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif, fontSize = 11.sp,
                            color = Color(0xFFC9CCD1),
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${formatTime(scrubTargetMs ?: ui.positionMs)} / ${formatTime(ui.durationMs)}",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                            color = Color(0xFF8B8E94),
                        ),
                        maxLines = 1,
                    )
                }
                SeekScrubber(
                    progress = ui.progress,
                    canSeek = ui.canSeek,
                    durationMs = ui.durationMs,
                    onSeek = deck.seek,
                    modifier = Modifier.fillMaxWidth(),
                    barHeight = 3.dp,
                    touchHeight = bezelH - 16.dp,
                    corner = 0.dp,
                    barAlignment = Alignment.BottomCenter,
                    onDragTimeChange = { scrubTargetMs = it },
                )
            }
        }
        // A screw in each corner of the body, over the plate, as the mockup has them.
        // The canvas takes no input, so the keys beneath stay touchable.
        Canvas(Modifier.fillMaxSize()) {
            val inset = 10.dp.toPx()
            val r = 3.4.dp.toPx()
            listOf(
                Offset(inset, inset),
                Offset(size.width - inset, inset),
                Offset(inset, size.height - inset),
                Offset(size.width - inset, size.height - inset),
            ).forEachIndexed { i, c -> drawScrew(c, r, 25f + i * 41f) }
        }
    }
}

/**
 * The door: a solid panel filling its whole area, with a smoked window cut into it
 * and the print row along its bottom edge. Behind the glass the cassette is
 * rendered at the window's full width and its true ratio (100.4 by 63.8), so its
 * height overflows the window above and below and the window's clip does the
 * cropping: the glass shows the label band and the reel band, seated so the
 * handwritten title sits fully inside the glass, with the corner screws and the
 * shell outline cropped off the top and the whole bottom bridge cropped away
 * below.
 */
@Composable
private fun LandscapeDoor(ui: DeckUi, anim: DeckAnimations, modifier: Modifier) {
    BoxWithConstraints(
        modifier
            .recess(corner = 12.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // Cassette scale behind the glass: the dp size of one shell unit when the
        // cassette's width equals the window's width. The window shows a 35 unit
        // band of the shell (see DoorWindow), capped by the height left after
        // the print row.
        val windowWidth = maxWidth
        val unit = windowWidth / 100.4f
        val windowHeight = minOf(maxHeight - 15.dp, unit * 35.0f).coerceAtLeast(0.dp)
        Column(Modifier.fillMaxSize()) {
            DoorWindow(ui, anim, windowWidth, windowHeight)
            Spacer(Modifier.weight(1f))
            DoorPrintRow(Modifier.fillMaxWidth())
        }
    }
}

/**
 * The door's smoked window and the cassette behind it. The window is deep inset,
 * dark smoked glass. Behind it
 * the cassette is rendered at the window's full width and its true ratio (100.4
 * by 63.8), measured at its oversized natural size and shifted up, so its height
 * overflows the window above and below and the window's clip does the cropping:
 * nothing of the shell's outline or bottom edge can show. The visible band of
 * the shell, in shell units, runs from y 10.9 toward y 45.9 as the window's
 * height allows (a full band is 35 units tall): from just above the handwritten
 * title (drawn at y 11.4) to just below the face window (its bottom edge is at
 * 45.5), so the title, the label and both reels are wholly in view, the corner
 * screws and the shell outline are cropped off the top, and the whole bottom
 * bridge (from 50.5) is cropped away below.
 */
@Composable
private fun DoorWindow(ui: DeckUi, anim: DeckAnimations, width: Dp, height: Dp) {
    val unit = width / 100.4f
    val cassetteHeight = width * (63.8f / 100.4f)
    val topCrop = unit * 10.9f
    Box(
        Modifier.width(width).height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF101114), Color(0xFF0A0A0C))),
            ),
    ) {
        FullCassetteFace(
            ui, anim,
            Modifier.requiredSize(width, cassetteHeight)
                .offset(y = -topCrop),
        )
        DoorGlass()
    }
}

/** The door's print row: AUTO REVERSE, the orange stripe, the tape types it takes. */
@Composable
private fun DoorPrintRow(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            "AUTO REVERSE",
            style = EngravedCaps.copy(fontSize = 7.5.sp, color = Color(0xFF74777E)),
            maxLines = 1,
        )
        Spacer(Modifier.weight(0.7f))
        Box(
            Modifier.weight(1f).height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(DeckColors.Accent),
        )
        Spacer(Modifier.weight(0.7f))
        Text(
            "METAL / CrO2 / NORMAL",
            style = EngravedCaps.copy(fontSize = 7.5.sp, color = Color(0xFF74777E)),
            maxLines = 1,
        )
    }
}

/**
 * The pod: the body's working furniture in the mockup's order. Source lamps, the
 * volume slider under its caption, two rows of rectangular pod keys (EQ, shuffle
 * and repeat, then source, settings and sleep, its countdown shown in the key
 * while it runs) and the phones jack as sculpture at the foot.
 */
@Composable
private fun LandscapePod(
    ui: DeckUi,
    deck: DeckControls,
    onTapes: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier
            .recess(corner = 12.dp)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        PodLamp("YT MUSIC", lit = ui.source == Source.YT_MUSIC)
        Spacer(Modifier.height(4.dp))
        PodLamp("APPLE MUSIC", lit = ui.source == Source.APPLE_MUSIC)
        Spacer(Modifier.height(4.dp))
        PodLamp("LOCAL", lit = ui.source == Source.LOCAL)
        Spacer(Modifier.height(6.dp))
        Text(
            "VOLUME",
            style = EngravedCaps.copy(fontSize = 7.5.sp, color = Color(0xFF83868D)),
            maxLines = 1,
        )
        VolumeSliderHorizontal(ui.volume, deck.volume, Modifier.fillMaxWidth().height(38.dp))
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EqualizerPodKey(Modifier.weight(1f))
            PodRectKey(
                description = "Shuffle",
                name = "SHUFFLE",
                glyph = PodGlyph.SHUFFLE,
                onPress = deck.shuffle,
                modifier = Modifier.weight(1f),
                lit = ui.shuffleOn,
                enabled = ui.canShuffle,
                stateText = if (ui.shuffleOn) "On" else "Off",
                stateLit = ui.shuffleOn,
            )
            PodRectKey(
                description = "Repeat",
                name = "REPEAT",
                glyph = if (ui.repeatMode == RepeatMode.ONE) PodGlyph.REPEAT_ONE else PodGlyph.REPEAT,
                onPress = deck.repeat,
                modifier = Modifier.weight(1f),
                lit = ui.repeatMode != RepeatMode.NONE,
                enabled = ui.canRepeat,
                stateText = when (ui.repeatMode) {
                    RepeatMode.NONE -> "Off"
                    RepeatMode.ALL -> "All"
                    RepeatMode.ONE -> "One"
                },
                stateLit = ui.repeatMode != RepeatMode.NONE,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PodRectKey(
                description = "Source",
                name = "SOURCE",
                glyph = PodGlyph.LIST,
                onPress = onTapes,
                modifier = Modifier.weight(1f),
            )
            PodRectKey(
                description = "Settings",
                name = "SETTINGS",
                glyph = PodGlyph.GEAR,
                onPress = onSettings,
                modifier = Modifier.weight(1f),
            )
            PodRectKey(
                description = "Sleep",
                name = "SLEEP",
                glyph = PodGlyph.MOON,
                onPress = deck.sleep,
                modifier = Modifier.weight(1f),
                onLongPress = deck.cancelSleep,
                longPressHint = "Long press to cancel the sleep timer",
                stateText = if (ui.sleepMinutes > 0) formatTime(ui.sleepRemainingMs) else null,
                stateLit = ui.sleepMinutes > 0,
            )
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PhonesJack(Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "PHONES",
                style = EngravedCaps.copy(fontSize = 7.5.sp, color = Color(0xFF83868D)),
                maxLines = 1,
            )
        }
    }
}

/**
 * The smoked glass over the door window: a cool tint, one broad diagonal of room
 * light, a soft top glare, and the deep shadow where the door's cut edge overhangs
 * the glass, the same treatment the case window uses, drawn over the oversized
 * cassette so the tape reads as sitting behind glass in a deep recess.
 */
@Composable
private fun DoorGlass() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val r = CornerRadius(8.dp.toPx())
        // Smoked tint over the whole pane.
        drawRect(Color(0xFF26303C).copy(alpha = 0.16f))
        // One long diagonal reflection, brightest toward its leading edge.
        val band = Path().apply {
            moveTo(w * 0.30f, 0f); lineTo(w * 0.52f, 0f)
            lineTo(w * 0.34f, h); lineTo(w * 0.12f, h); close()
        }
        drawPath(
            band,
            Brush.horizontalGradient(
                listOf(
                    Color.Transparent, Color.White.copy(alpha = 0.10f),
                    Color.White.copy(alpha = 0.03f), Color.Transparent,
                ),
                w * 0.12f, w * 0.52f,
            ),
        )
        // A soft glare along the top of the glass.
        drawRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), 0f, h * 0.3f),
        )
        // The deepest shadow, where the door's cut edge overhangs the top of the glass.
        drawRect(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.38f), Color.Transparent), 0f, 16.dp.toPx()),
        )
        // The recess shadow all round the pane, then a hairline of light on its edge.
        drawRoundRect(Color.Black.copy(alpha = 0.5f), style = Stroke(11.dp.toPx()), cornerRadius = r)
        drawRoundRect(Color.White.copy(alpha = 0.06f), style = Stroke(1.dp.toPx()), cornerRadius = r)
    }
}

/**
 * The mechanical counter in the top band: elapsed seconds mod 1000 in amber digits
 * in a black window, the same honest count the stack's receiver shows (it counts
 * seconds, not reel revolutions).
 */
@Composable
private fun BodyCounter(positionMs: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF050505), Color(0xFF16171B), Color(0xFF050505))),
                )
                .padding(horizontal = 9.dp, vertical = 3.dp),
        ) {
            Text(
                ((positionMs / 1000) % 1000).toInt().toString().padStart(3, '0'),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, color = Color(0xFFFFB347), letterSpacing = 3.sp,
                ),
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "COUNTER",
            style = EngravedCaps.copy(fontSize = 6.5.sp, color = Color(0xFF83868D)),
            maxLines = 1,
        )
    }
}

/** The red pilot lamp in the top band: lit while playing, dim at pause, dark stopped. */
@Composable
private fun PilotDot(playing: Boolean, paused: Boolean) {
    Canvas(Modifier.size(11.dp)) {
        val r = size.minDimension / 2f
        val level = when {
            playing -> 1f
            paused -> 0.45f
            else -> 0.16f
        }
        if (playing) {
            drawCircle(
                Brush.radialGradient(listOf(DeckColors.Led.copy(alpha = 0.5f), Color.Transparent), center, r),
                r,
            )
        }
        drawCircle(Color(0xFF0C0503), r * 0.95f)
        drawCircle(DeckColors.Led.copy(alpha = level), r * 0.66f)
        drawCircle(Color.White.copy(alpha = 0.2f + 0.45f * level), r * 0.2f, center - Offset(r * 0.2f, r * 0.2f))
    }
}

/** One source lamp on the pod: an orange dot and its name, lit for the live source. */
@Composable
private fun PodLamp(label: String, lit: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(9.dp)) {
            val r = size.minDimension / 2f
            if (lit) {
                drawCircle(
                    Brush.radialGradient(listOf(DeckColors.Accent.copy(alpha = 0.55f), Color.Transparent), center, r),
                    r,
                )
            }
            drawCircle(Color(0xFF0B0703), r * 0.95f)
            drawCircle(if (lit) DeckColors.Accent else DeckColors.Accent.copy(alpha = 0.13f), r * 0.62f)
            drawCircle(Color.White.copy(alpha = if (lit) 0.55f else 0.12f), r * 0.2f, center - Offset(r * 0.2f, r * 0.2f))
        }
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            style = EngravedCaps.copy(
                fontSize = 8.5.sp,
                color = if (lit) Color(0xFFD8DBE0) else Color(0xFF75787D),
            ),
            maxLines = 1,
        )
    }
}
