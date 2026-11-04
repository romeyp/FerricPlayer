package dev.foldman.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.foldman.deck.DeckUi
import dev.foldman.deck.LabelPalette
import dev.foldman.deck.Source
import dev.foldman.deck.Transport
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

// Cassette geometry in millimetres (a real Compact Cassette is 100.4 × 63.8).
private val HUB_L = Offset(28.75f, 32f)
private val HUB_R = Offset(71.25f, 32f)
private val WINDOW = Rect(15f, 21f, 85f, 43f)

/**
 * The part of the cassette the case's window shows: both hubs, the tape packs and the printed
 * label around them. The rest of the tape stays hidden behind the body, as on the real thing.
 */
private val CROP = Rect(8f, 12.5f, 92f, 48.5f)
const val TAPE_WINDOW_ASPECT = 84f / 36f

/**
 * The window in the case with the cassette behind it. [vertical] stands it on end (label side to
 * the left) for the folded phone, like the original portable players.
 */
@Composable
fun TapeWindow(ui: DeckUi, anim: DeckAnimations, modifier: Modifier = Modifier, vertical: Boolean = false) {
    val measurer = rememberTextMeasurer()
    val shell by androidx.compose.animation.animateColorAsState(DeckColors.shell(ui.source), tween(600), label = "shell")
    Canvas(
        modifier
            .quarterTurn(vertical)
            .aspectRatio(TAPE_WINDOW_ASPECT)
            .graphicsLayer(),
    ) {
        val u = size.width / CROP.width
        val r = CornerRadius(3.2f * u)
        val rim = 1.1f * u
        // Recess and chrome rim.
        drawRoundRect(Color.Black.copy(alpha = 0.45f), topLeft = Offset(0f, rim * 0.6f), cornerRadius = r)
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFFF4F5F7), Color(0xFF8C9096), Color(0xFFDADCDF))),
            cornerRadius = r,
        )
        val inner = RoundRect(rim, rim, size.width - rim, size.height - rim, CornerRadius(r.x - rim))
        val innerPath = Path().apply { addRoundRect(inner) }
        drawPath(innerPath, Color(0xFF0C0C0D))
        val slide = anim.insert.value
        val pop = anim.pop.value
        clipPath(innerPath) {
            // In and out through the top of the case (the left of the canvas when stood on end).
            val dx = if (vertical) slide * size.width * 1.1f + pop * u * 5f else 0f
            val dy = if (vertical) 0f else -slide * size.height * 1.3f - pop * u * 5f
            translate(dx, dy) {
                withTransform({
                    translate(-CROP.left * u, -CROP.top * u)
                    scale(u, u, Offset.Zero)
                }) {
                    drawCassette(ui, anim.reels, shell, measurer, u)
                }
            }
            // Smoked glass: tint, a long diagonal reflection and a soft top glare.
            drawRect(Color(0xFF26303C).copy(alpha = 0.22f))
            drawPath(
                Path().apply {
                    moveTo(size.width * 0.52f, 0f); lineTo(size.width * 0.70f, 0f)
                    lineTo(size.width * 0.46f, size.height); lineTo(size.width * 0.28f, size.height); close()
                },
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.04f), Color.Transparent),
                    size.width * 0.28f, size.width * 0.70f,
                ),
            )
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent), 0f, size.height * 0.3f))
            // Inner shadow where the case overhangs the glass.
            drawPath(innerPath, Color.Black.copy(alpha = 0.6f), style = Stroke(rim * 1.4f))
        }
        drawPath(innerPath, Color.White.copy(alpha = 0.35f), style = Stroke(0.6f))
    }
}

