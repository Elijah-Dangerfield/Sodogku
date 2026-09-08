package com.sodogku.devfeedback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sodogku.libraries.core.BuildInfo
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.isTesterBuild
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import kotlinx.coroutines.launch

/**
 * Wraps the app so a tester can reach the directive form from any screen.
 *
 * Two triggers, one feature:
 *
 * - **Swipe in from the right edge.** What the owner asked for, and the only
 *   trigger on iOS, where the right edge is free (the system's interactive pop
 *   lives on the left).
 * - **A visible handle on the same edge.** Android gesture navigation claims
 *   both screen edges for system back, so a swipe from the bare edge there does
 *   not reach this at all: measured on the emulator, it exits the app. The
 *   handle sits inboard and calls `systemGestureExclusion`, so a tap or a drag
 *   starting on it works. It also stops the gesture from being a secret nobody
 *   remembers, on either platform.
 *
 * Everything below is gated on [isTesterBuild]: in a player's build the
 * wrapper adds one `Box` and nothing else: no pointer handler, no per-frame
 * layer recording, no panel in the tree.
 */
@Composable
fun DevFeedbackHost(
    viewModel: DevFeedbackViewModel,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val enabled = BuildInfo.isTesterBuild
    if (!enabled) {
        Box(modifier = modifier.fillMaxSize()) { content() }
        return
    }

    val state by viewModel.stateFlow.collectAsState()
    val scope = rememberCoroutineScope()
    // Recording the content into a layer is what makes the screenshot possible
    // without a platform capture API or any permission. It costs a draw
    // indirection on every frame, which is why it only exists in tester builds.
    val captureLayer = rememberGraphicsLayer()

    fun open() {
        if (state.isOpen) return
        scope.launch {
            // Grab the frame before the panel covers it. A failed capture is
            // not worth blocking the report over. The form opens either way.
            val screenshot = Catching { captureLayer.toImageBitmap().encodeToJpeg() }
                .logOnFailure { "Could not capture the feedback screenshot" }
                .getOrNull()
                ?.let(::Screenshot)
            viewModel.takeAction(DevFeedbackAction.Open(screenshot))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .rightEdgeSwipe(enabled = true, onTriggered = ::open),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    captureLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(captureLayer)
                },
        ) {
            content()
        }

        if (!state.isOpen) {
            EdgeHandle(
                onClick = ::open,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        DevFeedbackPanel(state = state, onAction = viewModel::takeAction)
    }
}

@Composable
private fun EdgeHandle(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            // Sits a little in from the edge, and claims its own touches back
            // from the OS. Both are needed: the inset alone loses to a phone
            // with back sensitivity turned up, and the exclusion alone leaves
            // the handle sharing a hairline with the system's own target.
            .padding(end = Dimension.D500)
            .width(Dimension.D300)
            .height(Dimension.D1300)
            .excludeFromSystemGestures()
            .background(
                color = AppTheme.colors.accentPrimary.color,
                shape = RoundedCornerShape(Dimension.D200),
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = HANDLE_DESCRIPTION },
    )
}

/** What `scripts/dev/drive.py tap "Leave feedback"` looks for. */
private const val HANDLE_DESCRIPTION = "Leave feedback"
