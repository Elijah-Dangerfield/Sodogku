package com.sodogku.libraries.ui.components.dog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.sodogku.libraries.ui.PreviewContent
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.dog_flop_sheet
import sodogku.libraries.resources.generated.resources.dog_look_sheet
import sodogku.libraries.resources.generated.resources.dog_pant_sheet
import sodogku.libraries.resources.generated.resources.dog_tilt_sheet

/**
 * The dog, alive: looking about, blinking, settling.
 *
 * Driven from a sprite sheet rather than the source animated WebP, because
 * animated WebP does not play on Compose Multiplatform iOS at all — Coil's
 * animated path goes through Android's `ImageDecoder`, and Skia hands back a
 * single frame. A sheet renders identically on both platforms and costs *one*
 * bitmap no matter how many dogs are on the board.
 *
 * That last point is what makes this affordable. The original clip is 512px and
 * 60 frames, about 60MB decoded; ten of those would be an out-of-memory crash,
 * not an animation. Packed at board resolution it is one 240KB image shared by
 * every cell.
 *
 * [frameOffset] staggers where a dog starts in the loop, and [variant] picks
 * which loop. Without either, every dog on the board does the same thing at the
 * same instant, which reads as a rendering glitch rather than as a row of
 * animals.
 *
 * A dog keeps the loop it was given for as long as it is on screen. Switching
 * mid-attempt would draw the eye away from the puzzle, which is the opposite of
 * what idle motion is for.
 */
@Composable
fun AnimatedDog(
    size: Dp,
    modifier: Modifier = Modifier,
    frameOffset: Int = 0,
    variant: Int = 0,
    playing: Boolean = true,
) {
    val sheet = imageResource(DogLoops[variant.mod(DogLoops.size)])
    val frame = remember { mutableIntStateOf(frameOffset % FrameCount) }

    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        while (true) {
            delay(FrameDurationMillis)
            frame.intValue = (frame.intValue + 1) % FrameCount
        }
    }

    Canvas(modifier = modifier.size(size)) {
        // Read inside the draw scope, not in composition: the frame changes ~12
        // times a second and recomposing a board of dogs at that rate would be
        // the most expensive thing on screen.
        drawSheetFrame(sheet, frame.intValue)
    }
}

private fun DrawScope.drawSheetFrame(
    sheet: androidx.compose.ui.graphics.ImageBitmap,
    frame: Int,
) {
    val cell = sheet.width / Columns
    val column = frame % Columns
    val row = frame / Columns
    drawImage(
        image = sheet,
        srcOffset = IntOffset(column * cell, row * cell),
        srcSize = IntSize(cell, cell),
        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        filterQuality = FilterQuality.High,
    )
}

/**
 * The idle loops a placed dog can be given. All four are head-only and legible
 * at cell size; body animations would be unreadable at 34dp.
 *
 * They are separate sheets rather than one big one because a board only ever
 * shows a handful of dogs, and Compose caches each `imageResource` — so the
 * loops that are actually in play are the only ones decoded.
 */
private val DogLoops
    @Composable get() = listOf(
        Res.drawable.dog_look_sheet,
        Res.drawable.dog_tilt_sheet,
        Res.drawable.dog_pant_sheet,
        Res.drawable.dog_flop_sheet,
    )

/** Must match `scripts/build_dog_sprites.py --frames`. */
private const val FrameCount = 30

/** `ceil(sqrt(FrameCount))`, the grid the packer uses. */
private const val Columns = 6

/** The source clip is 2.5s; 30 frames across it is a calm 12fps. */
private const val FrameDurationMillis = 83L

@Preview
@Composable
private fun AnimatedDogPreview() {
    PreviewContent {
        AnimatedDog(size = DogHeroSize)
    }
}