/** Draws the cassette in its own millimetre space; [u] only scales stroke widths of text. */
private fun DrawScope.drawCassette(ui: DeckUi, reels: ReelMotion, shell: Color, measurer: TextMeasurer, u: Float) {
    val light = shell.luminance() > 0.5f
    val shellRR = RoundRect(0f, 0f, 100f, 64f, CornerRadius(3.4f))
    val shellPath = Path().apply { addRoundRect(shellRR) }

    // Drop shadow on the bay floor.
    translate(0.8f, 1.6f) { drawPath(shellPath, Color.Black.copy(alpha = 0.45f)) }
    drawPath(
        shellPath,
        Brush.verticalGradient(listOf(lerp(shell, Color.White, 0.18f), shell, lerp(shell, Color.Black, 0.22f)), 0f, 64f),
    )
    // Moulded grip ridges at the sides, each shadowed below its highlight like the grip rails.
    for (i in 0 until 7) {
        val y = 22f + i * 3f
        drawLine(Color.Black.copy(alpha = 0.25f), Offset(1.4f, y + 0.32f), Offset(3.2f, y + 0.32f), 0.5f)
        drawLine(lerp(shell, Color.White, 0.12f), Offset(1.4f, y), Offset(3.2f, y), 0.5f)
        drawLine(Color.Black.copy(alpha = 0.25f), Offset(96.8f, y + 0.32f), Offset(98.6f, y + 0.32f), 0.5f)
        drawLine(lerp(shell, Color.White, 0.12f), Offset(96.8f, y), Offset(98.6f, y), 0.5f)
    }
    drawGripRails(100f)

    // Window: tape packs and hubs seen through smoked plastic.
    val windowRR = RoundRect(WINDOW, CornerRadius(3.2f))
    val windowPath = Path().apply { addRoundRect(windowRR) }
    clipPath(windowPath) {
        drawRect(Color(0xFF1A1714), WINDOW.topLeft, WINDOW.size)
        drawPack(HUB_L, reels.leftRadius, reels.leftAngle)
        drawPack(HUB_R, reels.rightRadius, reels.rightAngle)
        // Tape running from each pack down to the guide rollers.
        drawLine(DeckColors.Tape, Offset(HUB_L.x - reels.leftRadius, HUB_L.y), Offset(HUB_L.x - reels.leftRadius - 1f, 64f), 0.6f)
        drawLine(DeckColors.Tape, Offset(HUB_R.x + reels.rightRadius, HUB_R.y), Offset(HUB_R.x + reels.rightRadius + 1f, 64f), 0.6f)
        drawRect(Color(0xFF6F7680).copy(alpha = 0.18f), WINDOW.topLeft, WINDOW.size)
    }
    drawHub(HUB_L, reels.leftAngle, reels.leftDelta)
    drawHub(HUB_R, reels.rightAngle, reels.rightDelta)
    // Window glare and moulded frame.
    clipPath(windowPath) {
        drawPath(
            Path().apply {
                moveTo(WINDOW.left + 18f, WINDOW.top); lineTo(WINDOW.left + 30f, WINDOW.top)
                lineTo(WINDOW.left + 20f, WINDOW.bottom); lineTo(WINDOW.left + 8f, WINDOW.bottom); close()
            },
            Color.White.copy(alpha = 0.07f),
        )
    }
    drawPath(windowPath, Color.Black.copy(alpha = 0.55f), style = Stroke(0.5f))

    drawLabel(ui, ui.label, measurer, windowRR, u)
    drawBottomBridge(shell, light)

    listOf(Offset(4.4f, 4.4f), Offset(95.6f, 4.4f), Offset(4.4f, 59.6f), Offset(95.6f, 59.6f), Offset(50f, 55.5f))
        .forEachIndexed { i, c -> drawScrew(c, 1.5f, 15f + i * 40f) }
    drawPath(shellPath, Color.Black.copy(alpha = 0.5f), style = Stroke(0.35f))
    drawPath(Path().apply { addRoundRect(RoundRect(0.4f, 0.4f, 99.6f, 63.6f, CornerRadius(3.1f))) }, Color.White.copy(alpha = 0.25f), style = Stroke(0.25f))
}

/**
 * The finger grips real cassettes mould along both side edges, so a thumb can pull the tape
 * out of a tight deck slot: a narrow band of fine vertical ribs in the exposed shell strip
 * beside the label, spaced 1.6 mm apart and running most of the shell height, stopping clear
 * of the corner screws and the label edges. Each rib is a rounded ridge lit on its left face
 * (white at 0.10) and shadowed on its right (black at 0.25), the same light logic as the
 * horizontal grip ridges, so on the black shell the band reads as moulded plastic catching
 * light rather than stripes painted on. Drawn in millimetre space for a shell [width] wide.
 */
private fun DrawScope.drawGripRails(width: Float) {
    for (cx in listOf(1.6f, 3.2f, 4.8f, width - 4.8f, width - 3.2f, width - 1.6f)) {
        drawLine(Color.White.copy(alpha = 0.10f), Offset(cx - 0.22f, 7f), Offset(cx - 0.22f, 57f), 0.45f, StrokeCap.Round)
        drawLine(Color.Black.copy(alpha = 0.25f), Offset(cx + 0.22f, 7f), Offset(cx + 0.22f, 57f), 0.45f, StrokeCap.Round)
    }
}

