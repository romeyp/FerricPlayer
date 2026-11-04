package dev.foldman.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foldman.deck.DeckUi
import dev.foldman.deck.DeckViewModel
import dev.foldman.deck.ParkedTape
import dev.foldman.deck.RepeatMode
import dev.foldman.deck.Source
import dev.foldman.deck.Transport
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode

/**
 * The unfolded phone stood upright: a stack of four black hifi components, the listening room
 * view next to the landscape deck's personal player. Receiver on top with an amber readout,
 * the cassette deck carrying the live tape behind its door window with piano key transport,
 * a CD player whose disc prints the artwork and turns while the music plays, and an amplifier
 * anchoring the bottom: Jerome's own EQ curve cast as slider sculpture, a spectrum display
 * and LED ladders on the same level feed as the VU meters, the EQ shortcut, and one big
 * rotary volume knob that really turns.
 *
 * Everything that looks pressable works: the receiver's SOURCE, SETTINGS and SLEEP buttons
 * (SLEEP cycles the sleep timer, a long press cancels it, and the readout counts it down),
 * the piano keys, the CD player's shuffle and repeat, the EQ button and the volume knob. The
 * receiver's small knobs, the amp's EQ sliders and its phones jack are panel sculpture, the
 * way showroom hardware wears controls this app cannot honestly drive (the real EQ lives in
 * the app the EQ button opens, and says so in the chooser).
 *
 * Two quiet garnishes finish the rack: the amplifier's bottom edge carries the maker's
 * line in engraved caps, and every lit element warms up over a breath when the stack is
 * entered, the way a real system wakes when its power is thrown.
 */

private val AmberLed = Color(0xFFFFB02E)
private val GreenLed = Color(0xFF51FF7E)
private val StackCaps = EngravedCaps.copy(color = Color(0xFF9BA0A6))

/**
 * The stack's power-on warm up, read by every lit element in the rack: a single multiplier
 * that rises from dark to full once each time the stack view is entered, like filaments and
 * displays waking when the power switch is thrown, then sits at full and changes nothing.
 * It is a brightness only; no element's own animation or behaviour runs through it, and it
 * never touches input. Outside the stack it defaults to full.
 */
private val LocalWarmUp = compositionLocalOf { 1f }

@Composable
fun StackDeck(
    ui: DeckUi,
    anim: DeckAnimations,
    vm: DeckViewModel,
    onTapes: () -> Unit,
    onSettings: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val levels = rememberNeedles(ui.transport, ui.source)
    // One shot per entry into the stack: remembered with this composition, so leaving the
    // view and coming back warms the rack again, and plain recompositions never replay it.
    val warmUp = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        warmUp.animateTo(1f, tween(durationMillis = 850, easing = FastOutSlowInEasing))
    }
    CompositionLocalProvider(LocalWarmUp provides warmUp.value) {
        Column(
            modifier
                .background(DeckColors.Backdrop)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            ReceiverPlate(ui, vm, onTapes, onSettings, Modifier.fillMaxWidth().weight(14f))
            CassettePlate(ui, anim, vm, onExpand, Modifier.fillMaxWidth().weight(36f))
            CdPlate(ui, vm, Modifier.fillMaxWidth().weight(18f))
            AmplifierPlate(ui, levels, vm, Modifier.fillMaxWidth().weight(32f))
        }
    }
}

/**
 * One component of the stack: dark brushed plate, corner screws, an engraved name in the
 * top right. A plate may also carry a [footLabel]: a small engraved line centred in its
 * bottom margin, below the works, the way a maker's name is stamped along a chassis edge.
 */
@Composable
private fun StackPlate(label: String, modifier: Modifier = Modifier, footLabel: String? = null, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.darkBrushed(corner = 8.dp)) {
        Box(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), content = content)
        Text(
            label, style = StackCaps.copy(fontSize = 7.5.sp),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 12.dp),
        )
        footLabel?.let {
            Text(
                it, style = StackCaps.copy(fontSize = 6.sp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
                maxLines = 1,
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val inset = 9.dp.toPx()
            val r = 3.4.dp.toPx()
            listOf(
                Offset(inset, inset), Offset(size.width - inset, inset),
                Offset(inset, size.height - inset), Offset(size.width - inset, size.height - inset),
            ).forEachIndexed { i, c -> drawScrew(c, r, 25f + i * 41f) }
        }
    }
}

// ---------------------------------------------------------------------------
// Receiver

@Composable
private fun ReceiverPlate(
    ui: DeckUi,
    vm: DeckViewModel,
    onTapes: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StackPlate("STEREO RECEIVER", modifier) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Wordmark("FERRIC", 17.sp)
                Spacer(Modifier.height(7.dp))
                VentGrille()
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PilotLamp(ui.transport)
                    Spacer(Modifier.width(5.dp))
                    Text("POWER", style = StackCaps.copy(fontSize = 6.5.sp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                LedReadout(
                    title = ui.title.ifBlank { "NO TAPE" },
                    line2 = "${formatTime(ui.positionMs)} / ${formatTime(ui.durationMs)}",
                    color = AmberLed,
                    glass = true,
                    modifier = Modifier.fillMaxWidth(),
                    // The readout stays a readout: it never scrubs. While the sleep timer
                    // is armed it carries the countdown at the end of its second line.
                    extra = if (ui.sleepMinutes > 0) "SLEEP ${formatTime(ui.sleepRemainingMs)}" else null,
                )
                Spacer(Modifier.height(6.dp))
                SourceLamps(ui.source, Modifier.align(Alignment.CenterHorizontally))
            }
            Spacer(Modifier.width(14.dp))
            Row {
                DecorKnob()
                Spacer(Modifier.width(8.dp))
                DecorKnob()
            }
            Spacer(Modifier.width(12.dp))
            Row {
                SquarePanelButton("SOURCE", RoundSymbol.LIST, onTapes)
                Spacer(Modifier.width(10.dp))
                SquarePanelButton("SETTINGS", RoundSymbol.GEAR, onSettings)
                Spacer(Modifier.width(10.dp))
                // SLEEP: taps cycle OFF, 15, 30, 45, 60, OFF; a long press cancels at once.
                // The countdown itself lives in the readout, in the receiver's amber.
                SquarePanelButton(
                    "SLEEP", RoundSymbol.MOON,
                    onPress = vm::cycleSleepTimer,
                    onLongPress = vm::cancelSleepTimer,
                )
            }
        }
    }
}

