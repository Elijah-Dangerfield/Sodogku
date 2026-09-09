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
import com.sodogku.libraries.core.BuildInfo
import com.sodogku.libraries.core.Platform
import androidx.compose.ui.Alignment
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.BoneLoader
import org.jetbrains.compose.resources.stringResource
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.splash_loading
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
 * Only the bone arrives, and then the whole overlay fades out.
 *
 * **This used to be a dog, standing exactly where the welcome screen draws its
 * own**, so the card and the rules appeared to arrive *around* a dog that never
 * moved. It was a nice trick and it did not survive contact: a disembodied head
 * on a cream field for a second and a half reads as a mascot waiting, not as an
 * app opening, and it only ever worked for the first-run player, since a
 * returning one has no dog underneath to hand off to. A loader says what is
 * actually happening, on every launch, to every player.
 *
 * The bone is deliberately not a spinner. See [BoneLoader].
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
    val boneAlpha = remember { Animatable(0f) }
    var hasReported by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        boneAlpha.animateTo(1f, tween(FadeInMillis, easing = LinearEasing))
        delay(HoldMillis.toLong())
        screenAlpha.animateTo(0f, tween(FadeOutMillis, easing = LinearEasing))
        if (!hasReported) {
            hasReported = true
            onComplete()
        }
    }

    SplashContent(screenAlpha = screenAlpha.value, boneAlpha = boneAlpha.value)
}

/**
 * The bone, on the cream the system already painted.
 *
 * Centred and nothing else on screen. The previous version laid out the whole
 * welcome column and drew only its dog, so the two screens could not drift; with
 * the dog gone there is nothing to keep in step, and this is just a box.
 */
@Composable
private fun SplashContent(screenAlpha: Float, boneAlpha: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background.color)
            .graphicsLayer { this.alpha = screenAlpha },
        contentAlignment = Alignment.Center,
    ) {
        BoneLoader(
            label = stringResource(Res.string.splash_loading),
            modifier = Modifier.graphicsLayer { this.alpha = boneAlpha },
        )
    }
}

@Preview
@Composable
private fun PreviewSplashOverlay() {
    PreviewContent {
        SplashContent(screenAlpha = 1f, boneAlpha = 1f)
    }
}