/** A pack of wound tape: concentric layers with a faint sheen that turns with the reel. */
private fun DrawScope.drawPack(center: Offset, radius: Float, angle: Float) {
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xFF241510), 0.5f to DeckColors.Tape, 0.9f to Color(0xFF5A3A26), 1f to Color(0xFF2E1C12),
            center = center, radius = radius,
        ),
        radius, center,
    )
    var r = ReelMotion.R_EMPTY + 1f
    while (r < radius - 0.4f) {
        drawCircle(Color.Black.copy(alpha = 0.16f), r, center, style = Stroke(0.12f))
        r += 1.35f
    }
    // Uneven winding shows as soft bright sectors that go round with the pack.
    rotate(angle, center) {
        for (k in 0 until 3) {
            drawArc(
                Color(0xFFB08360).copy(alpha = 0.10f + 0.05f * k),
                startAngle = k * 120f, sweepAngle = 26f, useCenter = true,
                topLeft = center - Offset(radius, radius), size = Size(radius * 2, radius * 2),
            )
        }
    }
    drawCircle(Color.Black.copy(alpha = 0.5f), radius, center, style = Stroke(0.25f))
}

/**
 * The white plastic hub and the deck's drive spindle inside it. When the reel turns faster than
 * the eye can follow, the spokes are drawn several times across the frame's sweep as motion blur.
 */
private fun DrawScope.drawHub(center: Offset, angle: Float, delta: Float) {
    drawCircle(Color.Black.copy(alpha = 0.35f), 6.6f, center + Offset(0.2f, 0.4f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFAFAF8), Color(0xFFD9D8D3)), center, 6.4f), 6.3f, center)
    drawCircle(Color(0xFF151515), 4.5f, center)
    val sweep = abs(delta)
    val copies = if (sweep > 5f) min(7, (sweep / 3.5f).roundToInt() + 1) else 1
    val alpha = if (copies == 1) 1f else (1.7f / copies).coerceAtMost(1f)
    for (i in 0 until copies) {
        val a = angle - delta * i / copies
        rotate(a, center) {
            // Six teeth on the inside of the hub.
            for (t in 0 until 6) {
                rotate(t * 60f, center) {
                    drawRect(Color(0xFFEDECE8).copy(alpha = alpha), Offset(center.x - 0.55f, center.y - 4.6f), Size(1.1f, 1.4f))
                }
            }
            // Drive spindle with three splines.
            for (t in 0 until 3) {
                rotate(t * 120f + 30f, center) {
                    drawRect(Color(0xFF8C8F94).copy(alpha = alpha), Offset(center.x - 0.5f, center.y - 3.3f), Size(1f, 3.3f))
                }
            }
        }
    }
    drawCircle(Brush.radialGradient(listOf(Color(0xFFCDD0D4), Color(0xFF6D7075)), center - Offset(0.5f, 0.5f), 2.6f), 2.1f, center)
    drawCircle(Color(0xFF2B2C2F), 0.5f, center)
}

