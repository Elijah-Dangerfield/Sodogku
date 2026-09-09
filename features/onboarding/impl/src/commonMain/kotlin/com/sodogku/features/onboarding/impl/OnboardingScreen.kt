package com.sodogku.features.onboarding.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.Screen
import com.sodogku.libraries.ui.components.button.ButtonGhost
import com.sodogku.libraries.ui.components.dog.LoopingDog
import com.sodogku.libraries.ui.components.game.drawPaw
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.system.DeepSurface
import com.sodogku.libraries.ui.system.LocalReduceAnimations
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import com.sodogku.system.VerticalSpacerD300
import com.sodogku.system.VerticalSpacerD400
import com.sodogku.system.VerticalSpacerD500
import com.sodogku.system.VerticalSpacerD700
import com.sodogku.system.HorizontalSpacerD500
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.app_name
import sodogku.libraries.resources.generated.resources.onboarding_rule_lines
import sodogku.libraries.resources.generated.resources.onboarding_rule_regions
import sodogku.libraries.resources.generated.resources.onboarding_rule_touch
import sodogku.libraries.resources.generated.resources.onboarding_skip_tutorial
import sodogku.libraries.resources.generated.resources.onboarding_start_tutorial
import sodogku.libraries.resources.generated.resources.onboarding_tagline

/**
 * First-launch welcome. Two ways out, both into the game: learn the rules on
 * the first three levels, or skip straight in. All routing lives in
 * [OnboardingViewModel] — this composable is a pure render of the state.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onAction: (OnboardingAction) -> Unit,
) {
    WelcomeContent(
        state = state,
        onAction = onAction,
        dogAlpha = 1f,
        restAlpha = 1f,
        // The one place the loop is switched off for a player who asked for
        // stills. `LoopingDog` holds frame 0, which is the same frame the splash
        // shows, so reduce-animations gets a composed screen rather than an
        // empty hole where the dog was.
        dogPlaying = !LocalReduceAnimations.current,
    )
}

/**
 * This screen with nothing drawn but its dog, for the iOS splash to fade out of.
 *
 * The splash exists so that launch reads as one screen rather than two: its dog
 * stands exactly where the welcome screen draws its own, so when the splash
 * fades the only thing that happens is the card, the rules and the buttons
 * arriving *around* a dog that never moved. A shared offset constant used to
 * carry that promise, and it only held while both columns kept the same shape.
 * Laying out the real column and hiding all but the dog makes the two agree by
 * construction instead, including after somebody edits this screen.
 *
 * **The amber is on the hidden side of that line, not the shown side**, and that
 * is a decision rather than an oversight. The system launch image is a flat
 * cream (`LaunchBackground` in the iOS asset catalogue) and the splash paints
 * the same cream so that the handover from UIKit to Compose shows nothing at
 * all. Painting the amber here would move that seam rather than remove it: the
 * first Compose frame would jump cream to amber, in front of the player, which
 * is the flash the splash exists to prevent. So the amber arrives with
 * everything else, cross-fading up behind a dog that is opaque in both layers
 * for the whole 450ms.
 *
 * The dog holds a still frame here, and must keep doing so. The splash and the
 * welcome screen are two separate dogs, each stepping its own coroutine: if both
 * played, they would cross-fade between whatever frames they happened to be on,
 * which is a double exposure rather than a handover.
 *
 * What is hidden is drawn but [inert] — a zero-alpha button is still a button
 * until it is told otherwise.
 */
@Composable
fun OnboardingDogHandoff(dogAlpha: Float) {
    WelcomeContent(
        state = OnboardingState(),
        onAction = {},
        dogAlpha = dogAlpha,
        restAlpha = 0f,
        dogPlaying = false,
        modifier = Modifier.inert(),
    )
}

