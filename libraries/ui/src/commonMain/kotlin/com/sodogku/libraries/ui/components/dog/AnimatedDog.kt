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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import com.sodogku.libraries.ui.system.LocalReduceAnimations
import org.jetbrains.compose.resources.DrawableResource
import sodogku.libraries.resources.generated.resources.dog_idle_hero_sheet
import sodogku.libraries.resources.generated.resources.dog_look_hero_sheet
import sodogku.libraries.resources.generated.resources.dog_tilt_hero_sheet
import kotlin.math.sin
import sodogku.libraries.resources.generated.resources.dog_flop_sheet
import sodogku.libraries.resources.generated.resources.dog_idle_sheet
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
 * The idle loops a placed dog can be given, weighted.
 *
 * `idle` and `look` are the two the game is built around: a dog that settles and
 * a dog that glances about. Both read as an animal sitting there, which is what
 * a *placed* dog is — the placement already had its moment, and the loop that
 * follows should not keep asking for attention.
 *
 * `pant` appears roughly one cell in six, so a full board has a couple of dogs
 * doing something slightly different and no board is a row of clones.
 *
 * `tilt` and `flop` are deliberately absent. They are the two that read as a
 * shake, and a head snapping about in the corner of the eye pulls focus off the
 * puzzle. The sheets stay on disk because they are the right loops for a
 * celebration or an empty state, where motion is the point.
 *
 * All of them are head-only and legible at cell size; a body animation would be
 * mush at 34dp. They are separate sheets rather than one atlas because a board
 * shows a handful of dogs and Compose caches each `imageResource`, so only the
 * loops actually in play are ever decoded.
 */
private val DogLoops
    @Composable get() = listOf(
        Res.drawable.dog_idle_sheet,
        Res.drawable.dog_look_sheet,
        Res.drawable.dog_idle_sheet,
        Res.drawable.dog_look_sheet,
        Res.drawable.dog_idle_sheet,
        Res.drawable.dog_pant_sheet,
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

/**
 * A dog that keeps itself company: plays a loop, holds still a moment, plays a
 * different one.
 *
 * [AnimatedDog] is one clip forever. That is right on the board, where a placed
 * dog should settle and stop asking for attention, and wrong anywhere the dog is
 * the subject of the screen, because a single loop on repeat stops being seen.
 *
 * Hero weight: 320px frames against the board's 128px, because this draws at
 * around 240dp and the board draws at 34dp. Same source clips, packed larger.
 *
 * The head also floats. It is a slow vertical drift of a couple of device
 * pixels, well under the movement inside the frames, so it reads as buoyancy
 * rather than as a second animation arguing with the first. Off with
 * [LocalReduceAnimations], along with everything else.
 *
 * [seed] decides the order of clips and the length of the pauses. Two dogs with
 * different seeds behave differently; the same seed behaves the same way twice,
 * which is what makes any of this reproducible.
 */
@Composable
fun LoopingDog(
    size: Dp,
    modifier: Modifier = Modifier,
    seed: Int = 0,
    playing: Boolean = true,
) {
    LoopingDogImpl(size, modifier, seed, playing, HeroLoops, float = true)
}

/**
 * [LoopingDog] at board weight, for a dog that is small but still the thing
 * being looked at — an empty state, a reward, a card.
 *
 * No float: at cell size a two-pixel drift is either invisible or a wobble, and
 * neither is worth the frame.
 */
@Composable
fun LoopingDogSmall(
    size: Dp,
    modifier: Modifier = Modifier,
    seed: Int = 0,
    playing: Boolean = true,
) {
    LoopingDogImpl(size, modifier, seed, playing, BoardLoops, float = false)
}

@Composable
private fun LoopingDogImpl(
    size: Dp,
    modifier: Modifier,
    seed: Int,
    playing: Boolean,
    loops: List<DrawableResource>,
    float: Boolean,
) {
    val still = !playing || LocalReduceAnimations.current || LocalInspectionMode.current
    val schedule = remember(seed, loops.size) { DogLoopSchedule(seed, loops.size) }

    val turn = remember { mutableIntStateOf(0) }
    val frame = remember { mutableIntStateOf(0) }
    val sheet = imageResource(loops[schedule.clipAt(turn.intValue).mod(loops.size)])

    LaunchedEffect(still, schedule) {
        if (still) {
            frame.intValue = 0
            return@LaunchedEffect
        }
        while (true) {
            // The hold sits on frame 0 of the clip about to play, so the pause
            // is the dog waiting to do something rather than freezing halfway
            // through having done it.
            repeat(schedule.holdTurnsAt(turn.intValue)) { delay(HoldUnitMillis) }
            for (next in 0 until FrameCount) {
                frame.intValue = next
                delay(FrameDurationMillis)
            }
            frame.intValue = 0
            turn.intValue += 1
        }
    }

    val bob = remember { Animatable(0f) }
    LaunchedEffect(still, float) {
        if (still || !float) {
            bob.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            bob.animateTo(1f, tween(FloatMillis, easing = LinearEasing))
            bob.snapTo(0f)
        }
    }

    Canvas(
        modifier = modifier
            .size(size)
            // Read in the draw phase. Reading `bob.value` in composition would
            // recompose this subtree every frame for a two-pixel drift.
            .graphicsLayer {
                translationY = sin(bob.value * TwoPi) * FloatPixels
            },
    ) {
        drawSheetFrame(sheet, frame.intValue)
    }
}

/**
 * The hero loops, and why these three.
 *
 * `idle` and `look` are the calm pair the game is built on. `tilt` is the one
 * that reads as a shake, which is exactly wrong on a board full of dogs and
 * exactly right on a screen with one: it is the gesture that makes a viewer feel
 * looked at.
 *
 * Three and not seven. Every clip a dog switches to is a sheet decoded and held,
 * about 12MB each at hero weight, and a fourth buys less variety than the holds
 * already provide.
 */
private val HeroLoops
    @Composable get() = listOf(
        Res.drawable.dog_idle_hero_sheet,
        Res.drawable.dog_look_hero_sheet,
        Res.drawable.dog_tilt_hero_sheet,
    )

/** The same idea at board weight, reusing the sheets the board already decodes. */
private val BoardLoops
    @Composable get() = listOf(
        Res.drawable.dog_idle_sheet,
        Res.drawable.dog_look_sheet,
        Res.drawable.dog_tilt_sheet,
    )

/** One unit of pause, a little longer than a frame so a hold reads as a hold. */
private const val HoldUnitMillis = 220L

/** A full drift up and back. Slow enough not to be a bounce. */
private const val FloatMillis = 3_200

private const val FloatPixels = 4f
private const val TwoPi = 6.2831855f