private fun DrawScope.drawLabel(
    ui: DeckUi,
    palette: LabelPalette,
    measurer: TextMeasurer,
    window: RoundRect,
    u: Float,
    strip: RoundRect = RoundRect(10f, 13.4f, 90f, 19.4f, CornerRadius(0.9f)),
    stripesTop: Boolean = false,
    splitLines: Boolean = false,
    tagY: Float = 44.2f,
    typeY: Float = 44.6f,
    blankLabel: Boolean = false,
) {
    val labelRect = RoundRect(5.5f, 4f, 94.5f, 48.5f, CornerRadius(2f))
    val labelPath = Path().apply {
        fillType = PathFillType.EvenOdd
        addRoundRect(labelRect)
        addRoundRect(RoundRect(window.left - 1f, window.top - 1f, window.right + 1f, window.bottom + 1f, CornerRadius(4f)))
    }
    val art = ui.artwork
    clipPath(labelPath) {
        if (art != null) {
            drawCroppedImage(art, Rect(labelRect.left, labelRect.top, labelRect.right, labelRect.bottom))
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Transparent, Color.Black.copy(alpha = 0.3f)), 4f, 48.5f), Offset(5.5f, 4f), Size(89f, 44.5f))
        } else {
            drawRect(palette.paper, Offset(5.5f, 4f), Size(89f, 44.5f))
            if (stripesTop) {
                // The face's window swallows the mid-label band, so its stripes run across the top.
                drawRect(palette.stripe, Offset(5.5f, 5.2f), Size(89f, 2.2f))
                drawRect(palette.stripe2, Offset(5.5f, 7.8f), Size(89f, 1.4f))
                drawRect(palette.stripe.copy(alpha = 0.55f), Offset(5.5f, 9.6f), Size(89f, 0.7f))
            } else {
                drawRect(palette.stripe, Offset(5.5f, 27f), Size(89f, 3.6f))
                drawRect(palette.stripe2, Offset(5.5f, 31.4f), Size(89f, 2.2f))
                drawRect(palette.stripe.copy(alpha = 0.55f), Offset(5.5f, 34.4f), Size(89f, 1.0f))
            }
        }
    }
    drawPath(labelPath, Color.Black.copy(alpha = 0.18f), style = Stroke(0.2f))

    // The paper strip you write the songs on.
    drawPath(Path().apply { addRoundRect(strip) }, palette.paper.copy(alpha = if (art != null) 0.94f else 1f))
    val stripCy = (strip.top + strip.bottom) / 2f
    if (splitLines) {
        drawLine(DeckColors.PaperLine.copy(alpha = 0.7f), Offset(17.5f, strip.top + (strip.bottom - strip.top) * 0.55f), Offset(89f, strip.top + (strip.bottom - strip.top) * 0.55f), 0.12f)
        drawLine(DeckColors.PaperLine.copy(alpha = 0.7f), Offset(17.5f, strip.bottom - 0.6f), Offset(89f, strip.bottom - 0.6f), 0.12f)
    } else {
        drawLine(DeckColors.PaperLine.copy(alpha = 0.7f), Offset(17.5f, 18.4f), Offset(89f, 18.4f), 0.12f)
    }
    drawCircle(palette.ink, 2.4f, Offset(13.6f, stripCy), style = Stroke(0.3f))
    val ink = palette.ink
    // Text is laid out in pixels and scaled down into millimetre space with the rest.
    fun text(s: String, x: Float, y: Float, sizeMm: Float, maxWidthMm: Float, style: TextStyle) {
        if (s.isBlank()) return
        val layout = measurer.measure(
            s, style.copy(fontSize = (sizeMm * u).toSp()),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = (maxWidthMm * u).toInt().coerceAtLeast(1)),
        )
        withTransform({ translate(x, y); scale(1f / u, 1f / u, Offset.Zero) }) { drawText(layout) }
    }
    // Handwritten lines shrink a step at a time before they are allowed to truncate: a real title
    // written small beats a full-size title with its tail cut off.
    fun handText(s: String, x: Float, y: Float, sizeMm: Float, maxWidthMm: Float, style: TextStyle) {
        if (s.isBlank()) return
        var sz = sizeMm
        while (true) {
            val layout = measurer.measure(
                s, style.copy(fontSize = (sz * u).toSp()),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = (maxWidthMm * u).toInt().coerceAtLeast(1)),
            )
            if (!layout.hasVisualOverflow || sz <= 3.2f) {
                withTransform({ translate(x, y); scale(1f / u, 1f / u, Offset.Zero) }) { drawText(layout) }
                return
            }
            sz *= 0.9f
        }
    }
    val hand = TextStyle(fontFamily = FontFamily.Cursive, color = ink, fontWeight = FontWeight.Bold)
    val print = TextStyle(fontFamily = FontFamily.SansSerif, color = ink, fontWeight = FontWeight.Bold, letterSpacing = (0.3f * u).toSp())
    text("A", 12.75f, stripCy - 2.0f, 3.6f, 5f, print)
    // A blank tape (a dual deck well whose source has not played yet) keeps only its printed
    // identity: no handwritten title, not even the placeholder, and no artist line.
    val title = if (blankLabel) "" else ui.title.ifBlank { placeholderTitle(ui) }
    val artist = if (blankLabel) "" else ui.artist
    if (splitLines) {
        // The face is big enough for the title to have a line of its own, the artist printed under it.
        handText(title, 17.5f, strip.top - 0.4f, 4.8f, 71f, hand)
        text(artist, 17.5f, strip.top + 3.8f, 2.4f, 71f, print)
    } else {
        val line = listOf(title, artist).filter { it.isNotBlank() }.joinToString("  —  ")
        text(line, 17.5f, 13.0f, 4.6f, 71f, hand)
    }

    val tag = TextStyle(fontFamily = FontFamily.SansSerif, color = if (art != null) Color.White else ink, fontWeight = FontWeight.Black)
    text(when (ui.source) { Source.LOCAL -> "FM 60"; Source.YT_MUSIC -> "YT ∞"; Source.APPLE_MUSIC -> "AM ∞" }, 79f, tagY, 2.6f, 14f, tag)
    text("TYPE I  NORMAL", 12f, typeY, 2.2f, 30f, tag.copy(fontWeight = FontWeight.Bold))
}

