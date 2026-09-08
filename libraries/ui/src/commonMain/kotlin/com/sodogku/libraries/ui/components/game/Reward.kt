package com.sodogku.libraries.ui.components.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import com.sodogku.libraries.ui.PreviewContent
import androidx.compose.ui.graphics.Color
import com.sodogku.libraries.ui.bounceClick
import com.sodogku.libraries.ui.system.glossy
import com.sodogku.libraries.ui.components.icon.Icon
import com.sodogku.libraries.ui.components.icon.IconSize
import com.sodogku.libraries.ui.components.icon.Icons
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import com.sodogku.system.clip
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.game_watch_ad_badge

/**
 * "Watch an ad, get something" as a standing offer.
 *
 * Deliberately not a [com.sodogku.libraries.ui.components.button.Button]: the
 * ad controls are the one place in the app where the player is being asked for
 * something rather than told something, and they should not wear the same
 * clothes as Continue and Cancel. The secondary accent separates them from
 * every ordinary CTA, so a player learns the colour once and can then tell an
 * offer from a toll without reading.
 *
 * The idle pulse beats and then rests. A continuous throb is what an
 * always-on-screen element cannot do — it stops reading as an invitation after
 * about ten seconds and starts reading as a nag — so the button draws the eye
 * once, holds still for a beat, and repeats.
 */
@Composable
fun RewardButton(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.accentSecondary.color,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    val pulse = remember { Animatable(1f) }
    val inPreview = LocalInspectionMode.current

    LaunchedEffect(enabled, inPreview) {
        if (!enabled || inPreview) {
            pulse.snapTo(1f)
            return@LaunchedEffect
        }
        while (true) {
            pulse.animateTo(PulseScale, tween(PulseRiseMillis))
            pulse.animateTo(1f, tween(PulseFallMillis))
            delay(PulseRestMillis)
        }
    }

    val content = if (enabled) {
        AppTheme.colors.onAccentSecondary
    } else {
        AppTheme.colors.onSurfaceDisabled
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimension.D300),
        modifier = modifier
            .bounceClick(enabled = enabled, onClick = onClick)
            .graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
            }
            // Same candy treatment as the boosters beside it. An offer that
            // looked flat next to two glossy buttons would read as the one
            // control on the row that is switched off.
            .clip(Radii.Round)
            .glossy(
                color = if (enabled) color else AppTheme.colors.surfaceDisabled.color,
                enabled = enabled,
            )
            .padding(
                start = Dimension.D500,
                end = Dimension.D500,
                top = Dimension.D300,
                bottom = Dimension.D400,
            ),
    ) {
        Icon(
            icon = Icons.PlayCircle.decorative,
            size = IconSize.Small,
            color = content,
        )
        Text(
            text = label,
            typography = AppTheme.typography.Body.B500,
            color = content,
        )
    }
}

/**
 * Marks a control the player already wants as one that plays an ad first.
 *
 * Sits on the button rather than replacing its label, because the action has
 * not changed — Next level is still Next level — and rewriting the label to
 * mention the ad would make the ad the thing the player is choosing.
 *
 * Same accent as [RewardButton] on purpose: one colour means "an ad is
 * involved" everywhere it appears.
 */
@Composable
fun RewardBadge(
    modifier: Modifier = Modifier,
    label: String = stringResource(Res.string.game_watch_ad_badge),
) {
    Text(
        text = label,
        typography = AppTheme.typography.Caption.C200,
        color = AppTheme.colors.onAccentSecondary,
        modifier = modifier
            .clip(Radii.Round)
            .background(AppTheme.colors.accentSecondary.color)
            .padding(horizontal = Dimension.D300, vertical = Dimension.D50),
    )
}

/** Barely there. Big enough to catch peripheral vision, small enough not to shove neighbours. */
private const val PulseScale = 1.05f

private const val PulseRiseMillis = 380
private const val PulseFallMillis = 520

/**
 * The rest between beats, and the number that decides whether this reads as an
 * offer or as pestering. Long enough that the button is still for most of the
 * time a player is looking at the board.
 */
private const val PulseRestMillis = 2_600L

@Preview
@Composable
private fun RewardButtonPreview() {
    PreviewContent {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D400)) {
            RewardButton(label = "Free bones")
            RewardButton(label = "Free bones", enabled = false)
            RewardBadge()
        }
    }
}
