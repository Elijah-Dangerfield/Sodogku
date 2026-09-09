package com.sodogku.libraries.ui.components.streak

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.bounceClick
import com.sodogku.libraries.ui.components.game.drawPaw
import com.sodogku.libraries.ui.system.Feel
import com.sodogku.libraries.ui.system.LocalHaptics
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The greyed-out paw that fills in when the player taps it.
 *
 * One tap, one fill, and the fill *is* the acknowledgement. There is no
 * separate confirm button underneath, because a moment that asks for two
 * gestures is a form. The caller is told when the fill lands, and decides what
 * to do next.
 *
 * The paw is [drawPaw] from the game shapes, and it stays a paw now that
 * [StreakMark] is a flame. The flame is the *count's* glyph — it says how long
 * the run is. This is the commitment that starts one, and the app's own mark is
 * the right thing to press to make a promise to it.
 */
@Composable
fun StreakIntentionMark(
    /** What the control is, e.g. "Start your streak". */
    label: String,
    /** Where it is now: waiting for a tap, or filled. */
    stateLabel: String,
    onFilled: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = Dimension.D1900,
) {
    val haptics = LocalHaptics.current
    val dim = AppTheme.colors.surfaceSecondary.color
    val lit = AppTheme.colors.accentPrimary.color

    // Filled outright in a preview or a screenshot test: an unfilled paw is the
    // state this component spends the least time in and the least useful one to
    // capture, and nothing will ever tap it there.
    val still = LocalInspectionMode.current
    val fill = remember { Animatable(if (still) 1f else 0f) }
    val taps = remember { mutableIntStateOf(0) }
    val spent = taps.intValue > 0

    LaunchedEffect(spent) {
        if (!spent) return@LaunchedEffect
        haptics.play(Feel.Place)
        fill.animateTo(1f, tween(FillMillis))
        haptics.play(Feel.Win)
        onFilled()
    }

    Box(
        modifier = modifier
            .size(size)
            .bounceClick(enabled = !spent) { taps.intValue++ }
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = stateLabel
                role = Role.Button
                if (spent) {
                    // Announced as spent rather than left activatable. A reader
                    // that can still press it would fire the fill a second time
                    // and a second `onFilled` with it.
                    disabled()
                } else {
                    onClick {
                        taps.intValue++
                        true
                    }
                }
            }
            .drawBehind {
                drawPaw(color = dim, filled = true)
                // A rising fill: the paw is clipped to the wet part and redrawn
                // lit, so the shape never changes and only the water line moves.
                val height = this.size.height * fill.value
                clipRect(
                    top = this.size.height - height,
                    bottom = this.size.height,
                ) {
                    drawPaw(color = lit, filled = true)
                }
            },
    )
}

/**
 * Long by this app's standards, and deliberately so.
 *
 * `Motion` caps feedback at ~300ms because a player sees it hundreds of times.
 * This one happens once, ever, and the fill is the whole content of the
 * moment. At 300ms it is a flicker and the player has no idea what happened.
 */
private const val FillMillis = 900

@Preview
@Composable
private fun StreakIntentionMarkPreview() {
    PreviewContent {
        StreakIntentionMark(
            label = "Start your streak",
            stateLabel = "Waiting",
            onFilled = {},
        )
    }
}
