package com.sodogku.libraries.ui.components.text

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.system.Feel
import com.sodogku.libraries.ui.system.LocalHaptics
import com.sodogku.libraries.ui.system.LocalReduceAnimations
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.system.AppTheme
import com.sodogku.system.typography.TypographyResource
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The biggest number on a screen, optionally arriving rather than simply being
 * there.
 *
 * [countUpFrom] is what makes it a celebration instead of a status. Pass the
 * value the player had before whatever just happened and the digits climb to
 * [value] and land with a thump; pass null and the number is already at rest.
 * A page the player went looking for should not perform at them, so the caller
 * decides, and "was there anything new" is the only thing it has to answer.
 *
 * **Count first, land second.** The number reaching its new value and *then*
 * being hit is the order that reads as an impact. Scaling while the digits are
 * still changing reads as a glitch, which is why the thump is after the loop
 * rather than around it.
 *
 * Outlined, because at this size the number is the screen and it has to hold its
 * own edge against whatever is behind it.
 *
 * Lifted out of `StreakHero` when the achievements page wanted the same moment.
 * Two screens copying this by eye is how the second one ends up with a different
 * idea of what winning looks like.
 */
@Composable
fun CountUpNumber(
    value: Int,
    modifier: Modifier = Modifier,
    countUpFrom: Int? = null,
    typography: TypographyResource = AppTheme.typography.Display.D1500,
    color: ColorResource = AppTheme.colors.accentBrand,
) {
    val still = LocalReduceAnimations.current || LocalInspectionMode.current
    val haptics = LocalHaptics.current

    var shown by remember { mutableIntStateOf(countUpFrom ?: value) }
    val thump = remember { Animatable(if (countUpFrom == null || still) 1f else SlamFrom) }

    LaunchedEffect(value, countUpFrom, still) {
        if (countUpFrom == null || still) {
            shown = value
            thump.snapTo(1f)
            return@LaunchedEffect
        }
        for (next in (countUpFrom + 1)..value) {
            shown = next
            delay(TickMillis)
        }
        thump.snapTo(SlamFrom)
        haptics.play(Feel.Win)
        thump.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }

    OutlinedText(
        text = shown.toString(),
        typography = typography,
        color = color,
        textAlign = TextAlign.Center,
        // Read in the layer, never in composition. A spring on a number this
        // size would otherwise re-lay-out the whole column every frame.
        modifier = modifier.graphicsLayer {
            scaleX = thump.value
            scaleY = thump.value
        },
    )
}

/**
 * Where the number starts before it lands.
 *
 * Well over one, so it arrives *shrinking* onto the screen rather than growing
 * out of it. A number that grows into place reads as appearing; one that slams
 * down from too large reads as landing, which is the difference the thump is for.
 */
private const val SlamFrom = 1.6f

/** Per digit while counting. Fast, because nobody is reading the intermediate values. */
private const val TickMillis = 90L

@Preview
@Composable
private fun CountUpNumberPreview() {
    PreviewContent {
        CountUpNumber(value = 12)
    }
}
