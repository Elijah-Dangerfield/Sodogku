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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import com.sodogku.system.clip
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * One measurement of a finished run: a caption, a value, and a colour.
 *
 * [spoken] is the whole fact as a sentence. The caption and value read fine
 * side by side and terribly one after the other, which is what a screen reader
 * does with two separate labels.
 */
data class Stat(
    val caption: String,
    val value: String,
    val tint: Color,
    val spoken: String,
)

/**
 * The row of facts on an outcome sheet.
 *
 * Replaces a stack of five lines: a verdict, a rating, a rolling score, a time,
 * and sometimes a reward. The note already in `GameOutcomeSheets` was that the
 * sheet is far too much text, and the reason is that a vertical list asks to be
 * *read*, one item at a time, while a row of pills is scanned in one look. Same
 * facts, a fraction of the attention.
 *
 * Each pill carries its own colour rather than sharing one. Four identical
 * chips in a row read as a single control split into quarters; four colours
 * read as four different things, which is what they are.
 *
 * Sized by weight, so the row always fills the sheet and every pill is the same
 * width whatever its number. Pills that resize around their contents make a
 * five-digit score visibly more important than a two-digit one.
 */
@Composable
fun StatPills(stats: List<Stat>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimension.D300),
    ) {
        stats.forEach { stat ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimension.D100),
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = stat.spoken }
                    .clip(Radii.Card)
                    .background(stat.tint)
                    .padding(vertical = Dimension.D400, horizontal = Dimension.D300),
            ) {
                Text(
                    text = stat.caption,
                    typography = AppTheme.typography.Label.L400,
                    color = AppTheme.colors.onAccentPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Text(
                    text = stat.value,
                    typography = AppTheme.typography.Heading.H600,
                    color = AppTheme.colors.onAccentPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Preview
@Composable
private fun StatPillsPreview() {
    PreviewContent {
        StatPills(
            stats = listOf(
                Stat("SCORE", "342", AppTheme.colors.accentPrimary.color, "342 points"),
                Stat("TIME", "1:24", AppTheme.colors.accentSecondary.color, "1 minute 24 seconds"),
                Stat("MISTAKES", "1", AppTheme.colors.danger.color, "1 mistake"),
            ),
        )
    }
}