/**
 * A dark glass readout panel with monospace text, amber on the receiver, green on the CD
 * player. [extra], when given, is a short note pinned to the right end of the second line
 * in the same hand: the receiver shows its sleep countdown there while the timer is armed.
 * With [glass], a faint diagonal of room light rests on the display window behind the
 * digits, the same sheen the cassette doors wear; the digits themselves are lit elements,
 * so their brightness rides the stack's power-on warm up while the glass stays put.
 */
@Composable
private fun LedReadout(title: String, line2: String, color: Color, modifier: Modifier = Modifier, extra: String? = null, glass: Boolean = false) {
    val glow = LocalWarmUp.current
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF17110A), Color(0xFF0B0805)))),
    ) {
        if (glass) {
            Box(
                Modifier.matchParentSize().background(
                    Brush.linearGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.36f to Color.Transparent,
                            0.5f to Color.White.copy(alpha = 0.06f),
                            0.64f to Color.Transparent,
                            1f to Color.Transparent,
                        ),
                    ),
                ),
            )
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                title,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color.copy(alpha = glow)),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    line2,
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = color.copy(alpha = 0.82f * glow)),
                    maxLines = 1,
                )
                extra?.let {
                    Spacer(Modifier.weight(1f))
                    Text(
                        it,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = color.copy(alpha = 0.82f * glow)),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** The receiver's pilot lamp: lit steady while the tape plays, dim at rest. Its light
 * rises with the stack's power-on warm up, like every lit element in the rack. */
@Composable
private fun PilotLamp(transport: Transport) {
    val warm = LocalWarmUp.current
    Canvas(Modifier.size(13.dp)) {
        val r = size.minDimension / 2f
        val level = when (transport) {
            Transport.PLAYING -> 1f
            Transport.PAUSED -> 0.4f
            else -> 0.14f
        } * warm
        if (level > 0.5f) {
            drawCircle(Brush.radialGradient(listOf(DeckColors.Led.copy(alpha = 0.5f * warm), Color.Transparent), center, r), r)
        }
        drawCircle(Color(0xFF0C0503), r * 0.95f)
        drawCircle(DeckColors.Led.copy(alpha = level), r * 0.66f)
        drawCircle(Color.White.copy(alpha = 0.2f + 0.45f * level), r * 0.2f, center - Offset(r * 0.2f, r * 0.2f))
    }
}

/**
 * The receiver's source pilot lamps: one per source the deck can play, the live one lit
 * amber, the rest dark, each with its name printed beside it. A glance reads the system.
 */
@Composable
private fun SourceLamps(source: Source, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        SourceLamp("YT", source == Source.YT_MUSIC)
        SourceLamp("APPLE", source == Source.APPLE_MUSIC)
        SourceLamp("LOCAL", source == Source.LOCAL)
    }
}

@Composable
private fun SourceLamp(label: String, lit: Boolean) {
    val warm = LocalWarmUp.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(8.dp)) {
            val r = size.minDimension / 2f
            if (lit) {
                drawCircle(Brush.radialGradient(listOf(AmberLed.copy(alpha = 0.55f * warm), Color.Transparent), center, r), r)
            }
            drawCircle(Color(0xFF0D0903), r * 0.95f)
            drawCircle(if (lit) AmberLed.copy(alpha = warm) else AmberLed.copy(alpha = 0.13f), r * 0.62f)
            drawCircle(Color.White.copy(alpha = if (lit) 0.55f * warm else 0.12f), r * 0.2f, center - Offset(r * 0.2f, r * 0.2f))
        }
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = StackCaps.copy(fontSize = 6.5.sp, color = if (lit) Color(0xFFD8DBE0) else Color(0xFF75787D)),
            maxLines = 1,
        )
    }
}

/** Speaker vent slots under the receiver's wordmark. */
@Composable
private fun VentGrille() {
    Canvas(Modifier.size(54.dp, 26.dp)) {
        val slotH = 2.6.dp.toPx()
        val gap = 1.9.dp.toPx()
        var y = 0f
        while (y + slotH <= size.height) {
            drawRoundRect(Color.Black.copy(alpha = 0.55f), topLeft = Offset(0f, y), size = Size(size.width, slotH), cornerRadius = CornerRadius(slotH / 2))
            drawLine(Color.White.copy(alpha = 0.10f), Offset(1f, y + slotH + 0.6f), Offset(size.width - 1f, y + slotH + 0.6f), 0.7f)
            y += slotH + gap
        }
    }
}

/** A small silver knob that turns nothing: receiver panel sculpture. */
@Composable
private fun DecorKnob() {
    Canvas(Modifier.size(28.dp)) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(Color.Black.copy(alpha = 0.6f), r, c + Offset(0f, 1.5f))
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFEDEEF0), Color(0xFF9A9DA2), Color(0xFF5E6165)), c - Offset(r * 0.35f, r * 0.4f), r * 1.65f),
            r * 0.92f, c,
        )
        val a = (-35.0 * PI / 180.0).toFloat()
        drawLine(
            DeckColors.Engrave,
            Offset(c.x + cos(a) * r * 0.35f, c.y + sin(a) * r * 0.35f),
            Offset(c.x + cos(a) * r * 0.78f, c.y + sin(a) * r * 0.78f),
            2f,
        )
    }
}

/**
 * A small square metal push button on a component's front panel, with its name printed under
 * it. A plain button fires on touch down, the way the panel's other keys do. A button given
 * an [onLongPress] instead holds its tap until release: if the long press lands first it
 * wins and the tap never fires, so a hold can mean something different from a tap (the
 * receiver's SLEEP button cancels its timer that way).
 */
