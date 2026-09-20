package com.sodogku.libraries.ui.components.streak

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.text.CountUpNumber
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.VerticalSpacerD300
import com.sodogku.system.VerticalSpacerD700
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The streak, big, with the week under it: the head of the status page.
 *
 * The three ceremonies used to share this and now draw the handoff's band
 * layout instead, so the one caller left is the page a player opens by tapping
 * the flame. That page does not perform: the number is simply there, which is
 * why there is no `countUpFrom` here any more.
 *
 * The strip is [WeekStrip], the same one the ceremonies draw, so the week a
 * player sees on a plain visit is the week they were shown the night before.
 */
@Composable
fun StreakHero(
    streak: Int,
    week: List<WeekStripDay>,
    dayLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Dog(pose = DogPose.Solved, size = DogSize)

        VerticalSpacerD300()

        CountUpNumber(value = streak)

        Text(
            text = dayLabel,
            typography = AppTheme.typography.Heading.H600,
            color = AppTheme.colors.accentBrand,
            textAlign = TextAlign.Center,
        )

        VerticalSpacerD700()

        WeekStrip(days = week, modifier = Modifier.fillMaxWidth())
    }
}

/** Big enough to be the second thing you see, small enough to leave room for the number. */
private val DogSize = Dimension.D1900

@Preview
@Composable
private fun StreakHeroPreview() {
    PreviewContent {
        StreakHero(streak = 6, dayLabel = "day streak", week = previewWeek())
    }
}
