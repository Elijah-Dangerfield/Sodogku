package com.sodogku.libraries.ui.components.feedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sodogku.libraries.ui.Border
import com.sodogku.libraries.ui.Elevation
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.blurShadow
import com.sodogku.libraries.ui.border
import com.sodogku.libraries.ui.bounceClick
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.system.LocalReduceAnimations
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import com.sodogku.system.clip
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * One "you just earned something": a glyph, what kind of thing it was, its
 * name, and one line on why.
 *
 * Deliberately carries no domain type. The design system has no business
 * knowing what an achievement is, and a toast built around strings is equally
 * usable for a level reward or a streak milestone later.
 */
data class UnlockToastItem(
    val glyph: String,
    val label: String,
    val title: String,
    /** Why it was earned, in one line. Null when there is nothing to explain. */
    val body: String? = null,
)

/**
 * Which of several unlocks is on screen: the head, until it is dismissed, and
 * then the next one.
 *
 * A value rather than a `remember` inside the composable, so the rule that
 * makes "one at a time" true is a function over a list and not something a
 * test would have to drive a composition to see. [UnlockToasts] holds one of
 * these and does nothing but draw its head.
 */
@Immutable
data class UnlockQueue(val pending: List<UnlockToastItem>) {

    /** The toast on screen, or null once every one of them has been. */
    val head: UnlockToastItem? get() = pending.firstOrNull()

    val isEmpty: Boolean get() = pending.isEmpty()

    /** The queue after the head has left. Advancing an empty queue is a no-op. */
    fun advance(): UnlockQueue = UnlockQueue(pending.drop(1))
}

/**
 * Congratulations that get out of the way on their own, one at a time.
 *
 * **A queue, not a stack.** Several badges can land in the same second (a first
 * clear can earn First Steps, Perfect Form and Speed Demon at once), and the
 * first version drew them all in a column. The 2026-09 handoff shows one card,
 * and one card is what the [Elevation.Toast] shadow and the full-width row are
 * drawn for: three of them stacked would be three floating cards over a board.
 * The cost is time, four seconds a badge, and a tap on the card pays it down.
 *
 * [onDismiss] fires once, when the last toast has left. A caller that clears the
 * list in response gets the toast removed cleanly; a caller that leaves the
 * list alone (the badges, which are cleared by the next attempt and counted
 * against a watermark elsewhere) gets nothing drawn for the rest of the list's
 * life, because the queue it feeds is remembered against the list.
 *
 * Only the card takes touches. The row is the width of the screen less a
 * gutter, and the gutter is padding outside the click, so a tap beside the
 * toast reaches whatever is under it. A toast over a board in play must never
 * cost the player a move.
 *
 * [trailing] is the seam for the handoff's reward pill, the `+3` bone on the
 * right of the card. Nothing fills it yet: badges grant nothing today, and a
 * pill promising bones that do not arrive is worse than no pill.
 */
@Composable
fun UnlockToasts(
    items: List<UnlockToastItem>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    var queue by remember(items) { mutableStateOf(UnlockQueue(items)) }
    val head = queue.head ?: return
    val done by rememberUpdatedState(onDismiss)

    // Keyed on what is left rather than on the item: two equal items in a row
    // would otherwise share one composition, and a toast that had already been
    // dismissed once would never leave a second time.
    key(queue.pending.size) {
        UnlockToastHost(
            item = head,
            trailing = trailing,
            modifier = modifier,
            onGone = {
                val next = queue.advance()
                queue = next
                if (next.isEmpty) done()
            },
        )
    }
}

/**
 * One toast's life: slide in, dwell, slide out, report that it has gone.
 */
