package com.sodogku.libraries.ui.components.header

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import com.sodogku.system.Dimension
import com.sodogku.system.Motion
import androidx.compose.ui.unit.dp
import com.sodogku.system.AppTheme
import com.sodogku.system.thenIf
import com.sodogku.system.typography.TypographyResource
import com.sodogku.libraries.ui.Elevation
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.icon.IconButton
import com.sodogku.libraries.ui.components.icon.Icons
import com.sodogku.libraries.ui.components.text.Text
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.common_back

@Composable
fun TopBar(
    title: String? = null,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    typographyToken: TypographyResource = AppTheme.typography.Display.D900,
    backgroundColor: Color = AppTheme.colors.background.color,
    actions: @Composable () -> Unit = {},
    scrollState: ScrollState? = null,
    liftOnScroll: Boolean = scrollState != null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            )
            .thenIf(liftOnScroll) { elevateOnScroll(scrollState) }
            ,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            if (onNavigateBack != null) {
                IconButton(
                    size = IconButton.Size.Large,
                    icon = Icons.ChevronLeft(stringResource(Res.string.common_back)),
                    onClick = onNavigateBack
                )
            }
            title?.let {
                Text(text = title, typography = typographyToken)
            }
        }
        
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions()
        }
    }
}

/**
 * The lift a header gets once there is content scrolled up underneath it.
 *
 * Drawn rather than `Modifier.shadow`, which casts on all four sides — above the
 * header as well as below, so on a screen where the header sits under the status
 * bar the lift appeared as a halo around the whole bar. A header is lifted off
 * the content *below* it and nothing else, so that is the only edge that gets a
 * shadow.
 *
 * The gradient is painted after the content and outside the node's own bounds,
 * which is what puts it on the content rather than on the header.
 */
private fun Modifier.elevateOnScroll(
    scrollState: ScrollState?,
): Modifier {

    checkNotNull(scrollState) {
        "ScrollState should not be null when liftOnScroll is true"
    }

    return this.composed {
        val shadowColor = AppTheme.colors.shadow.color
        // Animatable rather than `by animateDpAsState`, so the value is read in
        // the draw phase instead of in composition — the detekt rule this used
        // to suppress was right, and a lambda-taking draw is the phase-deferred
        // form it was asking for.
        val lift = remember { Animatable(0f) }
        val lifted = scrollState.canScrollBackward
        LaunchedEffect(lifted) { lift.animateTo(if (lifted) 1f else 0f, Motion.fade()) }

        Modifier.drawWithContent {
            drawContent()
            if (lift.value <= 0f) return@drawWithContent
            val depth = ShadowDepth.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(shadowColor.copy(alpha = shadowColor.alpha * ShadowPeak * lift.value), shadowColor.copy(alpha = 0f)),
                    startY = size.height,
                    endY = size.height + depth,
                ),
                topLeft = Offset(0f, size.height),
                size = Size(size.width, depth),
            )
        }
    }
}

/** How far the lift reaches onto the content. Short: it is a hint, not a scrim. */
private val ShadowDepth = Dimension.D400

/**
 * The theme's shadow colour at full strength drew a crisp dark line rather than
 * a shadow — the giveaway that it is a gradient and not a lift is that you can
 * see where it starts.
 */
private const val ShadowPeak = 0.55f

@Preview
@Composable
private fun PreviewHeader() {
    PreviewContent {
        com.sodogku.libraries.ui.components.header.TopBar(
            title = "Heading Title",
        )
    }
}

