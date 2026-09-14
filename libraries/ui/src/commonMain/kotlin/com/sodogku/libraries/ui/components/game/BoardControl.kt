package com.sodogku.libraries.ui.components.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.sodogku.libraries.ui.Elevation
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.bounceClick
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.elevation
import com.sodogku.libraries.ui.system.LocalReduceAnimations
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import com.sodogku.system.clip
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.cos
import kotlin.math.sin

/**
 * One control on the row under the board: a round white face, a picture, a word
 * beneath it, and a count.
 *
 * The row used to be coloured pills with the word inside them, each booster
 * keeping its own hue so a player learned "the blue one" before they read it.
 * The trouble with that is the row then competes with the board for colour, and
 * the board is where every colour already means something: a region. Moving the
 * controls to white circles on the cream leaves the pastels to the puzzle, and
 * puts the identifying colour in a *picture* instead of a fill.
 *
 * Which is the better carrier anyway. A bone is a bone at a glance and in any
 * language, and it is the same bone the life counter uses, so the two are
 * visibly the same currency.
 *
 * The label sits **outside** the circle rather than inside it. Inside, the word
 * has to shrink to fit a shape it does not fill, and the longest word decides
 * the size of every button.
 */
@Composable
fun BoardControl(
    label: String,
    modifier: Modifier = Modifier,
    /** Shown as a badge on the circle. Null draws no badge at all. */
    count: Int? = null,
    /**
     * Whether tapping this plays an ad, which the badge says out loud.
     *
     * **There is one badge corner**, and this wins it. A 70dp circle has room
     * for exactly one thing in its top-right and stacking two would put them
     * over the artwork; and the two never both have something to say. A booster
     * only reaches the ad when its count is zero, and a zero badge is the one
     * number a player can already read off the greyed-out button — so the
     * corner spends itself on what the tap *does* instead of on what is left.
     *
     * The caller decides. This is a picture of a decision made in the
     * ViewModel's language (Pro, config, phase, holdings), not a rule this
     * component is in any position to work out.
     */
    adBadge: Boolean = false,
    enabled: Boolean = true,
    /**
     * Whether to draw attention to this control.
     *
     * The whole button beats, twice, and the picture inside it shakes itself out
     * as it settles. The button scaling was previously refused on the grounds
     * that a control growing under a reaching thumb gets mis-tapped, and the
     * risk is real — so the beat is [AttentionScale], a few percent, which is
     * enough to see out of the corner of an eye and far too little to move a
     * touch target out from under a finger. The expanding ring it replaces read
     * as a notification badge rather than as a button asking to be pressed.
     */
    attention: Boolean = false,
    onClick: () -> Unit = {},
    art: @Composable () -> Unit,
) {
    val beating = beatsForAttention(
        attention = attention,
        reduceAnimations = LocalReduceAnimations.current,
        inspecting = LocalInspectionMode.current,
    )
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(beating) {
        if (!beating) {
            pulse.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            pulse.animateTo(1f, tween(AttentionMillis, easing = LinearEasing))
            pulse.snapTo(0f)
            delay(AttentionRestMillis)
        }
    }

    val face = if (enabled) AppTheme.colors.surfacePrimary.color else AppTheme.colors.surfaceDisabled.color

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimension.D200),
        modifier = modifier.semantics {
            contentDescription = if (count == null) label else "$label, $count left"
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(FaceSize)
                    // Read in the layer, never in composition: a beat that
                    // recomposed this subtree sixty times a second would be a
                    // steady cost for a decoration.
                    .graphicsLayer {
                        // Cosine rather than sine, so the beat starts and ends
                        // at rest instead of jumping to full size on the first
                        // frame and snapping back on the last.
                        val swell = (1f - cos(pulse.value * Tau * AttentionBeats)) / 2f
                        val scale = 1f + swell * AttentionScale
                        scaleX = scale
                        scaleY = scale
                    }
                    .elevation(Elevation.Button, Radii.Round.shape)
                    .clip(Radii.Round)
                    .background(face)
                    .bounceClick(onClick = onClick, enabled = enabled),
            ) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        // Damped by the remaining progress, so the picture
                        // shakes hardest as the beat lands and has settled by
                        // the time the button is at rest again.
                        val turns = pulse.value * Tau * ShakeCycles
                        rotationZ = sin(turns) * ShakeDegrees * (1f - pulse.value)
                    },
                ) {
                    art()
                }
            }

            when {
                adBadge -> RewardBadge(modifier = Modifier.align(Alignment.TopEnd))

                count != null -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(Radii.Round)
                        // Not `danger`. Red is the app's one colour for "this
                        // cost you something" — a wrong guess, a bone gone —
                        // and a count of what you are holding is the opposite
                        // of that. The primary accent also keeps this clear of
                        // the secondary one, which is what [RewardBadge] wears
                        // and means "an ad is involved" everywhere it appears.
                        .background(
                            if (count > 0) {
                                AppTheme.colors.accentPrimary.color
                            } else {
                                AppTheme.colors.textDisabled.color
                            },
                        )
                        .padding(horizontal = Dimension.D400, vertical = Dimension.D100),
                ) {
                    // Label rather than Caption, for the same reason the word
                    // under the circle is: the caption scale is footnote type,
                    // and this number is the only thing telling a player how
                    // many they have left.
                    //
                    // Medium rather than the scale's default SemiBold. A single
                    // digit set heavy on a small coloured disc closes up its
                    // counters and turns 8 into a blob; a step up in size and a
                    // step down in weight is more legible than the reverse.
                    Text(
                        text = count.toString(),
                        typography = AppTheme.typography.Label.L500.Medium,
                        color = AppTheme.colors.onAccentPrimary,
                    )
                }
            }
        }

        // Label rather than Caption. These three words name the only controls
        // under the board, so they are UI labels and not footnotes — the caption
        // scale is 8sp Normal, which on a cream page under a white disc read as
        // something the design had finished with.
        //
        // L500 Medium rather than L400 SemiBold: two points bigger and a weight
        // lighter. The heavier small size was legible but tight, and these words
        // sit under artwork rather than inside a control, so they want the
        // relaxed weight the clock under the board uses rather than the button
        // weight.
        Text(
            text = label,
            typography = AppTheme.typography.Label.L500.Medium,
            color = if (enabled) AppTheme.colors.textSecondary else AppTheme.colors.onSurfaceDisabled,
        )
    }
}

