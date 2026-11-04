package dev.foldman.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foldman.deck.DeckUi
import dev.foldman.deck.Transport
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class KeySymbol { STOP, REW, PLAY, FF, PAUSE, PREV, NEXT }

/**
 * The transport keys. Latched keys stay down, exactly as the mechanism would hold them. [vertical]
 * stacks them down the side strip, as on the folded phone.
 */
@Composable
fun TransportKeys(
    ui: DeckUi,
    onStop: () -> Unit,
    onRewind: () -> Unit,
    onPlay: () -> Unit,
    onFastForward: () -> Unit,
    onPause: () -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 86.dp,
    vertical: Boolean = false,
) {
    val t = ui.transport
    // Captions sit on the silver strip when stacked, on the dark key well otherwise.
    val cc = if (vertical) DeckColors.Engrave else Color(0xFF9EA1A7)
    val keys: @Composable (Modifier, Modifier) -> Unit = { k, play ->
        DeckKey(KeySymbol.REW, "REW", latched = t == Transport.REWINDING, onPress = onRewind, modifier = k, height = keyHeight, captionColor = cc)
        DeckKey(
            KeySymbol.PLAY, "PLAY", latched = t == Transport.PLAYING || t == Transport.PAUSED, onPress = onPlay,
            modifier = play, height = keyHeight, captionColor = cc, accent = true,
        )
        DeckKey(KeySymbol.FF, "FF", latched = t == Transport.FAST_FORWARDING, onPress = onFastForward, modifier = k, height = keyHeight, captionColor = cc)
        DeckKey(KeySymbol.PAUSE, "PAUSE", latched = t == Transport.PAUSED, onPress = onPause, modifier = k, height = keyHeight, captionColor = cc, mark = Color(0xFF3E9C5A))
        DeckKey(KeySymbol.STOP, "STOP·EJECT", latched = false, onPress = onStop, modifier = k, height = keyHeight, captionColor = cc, mark = Color(0xFFC8352B))
    }
    if (vertical) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) { keys(Modifier.fillMaxWidth(), Modifier.fillMaxWidth()) }
    } else {
        Row(
            modifier.recess(16.dp).padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) { keys(Modifier.weight(1f), Modifier.weight(1.35f)) }
    }
}

/**
 * One key: a metal cap whose front edge disappears into the slot as it goes down. It acts on
 * touch-down, as a mechanical key does, and clicks and buzzes on the way down and up.
 */
@Composable
fun DeckKey(
    symbol: KeySymbol,
    caption: String,
    latched: Boolean,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 86.dp,
    accent: Boolean = false,
    mark: Color? = null,
    captionColor: Color = Color(0xFF9EA1A7),
) {
    var pressed by remember { mutableStateOf(false) }
    val view = LocalView.current
    val sounds = LocalMechSounds.current
    val haptics = LocalHaptics.current
    // The gesture loop outlives recompositions; it calls whatever the latest action is.
    val action by rememberUpdatedState(onPress)
    val depth by animateFloatAsState(
        when {
            pressed -> 1f
            latched -> 0.72f
            else -> 0f
        },
        spring(dampingRatio = 0.5f, stiffness = 2200f), label = "key",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            caption, style = EngravedCaps.copy(color = captionColor, fontSize = 8.sp, letterSpacing = 1.sp),
            textAlign = TextAlign.Center, maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics {
                    role = Role.Button
                    contentDescription = caption
                    stateDescription = if (latched) "Down" else "Up"
                }
                .pointerInput(Unit) {
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
        ) { drawKey(symbol, depth, accent, latched, mark) }
    }
}

