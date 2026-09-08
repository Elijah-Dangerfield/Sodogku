package com.sodogku

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.CircularLoadingIndicator
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * What is on screen between the platform splash handing off and the app being
 * ready to draw a board.
 *
 * It is the dog, and for the first few seconds nothing else. A boot that
 * finishes quickly — which is nearly all of them, since the only awaited work is
 * a local cache read and a config resolve that falls back offline — should look
 * like the app opening, not like the app struggling. A spinner in that window is
 * a promise that something is slow, and it is usually a lie.
 *
 * The spinner appears only once the wait is long enough to be worth apologising
 * for, and it fades in under the dog rather than replacing it. There is no
 * cycling caption any more: the words were there to fill a spinner's silence,
 * and the dog does that better.
 */
@Composable
fun BootLoadingScreen(
    modifier: Modifier = Modifier,
    spinnerDelay: Duration = SpinnerDelay,
) {
    val spinner = remember { Animatable(0f) }
    LaunchedEffect(spinnerDelay) {
        delay(spinnerDelay)
        spinner.animateTo(1f, tween(SpinnerFadeMillis))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.background.color),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimension.D900),
        ) {
            Dog(pose = DogPose.Still)
            // The row keeps its height whether or not the spinner is showing, so
            // the dog does not jump down the screen when a slow boot reveals it.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height(SpinnerRowHeight),
            ) {
                CircularLoadingIndicator(
                    modifier = Modifier.graphicsLayer { alpha = spinner.value },
                )
            }
        }
    }
}

/**
 * Long enough that a normal boot never shows a spinner at all.
 *
 * The user asked for about five seconds, and five is also comfortably past the
 * point where a local read and an offline config fallback have both resolved —
 * so anything still waiting here is genuinely stuck on the network, which is the
 * only case a spinner honestly describes.
 */
private val SpinnerDelay = 5.seconds

private const val SpinnerFadeMillis = 400

/** Reserved whether or not the spinner has appeared, so nothing shifts. */
private val SpinnerRowHeight = Dimension.D1300

/** Long enough that the preview shows the resting state rather than a race. */
private val NeverInAPreview = 1.hours

@Preview
@Composable
private fun BootLoadingScreenPreview_JustTheDog() {
    PreviewContent {
        BootLoadingScreen(spinnerDelay = NeverInAPreview)
    }
}

@Preview
@Composable
private fun BootLoadingScreenPreview_SlowBoot() {
    PreviewContent {
        BootLoadingScreen(spinnerDelay = 0.milliseconds)
    }
}
