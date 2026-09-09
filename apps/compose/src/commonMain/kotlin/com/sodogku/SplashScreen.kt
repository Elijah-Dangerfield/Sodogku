package com.sodogku

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.sodogku.features.onboarding.impl.OnboardingDogHandoff
import com.sodogku.libraries.core.BuildInfo
import com.sodogku.libraries.core.Platform
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.system.AppTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview

private const val FadeInMillis = 450
private const val HoldMillis = 650
private const val FadeOutMillis = 450

/**
 * Two alphas, not one, and that is the whole design.
 *
 * The cream is opaque from the first frame. It is the same colour the system
 * already painted from `Info.plist`'s `UILaunchScreen`, so there is no moment
 * where anything else is visible — fading the background *in* would have shown
 * the bare window underneath for 450ms, which is the white flash the launch
 * screen exists to remove.
 *
 * Only the dog arrives. Then the whole overlay fades out, and because the dog is
 * standing where the first-run screen draws its own dog, at the same size, what
 * is revealed underneath is the same shape in the same place. The player sees
 * the amber, the card and the rules arrive *around* a dog that never moved. For
 * a returning player there is no dog underneath and the overlay simply fades;
 * the nesting below multiplies the two alphas, so that case needs no special
 * code.
 *
 * The welcome screen behind this is mostly amber now, and the cream here did not
 * follow it. The cream is not a colour choice, it is the launch image: it has to
 * be the colour UIKit already painted, or the handover from the launch image to
 * the first Compose frame is a visible jump. So the amber is one of the things
 * that arrives during the fade-out rather than something the splash shows, which
 * is a cross-fade the dog sits still through rather than a cut.
 */
@Composable
fun SplashOverlay(
    onComplete: () -> Unit,
) {
    if (BuildInfo.platform != Platform.iOS) {
        LaunchedEffect(Unit) { onComplete() }
        return
    }

    val screenAlpha = remember { Animatable(1f) }
    val dogAlpha = remember { Animatable(0f) }
    var hasReported by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        dogAlpha.animateTo(1f, tween(FadeInMillis, easing = LinearEasing))
        delay(HoldMillis.toLong())
        screenAlpha.animateTo(0f, tween(FadeOutMillis, easing = LinearEasing))
        if (!hasReported) {
            hasReported = true
            onComplete()
        }
    }

    SplashContent(screenAlpha = screenAlpha.value, dogAlpha = dogAlpha.value)
}

/**
 * The dog, on the cream, in the place the next screen will draw it.
 *
 * This used to be the wordmark in a script face, centred — a different thing in
 * a different place from anything that follows it, so the launch read as a
 * splash and then, separately, an app. The point of the handoff is that it
 * should not read as two screens at all: the dog is already where the welcome
 * screen puts it, so when the splash fades the only thing that changes is
 * everything *around* the dog arriving.
 *
 * So the geometry is not copied from `OnboardingScreen`, it *is*
 * `OnboardingScreen` — `OnboardingDogHandoff` lays out that screen's real column
 * and draws nothing but the dog. There is no offset here to keep in step with
 * one over there, and no way for the two to drift apart. That now matters more
 * than it used to: the welcome dog is centred in the amber field, whose height
 * is the screen minus a card measured from its own copy, so where the dog lands
 * is a number no constant here could have tracked.
 *
 * The dog is drawn from the same sprite sheet the welcome screen animates, held
 * on frame 0. `OnboardingDogHandoff` decides that; see `WelcomeDogLoop` there
 * for why a launch screen does not get the loop.
 */
@Composable
private fun SplashContent(screenAlpha: Float, dogAlpha: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background.color)
            .graphicsLayer { this.alpha = screenAlpha },
    ) {
        OnboardingDogHandoff(dogAlpha = dogAlpha)
    }
}

@Preview
@Composable
private fun PreviewSplashOverlay() {
    PreviewContent {
        SplashContent(screenAlpha = 1f, dogAlpha = 1f)
    }
}
