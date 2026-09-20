package com.sodogku.libraries.ui.components.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sodogku.libraries.ui.Border
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.border
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import com.sodogku.system.clip
import com.sodogku.system.typography.TypographyResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * One measurement of a finished run, in the 2026-09 handoff's chip: a label
 * bar in the chip's colour across the top, a light interior, and the value in
 * the chip's ink.
 *
 * Three colours rather than one, because the same colour cannot do all three
 * jobs. The amber that borders the score chip fails as text on the light
 * interior (1.8:1), so the value is set in [accentBrandInk][com.sodogku.system.color.Colors.accentBrandInk];
 * the purple and red are dark enough to be their own ink. Which is why the
 * call site names the value ink and the label ink separately instead of the
 * chip deriving them: a derivation that got the amber right would get the
 * purple wrong.
 *
 * [spoken] is the whole fact as a sentence, for the same reason [Stat] has one.
 */
@Immutable
data class StatChipSpec(
    val label: String,
    val value: String,
    /** The border and the label bar. */
    val tint: ColorResource,
    /** The label on the bar. Dark on amber, white on anything darker. */
    val labelInk: ColorResource,
    /** The value on the light interior. */
    val valueInk: ColorResource,
    val spoken: String,
    /** An emoji beside the value, or nothing. */
    val glyph: String? = null,
)

/**
 * The row of chips under a verdict: three across, equal widths.
 *
 * Three across until they cannot be. At the largest system text size a
 * third of the narrowest phone is not wide enough for "MISTAKES", and the
 * choice is between truncating the one word the chip exists to say and
 * stacking the chips. So the row measures what its widest chip needs and
 * stacks when a third of the row is short of it. Nothing in a chip is ever
 * ellipsized or clipped; `StatChipFitsTest` holds that at the largest text
 * size a player can pick.
 */
@Composable
fun StatChipRow(chips: List<StatChipSpec>, modifier: Modifier = Modifier) {
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = { chips.forEach { StatChip(it) } },
    ) { measurables, constraints ->
        val gap = ChipGap.roundToPx()
        val count = measurables.size
        val width = constraints.maxWidth
        val third = if (count == 0) width else (width - gap * (count - 1)) / count
        val widest = measurables.maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
        val sideBySide = widest <= third

        val chipWidth = if (sideBySide) third else width
        val chipHeight = measurables.maxOfOrNull { it.maxIntrinsicHeight(chipWidth) } ?: 0
        val placeables = measurables.map { it.measure(Constraints.fixed(chipWidth, chipHeight)) }

        if (sideBySide) {
            layout(width, chipHeight) {
                placeables.forEachIndexed { index, chip -> chip.place(index * (third + gap), 0) }
            }
        } else {
            layout(width, chipHeight * count + gap * (count - 1).coerceAtLeast(0)) {
                placeables.forEachIndexed { index, chip -> chip.place(0, index * (chipHeight + gap)) }
            }
        }
    }
}

@Composable
fun StatChip(spec: StatChipSpec, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .semantics { contentDescription = spec.spoken }
            // Border before the clip, as `Modifier.border`'s own doc asks, so
            // the stroke's outer edge is not shaved at the corners.
            .border(Border(spec.tint, ChipBorderWidth), Radii.Chip)
            .clip(Radii.Chip)
            .background(AppTheme.colors.surfacePrimary.color),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(spec.tint.color)
                .padding(vertical = LabelBarPadding, horizontal = ChipInset),
        ) {
            Text(
                text = spec.label,
                typography = StatChipLabelTypography,
                color = spec.labelInk,
                allCaps = true,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GlyphGap),
            modifier = Modifier.padding(vertical = ValuePadding, horizontal = ChipInset),
        ) {
            if (spec.glyph != null) {
                Text(text = spec.glyph, typography = AppTheme.typography.Body.B700, maxLines = 1, softWrap = false)
            }
            Text(
                text = spec.value,
                typography = StatChipValueTypography,
                color = spec.valueInk,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/**
 * The two sizes a chip is set in, named so `StatChipFitsTest` measures the
 * size the chip actually uses rather than a copy of it.
 */
internal val StatChipLabelTypography: TypographyResource
    @Composable get() = AppTheme.typography.Caption.C400.Bold.tracked(LabelTracking)

internal val StatChipValueTypography: TypographyResource
    @Composable get() = AppTheme.typography.Heading.H800

private val LabelTracking = 1.sp

private val ChipGap = Dimension.D500

/** Off the dimension scale, which steps 2, 4. A 2dp edge read as a hairline and 4 as a frame. */
private val ChipBorderWidth = 3.dp
private val LabelBarPadding = Dimension.D200
private val ValuePadding = Dimension.D500
private val ChipInset = Dimension.D300
private val GlyphGap = Dimension.D200

@Preview
@Composable
private fun StatChipRowPreview() {
    PreviewContent {
        StatChipRow(
            chips = listOf(
                StatChipSpec(
                    label = "Score",
                    value = "791",
                    tint = AppTheme.colors.accentBrand,
                    labelInk = AppTheme.colors.onAccentBrand,
                    valueInk = AppTheme.colors.accentBrandInk,
                    spoken = "791 points",
                    glyph = "⚡",
                ),
                StatChipSpec(
                    label = "Time",
                    value = "2:03",
                    tint = AppTheme.colors.accentSecondary,
                    labelInk = AppTheme.colors.onAccentSecondary,
                    valueInk = AppTheme.colors.accentSecondary,
                    spoken = "2 minutes 3 seconds",
                    glyph = "⏱",
                ),
                StatChipSpec(
                    label = "Mistakes",
                    value = "1",
                    tint = AppTheme.colors.danger,
                    labelInk = AppTheme.colors.onAccentPrimary,
                    valueInk = AppTheme.colors.danger,
                    spoken = "1 mistake",
                    glyph = "❌",
                ),
            ),
        )
    }
}