private fun DrawScope.drawKey(symbol: KeySymbol, depth: Float, accent: Boolean, latched: Boolean, mark: Color?) {
    val w = size.width
    val h = size.height
    val travel = h * 0.16f
    val front = h * 0.17f * (1f - depth * 0.85f)
    val top = travel * depth
    val faceH = h - travel - h * 0.17f
    val r = CornerRadius(w * 0.09f)
    // Slot shadow under the key.
    drawRoundRect(Color.Black.copy(alpha = 0.7f), topLeft = Offset(-1f, top + 2f), size = Size(w + 2f, faceH + front + 2f), cornerRadius = r)
    // Front edge, seen as the key stands proud of the slot.
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFF8D9095), Color(0xFF55585C)), top + faceH - r.x, top + faceH + front),
        topLeft = Offset(0f, top + faceH - r.x), size = Size(w, front + r.x), cornerRadius = r,
    )
    val base = if (accent) Color(0xFFD9DBDE) else Color(0xFFCFD1D5)
    val shade = 0.12f * depth
    drawRoundRect(
        Brush.verticalGradient(
            listOf(lerp(Color(0xFFF7F8F9), Color.Black, shade), lerp(base, Color.Black, shade), lerp(Color(0xFFA9ACB1), Color.Black, shade)),
            top, top + faceH,
        ),
        topLeft = Offset(0f, top), size = Size(w, faceH), cornerRadius = r,
    )
    // Fine ribbing across the top of the cap for grip.
    for (i in 1..4) {
        val y = top + faceH * 0.08f * i
        drawLine(Color.Black.copy(alpha = 0.07f), Offset(w * 0.12f, y), Offset(w * 0.88f, y), 1f)
    }
    drawRoundRect(Color.White.copy(alpha = 0.8f), topLeft = Offset(0.8f, top + 0.8f), size = Size(w - 1.6f, faceH - 1.6f), cornerRadius = r, style = Stroke(1.2f))
    if (accent || mark != null) {
        drawRoundRect(mark ?: DeckColors.Accent, topLeft = Offset(w * 0.18f, top + faceH - h * 0.1f), size = Size(w * 0.64f, h * 0.035f), cornerRadius = CornerRadius(h))
    }
    val c = Offset(w / 2f, top + faceH * 0.48f)
    val s = minOf(w, faceH) * 0.16f
    val ink = if (accent && latched) DeckColors.AccentDeep else DeckColors.Engrave
    drawSymbol(symbol, c, s, ink)
}

fun DrawScope.drawSymbol(symbol: KeySymbol, c: Offset, s: Float, ink: Color) {
    fun tri(cx: Float, dir: Float) = Path().apply {
        moveTo(cx - s * 0.8f * dir, c.y - s)
        lineTo(cx + s * 0.8f * dir, c.y)
        lineTo(cx - s * 0.8f * dir, c.y + s)
        close()
    }
    when (symbol) {
        KeySymbol.STOP -> drawRect(ink, Offset(c.x - s * 0.85f, c.y - s * 0.85f), Size(s * 1.7f, s * 1.7f))
        KeySymbol.PLAY -> drawPath(tri(c.x + s * 0.1f, 1f), ink)
        KeySymbol.FF -> {
            drawPath(tri(c.x - s * 0.7f, 1f), ink); drawPath(tri(c.x + s * 0.7f, 1f), ink)
        }
        KeySymbol.REW -> {
            drawPath(tri(c.x - s * 0.7f, -1f), ink); drawPath(tri(c.x + s * 0.7f, -1f), ink)
        }
        KeySymbol.PAUSE -> {
            drawRect(ink, Offset(c.x - s * 0.8f, c.y - s), Size(s * 0.55f, s * 2f))
            drawRect(ink, Offset(c.x + s * 0.25f, c.y - s), Size(s * 0.55f, s * 2f))
        }
        KeySymbol.PREV -> {
            drawRect(ink, Offset(c.x - s * 1.25f, c.y - s), Size(s * 0.3f, s * 2f))
            drawPath(tri(c.x - s * 0.15f, -1f), ink); drawPath(tri(c.x + s * 1.0f, -1f), ink)
        }
        KeySymbol.NEXT -> {
            drawPath(tri(c.x - s * 1.0f, 1f), ink); drawPath(tri(c.x + s * 0.15f, 1f), ink)
            drawRect(ink, Offset(c.x + s * 0.95f, c.y - s), Size(s * 0.3f, s * 2f))
        }
    }
}

