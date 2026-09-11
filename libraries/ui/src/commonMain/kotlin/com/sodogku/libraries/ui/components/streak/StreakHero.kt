package com.sodogku.libraries.ui.components.streak

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.text.CountUpNumber
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import com.sodogku.system.clip
import com.sodogku.system.VerticalSpacerD300
import com.sodogku.system.VerticalSpacerD700
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * One day on the week strip under the number.
 *
 * A week rather than the five-week grid `StreakCalendar` draws, because the two
 * screens this appears on are about the run *now*: the strip is there so the
 * number has a shape, not so the player can audit a month.
 */
data class StreakWeekDay(
    val initial: String,
    val filled: Boolean,
    val isToday: Boolean,
    /** What a screen reader says for this day. The initial alone is ambiguous. */
    val spoken: String,
)

/**
 * The streak, big, with the week under it.
 *
 * Shared by the moment the player commits to a run and every moment it grows,
 * because those two screens are the same picture with a different number and a
 * different button. Keeping them one component is what stops the commitment
 * screen quietly drifting into a different idea of what a streak looks like.
 *
 * [countUpFrom] is what makes it a celebration rather than a status. Pass the
 * previous streak and the number counts to [streak] and lands with a thump;
 * pass null and it is simply there. The status page in the drawer passes null on
 * purpose: a page you opened yourself should not perform.
 */
@Composable
fun StreakHero(
    streak: Int,
    week: List<StreakWeekDay>,
    dayLabel: String,
    modifier: Modifier = Modifier,
    countUpFrom: Int? = null,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Dog(pose = DogPose.Solved, size = DogSize)

        VerticalSpacerD300()

        CountUpNumber(value = streak, countUpFrom = countUpFrom)

        Text(
            text = dayLabel,
            typography = AppTheme.typography.Heading.H600,
            color = AppTheme.colors.accentBrand,
            textAlign = TextAlign.Center,
        )

        VerticalSpacerD700()

        StreakWeekStrip(week = week, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Seven cells: initial above, a filled or empty disc below.
 *
 * Today's cell is marked whether or not it is filled, so a player who has not
 * played yet can see which one is theirs to fill. Duolingo does the same thing
 * and it is the only part of the strip that is doing work.
 */
@Composable
private fun StreakWeekStrip(week: List<StreakWeekDay>, modifier: Modifier = Modifier) {
    val filled = AppTheme.colors.accentBrand.color
    val empty = AppTheme.colors.surfaceDisabled.color

    Row(
        modifier = modifier
            .clip(Radii.Card)
            .background(AppTheme.colors.surfaceSecondary.color)
            .padding(vertical = Dimension.D500, horizontal = Dimension.D400),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        week.forEach { day ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.semantics { contentDescription = day.spoken },
            ) {
                Text(
                    text = day.initial,
                    typography = if (day.isToday) {
                        AppTheme.typography.Label.L500
                    } else {
                        AppTheme.typography.Label.L400
                    },
                    color = if (day.isToday) AppTheme.colors.text else AppTheme.colors.textSecondary,
                )
                VerticalSpacerD300()
                Box(
                    modifier = Modifier
                        .size(DiscSize)
                        .aspectRatio(1f)
                        .drawBehind {
                            drawCircle(color = if (day.filled) filled else empty)
                        },
                )
            }
        }
    }
}

/** Big enough to be the second thing you see, small enough to leave room for the number. */
private val DogSize = Dimension.D1900

private val DiscSize = Dimension.D1000

@Preview
@Composable
private fun StreakHeroPreview() {
    PreviewContent {
        StreakHero(
            streak = 6,
            dayLabel = "day streak",
            week = listOf("M", "T", "W", "T", "F", "S", "S").mapIndexed { index, initial ->
                StreakWeekDay(
                    initial = initial,
                    filled = index <= 3,
                    isToday = index == 3,
                    spoken = initial,
                )
            },
        )
    }
}
