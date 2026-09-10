package com.sodogku.devfeedback

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.sodogku.libraries.core.BuildInfo
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.isTesterBuild
import com.sodogku.libraries.core.logOnFailure
import kotlinx.coroutines.launch

/**
 * Wraps the app so a tester can reach the directive form from any screen.
 *
 * One trigger: a small button floating over everything, which can be dragged
 * anywhere and stays where it is put. It replaced a handle pinned to the right
 * edge, plus a swipe in from that edge — the handle was hard to grab, and on a
 * big grid it sat on cells the player needed to tap. See [DevFeedbackFab] for
 * why it has to move and how it avoids taking touches meant for the app.
 *
 * Everything below is gated on [isTesterBuild]: in a player's build the
 * wrapper adds one `Box` and nothing else: no pointer handler, no per-frame
 * layer recording, no panel in the tree.
 */
@Composable
fun DevFeedbackHost(
    viewModel: DevFeedbackViewModel,
    fabCache: DevFeedbackFabCache,
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

    Box(modifier = modifier.fillMaxSize()) {
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

        FabLayer(cache = fabCache, onClick = ::open)

        // Last, so the panel covers the button rather than the other way round.
        // Nothing hides the button while the form is open because nothing needs
        // to: the panel is full screen and opaque.
        DevFeedbackPanel(state = state, onAction = viewModel::takeAction)
    }
}

/**
 * The button's own state read, kept in a composable of its own.
 *
 * Deliberately not read in [DevFeedbackHost]: a state read there recomposes the
 * host, and the host's content lambda is the entire app. Dropping the button in
 * a new place would re-run the whole tree's composition for a position change
 * nothing above this node cares about.
 *
 * Nothing is drawn until the first value arrives from disk. The alternative is
 * to start at the default and jump once the read lands, which also flashes a
 * button the owner has switched off.
 */
@Composable
private fun FabLayer(cache: DevFeedbackFabCache, onClick: () -> Unit) {
    val stored by cache.updates.collectAsState(initial = null)
    val settings = stored ?: return
    if (settings.hidden) return

    val scope = rememberCoroutineScope()
    // Remembered so its identity is stable, because `pointerInput` is keyed on
    // it: a fresh lambda each composition would tear down and rebuild the
    // gesture detector, cancelling a drag in progress.
    val onSettled: (FabPlacement) -> Unit = remember(cache, scope) {
        { placement ->
            scope.launch {
                Catching { cache.update { it.withPlacement(placement) } }
                    .logOnFailure { "Could not remember where the feedback button was put" }
            }
        }
    }

    DevFeedbackFab(
        placement = settings.placement,
        onSettled = onSettled,
        onClick = onClick,
    )
}
