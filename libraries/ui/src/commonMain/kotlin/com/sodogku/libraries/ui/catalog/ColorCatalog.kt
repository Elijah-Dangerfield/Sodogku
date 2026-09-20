package com.sodogku.libraries.ui.catalog

import androidx.compose.runtime.Composable
import com.sodogku.system.AppTheme

/*
 * Color catalog content. Broken into granular section-group composables so the (large) color
 * area can be spread across columns and split across multiple previews — a single combined preview
 * of all of color is past the IDE's max render size. The previews themselves live in
 * DesignSystemPreview.kt (ColorPrimaryPreview / ColorSupportPreview).
 */

/** Surfaces + the text ramp. */
@Composable
internal fun ColorSurfacesContent() {
    val c = AppTheme.colors
    CatalogSection(
        "Surfaces",
        "The neutral elevation ladder. Each step sits visually 'on top of' the one before it.",
    ) {
        SwatchFlow {
            ColorRow("background", c.background, c.onBackground, "App canvas, scaffolds, and the base of full-screen sheets.")
            ColorRow("surfacePrimary", c.surfacePrimary, c.onSurfacePrimary, "The default container — Card, sheets, menus, list rows.")
            ColorRow("surfaceSecondary", c.surfaceSecondary, c.onSurfaceSecondary, "A thing ON a surface — text inputs, nested containers, selected rows.")
            ColorRow("surfaceTertiary", c.surfaceTertiary, c.onSurfaceTertiary, "The highest layer — pressed states, floating menus, tooltips.")
            ColorRow("surfaceDisabled", c.surfaceDisabled, c.onSurfaceDisabled, "Fill for a disabled control (e.g. a disabled filled button).")
            ColorRow("surfaceMuted", c.surfaceMuted, null, "A thing not yet: an empty day, the disc behind a locked badge.")
            ColorRow("track", c.track, null, "The unfilled length of a progress bar.")
            ColorRow("bandLoss", c.bandLoss, c.onBand, "The band behind a lost streak.")
            ColorRow("watermarkOnBrand", c.watermarkOnBrand, null, "The paw watermark across an amber band.")
            ColorRow("sweatDrop", c.sweatDrop, null, "The drop of sweat on the lost dog.")
            ColorRow("backgroundOverlay", c.backgroundOverlay, null, "Dims the screen behind a modal or bottom sheet.")
            ColorRow("shadow", c.shadow, null, "Drop-shadow color cast by elevated surfaces.")
        }
    }

    CatalogSection(
        "Text",
        "One foreground ramp that works on the background and every surface. Step down for less emphasis.",
    ) {
        SwatchFlow {
            ColorRow("text", c.text, c.background, "Primary text and active icons.")
            ColorRow("textSecondary", c.textSecondary, c.background, "Supporting text, captions, inactive icons, metadata.")
            ColorRow("textMuted", c.textMuted, c.background, "Counts, fractions, the line under a badge name. 4.1:1, so never body copy.")
            ColorRow("textDisabled", c.textDisabled, c.background, "Disabled text and icons.")
            ColorRow("textOutline", c.textOutline, c.accentBrand, "The stroke behind a display numeral.")
            ColorRow("textEnded", c.textEnded, c.background, "A number that has ended: the run on the lost-streak screen.")
            ColorRow("onBand", c.onBand, c.accentPrimary, "Kicker and status text on a coloured band.")
        }
    }
}

/** The two accent pairs. */
@Composable
internal fun ColorAccents() {
    val c = AppTheme.colors
    CatalogSection(
        "Accent · Primary",
        "The brand. The main CTA, focus rings, selected states.",
    ) {
        SwatchFlow {
            ColorRow("accentPrimary", c.accentPrimary, c.onAccentPrimary, "Primary buttons, focus, selection — the one thing you want tapped.")
            ColorRow("onAccentPrimary", c.onAccentPrimary, c.accentPrimary, "Text and icons rendered on an accentPrimary fill.")
        }
    }

    CatalogSection(
        "Accent · Secondary",
        "The second brand accent — only for the rare screen with two primary-level CTAs in different colors.",
    ) {
        SwatchFlow {
            ColorRow("accentSecondary", c.accentSecondary, c.onAccentSecondary, "A second, distinct primary-level action (e.g. 'Upgrade' beside 'Continue').")
            ColorRow("onAccentSecondary", c.onAccentSecondary, c.accentSecondary, "Text and icons on an accentSecondary fill.")
            ColorRow("accentBrand", c.accentBrand, c.onAccentBrand, "The app's own amber. Not status.warning, which happens to be the same value and means 'careful'.")
            ColorRow("onAccentBrand", c.onAccentBrand, c.accentBrand, "Dark ink, not white — white on this amber is 1.8:1.")
        }
    }

    CatalogSection(
        "Accent · slabs and inks",
        "The two primary accents as the solid lip under a button, and the amber as something readable on a light surface.",
    ) {
        SwatchFlow {
            ColorRow("accentPrimaryDeep", c.accentPrimaryDeep, c.onAccentPrimary, "The slab under a blue button.")
            ColorRow("accentBrandDeep", c.accentBrandDeep, c.onAccentBrand, "The slab under an amber button.")
            ColorRow("accentBrandInk", c.accentBrandInk, c.surfacePrimary, "Amber as text on a light surface, 14sp and up.")
            ColorRow("accentBrandInkSmall", c.accentBrandInkSmall, c.surfacePrimary, "Amber as text under 14sp.")
            ColorRow("accentBrandSoft", c.accentBrandSoft, c.text, "A pale wash for decoration behind a hero: the rays on a clear.")
            ColorRow("bone", c.bone, c.text, "The gold of a bone. Not the amber, so a bone and a paw stay two things.")
        }
    }
}

/** Status states + borders. */
@Composable
internal fun ColorStatusBorders() {
    val c = AppTheme.colors
    CatalogSection(
        "Status",
        "Universal state meaning. Reach for a role, never a raw color.",
    ) {
        SwatchFlow {
            ColorRow("status.okay", c.status.okay, null, "Positive confirmation — saved, completed, synced.")
            ColorRow("status.warning", c.status.warning, null, "Caution — expiring soon, degraded, risky action.")
            ColorRow("status.bad", c.status.bad, null, "A failing state — sync broken, service down.")
            ColorRow("status.okayInk", c.status.okayInk, c.surfacePrimary, "okay as text on a light surface, where the fill is too pale.")
            ColorRow("danger", c.danger, c.danger.onColor, "Errors and destructive actions (delete, sign out).")
            ColorRow("missFill", c.missFill, c.missMark, "A missed day on the week strip.")
            ColorRow("missMark", c.missMark, null, "The cross drawn on a missed day.")
        }
    }

    CatalogSection(
        "Borders",
        "Edges and dividers. Strength signals interaction state.",
    ) {
        SwatchFlow {
            ColorRow("border", c.border, null, "Default edges, dividers, input rest state.")
            ColorRow("borderSecondary", c.borderSecondary, null, "Focused / selected edges, and quiet rest rings (radio, switch).")
            ColorRow("borderStrong", c.borderStrong, null, "An edge with nothing filled inside it: the ghost button.")
            ColorRow("borderDisabled", c.borderDisabled, null, "Edges of disabled controls.")
            ColorRow("rule", c.rule, null, "The 2dp line across a section header.")
            ColorRow("cardInset", c.cardInset, null, "The soft stroke just inside an achievements card's edge.")
            ColorRow("badgeEarnedDisc", c.badgeEarnedDisc, null, "The pale amber disc behind every earned badge.")
        }
    }
}