@Composable
private fun SquarePanelButton(
    caption: String,
    symbol: RoundSymbol,
    onPress: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    var pressed by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    val view = LocalView.current
    val sounds = LocalMechSounds.current
    val haptics = LocalHaptics.current
    val action by rememberUpdatedState(onPress)
    val longAction by rememberUpdatedState(onLongPress)
    val depth by animateFloatAsState(if (pressed) 1f else 0f, spring(stiffness = 3000f), label = "square")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(36.dp)
                .semantics { role = Role.Button; contentDescription = caption }
                .pointerInput(longAction != null) {
                    if (longAction == null) {
                        detectTapGestures(onPress = {
                            pressed = true
                            if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            sounds?.keyDown()
                            action()
                            tryAwaitRelease()
                            pressed = false
                            if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_RELEASE)
                            sounds?.keyUp()
                        })
                    } else {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                longFired = false
                                if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                sounds?.keyDown()
                                tryAwaitRelease()
                                pressed = false
                                if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_RELEASE)
                                sounds?.keyUp()
                                if (!longFired) action()
                            },
                            onLongPress = {
                                longFired = true
                                if (haptics) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                longAction?.invoke()
                            },
                        )
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = CornerRadius(5.dp.toPx())
                drawRoundRect(Color.Black.copy(alpha = 0.65f), topLeft = Offset(0f, 1.5f), size = size, cornerRadius = r)
                val drop = depth * 1.5f
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color(0xFF3A3D42), Color(0xFF222428), Color(0xFF141518))),
                    topLeft = Offset(0f, drop), size = Size(size.width, size.height - drop), cornerRadius = r,
                )
                drawRoundRect(Color.White.copy(alpha = 0.25f), topLeft = Offset(0f, drop), size = Size(size.width, size.height - drop), cornerRadius = r, style = Stroke(1f))
                drawRoundSymbol(symbol, center + Offset(0f, drop), size.minDimension * 0.30f, Color(0xFFD8DBE0))
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(caption, style = StackCaps.copy(fontSize = 6.5.sp), maxLines = 1)
    }
}

// ---------------------------------------------------------------------------
// Cassette deck

@Composable
private fun CassettePlate(
    ui: DeckUi,
    anim: DeckAnimations,
    vm: DeckViewModel,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playing = ui.transport == Transport.PLAYING
    StackPlate("STEREO CASSETTE DECK", modifier) {
        Column(Modifier.fillMaxSize()) {
            // The dual door: one well per streaming library, the way the dubbing decks Jerome
            // ran for years carried two tapes at once. The live well holds the deck's own
            // tape; the parked well keeps the other app's last track where it left off.
            // The counter hangs in the divider gap under the doors, clear of both faces.
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TapeWell(ui, anim, Source.YT_MUSIC, vm, Modifier.weight(1f).fillMaxHeight())
                    TapeWell(ui, anim, Source.APPLE_MUSIC, vm, Modifier.weight(1f).fillMaxHeight())
                }
                TapeCounter(
                    seconds = ui.positionMs / 1000,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PianoKey(
                    caption = "PREV", symbol = KeySymbol.PREV,
                    onPress = vm::previous, enabled = ui.hasPrevious,
                    modifier = Modifier.weight(1f),
                )
                PianoKey(
                    caption = if (playing) "PAUSE" else "PLAY",
                    symbol = if (playing) KeySymbol.PAUSE else KeySymbol.PLAY,
                    onPress = if (playing) vm::pauseKey else vm::play,
                    latched = playing || ui.transport == Transport.PAUSED,
                    accent = true,
                    modifier = Modifier.weight(1.45f),
                )
                PianoKey(
                    caption = "NEXT", symbol = KeySymbol.NEXT,
                    onPress = vm::next, enabled = ui.hasNext,
                    modifier = Modifier.weight(1f),
                )
                PianoKey(
                    caption = "EXPAND", symbol = null, expandGlyph = true,
                    onPress = onExpand,
                    modifier = Modifier.weight(0.8f),
                )
            }
        }
    }
}

/**
 * One well of the dual deck: an engraved source name and its lamp over a recessed door.
 * The live well is the deck itself, the real [ui] and its physics. A parked well shows its
 * source's held tape (see [ParkedTape]) standing still, and tapping its door hands the deck
 * to that source and plays, the same select-then-play the shelf's source tapes perform.
 * A source with no held tape shows a blank cassette, unwritten label and even packs, and
 * its door still takes the tap. When the local folder is the deck's source, both wells
 * are parked and both lamps are dark.
 */
@Composable
private fun TapeWell(
    ui: DeckUi,
    anim: DeckAnimations,
    source: Source,
    vm: DeckViewModel,
    modifier: Modifier = Modifier,
) {
    val live = ui.source == source
    val tape = ui.parked[source] ?: ParkedTape()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WellLamp(lit = live)
            Spacer(Modifier.width(6.dp))
            Text(
                if (source == Source.YT_MUSIC) "YT MUSIC" else "APPLE MUSIC",
                style = StackCaps.copy(
                    fontSize = 8.sp,
                    color = if (live) Color(0xFFD8DBE0) else Color(0xFF75787D),
                ),
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(5.dp))
        // The door zone. Its bottom band stays empty for the plate's tape counter, which
        // hangs in the divider gap between the wells, so the face never slides under it.
        // The band is sized to the counter: band plus door padding (28 + 6) clears the
        // counter's height, so its window never reaches a face.
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(bottom = 28.dp)) {
            val faceW = minOf(maxWidth - 12.dp, (maxHeight - 12.dp) * (100.4f / 63.8f))
            if (live) {
                Box(
                    Modifier.align(Alignment.Center).recess(corner = 10.dp).padding(6.dp),
                ) {
                    FullCassetteFace(ui, anim, Modifier.width(faceW))
                }
            } else {
                val parkedAnim = rememberParkedAnimations(if (tape.hasTrack) tape.progress else 0.5f)
                val wellUi = DeckUi(
                    source = source,
                    transport = Transport.STOPPED,
                    title = tape.title,
                    artist = tape.artist,
                    artwork = tape.artwork,
                    label = tape.label,
                    positionMs = (tape.progress * 1000f).toLong(),
                    durationMs = 1000,
                )
                val view = LocalView.current
                val sounds = LocalMechSounds.current
                val haptics = LocalHaptics.current
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Play ${if (source == Source.YT_MUSIC) "YT Music" else "Apple Music"}"
                        }
                        .pointerInput(source) {
                            detectTapGestures {
                                if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                sounds?.keyDown()
                                vm.selectSource(source)
                                vm.play()
                            }
                        },
                ) {
                    Box(Modifier.recess(corner = 10.dp).padding(6.dp)) {
                        FullCassetteFace(wellUi, parkedAnim, Modifier.width(faceW), blankLabel = !tape.hasTrack)
                    }
                }
            }
        }
    }
}

