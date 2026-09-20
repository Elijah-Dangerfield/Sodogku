package com.sodogku.system.color

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sodogku.libraries.ui.system.LocalContentColor
import com.sodogku.libraries.ui.system.color.BadgeSetPalette
import com.sodogku.libraries.ui.system.color.ColorCard
import com.sodogku.libraries.ui.system.color.defaultBadgeSets
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.libraries.ui.system.color.toHexString
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import org.jetbrains.compose.ui.tooling.preview.Preview

@Immutable
@Suppress("LongParameterList")
interface Colors {

    val accentPrimary: ColorResource
    val onAccentPrimary: ColorResource
    val accentSecondary: ColorResource
    val onAccentSecondary: ColorResource

    /**
     * The amber. The app's own colour, as opposed to a role in a hierarchy.
     *
     * It had no slot, so everything that wanted it reached into
     * `status.warning` -- the welcome field, the welcome CTA, the paywall. That
     * token means "caution: expiring soon, degraded, risky action", and the one
     * thing the brand colour must never say is *careful*. Nothing was visibly
     * wrong, because Amber600 is Amber600 whichever name you ask for it by, and
     * that is exactly the problem: the day someone retunes warning to a more
     * alarming orange, the welcome screen goes with it.
     *
     * Note [onAccentBrand] is the dark ink, not white. White on this amber is
     * 1.8:1; Brown900 is 6.5:1. It is the one accent in the set that reads dark,
     * which is a property of the colour rather than a choice, and having it in
     * the palette stops each call site rediscovering it.
     */
    val accentBrand: ColorResource
    val onAccentBrand: ColorResource

    /**
     * The solid slab under a button in the accent's own colour.
     *
     * Buttons in this app are a face on a darker lip, not a face over a blur.
     * The lip used to be derived by darkening the face, which lands near but
     * never on the tone a designer picks by eye; these are the picked tones, so
     * a blue button's slab is the same blue-dark on every screen that draws
     * one. Only the two accents that carry a primary action have a slab.
     */
    val accentPrimaryDeep: ColorResource
    val accentBrandDeep: ColorResource

    /**
     * The amber as text on a light surface. The fill itself is 1.8:1 against
     * white and is not a colour anything readable can be set in; these two are
     * the same amber pulled dark enough to be, the second for text under 14sp
     * where the first is still short.
     */
    val accentBrandInk: ColorResource
    val accentBrandInkSmall: ColorResource

    /** A pale amber wash for decoration behind a hero: the rays on a clear. */
    val accentBrandSoft: ColorResource

    /** The gold of a bone. Deliberately not the amber, so a bone and a paw stay two things. */
    val bone: ColorResource

    /* Backgrounds */
    val shadow: ColorResource
    val background: ColorResource
    val backgroundOverlay: ColorResource
    val onBackground: ColorResource
    val border: ColorResource

    val borderSecondary: ColorResource
    val borderDisabled: ColorResource

    /**
     * A border that has to read as an edge on its own, with nothing filled
     * inside it: the ghost button under a primary. [border] is a step off the
     * cream it usually sits on and is meant to be found rather than seen.
     */
    val borderStrong: ColorResource

    /** The 2dp line that fills the gap in a section header. */
    val rule: ColorResource

    /**
     * The soft stroke drawn just inside a card's edge: the achievements cards,
     * where the design uses an inset box-shadow rather than a border. Paler
     * than [borderStrong] and warmer than [border], because it sits on an
     * ivory card over the cream page and has to read as the card's own edge
     * rather than as a line drawn round it.
     */
    val cardInset: ColorResource

    /**
     * The disc behind an earned badge, on the grid, the spotlight and the
     * unlock toast alike. A pale amber rather than the set's own colour: the
     * handoff board draws every earned disc this one shade so that "earned"
     * reads the same across nine shelves, and the set colour is left to say
     * which shelf a locked badge is climbing.
     */
    val badgeEarnedDisc: ColorResource

    /* Texts */
    val text: ColorResource
    val textSecondary: ColorResource

    /** Below [textSecondary]: counts, fractions, the line under a badge name. */
    val textMuted: ColorResource
    val textDisabled: ColorResource
    val danger: ColorResource

