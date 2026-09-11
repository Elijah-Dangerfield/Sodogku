package com.sodogku.libraries.ui.components.dog

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.system.LocalReduceAnimations
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
 * rather than a stylistic one. [Weight.Board] poses are drawn at 192px because a
 * cell on a 10x10 grid is about 108 physical pixels, and up to a hundred of them
 * are on screen at once. [Weight.Hero] poses are drawn at 512px and there is
 * only ever one of them on screen.
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

/**
 * Whether a dog moves, and how.
 *
 * Three values because there are three things a caller has ever wanted, not
 * because there are three sprite sheets. Weight is not on this axis: the two
 * halves of [Alive] that used to be separate entry points (`LoopingDog` and
 * `LoopingDogSmall`) differed only in which sheets they drew and whether they
 * floated, and only the hero half ever had a caller.
 */
enum class DogMotion {
    /**
     * Never moves. Draws the pose the caller named.
     *
     * The overwhelming majority: every sheet, dialog, empty state, hero and
     * booster control in the app is this. A dog that was never going to move
     * has no decision to make, which is why it is the default.
     */
    None,

    /**
     * A dog that has been placed and is settling: one calm clip, on repeat.
     *
     * Board weight, staggered by `seed` so a board of them is not in lockstep.
     * The placement already had its moment, so the loop that follows should not
     * keep asking for attention — which is why this is one clip rather than the
     * changing repertoire [Alive] has.
     */
    Settled,

    /**
     * A dog that is the subject of the screen: clips, pauses between them, and
     * a slow float.
     *
     * Hero weight, so 320px frames rather than the board's 128px. One clip on
     * repeat stops being seen after about three passes, which is fine for a dog
     * in a grid cell and wrong for a dog somebody is looking at.
     */
    Alive,
}

/** Default render size for a hero pose, sized to the art we ship. */
val DogHeroSize: Dp = 160.dp

/**
 * Every dog in the app.
 *
 * One component, because the decision about whether a dog is allowed to move is
 * not one a call site can be trusted to remember. There used to be three entry
 * points and two of them checked [LocalInspectionMode]; the third looped
 * forever, so any preview containing a board spun until the tool gave up. Two
 * out of three is the shape that keeps producing that bug, because every new
 * caller has to know.
 *
 * So: [motion] says what the dog is *for*, and this decides whether it gets to.
 * See [dogHoldsStill] for the rule and [seed] for how two dogs on one screen
 * come apart.
 *
 * Never reach for a dog drawable directly. `DogsAreDrawnByTheDesignSystemTest`
 * in `:apps:integration` fails the build if anything outside this package does,
 * which is what keeps the decision here rather than back in thirty call sites.
 */
@Composable
fun Dog(
    modifier: Modifier = Modifier,
    pose: DogPose = DogPose.Still,
    size: Dp = DogHeroSize,
    motion: DogMotion = DogMotion.None,
    /**
     * Decorrelates this dog from the one beside it. Any stable per-dog number:
     * a cell index, a route, a badge id.
     *
     * Stable, not random. Two dogs with different seeds behave differently and
     * the same seed behaves the same way twice, which is the only reason any of
     * this is reproducible in a screenshot.
     */
    seed: Int = 0,
) {
    val moving = motion != DogMotion.None && !dogHoldsStill()
    when (motion) {
        DogMotion.None -> PosedDog(pose = pose, size = size, modifier = modifier)
        DogMotion.Settled -> SettledDog(size = size, seed = seed, moving = moving, modifier = modifier)
        DogMotion.Alive -> AliveDog(size = size, seed = seed, moving = moving, modifier = modifier)
    }
}

/**
 * Whether a dog that was asked to move has to hold still anyway.
 *
 * Two questions, and they are not the same kind of claim, which is why the
 * inspection check is its own return rather than the left half of an `||`.
 *
 * [LocalReduceAnimations] is a preference. A player asked for less movement and
 * we owe them that, but it is a matter of taste and a caller could one day have
 * a good reason to argue with it — the settings row that demonstrates what the
 * toggle does, say.
 *
 * [LocalInspectionMode] is not a preference. It is true in Android Studio's
 * preview renderer and in a composition test, and in both of those an endless
 * loop means the tool never reaches idle: the preview spins, the test hangs.
 * There is no dog worth showing that is worth a build that never finishes. So
 * if the two ever come apart, inspection wins, and it is written here as a
 * short-circuit so that ordering is in the code and not only in this comment.
 */
@Composable
private fun dogHoldsStill(): Boolean {
    if (LocalInspectionMode.current) return true
    return LocalReduceAnimations.current
}

@Composable
private fun PosedDog(pose: DogPose, size: Dp, modifier: Modifier) {
    Image(
        painter = painterResource(pose.resource),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}

@Preview
@Composable
private fun DogPosePreview() {
    PreviewContent {
        Dog(pose = DogPose.Solved)
    }
}

@Preview
@Composable
private fun DogSettledPreview() {
    PreviewContent {
        Dog(size = DogHeroSize, motion = DogMotion.Settled)
    }
}

@Preview
@Composable
private fun DogAlivePreview() {
    PreviewContent {
        Dog(size = DogHeroSize, motion = DogMotion.Alive)
    }
}