/**
 * Whether [BoardControl] should actually beat, given that it has been asked to.
 *
 * Pulled out of the composable because it is the one decision here that has a
 * wrong answer rather than an ugly one, and inside a `@Composable` it could not
 * be asserted: this repo has no Compose UI test harness, so a composition-local
 * read is a branch nothing can reach from a test.
 *
 * [reduceAnimations] is the setting, and it wins. A control that shakes itself
 * at somebody who has asked the app to hold still is not a nudge, it is the
 * thing they turned the setting on to stop — and this one is an attention-
 * seeking loop, which is the worst case of it.
 *
 * [inspecting] is the preview and screenshot path. The beat repeats forever, and
 * an animation that never settles is an idle state a screenshot test waits on
 * until it times out.
 */
internal fun beatsForAttention(
    attention: Boolean,
    reduceAnimations: Boolean,
    inspecting: Boolean,
): Boolean = attention && !reduceAnimations && !inspecting

/**
 * A bone, sized for the middle of a [BoardControl].
 *
 * [enabled] false drops [fill] and [edge] for a drained bone rather than dimming
 * the gold one with alpha. A bone at half opacity on a cream page reads as a
 * rendering fault.
 *
 * The colours invert instead: enabled is a gold bone on a white disc, disabled
 * is a white bone on the cream disc [BoardControl] switches to. The shape stays
 * exactly as legible — which is the point of greying a control out rather than
 * hiding it, since the player still has to know what the button *is* to
 * understand why it is off.
 */
@Composable
fun BoardControlBone(fill: Color, edge: Color, size: Dp = ArtSize, enabled: Boolean = true) {
    val bone = if (enabled) fill else AppTheme.colors.surfacePrimary.color
    val rim = if (enabled) edge else AppTheme.colors.onSurfaceDisabled.color
    Box(
        modifier = Modifier
            .size(width = size * BoneAspect, height = size)
            .drawBehind {
                // Tilted, because a bone lying flat reads as a minus sign at
                // this size.
                rotate(BoneTilt) { drawBone(fill = bone, edge = rim) }
            },
    )
}

/**
 * A paw, sized for the middle of a [BoardControl].
 *
 * Square where [BoardControlBone] is wide, so it is given its own size: at the
 * bone's height a paw is visibly the smaller of the two sitting side by side,
 * because the bone spends its bulk on width the paw does not have.
 */
@Composable
fun BoardControlPaw(color: Color, size: Dp = PawArtSize, enabled: Boolean = true) {
    val ink = if (enabled) color else AppTheme.colors.onSurfaceDisabled.color
    Box(
        modifier = Modifier
            .size(size)
            .drawBehind { drawPaw(color = ink, filled = true) },
    )
}

