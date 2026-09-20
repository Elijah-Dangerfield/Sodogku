/**
 * # Button Component System
 *
 * A button exposes ONE emphasis semantic — [ButtonType] — plus a [ButtonStyle] (Filled / Outlined
 * / Text) and a [ButtonSize]. Opting into `deep` renders a hard 3D "lip" under a filled, enabled
 * button that the face drops onto when pressed; the default treatment stays flat.
 *
 * ## Emphasis hierarchy (most → least prominent)
 *
 * | Type | Use case | Example |
 * |------|----------|---------|
 * | **Primary**   | The main CTA — accentPrimary by default | "Continue", "Save", "Sign In" |
 * | **Secondary** | Important but not the CTA — neutral fill / border | "Cancel", "Skip" |
 * | **Ghost**     | Minimal weight, inline links | "Forgot Password?", "Terms" |
 * | **Danger**    | Destructive action | "Delete", "Sign out" |
 *
 * Limit Primary to 1–2 per screen. Use Filled > Outlined > Text for decreasing emphasis.
 *
 * ## Accent (the rare two-CTA case)
 *
 * A *filled Primary* can be recolored with [ButtonAccent] (Primary = accentPrimary, Secondary =
 * accentSecondary). Accent is **role-named, never a literal color** — repointing an accent token
 * never touches a button. Reach for it only when a screen genuinely needs two primary-level
 * actions in different brand colors.
 *
 * ```kotlin
 * ButtonPrimary(onClick = { }) { Text("Continue") }
 * ButtonSecondary(onClick = { }) { Text("Cancel") }
 * ButtonGhost(onClick = { }) { Text("Forgot Password?") }
 * ButtonDanger(onClick = { }) { Text("Delete") }
 *
 * // two distinct primary-level CTAs
 * ButtonPrimary(onClick = { }, accent = ButtonAccent.Secondary) { Text("Upgrade") }
 *
 * // opt into the springy 3D lip
 * ButtonPrimary(onClick = { }) { Text("Continue") }   // deep by default
 *
 * // full control
 * Button(type = ButtonType.Primary, style = ButtonStyle.Outlined, size = ButtonSize.Small, onClick = { }) {
 *     Text("Continue")
 * }
 * ```
 */