@Composable
private fun UnlockToastHost(
    item: UnlockToastItem,
    trailing: (@Composable () -> Unit)?,
    modifier: Modifier,
    onGone: () -> Unit,
) {
    val inPreview = LocalInspectionMode.current
    // The slide is decoration and goes with the setting. The timer is not: a
    // toast that never left would be the one thing on the board the player
    // could not get past, so reduce-animations keeps the dismiss and drops
    // the travel.
    val still = LocalReduceAnimations.current

    // Hidden first and shown in the same composition, which is what gives the
    // enter transition somewhere to come from. Started visible, as the first
    // version was, the slide in never runs and only the exit ever animates.
    // Under inspection it starts shown instead, because nothing below is going
    // to change it and a preview wants the card, not an empty frame.
    val visible = remember { MutableTransitionState(initialState = inPreview).apply { targetState = true } }
    var dismissed by remember { mutableStateOf(false) }
    val gone by rememberUpdatedState(onGone)

    // Skipped under inspection: a screenshot test waits for an idle
    // composition, and a pending `delay` is not idle.
    LaunchedEffect(dismissed, inPreview, still) {
        if (inPreview) return@LaunchedEffect
        if (!dismissed) delay(DwellMillis)
        visible.targetState = false
        // Long enough for the exit to finish. Advancing the queue under the
        // animation makes the toast vanish rather than leave.
        if (!still) delay(ExitMillis.toLong())
        gone()
    }

    AnimatedVisibility(
        visibleState = visible,
        modifier = modifier,
        enter = if (still) EnterTransition.None else slideInVertically { -it } + fadeIn(),
        exit = if (still) ExitTransition.None else slideOutVertically { -it } + fadeOut(),
    ) {
        UnlockToast(
            item = item,
            trailing = trailing,
            onClick = { dismissed = true },
            // The gutter, outside the card and its click: the reason a tap
            // beside the toast reaches the board.
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimension.D700),
        )
    }
}

@Composable
private fun UnlockToast(
    item: UnlockToastItem,
    trailing: (@Composable () -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimension.D600),
        modifier = modifier
            // Before the shadow and the fill, or only the label scales.
            .bounceClick(onClick = onClick)
            .blurShadow(Elevation.Toast, Radii.R900.shape, AppTheme.colors.text)
            .clip(Radii.R900)
            .background(AppTheme.colors.surfacePrimary.color)
            .border(Border(AppTheme.colors.accentBrand, RingWidth), Radii.R900)
            .padding(Dimension.D700),
    ) {
        // The same pale disc every earned badge sits on in the grid, so the
        // toast and the shelf it points at read as one thing.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(DiscSize)
                .background(AppTheme.colors.badgeEarnedDisc.color, CircleShape),
        ) {
            Text(
                text = item.glyph,
                typography = AppTheme.typography.Display.D1000,
                maxLines = 1,
                softWrap = false,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                typography = KickerTypography,
                color = AppTheme.colors.accentBrandInkSmall,
                allCaps = true,
                maxLines = 1,
            )
            Text(
                text = item.title,
                typography = AppTheme.typography.Heading.H800,
                color = AppTheme.colors.text,
            )
            if (item.body != null) {
                Text(
                    text = item.body,
                    typography = AppTheme.typography.Body.B600,
                    color = AppTheme.colors.textMuted,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * How long a toast holds the screen. The handoff's "a few seconds": long enough
 * to read a name and the line under it, and a tap ends it sooner.
 */
private const val DwellMillis = 4_000L

private const val ExitMillis = 300

/** The handoff's 56, which the scale reaches as two steps added. */
private val DiscSize = Dimension.D1300 + Dimension.D300

/** The amber ring round the card. Matches the earned ring on a badge tile. */
private val RingWidth = 3.dp

/** `11.5px/700` spaced 1.2: the kicker over the badge name, in the body face. */
private val KickerTypography
    @Composable get() = AppTheme.typography.Body.B500.Bold.tracked(KickerTracking)

private val KickerTracking = 1.2.sp

@Preview
@Composable
private fun UnlockToastsPreview() {
    PreviewContent {
        UnlockToasts(
            items = listOf(
                UnlockToastItem(
                    glyph = "🧼",
                    label = "Badge unlocked",
                    title = "Spotless",
                    body = "Twenty five boards, no mistakes.",
                ),
                UnlockToastItem(glyph = "⚡", label = "Badge unlocked", title = "Speed Demon"),
            ),
            onDismiss = {},
        )
    }
}
