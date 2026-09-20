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
 * A big flat bone that fills up, for the moments the app is not ready yet.
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
 * No outline, unlike those bones. The 2026-09 handoff draws it as a grey track
 * with the amber fill clipped in from the left, and a rim on a shape that is
 * mostly track read as a hollow bone slowly gaining a lining. The fill is a clip
 * rather than a narrower bone for the same reason as before: a bone drawn
 * narrower is a *smaller* bone, not a partly filled one, so the same geometry is
 * drawn twice, once as the track and once as the fill, and the fill is clipped
 * to the sweep.
 */
@Composable
fun BoneLoader(
    modifier: Modifier = Modifier,
    width: Dp = DefaultWidth,
    height: Dp = DefaultHeight,
    label: String? = null,
) {
    val still = LocalReduceAnimations.current || LocalInspectionMode.current

    val track = AppTheme.colors.track.color
    val fill = AppTheme.colors.bone.color

    val sweep = remember { Animatable(0f) }
    LaunchedEffect(still) {
        if (still) {
            // A single filled bone, held. Reduce-animations should get the
            // shape rather than an empty track that never fills, which reads
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
                drawBone(fill = track)
                clipRect(left = 0f, top = 0f, right = size.width * sweep.value, bottom = size.height) {
                    drawBone(fill = fill)
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
 * merge into a lump. The handoff's 200 by 46, which the scale reaches as two
 * steps added rather than one named.
 */
private val DefaultWidth: Dp = Dimension.D1900 * 2
private val DefaultHeight: Dp = Dimension.D1200 + Dimension.D200

/** One pass end to end. Slow enough to read as filling, quick enough to repeat. */
private const val SweepMillis = 1_400

@Preview
@Composable
private fun BoneLoaderPreview() {
    PreviewContent {
        BoneLoader()
    }
}