    /**
     * The stroke behind a display numeral, so the biggest number on a screen
     * keeps its edge over whatever it lands on. A cream rather than white,
     * because the surfaces it sits on are cream and a white stroke reads as a
     * halo.
     */
    val textOutline: ColorResource

    /**
     * A number that has ended: the run on the lost-streak screen, drawn at
     * display size in the same outline the live number gets. Not
     * [textDisabled], which is a step too pale to hold 84sp on the cream, and
     * not [textMuted], which is body ink and would make the ended run read as
     * a caption rather than as the number it used to be.
     */
    val textEnded: ColorResource

    /**
     * Text on a coloured band: the kicker and the status bar on the streak
     * screens. Cream rather than white for the same reason as [textOutline].
     */
    val onBand: ColorResource

    /** The band behind a lost streak. A brown, the one thing in the app that is not amber or blue. */
    val bandLoss: ColorResource

    /**
     * The paw watermark across an amber band. The blue and brown bands wash
     * [onAccentPrimary] over themselves; on the amber a white paw disappears,
     * so it is drawn in a dark amber instead.
     */
    val watermarkOnBrand: ColorResource

    /** The drop of sweat on the lost dog. */
    val sweatDrop: ColorResource

    val status: StatusColor

    /* Surfaces */
    val surfacePrimary: ColorResource
    val onSurfacePrimary: ColorResource
    val surfaceSecondary: ColorResource
    val onSurfaceSecondary: ColorResource
    val surfaceTertiary: ColorResource
    val onSurfaceTertiary: ColorResource

    val surfaceDisabled: ColorResource
    val onSurfaceDisabled: ColorResource

    /**
     * A thing that is not yet: an empty day on the week strip, the disc behind
     * a locked badge. Sits on [surfaceSecondary] and has to be found there,
     * which [surfaceDisabled] is one step too light for.
     */
    val surfaceMuted: ColorResource

    /** The unfilled length of a progress bar. */
    val track: ColorResource

    /** A missed day on the week strip: the fill, and the cross drawn on it. */
    val missFill: ColorResource
    val missMark: ColorResource

    /**
     * The paw a clear did not earn, drawn as a stroke beside the ones it did.
     * A step past [borderSecondary], because the two sit at the same weight on
     * the same cream and one has to read as an outline where the other reads
     * as an edge.
     */
    val pawUnearned: ColorResource

    /**
     * The colour each set of badges is drawn in. Keyed by the set rather than
     * flattened into nine times three tokens here, because nothing asks for
     * "the campaign disc" on its own: a card asks for its set and draws the
     * three parts together. See [BadgeSetPalette].
     */
    val badgeSets: BadgeSetPalette
}

interface StatusColor {
    val okay: ColorResource
    val warning: ColorResource
    val bad: ColorResource

    /** [okay] as text on a light surface, where the fill is a shade too pale to read. */
    val okayInk: ColorResource
}

/**
 * Warm, not neutral.
 *
 * The page is a cream and every text colour is a warm brown. The neutral scale
 * the template shipped with is still there and still correct — it is simply not
 * what a bubbly puzzle game is made of. A cold grey page and pure black type
 * read as a tool; the same layout on cream with brown type reads as a toy,
 * before a single component changes.
 *
 * Contrast was checked rather than eyeballed. [text] on [background] is 11.8:1
 * and [textSecondary] on it is 6.0:1, both comfortably past WCAG AA for body
 * text, which is what makes the warmth affordable — a cream that had to be
 * paired with near-black to stay legible would not have been worth having.
 * [textMuted] is 4.1:1, which is why it is for counts and captions and not for
 * anything a player has to read.
 *
 * Retuned 2026-09-20 to the streak-and-rewards handoff: every value here moved
 * a shade warmer and deeper, and the whole app followed, because a palette
 * scoped to four screens is two palettes.
 */
