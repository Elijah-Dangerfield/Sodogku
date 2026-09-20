package com.sodogku.libraries.ui.components.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.typography.TypographyResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * [Text] with a stroke behind it, for words that have to land on top of the
 * board rather than beside it.
 *
 * The board is the one surface in the app with no fixed colour: a cell is
 * whatever its region is, and the floating score has to be legible over pink,
 * periwinkle, lime and amber in the same second. Picking an ink that reads on
 * all four is not possible, which is why the score was effectively invisible.
 * An outline sidesteps the problem: the fill can stay the app's own colour and
 * the stroke supplies the contrast, wherever it lands.
 *
 * Two passes of the same string, the back one stroked. Not a shadow, which
 * blurs and at small sizes reads as a printing fault rather than as an edge.
 *
 * The stroked copy is hidden from accessibility, so a screen reader is handed
 * one string rather than the same words twice.
 *
 * Ported from Virtu, which had the same problem on a board of its own.
 */
@Composable
fun OutlinedText(
    text: String,
    modifier: Modifier = Modifier,
    strokeColor: ColorResource = AppTheme.colors.surfacePrimary,
    strokeWidth: Dp = DefaultStrokeWidth,
    color: ColorResource? = null,
    lineBreak: LineBreak? = null,
    hyphens: Hyphens? = null,
    allCaps: Boolean? = LocalTextConfig.current.allCaps ?: false,
    typography: TypographyResource = LocalTextConfig.current.typography
        ?: AppTheme.typography.Default,
    textDecoration: TextDecoration = LocalTextConfig.current.textDecoration ?: TextDecoration.None,
    textAlign: TextAlign? = LocalTextConfig.current.textAlign,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    overflow: TextOverflow = LocalTextConfig.current.overflow ?: DefaultTextOverflow,
    softWrap: Boolean = LocalTextConfig.current.softWrap ?: true,
    maxLines: Int = LocalTextConfig.current.maxLines ?: Int.MAX_VALUE,
    minLines: Int = LocalTextConfig.current.minLines ?: 1,
) {
    val baseStyle = typography.toStyle(color, textDecoration, textAlign, hyphens, lineBreak)
    val processed = text.processHtmlTags().let { if (allCaps == true) it.toUpperCase() else it }

    // A zero stroke is a plain Text, not a Box wrapping one. Callers that switch
    // the outline off with a theme value should not pay a layout node for it.
    if (strokeWidth <= 0.dp) {
        BasicText(
            text = processed,
            modifier = modifier,
            style = baseStyle,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            onTextLayout = onTextLayout,
        )
        return
    }

    val strokePx = with(LocalDensity.current) { strokeWidth.toPx() }
    val outlineStyle = baseStyle.copy(
        color = strokeColor.color,
        // Round joins, because mitred ones spike outward at the sharp corners of
        // a glyph and the stroke stops reading as an even border.
        drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round),
        // Explicitly dropped: a stroke that also carried the typography's shadow
        // would draw the blur twice, once per pass.
        shadow = null,
    )

    Box(modifier = modifier) {
        BasicText(
            text = processed,
            style = outlineStyle,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            modifier = Modifier.semantics { hideFromAccessibility() },
            // The filled pass reports layout. Both are the same string in the
            // same style, so they measure identically, and reporting twice would
            // hand the caller two callbacks per frame.
            onTextLayout = {},
        )
        BasicText(
            text = processed,
            style = baseStyle,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            onTextLayout = onTextLayout,
        )
    }
}

/**
 * Half the stroke sits outside the glyph, so this is thinner than it sounds.
 * Wide enough to survive the board's colours, narrow enough not to close up the
 * counter of an 8.
 */
private val DefaultStrokeWidth = Dimension.D100

/**
 * The stroke behind a display numeral, in `textOutline`.
 *
 * Two widths, because the 2026-09 handoff draws two: 7 behind the streak count
 * at `Display.D1600`, and 6 behind everything a step down, the "Sharp work"
 * headline and the achievements count. Named here so the three screens that
 * draw one cannot each pick a number.
 */
val HeroNumeralStrokeWidth: Dp = 7.dp
val DisplayNumeralStrokeWidth: Dp = Dimension.D200

@Preview
@Composable
private fun OutlinedTextPreview() {
    PreviewContent {
        OutlinedText(
            text = "+56",
            typography = AppTheme.typography.Heading.H500,
            color = AppTheme.colors.accentPrimary,
        )
    }
}

@Preview
@Composable
private fun DisplayNumeralPreview() {
    PreviewContent {
        OutlinedText(
            text = "12",
            typography = AppTheme.typography.Display.D1600,
            color = AppTheme.colors.accentBrand,
            strokeColor = AppTheme.colors.textOutline,
            strokeWidth = HeroNumeralStrokeWidth,
        )
    }
}