private fun placeholderTitle(ui: DeckUi) = when (ui.source) {
    Source.LOCAL -> "my mixtape"
    Source.YT_MUSIC -> "YT Music"
    Source.APPLE_MUSIC -> "Apple Music"
}

/** Centre-crops [image] to fill [dst], like a printed label. */
private fun DrawScope.drawCroppedImage(image: ImageBitmap, dst: Rect) {
    val dstRatio = dst.width / dst.height
    val srcRatio = image.width.toFloat() / image.height
    val (sw, sh) = if (srcRatio > dstRatio) (image.height * dstRatio) to image.height.toFloat() else image.width.toFloat() to (image.width / dstRatio)
    val sx = ((image.width - sw) / 2f).roundToInt()
    // Album covers keep their faces in the upper middle; bias the crop up a little.
    val sy = ((image.height - sh) * 0.4f).roundToInt()
    drawImage(
        image,
        srcOffset = IntOffset(sx, sy), srcSize = IntSize(sw.roundToInt(), sh.roundToInt()),
        dstOffset = IntOffset(dst.left.roundToInt(), dst.top.roundToInt()),
        dstSize = IntSize(dst.width.roundToInt().coerceAtLeast(1), dst.height.roundToInt().coerceAtLeast(1)),
    )
}

/** The trapezoid at the bottom with the head openings, capstan holes and the exposed tape. */
private fun DrawScope.drawBottomBridge(shell: Color, light: Boolean, darken: Float = 0f) {
    val bridge = Path().apply {
        moveTo(16.5f, 64f); lineTo(21f, 50.5f); lineTo(79f, 50.5f); lineTo(83.5f, 64f); close()
    }
    val fill = lerp(shell, if (light) Color.Black else Color.White, 0.08f)
    drawPath(bridge, if (darken > 0f) lerp(fill, Color.Black, darken) else fill)
    drawPath(bridge, Color.Black.copy(alpha = 0.35f), style = Stroke(0.3f))
    listOf(31.5f, 68.5f).forEach { x ->
        drawCircle(Color(0xFF0E0E0F), 1.9f, Offset(x, 58.5f))
        drawCircle(Color.White.copy(alpha = 0.2f), 1.9f, Offset(x, 58.5f), style = Stroke(0.2f))
    }
    listOf(24.5f, 45.5f, 72f).forEachIndexed { i, x ->
        val w = if (i == 1) 9f else 3.5f
        drawRect(Color(0xFF0E0E0F), Offset(x, 61.2f), Size(w, 2.8f))
    }
    // The tape itself, running across the openings.
    drawRect(DeckColors.Tape, Offset(18f, 62.6f), Size(64f, 0.9f))
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

// The full face: the whole cassette at its true size, for the portrait deck.
private const val SHELL_W = 100.4f
private const val SHELL_H = 63.8f
private val FACE_WINDOW = Rect(8f, 19.5f, 92.4f, 45.5f)

/**
 * The complete cassette, unmasked: shell, label, the enlarged window and the bottom bridge the
 * case window used to crop away. Packs and hubs are the same ReelMotion physics [TapeWindow]
 * shows; the face just lets you see all of the tape at once. Drawn entirely in code, no assets.
 */
@Composable
fun FullCassetteFace(ui: DeckUi, anim: DeckAnimations, modifier: Modifier = Modifier, blankLabel: Boolean = false) {
    val measurer = rememberTextMeasurer()
    val shell by androidx.compose.animation.animateColorAsState(DeckColors.shell(ui.source), tween(600), label = "shellFace")
    // Powder-coat speckle, generated once: a single path per frame instead of thousands of rects.
    val speckle = remember {
        val rnd = Random(9)
        Path().apply {
            repeat(2600) {
                val x = rnd.nextFloat() * SHELL_W
                val y = rnd.nextFloat() * SHELL_H
                addRect(Rect(x, y, x + 0.35f, y + 0.35f))
            }
        }
    }
    // Moulded grain, generated once: a staggered lattice of tiny dimples in a single path,
    // drawn twice per frame (shadow pass, highlight pass) instead of thousands of circles.
    val grain = remember {
        val r = 0.16f
        val step = 1.25f
        Path().apply {
            var row = 0
            var y = step * 0.5f
            while (y < SHELL_H) {
                var x = step * 0.5f + if (row % 2 == 1) step * 0.5f else 0f
                while (x < SHELL_W) {
                    addOval(Rect(x - r, y - r, x + r, y + r))
                    x += step
                }
                y += step
                row++
            }
        }
    }
    Canvas(modifier.aspectRatio(SHELL_W / SHELL_H)) {
        val u = size.width / SHELL_W
        val slide = anim.insert.value
        val pop = anim.pop.value
        // In from below as the deck loads it, with the eject hop on top.
        val dy = slide * size.height * 1.2f - pop * u * 5f
        translate(0f, dy) {
            withTransform({ scale(u, u, Offset.Zero) }) {
                drawFullCassette(ui, anim.reels, shell, measurer, u, speckle, grain, blankLabel)
            }
        }
    }
}

private fun DrawScope.drawFullCassette(
    ui: DeckUi,
    reels: ReelMotion,
    shell: Color,
    measurer: TextMeasurer,
    u: Float,
    speckle: Path,
    grain: Path,
    blankLabel: Boolean = false,
) {
    val light = shell.luminance() > 0.5f
    val shellRR = RoundRect(0f, 0f, SHELL_W, SHELL_H, CornerRadius(3.4f))
    val shellPath = Path().apply { addRoundRect(shellRR) }

    // Drop shadow, shell base, a soft moulded sheen, then the speckle clipped to the shell.
    translate(0.8f, 1.6f) { drawPath(shellPath, Color.Black.copy(alpha = 0.45f)) }
    drawPath(
        shellPath,
        Brush.verticalGradient(listOf(lerp(shell, Color.White, 0.18f), shell, lerp(shell, Color.Black, 0.22f)), 0f, SHELL_H),
    )
    drawPath(
        shellPath,
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent), center = Offset(30f, 18f), radius = 60f),
    )
    clipPath(shellPath) {
        drawPath(speckle, Color.White.copy(alpha = 0.05f))
        // Moulded grain: the remembered dimple lattice drawn twice, a shadow pass and a highlight
        // pass offset a hair down and right, so each dimple reads as a tiny moulding indent.
        // Neutral inks at low alpha only. On the black shell the highlight pass carries the
        // stipple, the way pebbled injection moulding catches light on black hardware, so it
        // runs stronger than the shadow pass while staying subtler than the label print.
        drawPath(grain, Color.Black.copy(alpha = 0.05f))
        translate(0.14f, 0.18f) { drawPath(grain, Color.White.copy(alpha = 0.09f)) }
        // Gentle moulding shade: the shell falls away a little toward its bottom and side edges.
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.10f)), SHELL_H - 9f, SHELL_H),
            Offset(0f, SHELL_H - 9f), Size(SHELL_W, 9f),
        )
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Black.copy(alpha = 0.05f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.05f)),
                0f, SHELL_W,
            ),
        )
    }
    // The seam where the two moulded halves meet; the label and window cover most of it.
    drawLine(Color.Black.copy(alpha = 0.22f), Offset(0.5f, 31.9f), Offset(99.9f, 31.9f), 0.3f)
    drawLine(Color.White.copy(alpha = 0.10f), Offset(0.5f, 32.15f), Offset(99.9f, 32.15f), 0.2f)
    // Moulded grip ridges at the sides, each shadowed below its highlight like the grip rails.
    for (i in 0 until 7) {
        val y = 22f + i * 3f
        drawLine(Color.Black.copy(alpha = 0.25f), Offset(1.4f, y + 0.32f), Offset(3.2f, y + 0.32f), 0.5f)
        drawLine(lerp(shell, Color.White, 0.12f), Offset(1.4f, y), Offset(3.2f, y), 0.5f)
        drawLine(Color.Black.copy(alpha = 0.25f), Offset(97.2f, y + 0.32f), Offset(99f, y + 0.32f), 0.5f)
        drawLine(lerp(shell, Color.White, 0.12f), Offset(97.2f, y), Offset(99f, y), 0.5f)
    }
    drawGripRails(SHELL_W)

    // Window: recessed frame, packs and hubs, tape running to the guide rollers, then the glass.
    val windowRR = RoundRect(FACE_WINDOW, CornerRadius(3.2f))
    val windowPath = Path().apply { addRoundRect(windowRR) }
    drawPath(windowPath, Color.Black.copy(alpha = 0.55f), style = Stroke(0.9f))
    clipPath(windowPath) {
        drawRect(Color(0xFF1A1714), FACE_WINDOW.topLeft, FACE_WINDOW.size)
        drawWindowScale(measurer, u)
        drawPack(HUB_L, reels.leftRadius, reels.leftAngle)
        drawPack(HUB_R, reels.rightRadius, reels.rightAngle)
        drawLine(DeckColors.Tape, Offset(HUB_L.x - reels.leftRadius, HUB_L.y), Offset(11f, 44f), 0.6f)
        drawLine(DeckColors.Tape, Offset(HUB_R.x + reels.rightRadius, HUB_R.y), Offset(89.4f, 44f), 0.6f)
        drawRect(Color(0xFF6F7680).copy(alpha = 0.18f), FACE_WINDOW.topLeft, FACE_WINDOW.size)
    }
    drawHub(HUB_L, reels.leftAngle, reels.leftDelta)
    drawHub(HUB_R, reels.rightAngle, reels.rightDelta)
    drawGuideRoller(Offset(11f, 44f))
    drawGuideRoller(Offset(89.4f, 44f))
    clipPath(windowPath) {
        drawPath(
            Path().apply {
                moveTo(FACE_WINDOW.left + 26f, FACE_WINDOW.top); lineTo(FACE_WINDOW.left + 40f, FACE_WINDOW.top)
                lineTo(FACE_WINDOW.left + 30f, FACE_WINDOW.bottom); lineTo(FACE_WINDOW.left + 16f, FACE_WINDOW.bottom); close()
            },
            Color.White.copy(alpha = 0.07f),
        )
        drawRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), FACE_WINDOW.top, FACE_WINDOW.top + 8f),
            FACE_WINDOW.topLeft, FACE_WINDOW.size,
        )
        // Glass light: a broad, still diagonal sheen resting on the window glass the way
        // light lands on a real door in a lit room, soft at its edges and never moving.
        // It lives only on the window, over the packs and hubs behind the glass; the label
        // around it keeps its full print contrast.
        drawRect(
            Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.32f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.08f),
                    0.68f to Color.Transparent,
                    1f to Color.Transparent,
                ),
                start = Offset(FACE_WINDOW.left, FACE_WINDOW.top),
                end = Offset(FACE_WINDOW.right, FACE_WINDOW.bottom),
            ),
            FACE_WINDOW.topLeft, FACE_WINDOW.size,
        )
    }
    drawPath(windowPath, Color.Black.copy(alpha = 0.55f), style = Stroke(0.5f))

    drawLabel(
        ui, ui.label, measurer, windowRR, u,
        strip = RoundRect(10f, 11.8f, 90.4f, 18.2f, CornerRadius(0.9f)),
        stripesTop = true, splitLines = true, tagY = 46.4f, typeY = 46.8f,
        blankLabel = blankLabel,
    )
    // The maker's name printed on the label band under the window, the way real tapes carry it.
    drawMmText(
        measurer, "FERRIC", (SHELL_W - 30f) / 2f, 46.2f, 2.6f, 30f,
        TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black,
            color = if (light) DeckColors.Ink.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.55f),
            letterSpacing = (0.5f * u).toSp(),
        ),
        u,
    )
    // A small worn sticker on the lower shell, generic set dressing of our own.
    rotate(-6f, Offset(86f, 52.5f)) {
        drawRoundRect(Color(0xFFE8DFC8).copy(alpha = 0.85f), Offset(80f, 49.5f), Size(12f, 6f), CornerRadius(0.8f))
        drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(80f, 49.5f), Size(12f, 6f), CornerRadius(0.8f), style = Stroke(0.2f))
        drawMmText(
            measurer, "C-60", 82.2f, 51.2f, 2.4f, 9f,
            TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, color = DeckColors.Ink.copy(alpha = 0.7f)),
            u,
        )
    }

    drawBottomBridge(shell, light, darken = 0.12f)

    listOf(Offset(4.4f, 4.4f), Offset(96f, 4.4f), Offset(4.4f, 59.4f), Offset(96f, 59.4f), Offset(50.2f, 55.7f))
        .forEachIndexed { i, c -> drawScrew(c, 1.5f, 15f + i * 40f) }
    drawPath(shellPath, Color.Black.copy(alpha = 0.5f), style = Stroke(0.35f))
    drawPath(Path().apply { addRoundRect(RoundRect(0.4f, 0.4f, 100f, 63.4f, CornerRadius(3.1f))) }, Color.White.copy(alpha = 0.25f), style = Stroke(0.25f))
    drawPath(Path().apply { addRoundRect(RoundRect(1.2f, 1.2f, 99.2f, 62.6f, CornerRadius(2.7f))) }, Color.Black.copy(alpha = 0.18f), style = Stroke(0.3f))
}