val defaultColors = object : Colors {
    // Blue as primary accent - like a clear sky
    override val accentPrimary = ColorResource.Blue600
    override val onAccentPrimary = ColorResource.White
    // Purple as secondary - adds a touch of creativity and calm
    override val accentSecondary = ColorResource.Purple600
    override val onAccentSecondary = ColorResource.White
    // The amber the app is actually made of. Same value as status.warning and
    // not the same idea: see the doc on the interface.
    override val accentBrand = ColorResource.Amber600
    override val onAccentBrand = ColorResource.Brown900
    override val accentPrimaryDeep = ColorResource.Blue700
    override val accentBrandDeep = ColorResource.Amber700
    override val accentBrandInk = ColorResource.Amber800
    override val accentBrandInkSmall = ColorResource.Amber900
    override val accentBrandSoft = ColorResource.Amber200
    override val bone = ColorResource.Gold500

    override val shadow = ColorResource.Black_A30
    override val textDisabled = ColorResource.Brown300
    override val danger = ColorResource.Red600
    // Cards and pills are an off-white so they lift off the cream without
    // punching a hole in it. Everything below primary is a tint of the page
    // rather than a grey, or the ladder goes cold one step down from the
    // surface the player is actually looking at.
    override val surfacePrimary = ColorResource.Ivory
    override val surfaceDisabled = ColorResource.Cream200
    override val onSurfacePrimary = ColorResource.Brown900
    override val surfaceSecondary = ColorResource.Cream100
    override val onSurfaceSecondary = ColorResource.Brown900
    override val surfaceTertiary = ColorResource.Cream200
    override val onSurfaceTertiary = ColorResource.Brown700
    override val onSurfaceDisabled = ColorResource.Brown300
    override val surfaceMuted = ColorResource.Cream250
    override val track = ColorResource.Cream200
    override val missFill = ColorResource.Rose100
    override val missMark = ColorResource.Rose400
    override val pawUnearned = ColorResource.Cream350
    override val background = ColorResource.Cream50
    override val onBackground = ColorResource.Brown900
    override val border = ColorResource.Cream200
    override val borderSecondary = ColorResource.Cream300
    override val borderDisabled = ColorResource.Cream100
    override val borderStrong = ColorResource.Cream275
    override val rule = ColorResource.Cream225
    override val cardInset = ColorResource.Cream75
    override val badgeEarnedDisc = ColorResource.Amber100
    override val text = ColorResource.Brown900
    override val backgroundOverlay = ColorResource.Black_A70
    override val textSecondary = ColorResource.Brown700
    override val textMuted = ColorResource.Brown500
    override val textOutline = ColorResource.Cream10
    override val textEnded = ColorResource.Brown400
    override val onBand = ColorResource.Cream20
    override val bandLoss = ColorResource.Brown600
    override val watermarkOnBrand = ColorResource.Amber950
    override val sweatDrop = ColorResource.Sky300
    override val badgeSets = defaultBadgeSets

    override val status = object : StatusColor {
        override val okay = ColorResource.Green600
        override val warning = ColorResource.Amber600
        override val bad = ColorResource.Red600
        override val okayInk = ColorResource.Green800
    }
}

@Composable
private fun SectionTitle(text: String, colors: Colors) {
    Text(
        text = text,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.textSecondary.color,
        modifier = Modifier.padding(bottom = Dimension.D400)
    )
}

@Composable
private fun HeroPanel(colors: Colors) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radii.Card.shape)
            .background(colors.surfacePrimary.color)
            .border(1.dp, colors.border.color, Radii.Card.shape)
            .padding(Dimension.D700)
    ) {
        Text(
            text = "Color palette",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onSurfacePrimary.color
        )
        Text(
            text = "Modern light theme",
            fontSize = 14.sp,
            color = colors.textSecondary.color,
            modifier = Modifier.padding(top = Dimension.D200)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimension.D600),
            horizontalArrangement = Arrangement.spacedBy(Dimension.D500)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(Radii.Card.shape)
                    .background(colors.surfaceSecondary.color)
                    .padding(Dimension.D500)
            ) {
                Text(
                    text = "Active session",
                    fontSize = 14.sp,
                    color = colors.onSurfaceSecondary.color
                )
                Text(
                    text = "42m remaining",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceSecondary.color,
                    modifier = Modifier.padding(top = Dimension.D200)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(Radii.Card.shape)
                    .background(colors.backgroundOverlay.color)
                    .padding(Dimension.D500)
            ) {
                Text(
                    text = "Status",
                    fontSize = 14.sp,
                    color = colors.onBackground.color
                )
                Text(
                    text = "All good",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accentSecondary.color,
                    modifier = Modifier.padding(top = Dimension.D200)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimension.D600),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Recent activity",
                fontSize = 14.sp,
                color = colors.textSecondary.color
            )
            Box(
                modifier = Modifier
                    .clip(Radii.Button.shape)
                    .background(colors.accentPrimary.color)
                    .padding(horizontal = Dimension.D800, vertical = Dimension.D400)
            ) {
                Text(
                    text = "View all",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onAccentPrimary.color
                )
            }
        }
    }
}