/** A small round push button (AMS skip, counter reset, tape shelf). */
@Composable
fun MiniButton(
    caption: String,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    symbol: KeySymbol? = null,
    diameter: Dp = 34.dp,
    enabled: Boolean = true,
) {
    var pressed by remember { mutableStateOf(false) }
    val view = LocalView.current
    val sounds = LocalMechSounds.current
    val haptics = LocalHaptics.current
    val action by rememberUpdatedState(onPress)
    val depth by animateFloatAsState(if (pressed) 1f else 0f, spring(stiffness = 3000f), label = "mini")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(diameter)
                .semantics { role = Role.Button; contentDescription = caption }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(onPress = {
                        pressed = true
                        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        sounds?.keyUp()
                        action()
                        tryAwaitRelease()
                        pressed = false
                    })
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val r = size.minDimension / 2f
                drawCircle(Color.Black.copy(alpha = 0.6f), r, c)
                drawCircle(Color.White.copy(alpha = 0.5f), r, c + Offset(0f, 1.2f), style = Stroke(1f))
                val cap = r * (0.84f - depth * 0.05f)
                drawCircle(
                    Brush.radialGradient(
                        listOf(lerp(Color(0xFFF2F3F5), Color.Black, depth * 0.15f), Color(0xFF9B9EA3)),
                        c - Offset(cap * 0.35f, cap * 0.45f), cap * 1.7f,
                    ),
                    cap, c + Offset(0f, depth * 1.5f),
                )
                symbol?.let { drawSymbol(it, c + Offset(0f, depth * 1.5f), cap * 0.3f, if (enabled) DeckColors.Engrave else DeckColors.EngraveLight) }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(caption, style = EngravedCaps.copy(fontSize = 7.sp, letterSpacing = 1.sp), maxLines = 1)
    }
}

/** Glyphs for the small round buttons, drawn locally in drawSymbol's weight and ink. */
enum class RoundSymbol { GEAR, LIST, MOON }

fun DrawScope.drawRoundSymbol(symbol: RoundSymbol, c: Offset, s: Float, ink: Color) {
    when (symbol) {
        RoundSymbol.GEAR -> {
            drawCircle(ink, s * 0.40f, c, style = Stroke(s * 0.32f))
            for (t in 0 until 8) {
                val a = (t * 45.0 * PI / 180.0).toFloat()
                drawLine(
                    ink,
                    Offset(c.x + cos(a) * s * 0.60f, c.y + sin(a) * s * 0.60f),
                    Offset(c.x + cos(a) * s * 0.95f, c.y + sin(a) * s * 0.95f),
                    s * 0.28f, StrokeCap.Round,
                )
            }
        }
        RoundSymbol.LIST -> {
            for (i in -1..1) {
                val y = c.y + i * s * 0.55f
                drawCircle(ink, s * 0.15f, Offset(c.x - s * 0.72f, y))
                drawLine(ink, Offset(c.x - s * 0.15f, y), Offset(c.x + s * 0.92f, y), s * 0.24f, StrokeCap.Round)
            }
        }
        RoundSymbol.MOON -> {
            // A crescent: the full moon with a bite taken out of its upper right.
            val moon = Path().apply {
                addOval(Rect(c.x - s * 0.85f, c.y - s * 0.85f, c.x + s * 0.85f, c.y + s * 0.85f))
            }
            val bite = Path().apply {
                addOval(Rect(c.x - s * 0.02f, c.y - s * 1.12f, c.x + s * 1.48f, c.y + s * 0.38f))
            }
            moon.op(moon, bite, PathOperation.Difference)
            drawPath(moon, ink)
        }
    }
}

