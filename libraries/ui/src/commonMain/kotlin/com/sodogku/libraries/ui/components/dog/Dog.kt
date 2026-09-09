package com.sodogku.libraries.ui.components.dog

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sodogku.libraries.ui.PreviewContent
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.dog_focused
import sodogku.libraries.resources.generated.resources.dog_hardmode
import sodogku.libraries.resources.generated.resources.dog_paused
import sodogku.libraries.resources.generated.resources.dog_solved
import sodogku.libraries.resources.generated.resources.dog_still
import sodogku.libraries.resources.generated.resources.dog_thinking

/**
 * The dog, in one of the moods we have art for.
 *
 * Poses split into two weights, and the split is a hard performance boundary
 * rather than a stylistic one. [Board] poses are drawn at 192px because a cell
 * on a 10x10 grid is about 108 physical pixels, and up to a hundred of them are
 * on screen at once. [Hero] poses are drawn at 512px and there is only ever one
 * of them on screen.
 *
 * Putting a hero asset on the board would mean ~28MB of decoded bitmaps for one
 * puzzle, so the enum is the guardrail: a caller picks a mood, not a file.
 */
enum class DogPose(internal val resource: DrawableResource, internal val weight: Weight) {
    /** Resting. The pose that lands on a correct placement. */
    Still(Res.drawable.dog_still, Weight.Board),

    /** Alert. For the cell the player is about to commit to, and for hints. */
    Focused(Res.drawable.dog_focused, Weight.Board),

    /** Tongue out, trophy up. The win sheet and achievement unlocks. */
    Solved(Res.drawable.dog_solved, Weight.Hero),

    /** Puzzled. Empty states and loading. */
    Thinking(Res.drawable.dog_thinking, Weight.Hero),

    /** Asleep. The pause sheet. */
    Paused(Res.drawable.dog_paused, Weight.Hero),

    /** Braced. The failure sheet and the hardest bands. */
    HardMode(Res.drawable.dog_hardmode, Weight.Hero);

    internal enum class Weight { Board, Hero }
}

/** Default render size for a hero pose, sized to the art we ship. */
val DogHeroSize: Dp = 160.dp

/**
 * Renders [pose] at [size].
 *
 * Prefer this over reaching for `Res.drawable.dog_*` directly: it keeps the
 * board-versus-hero choice in one place, so no screen accidentally paints a
 * 512px asset into a grid cell.
 */
@Composable
fun Dog(
    pose: DogPose,
    modifier: Modifier = Modifier,
    size: Dp = DogHeroSize,
) {
    Image(
        painter = painterResource(pose.resource),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}

@Preview
@Composable
private fun DogPosesPreview() {
    PreviewContent {
        Dog(pose = DogPose.Solved)
    }
}