@Composable
private fun AccentPalette(colors: Colors) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        SectionTitle("Accent stack", colors)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimension.D500)
        ) {
            AccentChip(
                label = "Primary",
                background = colors.accentPrimary,
                foreground = colors.onAccentPrimary,
                supporting = colors.accentPrimary.toHexString()
            )
            AccentChip(
                label = "Secondary",
                background = colors.accentSecondary,
                foreground = colors.onAccentSecondary,
                supporting = colors.accentSecondary.toHexString()
            )
        }
    }
}

@Composable
private fun RowScope.AccentChip(
    label: String,
    background: ColorResource,
    foreground: ColorResource,
    supporting: String
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(Radii.Card.shape)
            .background(background.color.copy(alpha = 0.15f))
            .border(1.dp, background.color, Radii.Card.shape)
            .padding(Dimension.D500)
    ) {
        Box(
            modifier = Modifier
                .clip(Radii.Button.shape)
                .background(background.color)
                .padding(horizontal = Dimension.D800, vertical = Dimension.D400)
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = foreground.color
            )
        }
        Text(
            text = background.designSystemName,
            fontSize = 12.sp,
            color = background.color,
            modifier = Modifier.padding(top = Dimension.D300)
        )
        Text(
            text = supporting,
            fontSize = 10.sp,
            color = foreground.color.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun SurfaceStack(colors: Colors) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        SectionTitle("Surface ladder", colors)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimension.D500)
        ) {
            SurfaceCard(
                title = "Primary",
                background = colors.surfacePrimary,
                foreground = colors.onSurfacePrimary,
                border = colors.border,
                supporting = colors.surfacePrimary.toHexString()
            )
            SurfaceCard(
                title = "Secondary",
                background = colors.surfaceSecondary,
                foreground = colors.onSurfaceSecondary,
                border = colors.border,
                supporting = colors.surfaceSecondary.toHexString()
            )
            SurfaceCard(
                title = "Tertiary",
                background = colors.surfaceTertiary,
                foreground = colors.onSurfaceTertiary,
                border = colors.border,
                supporting = colors.surfaceTertiary.toHexString()
            )

            SurfaceCard(
                title = "Disabled",
                background = colors.surfaceDisabled,
                foreground = colors.onSurfaceDisabled,
                border = colors.border,
                supporting = colors.surfaceTertiary.toHexString()
            )
        }
    }
}

@Composable
private fun RowScope.SurfaceCard(
    title: String,
    background: ColorResource,
    foreground: ColorResource,
    border: ColorResource,
    supporting: String
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(Radii.Card.shape)
            .background(background.color)
            .border(1.dp, border.color, Radii.Card.shape)
            .padding(Dimension.D500)
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            color = foreground.color.copy(alpha = 0.9f)
        )
        Text(
            text = "Card content",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = foreground.color,
            modifier = Modifier.padding(top = Dimension.D200)
        )
        Text(
            text = supporting,
            fontSize = 10.sp,
            color = foreground.color.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = Dimension.D300)
        )
    }
}

@Composable
private fun TextHierarchy(colors: Colors) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        SectionTitle("Typography contrast", colors)
        Column(
            modifier = Modifier
                .clip(Radii.Card.shape)
                .background(colors.background.color)
                .border(1.dp, colors.border.color, Radii.Card.shape)
                .padding(Dimension.D600),
            verticalArrangement = Arrangement.spacedBy(Dimension.D500)
        ) {
            TextSample("Primary", colors.text, colors.text)
            TextSample("Secondary", colors.textSecondary, colors.textSecondary)
            TextSample("Disabled", colors.textDisabled, colors.textDisabled)
            TextSample("Danger", colors.danger, colors.danger)
        }
    }
}