/**
 * Amber to the top, cream card to the bottom, dog in the middle of the amber.
 *
 * The split is a weight and not a fraction. The card is as tall as its own copy
 * and no taller, and the amber takes whatever is left, so a short phone gives up
 * amber rather than clipping a rule off the bottom of the card. On the phones
 * this ships to that lands the card at roughly a third, which is the two-thirds
 * of amber the design asked for, arrived at from the side that cannot break.
 *
 * The dog is centred in the amber rather than in the screen. Centring it in the
 * screen would bury its chin behind the card on a short phone and strand it
 * high on a tall one; centred in the field it keeps the same relationship to
 * the card at every height, which is the relationship anybody actually looks at.
 */
@Composable
private fun WelcomeContent(
    state: OnboardingState,
    onAction: (OnboardingAction) -> Unit,
    dogAlpha: Float,
    restAlpha: Float,
    dogPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    Screen(
        modifier = modifier,
        // Full bleed on purpose: the status bar sits on the amber, and the card
        // insets its own content off the home indicator below.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            DogField(
                dogAlpha = dogAlpha,
                fieldAlpha = restAlpha,
                playing = dogPlaying,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            WelcomeCard(
                state = state,
                onAction = onAction,
                modifier = Modifier.alpha(restAlpha),
            )
        }
    }
}

/**
 * The amber, its paws, and the dog standing on it.
 *
 * The field is painted by a sibling that fills the box rather than by a
 * background on the box itself, because the box's own alpha would take the dog
 * down with it and the dog is the one thing that has to survive the handoff at
 * full opacity.
 *
 * The dog is sized against the field rather than fixed, and it has to be. Its
 * `Modifier.size` is clamped by whatever constraints it is handed, so on a short
 * phone a fixed 240dp square arrives as a 240 by 190 rectangle, and
 * `AnimatedDog` draws the frame into exactly the box it is given: a stretched
 * dog, not a cropped one. Taking the smaller of the two up front means the
 * fallback is a smaller dog at the proportions it was drawn at.
 *
 * A fifth of the sprite's frame is transparent above the ears, so a dog that
 * exactly fills the field still has margin. That is why the cap is the whole
 * height rather than a fraction of it: a fraction pays for the margin twice and
 * the dog ends up small on the phones with the least room to spare.
 *
 * The status bar is padded off the *dog*, not off the field, so the amber still
 * runs edge to edge and under the clock while the dog stays out from behind it.
 */
@Composable
private fun DogField(
    dogAlpha: Float,
    fieldAlpha: Float,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val field = AppTheme.colors.status.warning.color
    val texture = ColorResource.White.withAlpha(FieldTextureAlpha).color

    val statusBar = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)

    BoxWithConstraints(modifier = modifier) {
        val dogSize = WelcomeDogSize
            .coerceAtMost(maxHeight - statusBar.asPaddingValues().calculateTopPadding())

        Spacer(
            modifier = Modifier
                .matchParentSize()
                .alpha(fieldAlpha)
                .drawBehind {
                    drawRect(field)
                    drawFieldPaws(texture)
                },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(statusBar),
            contentAlignment = Alignment.Center,
        ) {
            // Hero weight, so 320px frames rather than the board's 128px: this
            // draws at 240dp, and the old sheet was a five-times upscale. Also
            // the reason it is `LoopingDog` and not `AnimatedDog` — one clip on
            // repeat stops being seen after about three passes, and this dog is
            // the screen.
            LoopingDog(
                size = dogSize,
                playing = playing,
                modifier = Modifier.alpha(dogAlpha),
            )
        }
    }
}

/**
 * The card, flush with the bottom edge and rounded only where it meets the amber.
 *
 * Not a bottom sheet, and deliberately not built out of one. Nothing here
 * drags, nothing dismisses, and there is no scrim: it is the bottom third of
 * the layout that happens to have two round corners. A sheet would have given
 * the player a gesture that either does nothing or, worse, does something.
 */
