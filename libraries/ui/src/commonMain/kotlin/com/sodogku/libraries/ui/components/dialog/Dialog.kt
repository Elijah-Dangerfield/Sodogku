package com.sodogku.libraries.ui.components.dialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.system.LocalReduceAnimations
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Motion
import com.sodogku.system.Radii
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.roundToInt
import kotlin.random.Random


/**
 * Public dialog entry point that mirrors Compose's windowed dialog API but renders
 * entirely inside our Compose hierarchy. Supply a [DialogState] if you need to trigger
 * animated dismissals from inside the dialog; otherwise a default state is provided.
 *
 * The card pads its own content ([ModalDialogDefaults.ContentPadding]). Callers
 * hand over the copy and the buttons and get the breathing room for free —
 * before this existed every dialog in the app had its title, its body and its
 * close button flush against the card edges, because padding was something each
 * call site had to remember and none of them did.
 */
@Composable
fun Dialog(
    state: DialogState = rememberDialogState(),
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: ModalDialogProperties = ModalDialogProperties(),
    animationSpec: ModalDialogAnimationSpec = ModalDialogDefaults.animationSpec(),
    scrimColor: Color = ModalDialogDefaults.scrimColor(),
    contentAlignment: Alignment = Alignment.Center,
    contentPadding: PaddingValues = ModalDialogDefaults.ContentPadding,
    content: @Composable () -> Unit = {},
) {
    HostedDialog(
        state = state,
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        properties = properties,
        animationSpec = animationSpec,
        scrimColor = scrimColor,
        contentAlignment = contentAlignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(ModalDialogDefaults.WidthFraction)
                .animateContentSize()
                // clip to the *shape*, not the bounds: with a rectangular clip a
                // full-width button at the bottom of the card squared off the
                // card's rounded corners.
                .clip(Radii.Card.shape)
                .background(AppTheme.colors.surfacePrimary.color)
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Preview
@Composable
private fun PreviewDialog() {
    PreviewContent {
        Dialog(
            onDismissRequest = { -> },
        ) {
            Text("This is all a dialog is")
        }
    }
}

/**
 * Window-free dialog host that handles scrim + content animations and dismissal behaviour entirely
 * in Compose Multiplatform.
 */
/**
 * Lower-level alternative used when callers want to provide their own dialog surface.
 * Registers the provided [content] with [DialogHostState] so it renders on top of the app.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HostedDialog(
    state: DialogState = rememberDialogState(),
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: ModalDialogProperties = ModalDialogProperties(),
    animationSpec: ModalDialogAnimationSpec = ModalDialogDefaults.animationSpec(),
    scrimColor: Color = ModalDialogDefaults.scrimColor(),
    contentAlignment: Alignment = Alignment.Center,
    hostState: DialogHostState? = LocalDialogHostState.current,
    content: @Composable BoxScope.() -> Unit,
) {
    val resolvedHostState = hostState ?: return
    val entryId = remember { Random.nextLong() }

    val currentModifier by rememberUpdatedState(modifier)
    val currentProperties by rememberUpdatedState(properties)
    val currentAnimation by rememberUpdatedState(animationSpec)
    val currentScrim by rememberUpdatedState(scrimColor)
    val currentAlignment by rememberUpdatedState(contentAlignment)
    val currentOnDismissComplete by rememberUpdatedState(onDismissRequest)
    val currentContent by rememberUpdatedState(content)
    val visible = state.isVisible

    val requestDismiss = remember(state) {
        {
            state.dismiss()
        }
    }

    SideEffect {
        resolvedHostState.upsert(
            DialogHostEntry(
                id = entryId,
                visible = visible,
                modifier = currentModifier,
                properties = currentProperties,
                animationSpec = currentAnimation,
                scrimColor = currentScrim,
                contentAlignment = currentAlignment,
                requestDismiss = requestDismiss,
                onDismissed = currentOnDismissComplete,
                content = currentContent
            )
        )
    }

    DisposableEffect(resolvedHostState, entryId) {
        onDispose { resolvedHostState.remove(entryId) }
    }
}

/**
 * Renders the actual scrim + animated surface for a hosted dialog.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DialogOverlay(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    onDismissComplete: () -> Unit,
    modifier: Modifier = Modifier,
    properties: ModalDialogProperties = ModalDialogProperties(),
    animationSpec: ModalDialogAnimationSpec = ModalDialogDefaults.animationSpec(),
    scrimColor: Color = ModalDialogDefaults.scrimColor(),
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val isInPreview = LocalInspectionMode.current
    val transitionState = remember { MutableTransitionState(isInPreview) }
    val dismissComplete by rememberUpdatedState(onDismissComplete)
    var pendingDismiss by remember { mutableStateOf(false) }
    transitionState.targetState = visible

    LaunchedEffect(visible) {
        if (!visible) {
            pendingDismiss = true
        } else {
            pendingDismiss = false
        }
    }

    val shouldRender =
        transitionState.currentState || transitionState.targetState || pendingDismiss

    if (!shouldRender) {
        return
    }

    LaunchedEffect(
        pendingDismiss,
        transitionState.currentState,
        transitionState.targetState
    ) {
        val shouldFinishDismiss =
            pendingDismiss && !transitionState.currentState && !transitionState.targetState
        if (shouldFinishDismiss) {
            pendingDismiss = false
            dismissComplete()
        }
    }

    BackHandler(
        enabled = shouldRender && transitionState.currentState,
        onBack = {
            if (properties.dismissOnBackPress) {
                onDismissRequest()
            }
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) {
                dialog()
                stateDescription = "Dialog"
            },
        contentAlignment = contentAlignment
    ) {
        AnimatedVisibility(
            visibleState = transitionState,
            enter = animationSpec.scrimEnter,
            exit = animationSpec.scrimExit
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor)
                    .pointerInput(properties.dismissOnClickOutside) {
                        detectTapGestures {
                            if (properties.dismissOnClickOutside) {
                                onDismissRequest()
                            }
                        }
                    }
            )
        }

        AnimatedVisibility(
            visibleState = transitionState,
            enter = animationSpec.contentEnter,
            exit = animationSpec.contentExit
        ) {
            Box(modifier = modifier, content = content)
        }
    }
}

@Stable
data class ModalDialogProperties(
    val dismissOnBackPress: Boolean = true,
    val dismissOnClickOutside: Boolean = true,
)

@Stable
data class ModalDialogAnimationSpec(
    val scrimEnter: EnterTransition = ModalDialogDefaults.scrimEnter(),
    val scrimExit: ExitTransition = ModalDialogDefaults.scrimExit(),
    val contentEnter: EnterTransition = ModalDialogDefaults.contentEnter(),
    val contentExit: ExitTransition = ModalDialogDefaults.contentExit(),
)

object ModalDialogDefaults {

    /**
     * How wide the card sits on the page. Not `fillMaxWidth()`: the strip of
     * scrim either side is what the player taps to dismiss.
     */
    const val WidthFraction: Float = 0.85f

    /**
     * The breathing room inside every dialog card.
     *
     * Vertical is a step larger than horizontal because the top and bottom of a
     * dialog are a heading and a full-width button, and both read as crowded at
     * the same value that looks right beside a line of body text.
     */
    val ContentPadding: PaddingValues =
        PaddingValues(horizontal = Dimension.D800, vertical = Dimension.D900)

    /** Scrim fade. Settles well before the card does, so the card lands on a dim that is already there. */
    private const val ScrimEnterMillis = 200
    private const val ExitMillis = 160

    /** Fade-only enter and exit, for `reduceAnimations`. Short enough to read as instant. */
    private const val ReducedMillis = 90

    /** How far below its resting place the card starts, as a fraction of its own height. */
    private const val RiseFraction = 0.10f

    /** How small the card starts. Far enough down that the overshoot is legible. */
    private const val EnterScale = 0.82f

    /** How far the card shrinks on the way out. Shallower than the entrance: leaving is not an event. */
    private const val ExitScale = 0.92f

    @Composable
    fun scrimColor(): Color = AppTheme.colors.backgroundOverlay.color

    /**
     * The spec a dialog animates with, honouring the player's reduce-animations
     * setting.
     *
     * Resolved at the *call site*, not inside [DialogOverlay]: the host renders
     * every dialog from a snapshot captured when the caller composed, so a spec
     * read in the host would be read outside the subtree the setting is
     * provided to.
     */
    @Composable
    fun animationSpec(): ModalDialogAnimationSpec =
        animationSpecFor(LocalReduceAnimations.current)

    /**
     * [animationSpec] without the composition local, so the choice itself can be
     * tested.
     *
     * Reduced motion is a plain fade on *both* layers rather than a shortened
     * spring: the setting exists for players who find movement unpleasant, and a
     * fast bounce is still a bounce.
     */
    fun animationSpecFor(reduceAnimations: Boolean): ModalDialogAnimationSpec =
        if (reduceAnimations) {
            ModalDialogAnimationSpec(
                scrimEnter = fadeIn(tween(ReducedMillis)),
                scrimExit = fadeOut(tween(ReducedMillis)),
                contentEnter = fadeIn(tween(ReducedMillis)),
                contentExit = fadeOut(tween(ReducedMillis)),
            )
        } else {
            ModalDialogAnimationSpec()
        }

    fun scrimEnter(): EnterTransition = fadeIn(
        animationSpec = tween(ScrimEnterMillis)
    )

    fun scrimExit(): ExitTransition = fadeOut(
        animationSpec = tween(ExitMillis)
    )

    /**
     * The card springs up from below and overshoots before settling — the same
     * gesture a dog landing on a cell makes, so the app has one idea of how
     * things arrive rather than one per surface.
     *
     * The scale uses [Motion.Pop] and the rise is the same spring rebuilt for
     * `IntOffset`, which `slideInVertically` needs and `Motion` has no token
     * for. They have to match: on different curves the card looks like it is
     * being assembled rather than arriving. The fade is a plain tween and
     * finishes early, because a card still translucent while it bounces reads as
     * a rendering fault.
     */
    fun contentEnter(): EnterTransition =
        scaleIn(initialScale = EnterScale, animationSpec = Motion.Pop) +
            slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = IntOffset.VisibilityThreshold,
                ),
                initialOffsetY = { (it * RiseFraction).roundToInt() }
            ) +
            fadeIn(animationSpec = tween(Motion.FadeMillis))

    fun contentExit(): ExitTransition =
        fadeOut(animationSpec = tween(ExitMillis)) +
            scaleOut(
                targetScale = ExitScale,
                animationSpec = tween(ExitMillis)
            )
}