/**
 * A round transport button for the portrait deck: the [MiniButton] cap recipe at deck size, with
 * a [drawSymbol] or [drawRoundSymbol] glyph, an optional orange ring, and the same click and buzz
 * as the piano keys.
 */
@Composable
fun RoundTransportButton(
    caption: String,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    symbol: KeySymbol? = null,
    roundSymbol: RoundSymbol? = null,
    diameter: Dp = 52.dp,
    enabled: Boolean = true,
    ring: Boolean = false,
    active: Boolean = false,
) {
    var pressed by remember { mutableStateOf(false) }
    val view = LocalView.current
    val sounds = LocalMechSounds.current
    val haptics = LocalHaptics.current
    val action by rememberUpdatedState(onPress)
    val depth by animateFloatAsState(if (pressed) 1f else 0f, spring(stiffness = 3000f), label = "round")
    Box(
        modifier
            .size(diameter)
            .semantics { role = Role.Button; contentDescription = caption }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
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
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f
            drawCircle(Color.Black.copy(alpha = 0.6f), r, c)
            drawCircle(Color.White.copy(alpha = 0.5f), r, c + Offset(0f, 1.2f), style = Stroke(1f))
            val cap = r * (0.84f - depth * 0.05f)
            val cy = c + Offset(0f, depth * 1.5f)
            drawCircle(
                Brush.radialGradient(
                    listOf(lerp(Color(0xFFF2F3F5), Color.Black, depth * 0.15f), Color(0xFF9B9EA3)),
                    cy - Offset(cap * 0.35f, cap * 0.45f), cap * 1.7f,
                ),
                cap, cy,
            )
            if (ring) drawCircle(if (active) DeckColors.Accent else DeckColors.Accent.copy(alpha = 0.75f), cap, cy, style = Stroke(2.2f))
            val ink = when {
                !enabled -> DeckColors.EngraveLight
                active -> DeckColors.AccentDeep
                else -> DeckColors.Engrave
            }
            symbol?.let { drawSymbol(it, cy, cap * 0.3f, ink) }
            roundSymbol?.let { drawRoundSymbol(it, cy, cap * 0.42f, ink) }
        }
    }
}

/**
 * A small dark cap carrying the expand or contract glyph: four arrows pointing outward to grow
 * the cassette, or inward to return to the full deck. The glyph is drawn here, in the same line
 * weight as the rest of the deck's symbols.
 */
@Composable
fun ExpandToggleButton(
    expanded: Boolean,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 38.dp,
) {
    var pressed by remember { mutableStateOf(false) }
    val view = LocalView.current
    val sounds = LocalMechSounds.current
    val haptics = LocalHaptics.current
    val action by rememberUpdatedState(onPress)
    val depth by animateFloatAsState(if (pressed) 1f else 0f, spring(stiffness = 3000f), label = "expand")
    Box(
        modifier
            .size(diameter)
            .semantics {
                role = Role.Button
                contentDescription = if (expanded) "Show full deck" else "Expand cassette"
            }
            .pointerInput(Unit) {
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
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f
            drawCircle(Color.Black.copy(alpha = 0.68f), r, c + Offset(0f, 1.5f))
            val cap = r * (0.88f - depth * 0.05f)
            val cy = c + Offset(0f, depth * 1.5f)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFF3A3D42), Color(0xFF232529), Color(0xFF131417)),
                    center = cy - Offset(cap * 0.35f, cap * 0.45f), radius = cap * 1.65f,
                ),
                cap, cy,
            )
            drawCircle(Color.White.copy(alpha = 0.28f), cap, cy, style = Stroke(1.1f))
            val ink = Color(0xFFD8DBE0)
            val stroke = cap * 0.13f
            listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f).forEach { (sx, sy) ->
                val inner = Offset(cy.x + sx * cap * 0.20f, cy.y + sy * cap * 0.20f)
                val outer = Offset(cy.x + sx * cap * 0.58f, cy.y + sy * cap * 0.58f)
                val start = if (expanded) outer else inner
                val end = if (expanded) inner else outer
                drawLine(ink, start, end, stroke, StrokeCap.Round)
                drawLine(ink, end, Offset(end.x - sx * cap * 0.30f, end.y), stroke, StrokeCap.Round)
                drawLine(ink, end, Offset(end.x, end.y - sy * cap * 0.30f), stroke, StrokeCap.Round)
            }
        }
    }
}