/**
 * The index scale real cassettes print on the backing behind the window: a rule at hub height
 * spanning the window, ticks every 5 units (longer at the quarters) and numerals at 0, 25, 50,
 * 75, 100 in a plain period grotesque. It is drawn before the packs, so the tape covers it as it
 * winds and the scale is read against the edge of the pack, exactly as on the real thing; it
 * never paints over a pack or a hub, and the quarter numerals pass behind the reels.
 */
private fun DrawScope.drawWindowScale(measurer: TextMeasurer, u: Float) {
    val ink = Color(0xFFEDE6D6)
    val x0 = 10.6f
    val x1 = 89.8f
    val cy = HUB_L.y
    drawLine(ink.copy(alpha = 0.45f), Offset(x0, cy), Offset(x1, cy), 0.16f)
    val step = (x1 - x0) / 20f
    for (i in 0..20) {
        val x = x0 + i * step
        val major = i % 5 == 0
        val half = if (major) 1.4f else 0.7f
        drawLine(
            ink.copy(alpha = if (major) 0.55f else 0.45f),
            Offset(x, cy - half), Offset(x, cy + half),
            if (major) 0.18f else 0.13f,
        )
    }
    val style = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        color = ink.copy(alpha = 0.6f),
    )
    listOf(0, 25, 50, 75, 100).forEach { n ->
        drawCenteredMmText(measurer, n.toString(), x0 + (x1 - x0) * n / 100f, cy + 2.3f, 1.9f, style, u)
    }
}

