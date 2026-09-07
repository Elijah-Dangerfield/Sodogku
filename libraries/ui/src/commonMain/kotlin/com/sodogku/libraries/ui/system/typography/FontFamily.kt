package com.sodogku.system.typography

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import sodogku.libraries.ui.generated.resources.DMSerifText_Italic
import sodogku.libraries.ui.generated.resources.DMSerifText_Regular
import sodogku.libraries.ui.generated.resources.Res
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

val SansSerifFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(
            resource = Res.font.Roboto_Light, weight = FontWeight.Light
        ), Font(
            resource = Res.font.Roboto_Regular, weight = FontWeight.Normal
        ), Font(
            resource = Res.font.Roboto_Medium, weight = FontWeight.Medium
        ), Font(
            resource = Res.font.Roboto_Bold, weight = FontWeight.Bold
        ), Font(
            resource = Res.font.Roboto_SemiBold, weight = FontWeight.SemiBold
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