package com.sodogku.libraries.ui.components.button

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.libraries.ui.system.DeepSurface
import com.sodogku.libraries.ui.system.color.animateColorResourceAsState
import com.sodogku.libraries.ui.components.icon.IconResource
import com.sodogku.system.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.sodogku.libraries.ui.catalog.BUTTON_SUBTITLE
import com.sodogku.libraries.ui.catalog.ButtonCatalogBody
import com.sodogku.libraries.ui.catalog.CatalogPage

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: IconResource? = null,
    type: ButtonType = LocalButtonType.current,
    accent: ButtonAccent = ButtonAccent.Primary,
    size: ButtonSize = LocalButtonSize.current,
    style: ButtonStyle = LocalButtonStyle.current,
    enabled: Boolean = true,
    deep: Boolean = true,
    onDisabledTap: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val backgroundColor = backgroundColor(type, accent, style, enabled)
        ?.let { targetColor ->
            key(type, accent, style) {
                animateColorResourceAsState(
                    targetValue = targetColor,
                    label = "Background_Color_Anim"
                )
            }.value
        }

    val contentColor by key(type, accent, style) {
        animateColorResourceAsState(
            targetValue = contentColor(type, accent, style, enabled),
            label = "Content_Color_Anim"
        )
    }

    val borderColor = borderColor(type, accent, style, enabled)
    val deepColor = deepColor(type, accent, style, enabled, deep)

    BasicButton(
        backgroundColor = backgroundColor,
        borderColor = borderColor,
        contentColor = contentColor,
        deepColor = deepColor,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        size = size,
        enabled = enabled,
        onDisabledTap = onDisabledTap,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * Emphasis hierarchy — the only semantic a button exposes (most → least prominent):
 * Primary > Secondary > Ghost. Danger is destructive emphasis, orthogonal to the ladder.
 *
 * - **Primary** — the main CTA. accentPrimary filled by default; recolor with [ButtonAccent].
 *   1–2 per screen.
 * - **Secondary** — important but not the CTA. Neutral fill (filled) or border (outlined).
 * - **Ghost** — minimal weight, text-only. Inline links, supplementary actions.
 * - **Danger** — destructive action (danger token).
 */
enum class ButtonType {
    /** The main CTA — accentPrimary filled by default. */
    Primary,

    /** Important but not the CTA — neutral fill / border. */
    Secondary,

    /** Text-only button — minimal visual weight. */
    Ghost,

    /** Destructive action. */
    Danger,
}

/**
 * Which accent a *filled Primary* renders. Role-named, never a literal color, so repointing an
 * accent token never touches a button.
 *
 * [Brand] exists because its absence sent a call site out of the button system
 * altogether. The welcome screen's CTA is the app's amber, there was no accent
 * that meant the amber, so it was hand-built out of [DeepSurface] -- the
 * primitive `BasicButton` uses internally. It looked right and was wrong: it
 * missed the disabled ladder, the press spring's tuning, the size scale and the
 * `Role.Button` semantics, and every later change to buttons skipped it. That is
 * the tell for a missing enum case, not for a component that needs escaping.
 *
 * Reach for [DeepSurface] only when the thing genuinely is not a button.
 */
enum class ButtonAccent {
    /** The main CTA. Blue. */
    Primary,

    /** Blue's counterpart. Purple; also what "an ad is involved" wears. */
    Secondary,

    /** The app's own amber, with dark ink on it rather than white. */
    Brand,
}

enum class ButtonSize {
    /**
     * The one button on a full-screen moment: the streak screens, board
     * cleared, the achievements page. Bigger than [Large] in every dimension
     * the 2026-09 handoff names — an 18dp corner, 20dp of vertical padding, a
     * 6dp slab, an 18sp tracked label — and a 2dp border when outlined, so the
     * ghost action under it reads as an edge on its own. Opt in per screen;
     * nothing defaults to it.
     */
    Hero,
    Large,
    Medium,
    Small,
    ExtraSmall
}

/**
 * The visual treatment, independent of [ButtonType] emphasis.
 *
 * - **Filled** — solid background; highest prominence. Filled + enabled + `deep` gets the 3D lip.
 * - **Outlined** — border only, transparent fill; medium prominence, works on any surface.
 * - **Text** — no background or border; minimal weight, reads as a link.
 */
enum class ButtonStyle {
    Filled,
    Outlined,
    Text,
}

@Composable
fun ProvideButtonConfig(
    type: ButtonType = LocalButtonType.current,
    size: ButtonSize = LocalButtonSize.current,
    style: ButtonStyle = LocalButtonStyle.current,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalButtonType provides type,
        LocalButtonSize provides size,
        LocalButtonStyle provides style,
        content = content
    )
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Convenience Functions - For Better Code Readability
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Primary button - Main call-to-action.
 *
 * Use for the most important action on a screen (e.g., "Continue", "Save", "Submit").
 * Limit to 1-2 per screen for maximum impact.
 *
 * @see Button for full documentation
 */
@Composable
fun ButtonPrimary(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: IconResource? = null,
    accent: ButtonAccent = ButtonAccent.Primary,
    size: ButtonSize = LocalButtonSize.current,
    style: ButtonStyle = ButtonStyle.Filled,
    onDisabledTap: (() -> Unit)? = null,
    enabled: Boolean = true,
    deep: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        type = ButtonType.Primary,
        accent = accent,
        size = size,
        onDisabledTap = onDisabledTap,
        style = style,
        enabled = enabled,
        deep = deep,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * Secondary button - important but not primary.
 *
 * Use for important actions that aren't the main CTA (e.g., "Cancel", "Skip", "Back").
 * Multiple allowed per screen.
 *
 * @see Button for full documentation
 */
@Composable
fun ButtonSecondary(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: IconResource? = null,
    size: ButtonSize = LocalButtonSize.current,
    style: ButtonStyle = ButtonStyle.Outlined,
    onDisabledTap: (() -> Unit)? = null,
    enabled: Boolean = true,
    deep: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        type = ButtonType.Secondary,
        size = size,
        onDisabledTap = onDisabledTap,
        style = style,
        enabled = enabled,
        deep = deep,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * Ghost button - Text-only with minimal visual weight.
 *
 * Use for supplementary actions and inline links (e.g., "Forgot Password?", "Terms").
 *
 * @see Button for full documentation
 */
@Composable
fun ButtonGhost(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: IconResource? = null,
    size: ButtonSize = LocalButtonSize.current,
    style: ButtonStyle = ButtonStyle.Text,
    enabled: Boolean = true,
    deep: Boolean = true,
    onDisabledTap: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        type = ButtonType.Ghost,
        size = size,
        onDisabledTap = onDisabledTap,
        style = style,
        enabled = enabled,
        deep = deep,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * Danger button
 * @see Button for full documentation
 */
@Composable
fun ButtonDanger(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: IconResource? = null,
    size: ButtonSize = LocalButtonSize.current,
    style: ButtonStyle = ButtonStyle.Filled,
    onDisabledTap: (() -> Unit)? = null,
    enabled: Boolean = true,
    deep: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        type = ButtonType.Danger,
        onDisabledTap = onDisabledTap,
        size = size,
        style = style,
        enabled = enabled,
        deep = deep,
        interactionSource = interactionSource,
        content = content
    )
}

private val LocalButtonType =
    compositionLocalOf { ButtonType.Primary }
internal val LocalButtonSize =
    compositionLocalOf { ButtonSize.Large }
private val LocalButtonStyle =
    compositionLocalOf { ButtonStyle.Filled }

// ── accent token resolvers ───────────────────────────────────
@Composable
@ReadOnlyComposable
private fun accentSolid(a: ButtonAccent) = when (a) {
    ButtonAccent.Primary -> AppTheme.colors.accentPrimary
    ButtonAccent.Secondary -> AppTheme.colors.accentSecondary
    ButtonAccent.Brand -> AppTheme.colors.accentBrand
}

@Composable
@ReadOnlyComposable
private fun onAccent(a: ButtonAccent) = when (a) {
    ButtonAccent.Primary -> AppTheme.colors.onAccentPrimary
    ButtonAccent.Secondary -> AppTheme.colors.onAccentSecondary
    ButtonAccent.Brand -> AppTheme.colors.onAccentBrand
}

/**
 * The hard band under a `deep` filled button, for the accents that have no
 * slab token of their own. The two that carry a primary action read theirs
 * from the palette (see [deepSolid]); purple and the danger red are darkened
 * from the face so a repoint of either still lands on a plausible lip.
 */
private const val DeepDarkenFraction = 0.3f

private fun ColorResource.deepened(): ColorResource = ColorResource.FromColor(
    color = lerp(color, Color.Black, DeepDarkenFraction),
    name = "$designSystemName-deep",
)

/**
 * The slab under a filled Primary, by accent. Blue and amber have a picked
 * tone in the palette; the derived one landed near it and never on it, and a
 * button whose lip is a shade off the design is the kind of thing a designer
 * sees and cannot name.
 */
@Composable
@ReadOnlyComposable
private fun deepSolid(a: ButtonAccent) = when (a) {
    ButtonAccent.Primary -> AppTheme.colors.accentPrimaryDeep
    ButtonAccent.Secondary -> AppTheme.colors.accentSecondary.deepened()
    ButtonAccent.Brand -> AppTheme.colors.accentBrandDeep
}

@Composable
@ReadOnlyComposable
private fun backgroundColor(
    type: ButtonType,
    accent: ButtonAccent,
    style: ButtonStyle,
    enabled: Boolean,
): ColorResource? = when {
    !enabled && style == ButtonStyle.Filled -> AppTheme.colors.surfaceDisabled
    style != ButtonStyle.Filled -> null
    else -> when (type) {
        ButtonType.Primary -> accentSolid(accent)
        ButtonType.Secondary -> AppTheme.colors.surfacePrimary
        ButtonType.Ghost -> null
        ButtonType.Danger -> AppTheme.colors.danger
    }
}

@Composable
@ReadOnlyComposable
/**
 * The band behind a filled button's face, which the face drops onto when
 * pressed.
 *
 * On by default. In a game every CTA should look like something you could press
 * rather than a rectangle of colour, and a per-call-site opt-in meant every
 * screen shipped flat until someone remembered — which is what happened: the
 * mechanism was here from C3a and nothing in the app used it.
 *
 * Ghost buttons still get nothing. A lip under a borderless text button is a
 * shadow under a link.
 */
private fun deepColor(
    type: ButtonType,
    accent: ButtonAccent,
    style: ButtonStyle,
    enabled: Boolean,
    deep: Boolean,
): ColorResource? = when {
    // only filled, enabled buttons that opt in get the lip
    !deep || !enabled || style != ButtonStyle.Filled -> null
    else -> when (type) {
        ButtonType.Primary -> deepSolid(accent)
        ButtonType.Secondary -> AppTheme.colors.border
        ButtonType.Ghost -> null
        ButtonType.Danger -> AppTheme.colors.danger.deepened()
    }
}

@Composable
@ReadOnlyComposable
private fun borderColor(
    type: ButtonType,
    accent: ButtonAccent,
    style: ButtonStyle,
    enabled: Boolean
): ColorResource? = when {
    style != ButtonStyle.Outlined -> null
    !enabled -> AppTheme.colors.borderDisabled
    else -> when (type) {
        ButtonType.Primary -> accentSolid(accent)
        // `borderStrong`, not `border`: an outlined button has nothing filled
        // inside its edge, and the cream `border` is tuned to be found on a
        // card rather than to hold a shape on its own.
        ButtonType.Secondary -> AppTheme.colors.borderStrong
        ButtonType.Ghost -> null
        ButtonType.Danger -> AppTheme.colors.danger
    }
}

@Composable
@ReadOnlyComposable
private fun contentColor(
    type: ButtonType,
    accent: ButtonAccent,
    style: ButtonStyle,
    enabled: Boolean
): ColorResource = when {
    !enabled -> AppTheme.colors.textDisabled
    style == ButtonStyle.Filled -> when (type) {
        ButtonType.Primary -> onAccent(accent)
        ButtonType.Secondary -> AppTheme.colors.onSurfacePrimary
        ButtonType.Ghost -> AppTheme.colors.text
        ButtonType.Danger -> AppTheme.colors.danger.onColor
    }
    style == ButtonStyle.Outlined -> when (type) {
        ButtonType.Primary -> accentSolid(accent)
        ButtonType.Secondary -> AppTheme.colors.text
        ButtonType.Ghost -> AppTheme.colors.text
        ButtonType.Danger -> AppTheme.colors.danger
    }
    else -> when (type) { // Text
        ButtonType.Danger -> AppTheme.colors.danger
        ButtonType.Secondary -> AppTheme.colors.textSecondary
        else -> AppTheme.colors.text
    }
}

@Preview(widthDp = 1100, heightDp = 1700)
@Composable
private fun ButtonsPreview() {
    CatalogPage(title = "Buttons", subtitle = BUTTON_SUBTITLE) { ButtonCatalogBody() }
}
