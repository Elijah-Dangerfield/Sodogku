package com.sodogku.libraries.ui.components.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.dog.AnimatedDog
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.system.color.RegionPalette
import com.sodogku.libraries.ui.system.color.drawBoardMark
import com.sodogku.libraries.ui.system.color.drawRegionGlyph
import com.sodogku.system.Motion
import com.sodogku.system.Radii
import com.sodogku.system.clip
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.sin

/** What is currently in a cell. */
enum class BoardCellState {
    /** Nothing yet. */
    Empty,

    /** Ruled out, by auto-mark or by the player's own note. */
    Marked,

    /**
     * Ruled out the expensive way: the player guessed here and it cost a bone.
     * Kept visually distinct because it is different information — a square
     * someone *paid* for, not one they reasoned out.
     */
    Wrong,

    /** A dog. */
    Occupied,
}

/**
 * One square of the board.
 *
 * A 10x10 puts a hundred of these on screen, so it stays cheap: the region
 * glyph and the cross are drawn rather than composed, the fill is a
 * `drawBehind`, and the dog is one downscaled image.
 *
 * Every animation lives here rather than at the call site, so no screen can
 * forget to animate a cell and every cell in the app behaves identically:
 *
 * - **Entrance** — [entranceDelayMillis] staggers the drop-in. The grid feeds it
 *   a diagonal offset so the board lands as a wave instead of appearing at once.
 * - **Mark** — the cross draws stroke by stroke. A note being *made* reads
 *   differently from a fact that was always true.
 * - **Placement** — the dog overshoots and settles.
 * - **Strike** — [strikeNonce] shakes the cell and flashes a red cross. A nonce
 *   rather than a boolean, so the same cell can be got wrong twice running.
 */
@Composable
fun BoardCell(
    region: Int,
    state: BoardCellState,
    modifier: Modifier = Modifier,
    size: Dp = DefaultCellSize,
    colorblind: Boolean = false,
    strikeNonce: Int = 0,
    entranceDelayMillis: Int = 0,
    /** Staggers the placed dog's idle loop so a board of them is not in lockstep. */
    animationOffset: Int = 0,
    /** False swaps the living dog for a still, for the battery setting. */
    animated: Boolean = true,
    enabled: Boolean = true,
    onTap: () -> Unit = {},
) {
    val style = RegionPalette[region]

    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (entranceDelayMillis > 0) delay(entranceDelayMillis.toLong())
        entrance.animateTo(1f, Motion.Pop)
    }

    val pop = remember { Animatable(if (state == BoardCellState.Occupied) 1f else 0f) }
    val mark = remember {
        Animatable(if (state == BoardCellState.Empty || state == BoardCellState.Occupied) 0f else 1f)
    }
    LaunchedEffect(state) {
        when (state) {
            BoardCellState.Occupied -> {
                mark.snapTo(0f)
                pop.animateTo(Motion.PopOvershoot, Motion.Pop)
                pop.animateTo(1f, Motion.Tap)
            }
            BoardCellState.Marked, BoardCellState.Wrong -> {
                pop.snapTo(0f)
                mark.animateTo(1f, tween(Motion.MarkDrawMillis))
            }
            BoardCellState.Empty -> {
                pop.animateTo(0f, Motion.Tap)
                mark.animateTo(0f, Motion.fade())
            }
        }
    }

    val shake = remember { Animatable(0f) }
    val nonce by rememberUpdatedState(strikeNonce)
    LaunchedEffect(nonce) {
        if (nonce == 0) return@LaunchedEffect
        shake.snapTo(0f)
        shake.animateTo(1f, tween(Motion.ShakeMillis))
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val enter = entrance.value
                scaleX = enter
                scaleY = enter
                alpha = enter
                translationY = (1f - enter) * -EntranceDropPx
                translationX = sin(shake.value * ShakeCycles) * ShakeAmplitudePx * (1f - shake.value)
            }
            .clip(Radii.Cell)
            .drawBehind {
                drawRect(style.fill)
                if (colorblind) {
                    drawRegionGlyph(
                        style.glyph,
                        style.ink.copy(alpha = style.ink.alpha * GlyphAlpha),
                        GlyphFraction,
                    )
                }
                if (mark.value > 0f) {
                    val ink = if (state == BoardCellState.Wrong) StrikeInk else style.ink
                    drawBoardMark(ink, MarkFraction, mark.value)
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                // `onDoubleTap` is deliberately not registered here. Compose
                // withholds `onTap` until the double-tap timeout expires once it
                // is, which would put ~300ms of lag on the gesture players use
                // most. The second tap is recognised upstream instead, so the
                // cross appears instantly and converts if another tap follows.
                detectTapGestures(onTap = { onTap() })
            },
    ) {
        if (pop.value > 0f) {
            // A placed dog is alive: it looks around and blinks. Driven from a
            // shared sprite sheet, so ten of them on a board cost one bitmap.
            val dogModifier = Modifier.graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                alpha = pop.value.coerceIn(0f, 1f)
            }
            if (animated) {
                AnimatedDog(
                    size = size * DogFraction,
                    frameOffset = animationOffset,
                    modifier = dogModifier,
                )
            } else {
                Dog(pose = DogPose.Still, size = size * DogFraction, modifier = dogModifier)
            }
        }
    }
}

/** Fits a 10x10 board on the narrowest phone we support with room for padding. */
val DefaultCellSize: Dp = 34.dp

/** A square that cost a bone stays red, for the rest of the attempt. */
private val StrikeInk = Color(0xE6D32F2F)

/**
 * The region glyph is a watermark, not a badge. Loud enough to tell two fills
 * apart at a glance, quiet enough that a board of them does not compete with
 * the crosses and dogs the player is actually reading.
 */
private const val GlyphFraction = 0.42f
private const val GlyphAlpha = 0.45f
private const val MarkFraction = 0.46f
private const val DogFraction = 0.82f
private const val ShakeCycles = 18f
private const val ShakeAmplitudePx = 7f
private const val EntranceDropPx = 26f

@Preview
@Composable
private fun BoardCellStatesPreview() {
    PreviewContent {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(8.dp),
        ) {
            BoardCell(region = 0, state = BoardCellState.Empty, size = 48.dp)
            BoardCell(region = 3, state = BoardCellState.Marked, size = 48.dp)
            BoardCell(region = 6, state = BoardCellState.Occupied, size = 48.dp)
            BoardCell(region = 2, state = BoardCellState.Empty, size = 48.dp, colorblind = true)
            BoardCell(region = 8, state = BoardCellState.Marked, size = 48.dp, colorblind = true)
        }
    }
}
