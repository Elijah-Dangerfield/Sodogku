package com.sodogku.libraries.ui.components.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Motion
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.roundToInt

/**
 * The running score, counting up to each new value rather than snapping.
 *
 * The count-up is the point. A number that jumps from 3,840 to 4,896 reads as a
 * different number; one that rolls there reads as *earning* 1,056, which is the
 * feedback the whole scoring system exists to deliver.
 */
@Composable
fun ScoreCounter(
    score: Int,
    modifier: Modifier = Modifier,
) {
    var displayed by remember { mutableIntStateOf(score) }
    val from = remember { mutableIntStateOf(score) }

    LaunchedEffect(score) {
        val start = displayed
        if (start == score) return@LaunchedEffect
        from.intValue = start
        val progress = Animatable(0f)
        progress.animateTo(1f, tween(durationMillis = CountUpMillis)) {
            displayed = (start + (score - start) * value).roundToInt()
        }
        displayed = score
    }

    Text(
        text = displayed.toString(),
        typography = AppTheme.typography.Display.D900,
        color = AppTheme.colors.text,
        modifier = modifier,
    )
}

/**
 * Points floating up from a placement, with the praise word above them.
 *
 * Keyed on [nonce] rather than on the value: two placements can be worth exactly
 * the same points, and a value-keyed animation would silently skip the second.
 */
@Composable
fun FloatingPoints(
    points: Int,
    praise: String?,
    nonce: Int,
    modifier: Modifier = Modifier,
    riseBy: Dp = Dimension.D1300,
) {
    val progress = remember { Animatable(1f) }

    LaunchedEffect(nonce) {
        if (nonce == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = FloatMillis))
    }

    if (progress.value >= 1f) return

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.graphicsLayer {
            translationY = -riseBy.toPx() * progress.value
            alpha = (1f - progress.value).coerceIn(0f, 1f)
            val scale = FloatStartScale + (1f - FloatStartScale) * (progress.value * FloatScaleRamp)
                .coerceAtMost(1f)
            scaleX = scale
            scaleY = scale
        },
    ) {
        Text(
            text = buildString {
                if (praise != null) append(praise).append('\n')
                append('+').append(points)
            },
            typography = AppTheme.typography.Heading.H500,
            color = AppTheme.colors.accentPrimary,
        )
    }
}

private const val CountUpMillis = 420
private const val FloatMillis = 900
private const val FloatStartScale = 0.7f
private const val FloatScaleRamp = 4f

@Preview
@Composable
private fun ScoreCounterPreview() {
    PreviewContent {
        Box(contentAlignment = Alignment.Center) {
            ScoreCounter(score = 4_896)
            FloatingPoints(points = 1_056, praise = "Excellent", nonce = 1)
        }
    }
}
