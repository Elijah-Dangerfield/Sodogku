package com.sodogku.libraries.ui.components.feedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import com.sodogku.libraries.ui.Elevation
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.bounceClick
import com.sodogku.libraries.ui.components.icon.Icon
import com.sodogku.libraries.ui.components.icon.IconSize
import com.sodogku.libraries.ui.components.icon.Icons
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.elevation
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import com.sodogku.system.clip
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * One line of "you just earned something": a glyph, what kind of thing it was,
 * and its name.
 *
 * Deliberately carries no domain type. The design system has no business
 * knowing what an achievement is, and a toast built around three strings is
 * equally usable for a level reward or a streak milestone later.
 */
data class UnlockToastItem(
    val glyph: String,
    val label: String,
    val title: String,
)

/**
 * Congratulations that get out of the way on their own.
 *
 * A **stack**, not a single toast, because the common case is several at once:
 * a player's first clear can earn First Steps, Perfect Form and Speed Demon in
 * the same second, and a queue that showed them one after another would hold
 * the celebration hostage for nine seconds on the first level of the game.
 *
 * They dismiss on a tap as well as on the timer. The toast sits over the win
 * sheet, so somebody who wants to get to Next level should not have to wait for
 * a reward to finish congratulating them.
 */
@Composable
fun UnlockToasts(
    items: List<UnlockToastItem>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var shown by remember(items) { mutableStateOf(items.isNotEmpty()) }
    val inPreview = LocalInspectionMode.current

    // Skipped under inspection: a screenshot test waits for an idle
    // composition, and a pending `delay` is not idle.
    LaunchedEffect(items, inPreview) {
        if (items.isEmpty() || inPreview) return@LaunchedEffect
        delay(DwellMillis)
        shown = false
        // Long enough for the exit to finish. Clearing the list under the
        // animation makes the toast vanish rather than leave.
        delay(ExitMillis.toLong())
        onDismiss()
    }

    AnimatedVisibility(
        visible = shown,
        modifier = modifier,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimension.D300),
        ) {
            items.forEach { item ->
                UnlockToast(
                    item = item,
                    onClick = {
                        shown = false
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun UnlockToast(item: UnlockToastItem, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimension.D500),
        modifier = Modifier
            // Before the clip and the background, or only the label scales.
            .bounceClick(onClick = onClick)
            .widthIn(max = ToastMaxWidth)
            .elevation(Elevation.Card, Radii.Round.shape)
            .clip(Radii.Round)
            .background(AppTheme.colors.surfacePrimary.color)
            .padding(horizontal = Dimension.D700, vertical = Dimension.D400),
    ) {
        Text(text = item.glyph, typography = AppTheme.typography.Heading.H700)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                typography = AppTheme.typography.Caption.C300,
                color = AppTheme.colors.accentPrimary,
            )
            Text(text = item.title, typography = AppTheme.typography.Body.B600)
        }
        // A trophy on every pill, at the trailing edge rather than the leading
        // one. The glyph on the left is the badge's own, and it varies: a paw, a
        // bolt, a bathtub. Nothing about the pill said "this is an award" except
        // the word "unlocked" in caption type, so the shape had to be read to be
        // understood. The trophy is the same on all of them, which is the point
        // — it is the part that means "you earned something" rather than the
        // part that says which thing.
        Icon(
            icon = Icons.Trophy.decorative,
            size = IconSize.Small,
            color = AppTheme.colors.accentPrimary,
        )
    }
}

/**
 * How long a toast holds the screen. Long enough to read a two-word badge name,
 * short enough that three of them stacked are gone before the player has
 * decided what to tap.
 */
private const val DwellMillis = 2_600L

private const val ExitMillis = 300

private val ToastMaxWidth = Dimension.D1900 * 3

@Preview
@Composable
private fun UnlockToastsPreview() {
    PreviewContent {
        UnlockToasts(
            items = listOf(
                UnlockToastItem(glyph = "🐾", label = "Badge unlocked", title = "First Steps"),
                UnlockToastItem(glyph = "⚡", label = "Badge unlocked", title = "Speed Demon"),
            ),
            onDismiss = {},
        )
    }
}