@Composable
private fun WelcomeCard(
    state: OnboardingState,
    onAction: (OnboardingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.background.color, CardShape)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(horizontal = Dimension.D800)
            .padding(top = Dimension.D900, bottom = Dimension.D700),
    ) {
        Text(
            text = stringResource(Res.string.app_name),
            typography = AppTheme.typography.Display.D1000,
        )

        VerticalSpacerD300()

        Text(
            text = stringResource(Res.string.onboarding_tagline),
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.textSecondary,
        )

        VerticalSpacerD700()

        Rule(stringResource(Res.string.onboarding_rule_lines))
        Rule(stringResource(Res.string.onboarding_rule_regions))
        Rule(stringResource(Res.string.onboarding_rule_touch))

        VerticalSpacerD700()

        StartButton(
            enabled = !state.isFinishing,
            onClick = { onAction(OnboardingAction.Start) },
        )

        VerticalSpacerD400()

        ButtonGhost(
            onClick = { onAction(OnboardingAction.SkipTutorial) },
            enabled = !state.isFinishing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.onboarding_skip_tutorial))
        }
    }
}

/**
 * One rule, bulleted with the app's own mark.
 *
 * A paw rather than a dot or a tick, in the field's own amber, so the card and
 * the amber above it read as one screen. Decorative and unlabelled: the paw
 * says nothing the sentence beside it does not, and a screen reader announcing
 * "bullet" three times is three interruptions.
 */
@Composable
private fun Rule(text: String) {
    // Not the amber the field above is painted in. Amber-600 on this cream
    // measures 1.62:1, which is a shape you can only find once you already know
    // it is there, and these bullets are the first thing anyone reads about the
    // game. Brown-700 is 6.95:1 and still sits a step behind the sentence it
    // marks. The Pro sheet had the identical bug and took the identical fix.
    val ink = AppTheme.colors.textSecondary.color

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Spacer(
            modifier = Modifier
                .padding(top = Dimension.D100)
                .size(PawBulletSize)
                .drawBehind { drawPaw(ink, filled = true) },
        )
        HorizontalSpacerD500()
        Text(text = text, typography = AppTheme.typography.Body.B500)
    }
    VerticalSpacerD500()
}

/**
 * The primary, in the amber it is standing under.
 *
 * Built from [DeepSurface] rather than `ButtonPrimary` because the button
 * ladder's colours are role-named — Primary is `accentPrimary`, which is the
 * blue — and there is no accent slot that means "the amber". Recolouring the
 * ladder for one screen would repaint every primary button in the app, so this
 * borrows the same face-on-a-lip the ladder is drawn with and nothing else.
 *
 * Brown-900 on this amber measures 6.5:1. White measures 1.8:1, which is why
 * the copy is dark on it.
 */
@Composable
private fun StartButton(enabled: Boolean, onClick: () -> Unit) {
    val face = if (enabled) AppTheme.colors.status.warning else AppTheme.colors.surfaceDisabled
    val ink = if (enabled) AppTheme.colors.text else AppTheme.colors.onSurfaceDisabled

    DeepSurface(
        color = face.color,
        shape = Radii.Button,
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics { role = Role.Button },
    ) {
        Text(
            text = stringResource(Res.string.onboarding_start_tutorial),
            typography = AppTheme.typography.Label.L600,
            color = ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = Dimension.D700),
        )
    }
}

