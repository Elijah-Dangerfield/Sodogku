package com.sodogku.libraries.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.game.drawBone
import com.sodogku.libraries.ui.system.LocalReduceAnimations
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A big thick bone that fills up, for the moments the app is not ready yet.
 *
 * **Indeterminate, and drawn as though it were not.** There is no honest
 * percentage available at launch: the app is waiting on a local cache read and a
 * config resolve that falls back to bundled defaults, and neither reports
 * progress. So the fill sweeps end to end and starts again. That is the usual
 * shape of a lie in a progress bar, and it is fine here for the same reason a
 * spinner is fine: it claims motion, not completion. What it must not do is
 * creep to 90% and stop, which is what a fake determinate bar does.
 *
 * A bone rather than a bar because the app already draws bones for lives, for
 * the refill button and for the level reward chip, and a generic track here
 * would be the one loading affordance in the app that came from somewhere else.
 *
 * The fill is a clip rather than a second shape. `drawBone` paints an outline
 * and insets a fill over it, so a "half a bone" cannot be expressed by drawing a
 * narrower bone: that would be a *smaller* bone, not a partly filled one. Same
 * geometry twice, once empty and once full, with the full one clipped to the
 * sweep.
 */
@Composable
fun BoneLoader(
    modifier: Modifier = Modifier,
    width: Dp = DefaultWidth,
    height: Dp = DefaultHeight,
    label: String? = null,
) {
    val still = LocalReduceAnimations.current || LocalInspectionMode.current

    val empty = AppTheme.colors.surfaceDisabled.color
    val emptyEdge = AppTheme.colors.borderSecondary.color
    val full = AppTheme.colors.bone.color
    val fullEdge = BoneEdge

    val sweep = remember { Animatable(0f) }
    LaunchedEffect(still) {
        if (still) {
            // A single filled bone, held. Reduce-animations should get the
            // shape rather than an empty outline that never fills, which reads
            // as broken rather than as still.
            sweep.snapTo(1f)
            return@LaunchedEffect
        }
        while (true) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(SweepMillis, easing = LinearEasing))
        }
    }

    Box(
        modifier = modifier
            .size(width = width, height = height)
            .semantics { if (label != null) contentDescription = label }
            // Read in the draw phase, never in composition: the sweep changes
            // every frame and this is on screen for the whole of launch.
            .drawBehind {
                drawBone(fill = empty, edge = emptyEdge)
                clipRect(left = 0f, top = 0f, right = size.width * sweep.value, bottom = size.height) {
                    drawBone(fill = full, edge = fullEdge)
                }
            },
    )
}

/**
 * Wide and chunky. This is the only thing on the screen while it is up, so it is
 * sized to be looked at rather than to sit politely under something.
 *
 * The ratio matters more than either number: `drawBone` is drawn against a box
 * meaningfully wider than it is tall, and at anything near square the two lobes
 * merge into a lump.
 */
private val DefaultWidth: Dp = Dimension.D1900 * 2
private val DefaultHeight: Dp = Dimension.D1300

/** One pass end to end. Slow enough to read as filling, quick enough to repeat. */
private const val SweepMillis = 1_400

/**
 * The rim around the fill. The fill itself is the `bone` token; the rim has no
 * token because the handoff's loading bone has no rim at all, and this goes
 * with the outline when that lands rather than earning a name first.
 */
private val BoneEdge = Color(0xFFC8871B)

@Preview
@Composable
private fun BoneLoaderPreview() {
    PreviewContent {
        BoneLoader()
    }
}