/** A well's source lamp: amber and glowing while its source is the deck's, dark at rest,
 * and warming up with the rest of the rack when the stack powers on. */
@Composable
private fun WellLamp(lit: Boolean) {
    val warm = LocalWarmUp.current
    Canvas(Modifier.size(9.dp)) {
        val r = size.minDimension / 2f
        if (lit) {
            drawCircle(Brush.radialGradient(listOf(AmberLed.copy(alpha = 0.55f * warm), Color.Transparent), center, r), r)
        }
        drawCircle(Color(0xFF0D0903), r * 0.95f)
        drawCircle(if (lit) AmberLed.copy(alpha = warm) else AmberLed.copy(alpha = 0.13f), r * 0.62f)
        drawCircle(Color.White.copy(alpha = if (lit) 0.55f * warm else 0.12f), r * 0.2f, center - Offset(r * 0.2f, r * 0.2f))
    }
}

/**
 * A parked well's animation state: the cassette seated (no load-in slide) and its packs
 * settled onto the tape's last known position, with no motion of their own. Stepping the
 * reels with the transport at rest eases the shown position onto [progress] and stops:
 * tape speed stays at zero throughout, so the hubs never turn and the pack balance just
 * arrives and holds, as last seen.
 */
@Composable
private fun rememberParkedAnimations(progress: Float): DeckAnimations {
    val anim = remember { DeckAnimations() }
    LaunchedEffect(progress) {
        anim.insert.snapTo(0f)
        repeat(48) { anim.reels.step(0.05f, Transport.STOPPED, progress) }
    }
    return anim
}

/**
 * The deck's mechanical style tape counter: three digits in a dark window, counting the
 * playing track's elapsed seconds and rolling past 999 back to 000, the way the little
 * geared wheels on a real deck wrap. It wears the counter's clothes; it does not pretend
 * to count reel revolutions.
 */
@Composable
private fun TapeCounter(seconds: Long, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF050505), Color(0xFF1A1B1E), Color(0xFF050505))))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Text(
                (seconds % 1000).toInt().toString().padStart(3, '0'),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFFE9E5D9),
                    letterSpacing = 3.sp,
                ),
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text("COUNTER", style = StackCaps.copy(fontSize = 6.sp), maxLines = 1)
    }
}

/**
 * One chunky mechanical piano key: a wide dark rectangle whose face sinks a few pixels as it
 * fires on touch down, with its name printed above it on the panel. The play key latches down
 * while the tape runs, the way the mechanism holds it. A key the source cannot honour (skip
 * past the end of the queue) is drawn dim and takes no taps.
 */
