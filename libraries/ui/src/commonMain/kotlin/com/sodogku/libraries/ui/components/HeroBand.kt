package com.sodogku.libraries.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.game.Paw
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The coloured band at the top of a full-screen moment, with the hero hanging
 * off its bottom edge.
 *
 * Full bleed to the top of the screen, square there and rounded at the bottom,
 * with three faint paw marks washed across it. The [kicker] sits inside the
 * band; the [hero] is placed so its bottom lands [overhang] below the band's
 * edge, over the page. That overhang is the whole reason this is a layout
 * rather than a `Column` with a background: the band has to be clipped to its
 * rounded shape for the watermark, and a clip that took the dog's feet with it
 * would be the first thing anyone noticed.
 *
 * The band bleeds under the status bar on purpose. Add the inset to
 * [kickerTopPadding]; do not pad the band itself, or the top of the screen
 * shows a strip of page above the colour.
 *
 * The watermark is the paw from `paw.svg`, drawn at the three placements the
 * handoff's board fixes on a 390-wide frame and scaled with the band's width,
 * so a wider phone gets the same picture rather than the same pixels.
 */
@Composable
fun HeroBand(
    color: ColorResource,
    modifier: Modifier = Modifier,
    watermark: ColorResource = AppTheme.colors.onAccentPrimary,
    watermarkAlpha: Float = DefaultWatermarkAlpha,
    overhang: Dp = HeroOverhang,
    kickerTopPadding: Dp = KickerTop,
    kicker: @Composable () -> Unit,
    hero: @Composable () -> Unit,
) {
    val wash = watermark.color.copy(alpha = watermarkAlpha)
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Box(
                Modifier
                    .layoutId(BandSlot)
                    .clip(BandShape)
                    .background(color.color)
                    .drawBehind { drawWatermark(wash) },
            )
            Box(Modifier.layoutId(KickerSlot), contentAlignment = Alignment.Center) { kicker() }
            Box(Modifier.layoutId(HeroSlot), contentAlignment = Alignment.Center) { hero() }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val kickerPlaceable = measurables.first { it.layoutId == KickerSlot }.measure(loose)
        val heroPlaceable = measurables.first { it.layoutId == HeroSlot }.measure(loose)

        val heroTop = kickerTopPadding.roundToPx() + kickerPlaceable.height + KickerToHero.roundToPx()
        val height = heroTop + heroPlaceable.height
        val bandHeight = (height - overhang.roundToPx()).coerceAtLeast(0)
        val width = constraints.maxWidth

        val bandPlaceable = measurables.first { it.layoutId == BandSlot }
            .measure(constraints.copy(minWidth = width, maxWidth = width, minHeight = bandHeight, maxHeight = bandHeight))

        layout(width, height) {
            bandPlaceable.place(0, 0)
            kickerPlaceable.place((width - kickerPlaceable.width) / 2, kickerTopPadding.roundToPx())
            heroPlaceable.place((width - heroPlaceable.width) / 2, heroTop)
        }
    }
}

/**
 * Three paws at the handoff's placements: `translate rotate scale` on a
 * 390-wide box, applied in that order, which is what the SVG's transform list
 * means and what [withTransform] does with the same three calls.
 */
private fun DrawScope.drawWatermark(color: Color) {
    val frame = size.width / WatermarkFrameWidth
    withTransform({ scale(frame, frame, pivot = Offset.Zero) }) {
        WatermarkPaws.forEach { paw ->
            withTransform({
                translate(paw.x, paw.y)
                rotate(paw.degrees, pivot = Offset.Zero)
                scale(paw.scale, paw.scale, pivot = Offset.Zero)
            }) {
                drawPawArt(color)
            }
        }
    }
}

/** The paw in its own 120-unit box, as the SVG draws it. */
private fun DrawScope.drawPawArt(color: Color) {
    drawOval(
        color = color,
        topLeft = Offset(Paw.EXTENT / 2f - Paw.PAD_RADIUS_X, Paw.PAD_CENTRE_Y - Paw.PAD_RADIUS_Y),
        size = Size(Paw.PAD_RADIUS_X * 2f, Paw.PAD_RADIUS_Y * 2f),
    )
    Paw.TOES.forEach { (x, y) ->
        drawCircle(color = color, radius = Paw.TOE_RADIUS, center = Offset(x, y))
    }
}

private class WatermarkPaw(val x: Float, val y: Float, val degrees: Float, val scale: Float)

@Suppress("MagicNumber")
private val WatermarkPaws = listOf(
    WatermarkPaw(x = 26f, y = 40f, degrees = -20f, scale = 0.34f),
    WatermarkPaw(x = 312f, y = 26f, degrees = 18f, scale = 0.3f),
    WatermarkPaw(x = 344f, y = 140f, degrees = -8f, scale = 0.24f),
)

/** The frame the placements above were measured on. */
private const val WatermarkFrameWidth = 390f

private const val DefaultWatermarkAlpha = 0.14f

/** How far the hero's bottom sits below the band's edge. */
private val HeroOverhang = 56.dp

/**
 * Not on the dimension scale: 34 reads as a card and 40 as a sheet, and the
 * band is neither. It is the one corner in the app at this size.
 */
private val BandCornerRadius = 36.dp
private val BandShape = RoundedCornerShape(bottomStart = BandCornerRadius, bottomEnd = BandCornerRadius)

private val KickerTop = Dimension.D850
private val KickerToHero = Dimension.D750

private const val BandSlot = "band"
private const val KickerSlot = "kicker"
private const val HeroSlot = "hero"

@Preview
@Composable
private fun HeroBandPreview() {
    PreviewContent {
        HeroBand(color = AppTheme.colors.accentPrimary, kicker = {
            Text(
                text = "Streak update",
                typography = AppTheme.typography.Label.Kicker,
                color = AppTheme.colors.onBand,
                allCaps = true,
                textAlign = TextAlign.Center,
            )
        }) {
            Dog(pose = DogPose.Solved, size = PreviewDogSize)
        }
    }
}

@Preview
@Composable
private fun HeroBandLossPreview() {
    PreviewContent {
        HeroBand(
            color = AppTheme.colors.bandLoss,
            watermarkAlpha = LossWatermarkAlpha,
            kicker = {
                Text(
                    text = "Streak over",
                    typography = AppTheme.typography.Label.Kicker,
                    color = AppTheme.colors.onBand,
                    allCaps = true,
                )
            },
        ) {
            Dog(pose = DogPose.Thinking, size = PreviewDogSize)
        }
    }
}

private val PreviewDogSize = Dimension.D1900 + Dimension.D900
private const val LossWatermarkAlpha = 0.1f