/**
 * Drawn, but reachable by nothing: not a finger, not focus, not a screen reader.
 *
 * Alpha is a drawing property and every input system ignores it, so the splash's
 * invisible copy of this screen would otherwise take taps that land on the real
 * buttons underneath, sit in the keyboard focus order, and give VoiceOver a
 * second "Walk me through one" to read out. Three systems, three modifiers:
 *
 * - taps are consumed on the initial pass, which runs parent to child, so no
 *   descendant gesture detector ever sees an unconsumed down;
 * - the subtree is a focus group that cancels any attempt to enter it, since
 *   `canFocus = false` on its own only skips the group node, not its children;
 * - semantics are hidden for the subtree, which is what maps to VoiceOver's
 *   `accessibilityElementsHidden`.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.inert(): Modifier = this
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    }
    .focusProperties { onEnter = { cancelFocusChange() } }
    .focusGroup()
    .semantics { hideFromAccessibility() }

/**
 * Paws across the amber, a shade lighter than it, well clear of the dog.
 *
 * Fixed positions rather than a scatter drawn from a seed. A random arrangement
 * would reshuffle on every recomposition, and at nine paws nobody can tell a
 * fixed arrangement from a random one anyway. They are sized against the width
 * and placed against both axes, so a tall phone spreads them out instead of
 * growing them.
 *
 * The middle is empty because the dog is standing in it.
 */
private fun DrawScope.drawFieldPaws(color: Color) {
    FieldPaws.forEach { spot ->
        val extent = size.width * spot.scale
        val left = size.width * spot.x - extent / 2f
        val top = size.height * spot.y - extent / 2f
        rotate(spot.turn, pivot = Offset(left + extent / 2f, top + extent / 2f)) {
            translate(left = left, top = top) {
                inset(
                    left = 0f,
                    top = 0f,
                    right = size.width - extent,
                    bottom = size.height - extent,
                ) {
                    drawPaw(color, filled = true)
                }
            }
        }
    }
}

/** A paw's centre and size as fractions of the field, plus its turn in degrees. */
internal class PawSpot(val x: Float, val y: Float, val scale: Float, val turn: Float)

internal val FieldPaws = listOf(
    PawSpot(x = 0.12f, y = 0.13f, scale = 0.10f, turn = -18f),
    PawSpot(x = 0.31f, y = 0.06f, scale = 0.07f, turn = 26f),
    PawSpot(x = 0.80f, y = 0.11f, scale = 0.09f, turn = 12f),
    PawSpot(x = 0.93f, y = 0.27f, scale = 0.06f, turn = -32f),
    PawSpot(x = 0.07f, y = 0.42f, scale = 0.08f, turn = 8f),
    PawSpot(x = 0.91f, y = 0.64f, scale = 0.09f, turn = -10f),
    PawSpot(x = 0.16f, y = 0.80f, scale = 0.08f, turn = 30f),
    PawSpot(x = 0.64f, y = 0.90f, scale = 0.07f, turn = -22f),
    PawSpot(x = 0.40f, y = 0.95f, scale = 0.05f, turn = 14f),
)

/** Square along the bottom, where the card runs off the edge of the screen. */
private val CardShape = RoundedCornerShape(
    topStart = Dimension.D1000,
    topEnd = Dimension.D1000,
)

private const val FieldTextureAlpha = 0.16f

/**
 * As big as the sprite sheet will carry, and the sheet is what caps it.
 *
 * The hero stills are 512px and render at
 * [com.sodogku.libraries.ui.components.dog.DogHeroSize] about one to one. A
 * sheet frame is 128px, because thirty of them have to fit in one bitmap that a
 * whole board shares, so even 160dp is already a 4x upscale at 3x density. The
 * art survives it better than most would: it is soft gradients with almost no
 * hard edges, and the first things to go are the eye highlights and the mouth
 * line. 240dp is where they are still there.
 *
 * The frame is mostly margin — the head is 106 by 77 of the 128 — so 240dp of
 * box is about 200dp of dog, which is what actually has to hold the field.
 */
private val WelcomeDogSize: Dp = Dimension.D1900 * 2.4f


/** Big enough to read as a paw, small enough to sit on a line of body copy. */
private val PawBulletSize: Dp = Dimension.D600

@Preview
@Composable
private fun OnboardingScreenPreview() {
    PreviewContent {
        OnboardingScreen(
            state = OnboardingState(),
            onAction = {},
        )
    }
}
