package com.sodogku.libraries.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A chapter heading for a grid: a colour dot, the name, a rule filling the gap,
 * and a count at the far end.
 *
 * The achievements page reads its sets as chapters, and the dot is what ties a
 * chapter to the colour its cards are drawn in. The rule is what makes the row
 * a heading rather than a line of text with a number after it; without it the
 * count floats.
 *
 * The rule is hidden from a screen reader, which would otherwise announce an
 * empty box between the name and the count.
 */
@Composable
fun SectionHeader(
    title: String,
    dot: ColorResource,
    count: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gap),
    ) {
        Box(Modifier.size(DotSize).background(dot.color, CircleShape))
        Text(
            text = title,
            typography = AppTheme.typography.Heading.H800,
            color = AppTheme.colors.text,
            maxLines = 1,
        )
        Box(
            Modifier
                .weight(1f)
                .height(RuleThickness)
                .background(AppTheme.colors.rule.color)
                .clearAndSetSemantics { },
        )
        Text(
            text = count,
            typography = AppTheme.typography.Body.B500.Bold,
            color = AppTheme.colors.textMuted,
            maxLines = 1,
        )
    }
}

private val DotSize = Dimension.D400
private val RuleThickness = Dimension.D50
private val Gap = Dimension.D400

@Preview
@Composable
private fun SectionHeaderPreview() {
    PreviewContent {
        SectionHeader(title = "The campaign", dot = AppTheme.colors.status.okay, count = "2 of 9")
    }
}
