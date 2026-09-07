package com.sodogku.libraries.ui.components.board

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.bounceCombinedClick
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.system.color.RegionPalette
import com.sodogku.libraries.ui.system.color.drawBoardMark
import com.sodogku.libraries.ui.system.color.drawRegionGlyph
import com.sodogku.system.Motion
import com.sodogku.system.Radii
import com.sodogku.system.clip
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.sin

/** What is currently in a cell. */
enum class BoardCellState {
    /** Nothing yet. Tappable. */
    Empty,

    /** Ruled out, either by auto-mark or by the player. Still tappable, to clear it. */
    Marked,

    /** A dog. */
    Occupied,
}

/**
 * One square of the board.
 *
 * The whole grid is made of these and a 10x10 puts a hundred on screen, so it
 * stays cheap on purpose: the glyph is drawn rather than composed, the fill is a
 * `drawBehind` rather than a stack of boxes, and the dog is a single downscaled
 * image.
 *
 * The pop and the shake are driven here rather than by the caller, so every cell
 * in the app animates identically and a screen cannot forget to animate one.
 * [strikeNonce] is how a wrong tap is signalled: change it to any new value and
 * the cell shakes once. A boolean could not fire twice in a row on the same cell.
 */
@Composable
fun BoardCell(
    region: Int,
    state: BoardCellState,
    modifier: Modifier = Modifier,
    size: Dp = DefaultCellSize,
    colorblind: Boolean = false,
    strikeNonce: Int = 0,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    val style = RegionPalette[region]

    val pop = remember { Animatable(if (state == BoardCellState.Occupied) 1f else 0f) }
    LaunchedEffect(state) {
        val target = if (state == BoardCellState.Occupied) 1f else 0f
        if (state == BoardCellState.Occupied && pop.value == 0f) {
            pop.snapTo(0f)
            pop.animateTo(Motion.PopOvershoot, Motion.Pop)
            pop.animateTo(1f, Motion.Tap)
        } else {
            pop.animateTo(target, Motion.Tap)
        }
    }

    val shake = remember { Animatable(0f) }
    val currentNonce by rememberUpdatedState(strikeNonce)
    LaunchedEffect(currentNonce) {
        if (currentNonce == 0) return@LaunchedEffect
        launch {
            shake.snapTo(0f)
            shake.animateTo(1f, Motion.fade())
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                translationX = sin(shake.value * ShakeCycles) * ShakeAmplitudePx * (1f - shake.value)
            }
            .clip(Radii.Cell)
            .drawBehind {
                drawRect(style.fill)
                if (colorblind) drawRegionGlyph(style.glyph, style.ink, GlyphFraction)
            }
            .bounceCombinedClick(
                enabled = enabled,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
    ) {
        when (state) {
            BoardCellState.Empty -> Unit

            BoardCellState.Marked -> Box(
                modifier = Modifier
                    .size(size * MarkFraction)
                    .drawBehind { drawBoardMark(style.ink, fraction = 1f) },
            )

            BoardCellState.Occupied -> Dog(
                pose = DogPose.Still,
                size = size * DogFraction,
                modifier = Modifier.graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                    alpha = pop.value.coerceIn(0f, 1f)
                },
            )
        }
    }
}

/** Fits a 10x10 board on the narrowest phone we support with room for padding. */
val DefaultCellSize: Dp = 34.dp

private const val GlyphFraction = 0.55f
private const val MarkFraction = 0.42f
private const val DogFraction = 0.82f
private const val ShakeCycles = 18f
private const val ShakeAmplitudePx = 7f

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
