package dev.foldman.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foldman.deck.Source
import kotlin.random.Random

object DeckColors {
    val Backdrop = Color(0xFF141210)
    val MetalHi = Color(0xFFE4E5E7)
    val Metal = Color(0xFFC3C5C9)
    val MetalLo = Color(0xFF8F9297)
    val Recess = Color(0xFF1C1C1F)
    val Engrave = Color(0xFF3A3C41)
    val EngraveLight = Color(0xFF6B6E74)
    val Accent = Color(0xFFF0722B)
    val AccentDeep = Color(0xFFC4501A)
    val Led = Color(0xFFFF4A26)
    val DarkMetalHi = Color(0xFF33353A)
    val DarkMetal = Color(0xFF222327)
    val DarkMetalLo = Color(0xFF131416)
    val Paper = Color(0xFFF2EBDC)
    val PaperLine = Color(0xFFB9CFE0)
    val Ink = Color(0xFF2A2522)
    val Tape = Color(0xFF3D2618)

    /** The cassette body: a fixed rich black for every source, per Jerome's 1.1.5 ruling. */
    val CassetteShell = Color(0xFF17181C)

    fun shell(source: Source) = CassetteShell
}

/**
 * Brushed aluminium with a bevelled edge and corner screws: the walkman's body. The brushing is a
 * few thousand hairlines baked once per size.
 */
fun Modifier.chassis(corner: Dp = 30.dp, screws: Boolean = true): Modifier = drawWithCache {
    val r = corner.toPx()
    val rnd = Random(7)
    val hairlines = List(4) { Path() }
    var y = 0f
    while (y < size.height) {
        val x0 = rnd.nextFloat() * size.width * 0.3f
        val x1 = size.width - rnd.nextFloat() * size.width * 0.3f
        val p = hairlines[rnd.nextInt(4)]
        p.moveTo(x0, y)
        p.lineTo(x1, y)
        y += 0.6f + rnd.nextFloat() * 1.4f
    }
    val body = Brush.verticalGradient(
        0f to DeckColors.MetalHi, 0.18f to DeckColors.Metal, 0.75f to Color(0xFFB6B8BC), 1f to DeckColors.MetalLo,
    )
    val sheen = Brush.linearGradient(
        0f to Color.Transparent, 0.45f to Color.White.copy(alpha = 0.16f), 0.55f to Color.Transparent,
        start = Offset(0f, 0f), end = Offset(size.width, size.height),
    )
    onDrawBehind {
        drawRoundRect(Color.Black.copy(alpha = 0.55f), topLeft = Offset(0f, 6f), cornerRadius = CornerRadius(r))
        drawRoundRect(body, cornerRadius = CornerRadius(r))
        val alphas = floatArrayOf(0.05f, 0.08f, 0.04f, 0.10f)
        hairlines.forEachIndexed { i, p ->
            drawPath(p, if (i % 2 == 0) Color.White.copy(alpha = alphas[i]) else Color.Black.copy(alpha = alphas[i]), style = Stroke(0.8f))
        }
        drawRoundRect(sheen, cornerRadius = CornerRadius(r))
        // Bevel: bright top-left lip, dark bottom-right.
        drawRoundRect(Color.White.copy(alpha = 0.7f), cornerRadius = CornerRadius(r), style = Stroke(2.5f))
        drawRoundRect(
            Color.Black.copy(alpha = 0.25f), topLeft = Offset(1.5f, 1.5f), size = Size(size.width - 3f, size.height - 3f),
            cornerRadius = CornerRadius(r - 1.5f), style = Stroke(1.5f),
        )
        if (screws) {
            val inset = r * 0.62f
            val sr = 5.dp.toPx()
            listOf(
                Offset(inset, inset), Offset(size.width - inset, inset),
                Offset(inset, size.height - inset), Offset(size.width - inset, size.height - inset),
            ).forEachIndexed { i, c -> drawScrew(c, sr, 20f + i * 37f) }
        }
    }
}

/**
 * Dark brushed metal for the portrait deck: the same seeded hairline technique as [chassis] but in
 * near-black tones, with the bevels kept quiet so the gauges and the tape read against it.
 */
