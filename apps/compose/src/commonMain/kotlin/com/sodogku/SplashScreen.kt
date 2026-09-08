package com.sodogku

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.sodogku.libraries.core.BuildInfo
import com.sodogku.libraries.core.Platform
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogHeroTopInset
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
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
 * the title and the tagline arrive *around* a dog that never moved. For a
 * returning player there is no dog underneath and the overlay simply fades; the
 * nesting below multiplies the two alphas, so that case needs no special code.
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
 * The geometry below therefore has to match `OnboardingScreen`'s column exactly —
 * system-bar insets, then the same leading spacer — or the dog jumps by however
 * much the two disagree, which is the one thing a viewer notices. The spacer is
 * `DogHeroTopInset` in both places rather than the same number written twice, so
 * moving one moves the other.
 */
@Composable
private fun SplashContent(screenAlpha: Float, dogAlpha: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background.color)
            .graphicsLayer { this.alpha = screenAlpha },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = Dimension.D800),
        ) {
            Spacer(modifier = Modifier.height(DogHeroTopInset))
            Dog(
                pose = DogPose.Still,
                modifier = Modifier.graphicsLayer { this.alpha = dogAlpha },
            )
        }
    }
}

@Preview
@Composable
private fun PreviewSplashOverlay() {
    PreviewContent {
        SplashContent(screenAlpha = 1f, dogAlpha = 1f)
    }
}