/**
 * The circle.
 *
 * Smaller than it started, twice over: 100dp, then 84dp, now 70dp, while the
 * picture inside it went the other way. A 100dp circle around a 28dp bone is
 * mostly white — the thing that identifies the control was the smallest part of
 * it, and the row took more vertical space than a row of three buttons needs.
 *
 * 70dp is the floor rather than a waypoint. It is still comfortably past the
 * 48dp minimum touch target, and it is fixed: the art inside it has since come
 * down a step, which is a different change from shrinking the button, and the
 * two are easy to confuse when the only thing you can see is the result.
 */
private val FaceSize = Dimension.D1500

/**
 * The bone's height. A step down from where it was.
 *
 * At 40dp the bone was 58dp across inside a 70dp circle — six dp of white on
 * each side — so the disc had stopped reading as a button with a picture on it
 * and started reading as a picture with a rim. 28dp leaves the art clearly
 * inside the shape that carries it, which is what makes the row look like three
 * buttons rather than three stickers.
 */
private val ArtSize = Dimension.D1000

/** The paw's side. Square art needs more than the bone's height to weigh the same. */
private val PawArtSize = Dimension.D1100

/** Matches the aspect `drawBone` is drawn against; a bone in a square is a blob. */
private const val BoneAspect = 1.45f

private const val BoneTilt = -20f

private const val AttentionMillis = 1_100

/**
 * The gap between one beat and the next.
 *
 * Down from 2.4s. The detector hands this component a burst of a fixed length
 * and the burst is the frequency budget, so the rest is what decides how much
 * movement actually happens inside one — at 2.4s a seven-second burst held two
 * beats and a stump of a third, and the owner's report was that he was not
 * seeing them. At 1.4s the same burst holds nearly three whole beats without
 * the button being allowed to ask for any more of the player's time than it
 * already was.
 *
 * It does not go to zero. The stillness between beats is what stops this
 * reading as a loading spinner: a thing that moves continuously is a thing the
 * eye stops resolving as a request.
 */
private const val AttentionRestMillis = 1_400L

private const val Tau = 6.2831855f

/**
 * How far the button swells at the top of a beat — eight percent.
 *
 * Still small on purpose, and this is the constant with a hard ceiling on it.
 * The row sits under the board exactly where the hand already is, and a control
 * that grows under a thumb already on its way to it is a control that gets
 * mis-tapped. Eight percent of a 70dp circle is under three dp of travel on each
 * edge: legible as movement in peripheral vision, and nowhere near enough to
 * walk out from under a finger.
 *
 * When this is not loud enough, raise [ShakeDegrees] instead. The art moves
 * inside the circle, so it can swing as far as it likes without the target
 * moving at all.
 */
private const val AttentionScale = 0.08f

/** Two beats per burst, so it reads as a pulse rather than as a single twitch. */
private const val AttentionBeats = 2f

/**
 * How many times the picture swings back and forth while the button beats.
 *
 * It used to be fed to `sin` as raw radians, which is the whole reason the shake
 * was hard to see: nine radians is one and a half swings, so the art leaned over
 * and came back once in a bit over a second. That is a slow tilt, not a shake,
 * and at seven degrees of it there was nothing to catch an eye. Multiplied by
 * [Tau] the name is true and the number means what it says.
 */
private const val ShakeCycles = 2.5f

/**
 * How far it swings at the start, before the damping takes it down.
 *
 * The art is inside the circle rather than being the control, so unlike
 * [AttentionScale] this one is not limited by the touch target — nothing the
 * player is reaching for moves. It is limited by taste instead.
 *
 * Eighteen, up from ten, and this is the lever [AttentionScale] names for
 * exactly this complaint: the owner asked to see the picture shake and not just
 * the button breathe. Ten degrees on a 28dp bone is about two and a half dp at
 * the tips, which is a lean; eighteen is a shake. The damping still takes it to
 * nothing by the end of the beat, so the louder swing is spent in the first
 * third of the movement and the control is at rest for the rest of it.
 */
private const val ShakeDegrees = 18f

@Preview
@Composable
private fun BoardControlPreview() {
    PreviewContent {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D600)) {
            BoardControl(label = "Hint", count = 3) {
                BoardControlPaw(color = Color(0xFFD98324))
            }
            BoardControl(label = "Hint", count = 0, adBadge = true) {
                BoardControlPaw(color = Color(0xFFD98324))
            }
            BoardControl(label = "Refill Bones", enabled = false) {
                BoardControlBone(fill = Color(0xFFF5C043), edge = Color(0xFFC8871B), enabled = false)
            }
        }
    }
}