@Composable
private fun TextSample(label: String, swatch: ColorResource, hexColor: ColorResource) {
    Column {
        Text(
            text = label,
            fontSize = 14.sp,
            color = swatch.color
        )
        Text(
            text = hexColor.toHexString(),
            fontSize = 11.sp,
            color = swatch.color.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun SemanticStrip(colors: Colors) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        SectionTitle("System states", colors)
        Row(
            modifier = Modifier
                .clip(Radii.Card.shape)
                .border(1.dp, colors.border.color, Radii.Card.shape)
                .background(colors.surfaceSecondary.color)
                .padding(Dimension.D400),
            horizontalArrangement = Arrangement.spacedBy(Dimension.D400)
        ) {
            SemanticBadge("Background", colors.background, colors.onBackground)
            SemanticBadge("Overlay", colors.backgroundOverlay, colors.onBackground)
            SemanticBadge("Shadow", colors.shadow, colors.onBackground)
            SemanticBadge("Danger", colors.danger, colors.onAccentSecondary)
        }
    }
}

@Composable
private fun RowScope.SemanticBadge(
    label: String,
    background: ColorResource,
    content: ColorResource
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(Radii.Card.shape)
            .background(background.color)
            .padding(Dimension.D400)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = content.color.copy(alpha = 0.8f)
        )
        Text(
            text = background.designSystemName,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = content.color,
            modifier = Modifier.padding(top = Dimension.D200)
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PaletteGridSection(colors: Colors) {
    val palette = listOf(
        colors.background,
        colors.backgroundOverlay,
        colors.onBackground,
        colors.surfacePrimary,
        colors.onSurfacePrimary,
        colors.surfaceSecondary,
        colors.onSurfaceSecondary,
        colors.surfaceTertiary,
        colors.onSurfaceTertiary,
        colors.surfaceDisabled,
        colors.onSurfaceDisabled,
        colors.accentPrimary,
        colors.onAccentPrimary,
        colors.accentSecondary,
        colors.onAccentSecondary,
        colors.accentBrand,
        colors.accentBrandDeep,
        colors.accentBrandInk,
        colors.accentBrandInkSmall,
        colors.accentBrandSoft,
        colors.accentPrimaryDeep,
        colors.bone,
        colors.text,
        colors.textSecondary,
        colors.textMuted,
        colors.textDisabled,
        colors.textOutline,
        colors.textEnded,
        colors.onBand,
        colors.bandLoss,
        colors.watermarkOnBrand,
        colors.sweatDrop,
        colors.danger,
        colors.border,
        colors.borderStrong,
        colors.borderDisabled,
        colors.rule,
        colors.cardInset,
        colors.badgeEarnedDisc,
        colors.track,
        colors.surfaceMuted,
        colors.missFill,
        colors.missMark,
        colors.pawUnearned,
        colors.shadow
    ).distinctBy { it.designSystemName }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        SectionTitle("Palette grid", colors)
        Box(
            modifier = Modifier
                .clip(Radii.Card.shape)
                .background(colors.surfaceSecondary.color)
                .border(1.dp, colors.border.color, Radii.Card.shape)
                .padding(Dimension.D300)
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimension.D300),
                verticalArrangement = Arrangement.spacedBy(Dimension.D300)
            ) {
                palette.forEach { swatch ->
                    ColorCard(
                        colorResource = swatch,
                        title = swatch.designSystemName,
                        description = swatch.toHexString()
                    )
                }
            }
        }
    }
}

@Composable
fun PreviewColorSwatch(colors: Colors) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background.color)
            .padding(horizontal = Dimension.D800, vertical = Dimension.D600),
        verticalArrangement = Arrangement.spacedBy(Dimension.D700)
    ) {
        item { HeroPanel(colors) }
        item { AccentPalette(colors) }
        item { SurfaceStack(colors) }
        item { TextHierarchy(colors) }
        item { SemanticStrip(colors) }
        item { PaletteGridSection(colors) }
    }
}

@Preview(widthDp = 600, heightDp = 2000)
@Composable
private fun PreviewDefaultColors() {
    PreviewColorSwatch(defaultColors)
}

@Composable
fun ProvideContentColor(color: ColorResource, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalContentColor provides color,
        androidx.compose.material3.LocalContentColor provides color.color,
        content = content
    )
}