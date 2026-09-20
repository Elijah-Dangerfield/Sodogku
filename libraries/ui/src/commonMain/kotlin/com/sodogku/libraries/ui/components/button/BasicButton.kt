package com.sodogku.libraries.ui.components.button

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import com.sodogku.system.Radius
import com.sodogku.system.thenIf
import com.sodogku.libraries.ui.Border
import com.sodogku.libraries.ui.Elevation
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.StandardBorderWidth
import com.sodogku.libraries.ui.bounceClick
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.libraries.ui.components.Surface
import com.sodogku.libraries.ui.components.icon.SmallIcon
import com.sodogku.libraries.ui.components.icon.IconResource
import com.sodogku.libraries.ui.components.text.ProvideTextConfig
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.components.text.TextConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BasicButton(
    backgroundColor: ColorResource?,
    borderColor: ColorResource?,
    contentColor: ColorResource,
    size: ButtonSize,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    deepColor: ColorResource? = null,          // the 3D lip; null = flat button
    icon: IconResource? = null,
    contentPadding: PaddingValues = size.padding(hasIcon = icon != null),
    enabled: Boolean = true,
    onDisabledTap: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
        val effectiveOnClick = if (enabled) onClick else onDisabledTap

        val radius = size.radius()
        val face: @Composable () -> Unit = {
            Surface(
                modifier = Modifier.semantics { role = Role.Button },
                radius = radius,
                // When a deepColor is present the 3D lip replaces the drop
                // shadow entirely, so no elevation there.
                elevation = if (deepColor == null && backgroundColor != null) Elevation.Button else Elevation.None,
                color = backgroundColor,
                contentColor = contentColor,
                border = borderColor?.let { Border(it, size.borderWidth()) },
                contentPadding = contentPadding,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(
                        ButtonIconSpacing,
                        Alignment.CenterHorizontally
                    )
                ) {
                    if (icon != null) SmallIcon(icon = icon)
                    ProvideTextConfig(size.textConfig(), content = content)
                }
            }
        }

        if (deepColor == null) {
            // Flat path (outlined / text / disabled / default): original behaviour + bounce.
            Box(
                contentAlignment = Alignment.Center,
                // Propagate min constraints so a caller-supplied `Modifier.fillMaxWidth()`
                // (which sets minWidth = maxWidth on the outer Box) flows down to the Surface
                // and stretches the button. Default behavior — no width modifier — leaves
                // minWidth at 0, so the button still wraps content inside weighted Rows.
                propagateMinConstraints = true,
                modifier = modifier.thenIf(effectiveOnClick != null) {
                    bounceClick(
                        mutableInteractionSource = interactionSource,
                        onClick = effectiveOnClick!!
                    )
                },
            ) { face() }
        } else {
            // Springy 3D lip: a hard "deep" band sits behind the face; the face DROPS onto it
            // on press — no soft Material shadow, no scale bounce.
            val depthAtRest = size.pressDepth()
            val pressed by interactionSource.collectIsPressedAsState()
            // Kept as State: `.offset { }` already defers to the layout phase,
            // so `by` here would be the one thing forcing the button and its
            // content to recompose on every frame of the press spring.
            val drop = animateDpAsState(
                targetValue = if (pressed) depthAtRest else 0.dp,
                animationSpec = spring(stiffness = Spring.StiffnessHigh),
                label = "ButtonLip",
            )
            Box(
                contentAlignment = Alignment.Center,
                propagateMinConstraints = true,
                modifier = modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = effectiveOnClick != null,
                    onClick = { effectiveOnClick?.invoke() },
                ),
            ) {
                // deep band fills the whole box (face + the reserved lip strip)
                Box(Modifier.matchParentSize().clip(radius.shape).background(deepColor.color))
                // face sized normally + a constant bottom reserve (stable height); drops on press.
                // propagateMinConstraints carries the outer Box's min width (set by a caller's
                // fillMaxWidth) down to the face Surface, so a full-width button's face stretches to
                // match the lip band instead of wrapping its content on the left.
                Box(
                    propagateMinConstraints = true,
                    modifier = Modifier
                        .padding(bottom = depthAtRest)
                        .offset { IntOffset(x = 0, y = drop.value.roundToPx()) },
                ) { face() }
            }
        }
    }
}