@Composable
private fun PianoKey(
    caption: String,
    symbol: KeySymbol?,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    latched: Boolean = false,
    accent: Boolean = false,
    enabled: Boolean = true,
    expandGlyph: Boolean = false,
) {
    var pressed by remember { mutableStateOf(false) }
    val view = LocalView.current
    val sounds = LocalMechSounds.current
    val haptics = LocalHaptics.current
    val action by rememberUpdatedState(onPress)
    val depth by animateFloatAsState(
        when {
            pressed -> 1f
            latched -> 0.6f
            else -> 0f
        },
        spring(dampingRatio = 0.55f, stiffness = 2400f), label = "piano",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            caption,
            style = StackCaps.copy(fontSize = 7.5.sp, color = if (enabled) Color(0xFF9BA0A6) else Color(0xFF5A5D62)),
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = caption
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(onPress = {
                        pressed = true
                        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
                        sounds?.keyDown()
                        action()
                        tryAwaitRelease()
                        pressed = false
                        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_RELEASE)
                        sounds?.keyUp()
                    })
                },
        ) {
            val travel = 3.5.dp.toPx() * depth
            val r = CornerRadius(4.dp.toPx())
            val alpha = if (enabled) 1f else 0.4f
            // The slot the key sinks into.
            drawRoundRect(Color.Black.copy(alpha = 0.7f * alpha), cornerRadius = r)
            // Key body, sinking with the press; a lighter front lip stays visible at the bottom.
            val bodyTop = travel
            val bodyH = size.height - 2.dp.toPx()
            drawRoundRect(
                Brush.verticalGradient(
                    listOf(Color(0xFF33363B), Color(0xFF1D1E22), Color(0xFF26282D)),
                    bodyTop, bodyTop + bodyH,
                ),
                topLeft = Offset(0f, bodyTop), size = Size(size.width, bodyH), cornerRadius = r,
            )
            drawRoundRect(
                Color.White.copy(alpha = 0.22f * alpha),
                topLeft = Offset(0f, bodyTop), size = Size(size.width, bodyH), cornerRadius = r, style = Stroke(1f),
            )
            drawLine(
                Color.White.copy(alpha = 0.14f * alpha),
                Offset(r.x, bodyTop + 1f), Offset(size.width - r.x, bodyTop + 1f), 1f,
            )
            val c = Offset(size.width / 2f, bodyTop + bodyH / 2f)
            val ink = when {
                !enabled -> Color(0xFF6E7176)
                accent && latched -> DeckColors.Accent
                else -> Color(0xFFD8DBE0)
            }
            if (expandGlyph) {
                // Four arrows pointing outward, in the expand cap's own geometry at key scale.
                val s = size.minDimension * 0.20f
                val stroke = s * 0.24f
                listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f).forEach { (sx, sy) ->
                    val inner = Offset(c.x + sx * s * 0.35f, c.y + sy * s * 0.35f)
                    val outer = Offset(c.x + sx * s, c.y + sy * s)
                    drawLine(ink, inner, outer, stroke)
                    drawLine(ink, outer, Offset(outer.x - sx * s * 0.55f, outer.y), stroke)
                    drawLine(ink, outer, Offset(outer.x, outer.y - sy * s * 0.55f), stroke)
                }
            } else {
                symbol?.let { drawSymbol(it, c, size.minDimension * 0.17f, ink) }
            }
            if (accent) {
                drawRoundRect(
                    DeckColors.Accent.copy(alpha = if (latched) 0.95f else 0.55f),
                    topLeft = Offset(size.width * 0.22f, bodyTop + bodyH - 4.dp.toPx()),
                    size = Size(size.width * 0.56f, 2.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx()),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// CD player

@Composable
private fun CdPlate(
    ui: DeckUi,
    vm: DeckViewModel,
    modifier: Modifier = Modifier,
) {
    // While a scrub drag is live on the plate's foot hairline, the green readout's elapsed
    // time shows where the finger is headed instead of where the tape is.
    var scrubTargetMs by remember { mutableStateOf<Long?>(null) }
    StackPlate("COMPACT DISC PLAYER", modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                // Left zone: the tray, and in it the disc as the component's centerpiece. The
                // disc's square is sized off the plate height (aspect ratio on the row height,
                // which is the plate's content height after the foot hairline), so the disc
                // lands at roughly plate height minus 24 dp once the plate padding and the
                // hairline are out of it. In 1.4.0 the disc Canvas carried fillMaxHeight and
                // no width at all: a bare Canvas has no intrinsic width, measured zero wide,
                // and every circle drew at radius zero, so the disc never appeared.
                BoxWithConstraints(Modifier.fillMaxHeight().aspectRatio(1f)) {
                    Box(Modifier.fillMaxSize().recess(corner = 10.dp).padding(5.dp)) {
                        CdDisc(ui, Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    LedReadout(
                        title = if (ui.trackCount > 0) "TRK ${ui.trackIndex + 1} / ${ui.trackCount}" else "CD",
                        line2 = formatTime(scrubTargetMs ?: ui.positionMs),
                        color = GreenLed,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        LedToggleButton(
                            caption = "SHUFFLE",
                            symbol = StripSymbol.SHUFFLE,
                            onPress = vm::toggleShuffle,
                            lit = ui.shuffleOn,
                            enabled = ui.canShuffle,
                            stateText = if (ui.shuffleOn) "On" else "Off",
                        )
                        Spacer(Modifier.width(16.dp))
                        LedToggleButton(
                            caption = "REPEAT",
                            symbol = if (ui.repeatMode == RepeatMode.ONE) StripSymbol.REPEAT_ONE else StripSymbol.REPEAT,
                            onPress = vm::cycleRepeat,
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
            // Tape position hairline along the foot of the tray: the shared scrubber, a
            // real control on sources that advertise seek, over a 20 dp invisible touch
            // target so a finger can land on a 3 dp line.
            SeekScrubber(
                progress = ui.progress,
                canSeek = ui.canSeek,
                durationMs = ui.durationMs,
                onSeek = vm::seekToFraction,
                modifier = Modifier.fillMaxWidth(),
                barHeight = 3.dp,
                touchHeight = 20.dp,
                corner = 2.dp,
                onDragTimeChange = { scrubTargetMs = it },
            )
        }
    }
}

/**
 * The disc: silver, ringed, its label printed with the current artwork, turning slowly while
 * the music plays and holding still while it is paused. Rotation accumulates only during
 * play, so the disc never creeps when the room is quiet.
 */
@Composable
private fun CdDisc(ui: DeckUi, modifier: Modifier = Modifier) {
    val playing = ui.transport == Transport.PLAYING
    var spin by remember { mutableFloatStateOf(0f) }
    val spinning by rememberUpdatedState(playing)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L && spinning) {
                    val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
                    spin = (spin + dt * 42f) % 360f
                }
                last = now
            }
        }
    }
    val artwork = ui.artwork
    val stripe = ui.label.stripe
    val stripe2 = ui.label.stripe2
    Canvas(modifier) {
        val d = size.minDimension
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = d / 2f * 0.97f
        // The tray the disc sits in.
        drawCircle(Color.Black.copy(alpha = 0.6f), r * 1.03f, c + Offset(0f, 2.dp.toPx()))
        drawCircle(Color(0xFF0D0E10), r * 1.02f, c)
        drawCircle(Color.White.copy(alpha = 0.10f), r * 1.02f, c, style = Stroke(1f))
        rotate(spin, c) {
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFE3E6EA), Color(0xFFB7BBC1), Color(0xFF8B9096)), c, r),
                r, c,
            )
            // The rainbow sheen a CD throws across a room.
            drawCircle(
                Brush.sweepGradient(
                    listOf(
                        Color(0x33FF5A5A), Color(0x33FFB02E), Color(0x3351FF7E),
                        Color(0x335AA8FF), Color(0x33C05AFF), Color(0x33FF5A5A),
                    ),
                    c,
                ),
                r, c,
            )
            // Groove rings in the data area.
            for (fr in listOf(0.86f, 0.78f, 0.72f)) {
                drawCircle(Color.Black.copy(alpha = 0.10f), r * fr, c, style = Stroke(1f))
            }
            // The label: this album's artwork, printed on the disc like a picture disc.
            val lr = r * 0.64f
            if (artwork != null) {
                val labelPath = Path().apply { addOval(Rect(c.x - lr, c.y - lr, c.x + lr, c.y + lr)) }
                clipPath(labelPath) {
                    drawImage(
                        artwork,
                        dstOffset = IntOffset((c.x - lr).toInt(), (c.y - lr).toInt()),
                        dstSize = IntSize((lr * 2).toInt(), (lr * 2).toInt()),
                    )
                }
            } else {
                drawCircle(Brush.linearGradient(listOf(stripe, stripe2), c - Offset(lr, lr), c + Offset(lr, lr)), lr, c)
            }
            drawCircle(Color.White.copy(alpha = 0.35f), lr, c, style = Stroke(1f))
            // Spindle.
            drawCircle(Color(0xFFC9CCD1), r * 0.17f, c)
            drawCircle(Color(0xFF0B0B0C), r * 0.085f, c)
        }
        // Glass over the tray, which does not turn with the disc.
        drawCircle(
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), c - Offset(r, r), c + Offset(r * 0.2f, r * 0.2f)),
            r, c,
        )
    }
}