/** Glyphs for the control strip buttons at the bottom of the portrait now playing panel. */
enum class StripSymbol { EQ, SHUFFLE, REPEAT, REPEAT_ONE }

/**
 * Draws a strip glyph centred on [c], sized by [s] (roughly the glyph's half width). Shuffle is
 * two strands crossing into arrowheads; repeat is a closed loop with an arrowhead on the top
 * edge running right and one on the bottom edge running left, the way the tape travels;
 * REPEAT_ONE adds a small 1 inside the loop. EQ is a text label and is drawn by the button.
 */
fun DrawScope.drawStripSymbol(symbol: StripSymbol, c: Offset, s: Float, ink: Color) {
    val w = s * 0.17f
    fun p(x: Float, y: Float) = Offset(c.x + x * s, c.y + y * s)
    when (symbol) {
        StripSymbol.EQ -> Unit
        StripSymbol.SHUFFLE -> {
            val lower = Path().apply {
                moveTo(p(-0.92f, 0.42f).x, p(-0.92f, 0.42f).y)
                cubicTo(p(-0.35f, 0.42f).x, p(-0.35f, 0.42f).y, p(0.05f, -0.42f).x, p(0.05f, -0.42f).y, p(0.58f, -0.42f).x, p(0.58f, -0.42f).y)
            }
            val upper = Path().apply {
                moveTo(p(-0.92f, -0.42f).x, p(-0.92f, -0.42f).y)
                cubicTo(p(-0.35f, -0.42f).x, p(-0.35f, -0.42f).y, p(0.05f, 0.42f).x, p(0.05f, 0.42f).y, p(0.58f, 0.42f).x, p(0.58f, 0.42f).y)
            }
            drawPath(lower, ink, style = Stroke(w, cap = StrokeCap.Round))
            drawPath(upper, ink, style = Stroke(w, cap = StrokeCap.Round))
            // Arrowheads where each strand lands.
            drawLine(ink, p(0.56f, -0.70f), p(0.94f, -0.42f), w, StrokeCap.Round)
            drawLine(ink, p(0.56f, -0.14f), p(0.94f, -0.42f), w, StrokeCap.Round)
            drawLine(ink, p(0.56f, 0.14f), p(0.94f, 0.42f), w, StrokeCap.Round)
            drawLine(ink, p(0.56f, 0.70f), p(0.94f, 0.42f), w, StrokeCap.Round)
        }
        StripSymbol.REPEAT, StripSymbol.REPEAT_ONE -> {
            drawRoundRect(
                ink,
                topLeft = p(-0.70f, -0.55f),
                size = Size(s * 1.4f, s * 1.1f),
                cornerRadius = CornerRadius(s * 0.3f),
                style = Stroke(w),
            )
            val topHead = Path().apply {
                moveTo(p(0.42f, -0.82f).x, p(0.42f, -0.82f).y)
                lineTo(p(0.90f, -0.55f).x, p(0.90f, -0.55f).y)
                lineTo(p(0.42f, -0.28f).x, p(0.42f, -0.28f).y)
                close()
            }
            val bottomHead = Path().apply {
                moveTo(p(-0.42f, 0.28f).x, p(-0.42f, 0.28f).y)
                lineTo(p(-0.90f, 0.55f).x, p(-0.90f, 0.55f).y)
                lineTo(p(-0.42f, 0.82f).x, p(-0.42f, 0.82f).y)
                close()
            }
            drawPath(topHead, ink)
            drawPath(bottomHead, ink)
            if (symbol == StripSymbol.REPEAT_ONE) {
                drawLine(ink, p(-0.20f, -0.10f), p(0f, -0.30f), w, StrokeCap.Round)
                drawLine(ink, p(0f, -0.30f), p(0f, 0.30f), w, StrokeCap.Round)
                drawLine(ink, p(-0.20f, 0.30f), p(0.20f, 0.30f), w, StrokeCap.Round)
            }
        }
    }
}

