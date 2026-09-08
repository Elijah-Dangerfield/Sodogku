package com.sodogku.libraries.ui.components.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.button.ButtonPrimary
import com.sodogku.libraries.ui.components.button.ButtonSecondary
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import com.sodogku.system.clip
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.daily_done
import sodogku.libraries.resources.generated.resources.daily_freeze_cta
import sodogku.libraries.resources.generated.resources.daily_freeze_remaining
import sodogku.libraries.resources.generated.resources.daily_out_of_bones
import sodogku.libraries.resources.generated.resources.daily_play
import sodogku.libraries.resources.generated.resources.daily_resets_hours
import sodogku.libraries.resources.generated.resources.daily_resets_minutes
import sodogku.libraries.resources.generated.resources.daily_restore_cta
import sodogku.libraries.resources.generated.resources.daily_restore_days
import sodogku.libraries.resources.generated.resources.daily_review
import sodogku.libraries.resources.generated.resources.daily_streak
import sodogku.libraries.resources.generated.resources.daily_streak_none
import sodogku.libraries.resources.generated.resources.daily_title
import sodogku.libraries.resources.generated.resources.daily_today

/** How the player stands with today's board. Picks the card's copy and its CTA. */
enum class DailyCardState {
    /** Not played yet, and openable. */
    Open,

    /** The board on screen right now, so there is nowhere to send the player. */
    Current,

    Completed,

    /** Played and ran out of bones. Spent exactly like a clear. */
    Failed,
}

/**
 * The daily challenge, as the first thing in the level drawer.
 *
 * Takes formatted values rather than a status object: the date is a rendering
 * decision (short month, no year) and the countdown is a duration handed in from
 * one snapshot of the clock. Nothing here reads a clock or does date arithmetic,
 * so a card built from one snapshot cannot show today's date next to tomorrow's
 * reset.
 *
 * [freezesRemaining] and [restoreDays] are non-null only when that offer would
 * actually reconnect a run, and never both at once — one missed day is a freeze
 * and more than one is a restore. Both wear [RewardButton] because both cost an
 * ad, and one colour means "an ad is involved" everywhere in the app.
 */
@Composable
fun DailyCard(
    dateLabel: String,
    streak: Int,
    state: DailyCardState,
    paws: Int,
    resetsIn: Duration,
    modifier: Modifier = Modifier,
    /**
     * Whether a finished day offers a way back into it. Off for the board
     * already on screen, which has nowhere to go.
     */
    canReview: Boolean = false,
    freezesRemaining: Int? = null,
    /** How many missed days a restore would bridge. Always two or more. */
    restoreDays: Int? = null,
    onPlay: () -> Unit = {},
    onFreeze: () -> Unit = {},
    onRestore: () -> Unit = {},
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimension.D400),
        modifier = modifier
            .fillMaxWidth()
            .clip(Radii.Card)
            .background(AppTheme.colors.accentPrimary.color.copy(alpha = CardTint))
            .padding(Dimension.D500),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimension.D400),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Dog(pose = DogPose.Focused, size = Dimension.D1300)
            Column(modifier = Modifier.weight(HeaderFill)) {
                Text(
                    text = stringResource(Res.string.daily_title),
                    typography = AppTheme.typography.Heading.H600,
                )
                Text(
                    text = dateLabel,
                    typography = AppTheme.typography.Caption.C300,
                    color = AppTheme.colors.textSecondary,
                )
            }
        }

        // The streak is the number the card exists for, so it gets the display
        // scale the level and score already use mid-puzzle rather than another
        // caption nobody reads twice.
        Text(
            text = if (streak > 0) {
                stringResource(Res.string.daily_streak, streak)
            } else {
                stringResource(Res.string.daily_streak_none)
            },
            typography = AppTheme.typography.Body.B600,
            color = if (streak > 0) AppTheme.colors.text else AppTheme.colors.textSecondary,
        )

        when (state) {
            DailyCardState.Open -> ButtonPrimary(
                onClick = onPlay,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.daily_play))
            }

            DailyCardState.Current -> Text(
                text = stringResource(Res.string.daily_today),
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
            )

            DailyCardState.Completed -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimension.D400),
            ) {
                // Recalled, not awarded: the pop belongs to the win sheet. Three
                // empty paws would read as a nought-out-of-three score, so a day
                // that was spent without being scored shows none at all.
                if (paws > 0) PawRating(paws = paws, size = Dimension.D800, animated = false)
                Text(
                    text = stringResource(Res.string.daily_done),
                    typography = AppTheme.typography.Body.B500,
                    color = AppTheme.colors.textSecondary,
                )
            }

            DailyCardState.Failed -> Text(
                text = stringResource(Res.string.daily_out_of_bones),
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
            )
        }

        // A finished day still opens, on its result rather than its board. The
        // card used to go inert the moment the day was over, so a player who
        // left a daily and wanted back in found a card that did nothing and said
        // nothing.
        if (canReview) {
            ButtonSecondary(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.daily_review))
            }
        }

        Text(
            text = resetsIn.resetLabel(),
            typography = AppTheme.typography.Caption.C300,
            color = AppTheme.colors.textSecondary,
        )

        if (freezesRemaining != null) {
            RewardButton(
                label = stringResource(Res.string.daily_freeze_cta),
                onClick = onFreeze,
            )
            Text(
                text = stringResource(Res.string.daily_freeze_remaining, freezesRemaining),
                typography = AppTheme.typography.Caption.C200,
                color = AppTheme.colors.textSecondary,
            )
        }

        // The caption says how many days, because that is the whole difference
        // between this and the freeze and it is what makes the ad worth watching.
        if (restoreDays != null) {
            RewardButton(
                label = stringResource(Res.string.daily_restore_cta),
                onClick = onRestore,
            )
            Text(
                text = stringResource(Res.string.daily_restore_days, restoreDays),
                typography = AppTheme.typography.Caption.C200,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
}

/**
 * Hours and minutes, and minutes alone in the last hour.
 *
 * Deliberately not a ticking clock. The card is re-rendered from a fresh status
 * every time the drawer opens and again at the rollover, and a second-by-second
 * countdown on a pane that is usually closed buys a recomposition per second for
 * a number nobody is watching.
 */
@Composable
private fun Duration.resetLabel(): String {
    val hours = inWholeHours
    val minutes = inWholeMinutes % MinutesPerHour
    return if (hours > 0) {
        stringResource(Res.string.daily_resets_hours, hours, minutes)
    } else {
        stringResource(Res.string.daily_resets_minutes, minutes)
    }
}

private const val MinutesPerHour = 60

/** Enough tint to lift the card off the drawer without becoming a second surface. */
private const val CardTint = 0.16f

private const val HeaderFill = 1f

@Preview
@Composable
private fun DailyCardPreview() {
    PreviewContent {
        Column(verticalArrangement = Arrangement.spacedBy(Dimension.D500)) {
            DailyCard(
                dateLabel = "Sep 7",
                streak = 12,
                state = DailyCardState.Open,
                paws = 0,
                resetsIn = 5.hours + 12.minutes,
            )
            DailyCard(
                dateLabel = "Sep 7",
                streak = 0,
                state = DailyCardState.Completed,
                paws = 3,
                resetsIn = 42.minutes,
                freezesRemaining = 2,
            )
            DailyCard(
                dateLabel = "Sep 7",
                streak = 0,
                state = DailyCardState.Open,
                paws = 0,
                resetsIn = 3.hours,
                restoreDays = 3,
            )
        }
    }
}