fun Modifier.darkBrushed(corner: Dp = 0.dp): Modifier = drawWithCache {
    val r = corner.toPx()
    val rnd = Random(7)
    val hairlines = List(4) { Path() }
    var y = 0f
    while (y < size.height) {
        val x0 = rnd.nextFloat() * size.width * 0.3f
        val x1 = size.width - rnd.nextFloat() * size.width * 0.3f
        val p = hairlines[rnd.nextInt(4)]
        p.moveTo(x0, y)
        p.lineTo(x1, y)
        y += 0.6f + rnd.nextFloat() * 1.4f
    }
    val body = Brush.verticalGradient(
        0f to DeckColors.DarkMetalHi, 0.18f to DeckColors.DarkMetal, 0.75f to Color(0xFF1B1C1F), 1f to DeckColors.DarkMetalLo,
    )
    val sheen = Brush.linearGradient(
        0f to Color.Transparent, 0.45f to Color.White.copy(alpha = 0.06f), 0.55f to Color.Transparent,
        start = Offset(0f, 0f), end = Offset(size.width, size.height),
    )
    onDrawBehind {
        drawRoundRect(body, cornerRadius = CornerRadius(r))
        val alphas = floatArrayOf(0.05f, 0.08f, 0.04f, 0.10f)
        hairlines.forEachIndexed { i, p ->
            drawPath(p, if (i % 2 == 0) Color.White.copy(alpha = alphas[i] * 0.7f) else Color.Black.copy(alpha = alphas[i]), style = Stroke(0.8f))
        }
        drawRoundRect(sheen, cornerRadius = CornerRadius(r))
        // Subdued bevel: the silver chassis lip would glare on a dark deck.
        drawRoundRect(Color.White.copy(alpha = 0.22f), cornerRadius = CornerRadius(r), style = Stroke(2f))
        drawRoundRect(
            Color.Black.copy(alpha = 0.35f), topLeft = Offset(1.5f, 1.5f), size = Size(size.width - 3f, size.height - 3f),
            cornerRadius = CornerRadius((r - 1.5f).coerceAtLeast(0f)), style = Stroke(1.5f),
        )
    }
}

fun DrawScope.drawScrew(center: Offset, radius: Float, slotAngle: Float) {
    drawCircle(Color.Black.copy(alpha = 0.35f), radius * 1.15f, center + Offset(0f, radius * 0.15f))
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFEDEEF0), Color(0xFF9A9DA2), Color(0xFF6E7176)), center = center - Offset(radius * 0.3f, radius * 0.3f), radius = radius * 1.6f),
        radius, center,
    )
    rotate(slotAngle, center) {
        val w = radius * 0.28f
        drawLine(Color(0xFF45474B), center - Offset(radius * 0.75f, 0f), center + Offset(radius * 0.75f, 0f), w)
        drawLine(Color(0xFF45474B), center - Offset(0f, radius * 0.75f), center + Offset(0f, radius * 0.75f), w)
    }
}

/**
 * A dark recessed plate set into the metal, used by the now playing panel. The fill is a
 * brushed metal gradient running darker toward the bottom, with a speckle grain baked
 * once per size (seeded, never random per frame) coarse and strong enough to read as plate
 * grain at arm's length, a hairline of light along the inner top edge and a soft shade
 * pooling at the bottom, so the plate reads as material rather than a flat rectangle.
 * Everything stays dark enough that white text is unaffected.
 */
fun Modifier.recess(corner: Dp = 14.dp): Modifier = drawWithCache {
    val r = CornerRadius(corner.toPx())
    val inner = Brush.verticalGradient(listOf(Color(0xFF1C1E24), Color(0xFF101216)))
    val plate = RoundRect(0f, 0f, size.width, size.height - 1.5f, r)
    val platePath = Path().apply { addRoundRect(plate) }
    // Speckle grain, generated once per size: one path of tiny rects, drawn in two neutral
    // passes (shadow, then a fainter highlight offset a hair) like the cassette shell grain.
    val speckle = Path().apply {
        val rnd = Random(11)
        val count = (size.width * size.height / 390f).toInt().coerceIn(300, 4000)
        repeat(count) {
            val x = rnd.nextFloat() * size.width
            val y = rnd.nextFloat() * size.height
            addRect(Rect(x, y, x + 1.65f, y + 1.65f))
        }
    }
    onDrawBehind {
        drawRoundRect(Color.White.copy(alpha = 0.55f), topLeft = Offset(0f, 1.5f), cornerRadius = r)
        drawRoundRect(inner, size = Size(size.width, size.height - 1.5f), cornerRadius = r)
        clipPath(platePath) {
            drawPath(speckle, Color.Black.copy(alpha = 0.09f))
            translate(0.8f, 0.8f) { drawPath(speckle, Color.White.copy(alpha = 0.055f)) }
            // Soft inner shade pooling at the bottom of the plate.
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.20f)), size.height * 0.7f, size.height),
                Offset(0f, size.height * 0.7f), Size(size.width, size.height * 0.3f),
            )
            // Inner top highlight: light catching the plate's upper edge.
            drawLine(Color.White.copy(alpha = 0.14f), Offset(r.x, 1f), Offset(size.width - r.x, 1f), 1.dp.toPx())
        }
        drawRoundRect(Color.Black.copy(alpha = 0.6f), size = Size(size.width, size.height - 1.5f), cornerRadius = r, style = Stroke(2f))
    }
}

val EngravedCaps = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 9.sp,
    letterSpacing = 1.6.sp,
    color = DeckColors.Engrave,
)