/**
 * A control strip button: the [RoundTransportButton] metal cap recipe at strip size, with a
 * [StripSymbol] glyph. Lit buttons wear the deck's orange (Accent ring and ink); unlit ones the
 * engraved neutral. A button the active source cannot honour is drawn at low alpha and takes
 * no taps, so the strip never promises a state it cannot set.
 *
 * A button may also answer a long press ([onLongPress], with [longPressHint] spoken as part of
 * its accessibility description and offered as the accessibility long click). Such a button
 * defers its tap action to the release, so the long press can land without the tap firing on
 * the way down; the EQ shortcut uses this to open its app chooser.
 */
@Composable
fun DeckStripButton(
    description: String,
    onPress: () -> Unit,
    symbol: StripSymbol,
    modifier: Modifier = Modifier,
    lit: Boolean = false,
    enabled: Boolean = true,
    stateText: String? = null,
    diameter: Dp = 38.dp,
    onLongPress: (() -> Unit)? = null,
    longPressHint: String? = null,
) {
    var pressed by remember { mutableStateOf(false) }
    val view = LocalView.current
    val sounds = LocalMechSounds.current
    val haptics = LocalHaptics.current
    val action by rememberUpdatedState(onPress)
    val longAction by rememberUpdatedState(onLongPress)
    val depth by animateFloatAsState(if (pressed) 1f else 0f, spring(stiffness = 3000f), label = "strip")
    val measurer = rememberTextMeasurer()
    Box(
        modifier
            .size(diameter)
            .alpha(if (enabled) 1f else 0.35f)
            .semantics {
                role = Role.Button
                contentDescription = if (longPressHint != null) "$description. $longPressHint" else description
                stateText?.let { stateDescription = it }
                if (onLongPress != null) {
                    onLongClick(label = longPressHint ?: "Long press") { longAction?.invoke(); true }
                }
            }
            .pointerInput(enabled, onLongPress != null) {
                if (!enabled) return@pointerInput
                if (onLongPress == null) {
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
                            if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            sounds?.keyDown()
                            tryAwaitRelease()
                            pressed = false
                            if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_RELEASE)
                            sounds?.keyUp()
                        },
                        onTap = { action() },
                        onLongPress = {
                            if (haptics) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            longAction?.invoke()
                        },
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f
            drawCircle(Color.Black.copy(alpha = 0.6f), r, c)
            drawCircle(Color.White.copy(alpha = 0.5f), r, c + Offset(0f, 1.2f), style = Stroke(1f))
            val cap = r * (0.84f - depth * 0.05f)
            val cy = c + Offset(0f, depth * 1.5f)
            drawCircle(
                Brush.radialGradient(
                    listOf(lerp(Color(0xFFF2F3F5), Color.Black, depth * 0.15f), Color(0xFF9B9EA3)),
                    cy - Offset(cap * 0.35f, cap * 0.45f), cap * 1.7f,
                ),
                cap, cy,
            )
            if (lit) drawCircle(DeckColors.Accent, cap, cy, style = Stroke(2.2f))
            val ink = if (lit) DeckColors.Accent else DeckColors.Engrave
            if (symbol == StripSymbol.EQ) {
                val label = measurer.measure("EQ", TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ink))
                drawText(label, topLeft = Offset(cy.x - label.size.width / 2f, cy.y - label.size.height / 2f))
            } else {
                drawStripSymbol(symbol, cy, cap * 0.55f, ink)
            }
        }
    }
}