/**
 * A CD player front panel toggle: the control strip's own metal cap, an LED dot that follows
 * its state, and its name printed under it. Same state, same actions, same honest dimming as
 * the strip on the portrait panel.
 */
@Composable
private fun LedToggleButton(
    caption: String,
    symbol: StripSymbol,
    onPress: () -> Unit,
    lit: Boolean,
    enabled: Boolean,
    stateText: String,
) {
    val warm = LocalWarmUp.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(6.dp)) {
            val on = lit && enabled
            drawCircle(if (on) Color(0xFFE3382A).copy(alpha = warm) else Color(0xFF571511), size.minDimension / 2f)
            drawCircle(Color.White.copy(alpha = if (on) 0.6f * warm else 0.15f), size.minDimension * 0.18f, center - Offset(1f, 1f))
        }
        Spacer(Modifier.height(4.dp))
        DeckStripButton(
            description = caption,
            onPress = onPress,
            symbol = symbol,
            lit = lit,
            enabled = enabled,
            stateText = stateText,
        )
        Spacer(Modifier.height(3.dp))
        Text(caption, style = StackCaps.copy(fontSize = 6.5.sp), maxLines = 1)
    }
}

/**
 * Peak values with a hold circuit, for the amplifier's spectrum caps and ladder caps: a new
 * high refreshes the value and holds it for about 0.9 seconds, the way the VU dials' peak
 * lamps linger, then it falls back toward the live level. The peaks live in snapshot state
 * so the canvases redraw as they fall.
 */
@Stable
private class PeakHold(count: Int) {
    val peaks = mutableStateListOf<Float>().apply { repeat(count) { add(0f) } }
    private val heldUntil = LongArray(count)

    fun update(index: Int, value: Float, nowNanos: Long, dtSeconds: Float) {
        val v = value.coerceIn(0f, 1f)
        val peak = peaks[index]
        val next = when {
            v >= peak -> {
                heldUntil[index] = nowNanos + HOLD_NANOS
                v
            }
            nowNanos <= heldUntil[index] -> peak
            else -> maxOf(v, peak - dtSeconds * FALL_PER_SECOND)
        }
        if (next != peak) peaks[index] = next
    }

    private companion object {
        const val HOLD_NANOS = 900_000_000L
        const val FALL_PER_SECOND = 1.6f
    }
}

/** One band of the spectrum display: the needle level varied by the band's own phase. */
private fun spectrumValue(base: Float, phase: Float, i: Int, bands: Int): Float {
    val wobble = 0.5f + 0.5f * sin(phase * (1.1f + 0.11f * i) + i * 2.4f)
    val shape = 1.05f - 0.30f * (i / (bands - 1f))
    return (base * shape * (0.35f + 0.75f * wobble)).coerceIn(0f, 1f)
}

// ---------------------------------------------------------------------------
// Amplifier

@Composable
private fun AmplifierPlate(
    ui: DeckUi,
    levels: Needles,
    vm: DeckViewModel,
    modifier: Modifier = Modifier,
) {
    // The maker's line runs along the amp's bottom edge, quiet in the plate's margin:
    // the product and the house behind it, stamped the way real chassis wear their names.
    StackPlate("INTEGRATED AMPLIFIER", modifier, footLabel = "FERRIC PLAYER · BY PROTECH PRIME CONSULTING") {
        Row(Modifier.fillMaxSize().padding(top = 8.dp)) {
            Column(
                Modifier.weight(0.30f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EqSliderBank(Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EqualizerButton()
                    Spacer(Modifier.width(7.dp))
                    Text("EQ", style = StackCaps.copy(fontSize = 7.5.sp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.06f)))
            Spacer(Modifier.width(10.dp))
            Row(Modifier.weight(0.42f).fillMaxHeight()) {
                SpectrumDisplay(levels, Modifier.weight(1f).fillMaxHeight())
                Spacer(Modifier.width(8.dp))
                LedLadderPair(levels, Modifier.fillMaxHeight())
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.06f)))
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier.weight(0.28f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                VolumeKnob(ui.volume, vm::setVolume, Modifier.size(118.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    "VOLUME ${(ui.volume * 10).roundToInt()}",
                    style = StackCaps.copy(fontSize = 8.sp),
                    maxLines = 1,
                )
                Spacer(Modifier.height(12.dp))
                PhonesJack()
            }
        }
    }
}

/** Jerome's Poweramp curve, cast in metal: the band gains he runs, as fixed fader sculpture. */
private val EqBands = listOf(
    "31" to 8.1f, "62" to 5.8f, "125" to 3.8f, "250" to 1.4f,
    "500" to 4.0f, "1K" to 6.0f, "2K" to 4.7f, "8K" to 9.2f,
)

@Composable
private fun EqSliderBank(modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val warm = LocalWarmUp.current
    Canvas(modifier) {
        val labelH = 11.dp.toPx()
        val top = 3.dp.toPx()
        val bottom = size.height - labelH - 2.dp.toPx()
        val trackH = bottom - top
        val n = EqBands.size
        val slotW = size.width / n
        val labelStyle = TextStyle(fontSize = 6.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9BA0A6))
        // The 0 dB detent line running the width of the bank.
        val zeroY = bottom - trackH * 0.5f
        drawLine(Color.White.copy(alpha = 0.13f), Offset(slotW * 0.2f, zeroY), Offset(size.width - slotW * 0.2f, zeroY), 1f)
        EqBands.forEachIndexed { i, (label, gain) ->
            val cx = slotW * (i + 0.5f)
            val trackW = 4.dp.toPx()
            drawRoundRect(
                Color(0xFF0A0A0B),
                topLeft = Offset(cx - trackW / 2f, top),
                size = Size(trackW, trackH),
                cornerRadius = CornerRadius(trackW / 2),
            )
            drawLine(Color.White.copy(alpha = 0.08f), Offset(cx - trackW / 2f - 1f, top), Offset(cx - trackW / 2f - 1f, bottom), 1f)
            // Fader position from the gain, on a plus or minus 12 dB travel.
            val frac = ((gain + 12f) / 24f).coerceIn(0f, 1f)
            val capY = bottom - trackH * frac
            val capW = minOf(slotW * 0.72f, 17.dp.toPx())
            val capH = 8.dp.toPx()
            drawRoundRect(
                Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(cx - capW / 2f, capY - capH / 2f + 1.5f),
                size = Size(capW, capH),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFFE8EAED), Color(0xFF9A9DA2), Color(0xFF62656A)), capY - capH / 2f, capY + capH / 2f),
                topLeft = Offset(cx - capW / 2f, capY - capH / 2f),
                size = Size(capW, capH),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
            drawLine(DeckColors.Accent, Offset(cx - capW * 0.32f, capY), Offset(cx + capW * 0.32f, capY), 1.6f)
            // The amber lamp set into the cap, lit faders glowing on a dark panel, in the
            // receiver display's own amber. Brightened a step in 1.5.0 on Jerome's word from
            // the 1.4.2 deck: a third more core and a stronger halo, so each cap's lamp reads
            // at a glance in room light without turning the bank into a light show.
            val ledC = Offset(cx, capY)
            val ledR = 2.3.dp.toPx()
            drawCircle(
                Brush.radialGradient(listOf(AmberLed.copy(alpha = 0.75f * warm), Color.Transparent), ledC, ledR * 2.8f),
                ledR * 2.8f, ledC,
            )
            drawCircle(AmberLed.copy(alpha = warm), ledR, ledC)
            drawCircle(Color.White.copy(alpha = 0.85f * warm), ledR * 0.42f, ledC - Offset(ledR * 0.3f, ledR * 0.3f))
            val layout = measurer.measure(label, labelStyle)
            drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, bottom + 3.dp.toPx()))
        }
    }
}