/** A small metal guide roller at a bottom corner of the window, with the tape wrapping it. */
private fun DrawScope.drawGuideRoller(c: Offset) {
    drawCircle(Color.Black.copy(alpha = 0.4f), 1.8f, c + Offset(0.15f, 0.3f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFE8EAED), Color(0xFF7E8187)), c - Offset(0.4f, 0.4f), 2.6f), 1.6f, c)
    drawCircle(Color(0xFF2B2C2F), 0.45f, c)
}

/** Text laid out in pixels and scaled into millimetre space, as drawLabel's helper does. */
private fun DrawScope.drawMmText(
    measurer: TextMeasurer,
    s: String,
    x: Float,
    y: Float,
    sizeMm: Float,
    maxWidthMm: Float,
    style: TextStyle,
    u: Float,
) {
    if (s.isBlank()) return
    val layout = measurer.measure(
        s, style.copy(fontSize = (sizeMm * u).toSp()),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        constraints = Constraints(maxWidth = (maxWidthMm * u).toInt().coerceAtLeast(1)),
    )
    withTransform({ translate(x, y); scale(1f / u, 1f / u, Offset.Zero) }) { drawText(layout) }
}

/** [drawMmText] with the text centred on [cx] instead of starting at it. */
private fun DrawScope.drawCenteredMmText(
    measurer: TextMeasurer,
    s: String,
    cx: Float,
    y: Float,
    sizeMm: Float,
    style: TextStyle,
    u: Float,
) {
    if (s.isBlank()) return
    val layout = measurer.measure(
        s, style.copy(fontSize = (sizeMm * u).toSp()),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        constraints = Constraints(maxWidth = (30f * u).toInt().coerceAtLeast(1)),
    )
    val widthMm = layout.size.width / u
    withTransform({ translate(cx - widthMm / 2f, y); scale(1f / u, 1f / u, Offset.Zero) }) { drawText(layout) }
}
