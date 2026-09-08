package com.sodogku.system.typography

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import sodogku.libraries.ui.generated.resources.DMSerifText_Italic
import sodogku.libraries.ui.generated.resources.DMSerifText_Regular
import sodogku.libraries.ui.generated.resources.Res
import sodogku.libraries.ui.generated.resources.fredoka_bold
import sodogku.libraries.ui.generated.resources.fredoka_light
import sodogku.libraries.ui.generated.resources.fredoka_medium
import sodogku.libraries.ui.generated.resources.fredoka_regular
import sodogku.libraries.ui.generated.resources.fredoka_semibold
import sodogku.libraries.ui.generated.resources.Roboto_Bold
import sodogku.libraries.ui.generated.resources.Roboto_Light
import sodogku.libraries.ui.generated.resources.Roboto_Medium
import sodogku.libraries.ui.generated.resources.Roboto_Regular
import sodogku.libraries.ui.generated.resources.Roboto_SemiBold
import sodogku.libraries.ui.generated.resources.lust_script_regular
import sodogku.libraries.ui.generated.resources.poppins_bold
import sodogku.libraries.ui.generated.resources.poppins_light
import sodogku.libraries.ui.generated.resources.poppins_medium
import sodogku.libraries.ui.generated.resources.poppins_regular
import sodogku.libraries.ui.generated.resources.poppins_semibold


val BrandFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(
            resource = Res.font.lust_script_regular, weight = FontWeight.Normal
        ),
    )

/**
 * Fredoka, the rounded display face.
 *
 * This is what the earlier note in [SansSerifFontFamily] said to reach for when
 * Poppins stopped being round enough, and both of the two candidates it named
 * were rendered side by side against real strings before picking. Fredoka won
 * on three things that can be checked rather than argued:
 *
 * - **It is actually rounder.** Fredoka's terminals are round and its counters
 *   are open; Baloo 2 is rounded but tall and narrow, and its lining figures —
 *   which is what a level number and a score *are* — come out condensed.
 * - **It has the Light this scale declares.** Fredoka's weight axis is 300–700,
 *   which is exactly the five weights below. Baloo 2 starts at 400, so
 *   `FontWeight.Light` would have had to alias Regular and quietly do nothing.
 * - **260KB against 1.6MB.** Baloo 2 carries a Devanagari companion in every
 *   static instance. Five Fredoka weights cost less than one Baloo 2 weight,
 *   for a face we liked less.
 *
 * It is bound to Display, Heading and Label only — the game's own voice, and
 * everything a player looks *at*. Body and Caption stay on Poppins, because a
 * display face set at 12sp for a paragraph of settings copy or a privacy line
 * is playful at the reader's expense.
 */
val RoundedFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(resource = Res.font.fredoka_light, weight = FontWeight.Light),
        Font(resource = Res.font.fredoka_regular, weight = FontWeight.Normal),
        Font(resource = Res.font.fredoka_medium, weight = FontWeight.Medium),
        Font(resource = Res.font.fredoka_semibold, weight = FontWeight.SemiBold),
        Font(resource = Res.font.fredoka_bold, weight = FontWeight.Bold),
    )

/**
 * Poppins, not Roboto. Geometric and near-circular, and the face everything a
 * player *reads* rather than glances at is still set in: body copy, captions,
 * helper text and legal.
 *
 * It used to carry the whole scale, including Display. See [RoundedFontFamily]
 * for what took the top of the scale off it and why.
 */
val SansSerifFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(
            resource = Res.font.poppins_light, weight = FontWeight.Light
        ), Font(
            resource = Res.font.poppins_regular, weight = FontWeight.Normal
        ), Font(
            resource = Res.font.poppins_medium, weight = FontWeight.Medium
        ), Font(
            resource = Res.font.poppins_bold, weight = FontWeight.Bold
        ), Font(
            resource = Res.font.poppins_semibold, weight = FontWeight.SemiBold
        )
    )

val SerifFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(
            resource = Res.font.DMSerifText_Regular, weight = FontWeight.Normal
        ),

        Font(
            resource = Res.font.DMSerifText_Italic, style = FontStyle.Italic
        ),
    )