/**
 * Sixteen spectrum bars dancing on the deck's level feed: the stereo needle deflections set
 * the overall height, each band varies it with its own phase, the way the established
 * synthesized dance already stands in for remote audio the phone will not share. Green runs
 * to amber to red at the tips, drawn as stacked segments behind dark glass. Each band carries
 * a peak cap that lingers at its recent high on the shared hold circuit.
 */
@Composable
private fun SpectrumDisplay(levels: Needles, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "spectrum")
    val phase by transition.animateFloat(
        0f, (2f * PI).toFloat(),
        infiniteRepeatable(tween(2600, easing = LinearEasing), AnimRepeatMode.Restart),
        label = "phase",
    )
    // Peak caps, one per band, tracked off the same values the canvas draws.
    val bandCount = 16
    val peaks = remember { PeakHold(bandCount) }
    val phaseNow by rememberUpdatedState(phase)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                val base = (levels.left + levels.right) / 2f
                for (i in 0 until bandCount) {
                    peaks.update(i, spectrumValue(base, phaseNow, i, bandCount), now, dt)
                }
            }
        }
    }
    val warm = LocalWarmUp.current
    Canvas(modifier) {
        val r = CornerRadius(6.dp.toPx())
        drawRoundRect(Color(0xFF070907), cornerRadius = r)
        drawRoundRect(Color.White.copy(alpha = 0.10f), cornerRadius = r, style = Stroke(1f))
        val base = (levels.left + levels.right) / 2f
        val bands = 16
        val segs = 12
        val padX = 8.dp.toPx()
        val padTop = 8.dp.toPx()
        val padBottom = 8.dp.toPx()
        val innerW = size.width - padX * 2
        val innerH = size.height - padTop - padBottom
        val gapX = 2.5.dp.toPx()
        val barW = (innerW - gapX * (bands - 1)) / bands
        val gapY = 1.8.dp.toPx()
        val segH = (innerH - gapY * (segs - 1)) / segs
        for (i in 0 until bands) {
            val v = spectrumValue(base, phase, i, bands)
            val lit = (v * segs).roundToInt()
            val peakLit = (peaks.peaks[i] * segs).roundToInt()
            val x = padX + i * (barW + gapX)
            fun segColor(s: Int): Color {
                val frac = s / (segs - 1f)
                return when {
                    frac < 0.62f -> Color(0xFF3DDC5A)
                    frac < 0.85f -> AmberLed
                    else -> Color(0xFFFF4A26)
                }
            }
            for (s in 0 until segs) {
                val y = padTop + innerH - (s + 1) * segH - s * gapY
                // The live bar, plus the held peak cap floating at the band's recent high.
                // Lit segments and caps alike rise with the power-on warm up.
                val on = s < lit || s == peakLit - 1
                val color = segColor(s)
                drawRoundRect(
                    if (on) color.copy(alpha = warm) else color.copy(alpha = 0.10f),
                    topLeft = Offset(x, y),
                    size = Size(barW, segH),
                    cornerRadius = CornerRadius(1.dp.toPx()),
                )
            }
        }
    }
}

/** Twin ten segment LED ladders beside the spectrum, one per channel, on the same levels. */
@Composable
private fun LedLadderPair(levels: Needles, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    // Peak caps, one per channel, on the same hold circuit as the spectrum and the peak lamps.
    val peaks = remember { PeakHold(2) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                peaks.update(0, levels.left, now, dt)
                peaks.update(1, levels.right, now, dt)
            }
        }
    }
    val warm = LocalWarmUp.current
    Canvas(modifier.width(38.dp)) {
        val labelH = 10.dp.toPx()
        val top = 2.dp.toPx()
        val bottom = size.height - labelH - 2.dp.toPx()
        val segs = 10
        val gapY = 2.dp.toPx()
        val segH = (bottom - top - gapY * (segs - 1)) / segs
        val colW = 13.dp.toPx()
        val gapX = size.width - colW * 2
        val values = listOf(levels.left, levels.right)
        val tags = listOf("L", "R")
        val tagStyle = TextStyle(fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9BA0A6))
        values.forEachIndexed { ch, v ->
            val x = ch * (colW + gapX)
            val lit = (v.coerceIn(0f, 1f) * segs).roundToInt()
            val peakLit = (peaks.peaks[ch] * segs).roundToInt()
            for (s in 0 until segs) {
                val y = bottom - (s + 1) * segH - s * gapY
                val frac = s / (segs - 1f)
                val color = when {
                    frac < 0.62f -> Color(0xFF3DDC5A)
                    frac < 0.85f -> AmberLed
                    else -> Color(0xFFFF4A26)
                }
                // The live ladder, plus the held peak cap at the channel's recent high.
                // Lit segments and caps alike rise with the power-on warm up.
                val on = s < lit || s == peakLit - 1
                drawRoundRect(
                    if (on) color.copy(alpha = warm) else color.copy(alpha = 0.10f),
                    topLeft = Offset(x, y),
                    size = Size(colW, segH),
                    cornerRadius = CornerRadius(1.5.dp.toPx()),
                )
            }
            val layout = measurer.measure(tags[ch], tagStyle)
            drawText(layout, topLeft = Offset(x + colW / 2f - layout.size.width / 2f, bottom + 3.dp.toPx()))
        }
    }
}