/**
 * Lip depth by size — the constant strip the face drops onto.
 *
 * Chunky, but settled by looking rather than by argument. At the original 5/4/3
 * the lip read as a hairline that could be mistaken for a border, and a button
 * you cannot see is thick does not look like something you press. 8/6/5/4 was
 * still shy of the designs; 10/8/6/5 overshot and read as a slab under the face.
 * These are where it landed on screen.
 *
 * Keep Large in step with [com.sodogku.libraries.ui.system.DeepSurface]'s default
 * depth. The welcome screen's primary is a DeepSurface rather than a Button, and
 * two primaries at different thicknesses on adjacent screens is the kind of thing
 * you see without being able to say what is wrong.
 */
private fun ButtonSize.pressDepth(): Dp = when (this) {
    // Shallower than Large, not deeper: the hero button is wider and taller,
    // and the same 8dp under it read as a slab rather than a lip. 6 is what
    // the 2026-09 handoff draws under every one of its buttons.
    ButtonSize.Hero -> 6.dp
    ButtonSize.Large -> 8.dp
    ButtonSize.Medium -> 7.dp
    ButtonSize.Small -> 5.dp
    ButtonSize.ExtraSmall -> 4.dp
}

/**
 * Hero takes a fixed 18dp corner where the rest of the ramp takes a quarter
 * of the height: it sits in a column with chips and a week strip drawn at the
 * same 18dp, and a percentage on the tallest thing in that column was the one
 * corner that visibly did not match.
 */
private fun ButtonSize.radius(): Radius = when (this) {
    ButtonSize.Hero -> Radii.Chip
    else -> Radii.Button
}

private fun ButtonSize.borderWidth(): Dp = when (this) {
    ButtonSize.Hero -> HeroOutlinedButtonBorderWidth
    else -> OutlinedButtonBorderWidth
}

@Composable
private fun ButtonSize.textConfig(): TextConfig = when (this) {
    ButtonSize.ExtraSmall -> ExtraSmallButtonTextConfig

    ButtonSize.Small -> SmallButtonTextConfig

    ButtonSize.Medium -> MediumButtonTextConfig

    ButtonSize.Large -> LargeButtonTextConfig

    ButtonSize.Hero -> HeroButtonTextConfig
}

internal fun ButtonSize.padding(hasIcon: Boolean): PaddingValues =
    when (this) {
        ButtonSize.ExtraSmall -> if (hasIcon) ExtraSmallButtonWithIconPadding else ExtraSmallButtonPadding
        ButtonSize.Small -> if (hasIcon) SmallButtonWithIconPadding else SmallButtonPadding
        ButtonSize.Medium -> if (hasIcon) MediumButtonWithIconPadding else MediumButtonPadding
        ButtonSize.Large -> if (hasIcon) LargeButtonWithIconPadding else LargeButtonPadding
        ButtonSize.Hero -> HeroButtonPadding
    }

// Typography scale for buttons - uses Label typography (1.2x line height)
// designed specifically for UI elements. Body typography (1.5x) is for reading,
// not buttons, so we avoid it even for text-only buttons.
//
// Size progression:
// - ExtraSmall: L400 (10sp) - Minimal UI like chips, badges
// - Small: L500 (12sp) - Compact buttons in toolbars
// - Medium: L600 (14sp) - Most common button size
// - Large: L600.SemiBold (14sp, heavier weight) - Primary CTAs
// - Hero: L750.Bold, tracked (18sp) - The one button on a full-screen moment

private val HeroButtonTextConfig: TextConfig
    @Composable get() = TextConfig(
        typography = AppTheme.typography.Label.L750.Bold.tracked(HeroLabelTracking),
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        allCaps = true
    )

private val ExtraSmallButtonTextConfig: TextConfig
    @Composable get() = TextConfig(
        typography = AppTheme.typography.Label.L400,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        allCaps = true
    )

private val SmallButtonTextConfig: TextConfig
    @Composable get() = TextConfig(
        typography = AppTheme.typography.Label.L500,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        allCaps = true
    )

private val MediumButtonTextConfig: TextConfig
    @Composable get() = TextConfig(
        typography = AppTheme.typography.Label.L600,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        allCaps = true
    )

private val LargeButtonTextConfig: TextConfig
    @Composable get() = TextConfig(
        typography = AppTheme.typography.Label.L600.SemiBold,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        allCaps = true
    )


// Button padding follows a systematic approach:
// - Vertical padding stays consistent per size for predictable touch targets
// - Horizontal padding provides breathing room for text
// - Icon variants reduce start padding slightly (icon provides visual weight)
//   and increase end padding slightly (icon takes horizontal space)
// This creates balanced, symmetric button layouts

private val ExtraSmallButtonPadding = PaddingValues(
    horizontal = Dimension.D600,  // 14dp horizontal
    vertical = Dimension.D400      // 10dp vertical
)

private val ExtraSmallButtonWithIconPadding = PaddingValues(
    horizontal = Dimension.D500,  // 12dp both sides (icon adds visual weight)
    vertical = Dimension.D400      // 10dp vertical (same as text-only)
)

private val SmallButtonPadding = PaddingValues(
    horizontal = Dimension.D800,  // 20dp horizontal
    vertical = Dimension.D500      // 12dp vertical
)

private val SmallButtonWithIconPadding = PaddingValues(
    start = Dimension.D600,       // 14dp start (reduced, icon adds weight)
    end = Dimension.D800,          // 20dp end (extra space for icon)
    top = Dimension.D500,          // 12dp vertical (same as text-only)
    bottom = Dimension.D500
)

private val MediumButtonPadding = PaddingValues(
    horizontal = Dimension.D900,  // 24dp horizontal
    vertical = Dimension.D600      // 14dp vertical
)

private val MediumButtonWithIconPadding = PaddingValues(
    start = Dimension.D700,       // 16dp start (reduced, icon adds weight)
    end = Dimension.D900,          // 24dp end (extra space for icon)
    top = Dimension.D600,          // 14dp vertical (same as text-only)
    bottom = Dimension.D600
)

private val LargeButtonPadding = PaddingValues(
    horizontal = Dimension.D900,
    vertical = Dimension.D900
)

private val LargeButtonWithIconPadding = PaddingValues(
    start = Dimension.D800,       // 20dp start (reduced, icon adds weight)
    end = Dimension.D900,          // 24dp end (extra space for icon)
    top = Dimension.D900,          // 16dp vertical (same as text-only)
    bottom = Dimension.D900
)

// One padding whether or not there is an icon: a hero button fills its row,
// so there is no start edge for an icon to crowd.
private val HeroButtonPadding = PaddingValues(
    horizontal = Dimension.D800,
    vertical = Dimension.D800
)

/** Enough to keep 18sp bold capitals from closing up; too little to read as spaced. */
private val HeroLabelTracking = 0.6.sp

private val ButtonIconSpacing = Dimension.D200
private val OutlinedButtonBorderWidth = StandardBorderWidth
private val HeroOutlinedButtonBorderWidth = Dimension.D50

@Preview
@Composable
private fun LargeButton() {
    PreviewContent {
        BasicButton(
            backgroundColor = AppTheme.colors.accentPrimary,
            borderColor = null,
            contentColor = AppTheme.colors.onAccentPrimary,
            size = ButtonSize.Large,
            onClick = {},
            content = { Text(text = "Filled Button") }
        )
    }
}

@Preview
@Composable
private fun MediumButton() {
    PreviewContent {
        BasicButton(
            backgroundColor = null,
            borderColor = AppTheme.colors.border,
            contentColor = AppTheme.colors.text,
            size = ButtonSize.Medium,
            onClick = {},
            content = { Text(text = "Outlined Button") }
        )
    }
}

@Preview
@Composable
private fun SmallButton() {
    PreviewContent {
        BasicButton(
            backgroundColor = null,
            borderColor = null,
            contentColor = AppTheme.colors.accentPrimary,
            size = ButtonSize.Small,
            onClick = {},
            content = { Text(text = "Text Button") }
        )
    }
}