/**
 * The amplifier's phones jack: a dark socket in a machined metal ring, with its name printed
 * under it. Panel sculpture beside the volume knob, the way a real amp's front reads.
 */
@Composable
private fun PhonesJack() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(24.dp)) {
            val c = center
            val r = size.minDimension / 2f
            drawCircle(Color.Black.copy(alpha = 0.5f), r, c + Offset(0f, 1.5f))
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFD8DBE0), Color(0xFF83868C), Color(0xFF3A3C40)),
                    c - Offset(r * 0.3f, r * 0.35f), r * 1.6f,
                ),
                r * 0.96f, c,
            )
            drawCircle(Color(0xFF08090A), r * 0.62f, c)
            drawCircle(Color.Black, r * 0.34f, c)
            drawCircle(Color.White.copy(alpha = 0.12f), r * 0.34f, c, style = Stroke(1f))
        }
        Spacer(Modifier.height(3.dp))
        Text("PHONES", style = StackCaps.copy(fontSize = 6.5.sp), maxLines = 1)
    }
}

/**
 * The amplifier's volume knob: a big rotary control on a 270 degree sweep, from bottom left
 * (silent) clockwise to bottom right (full). Drag in a circle to turn it, and a tap on the
 * tick ring sets the level where it lands. It drives the same volume state as the sliders.
 */
@Composable
private fun VolumeKnob(volume: Float, onVolume: (Float) -> Unit, modifier: Modifier = Modifier) {
    val current by rememberUpdatedState(volume)
    val set by rememberUpdatedState(onVolume)
    var lastAngle by remember { mutableStateOf<Float?>(null) }

    fun angleOf(pos: Offset, w: Int, h: Int): Float {
        val dx = pos.x - w / 2f
        val dy = pos.y - h / 2f
        return (atan2(dy, dx) * 180f / PI).toFloat()
    }
    fun fractionForAngle(a: Float): Float {
        val raw = (a - 135f + 360f) % 360f
        return when {
            raw <= 270f -> raw / 270f
            raw < 315f -> 1f
            else -> 0f
        }
    }

    Canvas(
        modifier
            .semantics {
                contentDescription = "Volume"
                progressBarRangeInfo = ProgressBarRangeInfo(current, 0f..1f)
                setProgress { set(it.coerceIn(0f, 1f)); true }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { lastAngle = angleOf(it, size.width, size.height) },
                    onDragEnd = { lastAngle = null },
                    onDragCancel = { lastAngle = null },
                    onDrag = { change, _ ->
                        change.consume()
                        val a = angleOf(change.position, size.width, size.height)
                        val last = lastAngle
                        if (last != null) {
                            var d = a - last
                            while (d > 180f) d -= 360f
                            while (d < -180f) d += 360f
                            set((current + d / 270f).coerceIn(0f, 1f))
                        }
                        lastAngle = a
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { pos -> set(fractionForAngle(angleOf(pos, size.width, size.height))) })
            },
    ) {
        val c = center
        val outerR = size.minDimension / 2f
        val knobR = outerR - 9.dp.toPx()
        fun point(angleDeg: Float, r: Float): Offset {
            val rad = (angleDeg * PI / 180f).toFloat()
            return Offset(c.x + cos(rad) * r, c.y + sin(rad) * r)
        }
        // Tick ring: eleven stops over the 270 degree sweep, value 0 at 135 degrees.
        for (i in 0..10) {
            val a = 135f + i * 27f
            val major = i % 5 == 0
            drawLine(
                Color(0xFF9BA0A6).copy(alpha = if (major) 0.9f else 0.5f),
                point(a, knobR + 3.dp.toPx()),
                point(a, knobR + (if (major) 8.dp else 6.dp).toPx()),
                if (major) 1.8f else 1.2f,
            )
        }
        // Knob body with its shadow.
        drawCircle(Color.Black.copy(alpha = 0.6f), knobR, c + Offset(0f, 2.5.dp.toPx()))
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFF43464C), Color(0xFF232529), Color(0xFF121316)),
                c - Offset(knobR * 0.35f, knobR * 0.42f), knobR * 1.65f,
            ),
            knobR, c,
        )
        // Knurling around the rim.
        for (i in 0 until 36) {
            val a = (i * 10f * PI / 180f).toFloat()
            val p0 = Offset(c.x + cos(a) * knobR * 0.90f, c.y + sin(a) * knobR * 0.90f)
            val p1 = Offset(c.x + cos(a) * knobR * 0.99f, c.y + sin(a) * knobR * 0.99f)
            drawLine(Color.White.copy(alpha = 0.13f), p0, p1, 1.4f)
        }
        drawCircle(Color.White.copy(alpha = 0.22f), knobR, c, style = Stroke(1.2f))
        drawCircle(Color(0xFF17181B), knobR * 0.30f, c)
        drawCircle(Color.White.copy(alpha = 0.18f), knobR * 0.30f, c, style = Stroke(1f))
        // The indicator, in the deck's orange.
        val a = 135f + 270f * current
        drawLine(
            DeckColors.Accent,
            point(a, knobR * 0.34f),
            point(a, knobR * 0.86f),
            3.2.dp.toPx(),
        )
    }
}
