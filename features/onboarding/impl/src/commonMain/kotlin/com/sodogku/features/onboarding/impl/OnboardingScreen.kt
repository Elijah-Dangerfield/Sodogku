package com.sodogku.features.onboarding.impl

import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.dropShadow
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
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.Screen
import com.sodogku.libraries.ui.components.button.ButtonAccent
import com.sodogku.libraries.ui.components.button.ButtonGhost
import com.sodogku.libraries.ui.components.button.ButtonPrimary
import com.sodogku.libraries.ui.components.button.ButtonSize
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogMotion
import com.sodogku.libraries.ui.components.game.drawPaw
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.VerticalSpacerD300
import com.sodogku.system.VerticalSpacerD400
import com.sodogku.system.VerticalSpacerD500
import com.sodogku.system.VerticalSpacerD700
import com.sodogku.system.HorizontalSpacerD500
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.app_name
import sodogku.libraries.resources.generated.resources.onboarding_legal_and
import sodogku.libraries.resources.generated.resources.onboarding_legal_prefix
import sodogku.libraries.resources.generated.resources.onboarding_legal_privacy
import sodogku.libraries.resources.generated.resources.onboarding_legal_terms
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
    WelcomeContent(state = state, onAction = onAction)
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
    modifier: Modifier = Modifier,
) {
    Screen(
        modifier = modifier,
        // Full bleed on purpose: the status bar sits on the amber, and the card
        // insets its own content off the home indicator below.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            DogField(modifier = Modifier.fillMaxWidth().weight(1f))
            WelcomeCard(state = state, onAction = onAction)
        }
    }
}

/**
 * The amber, its paws, and the dog standing on it.
 *
 * The field is painted by a sibling Spacer rather than as a background on the
 * box, which is now only a habit: it was so the field could fade while the dog
 * stayed opaque through the splash handoff, and there is no handoff any more.
 * Left alone because a `drawBehind` sibling and a background modifier draw the
 * same pixels, and the paws want a DrawScope either way.
 *
 * The dog is sized against the field rather than fixed, and it has to be. Its
 * `Modifier.size` is clamped by whatever constraints it is handed, so on a short
 * phone a fixed 240dp square arrives as a 240 by 190 rectangle, and
 * `Dog` draws the frame into exactly the box it is given: a stretched dog, not
 * a cropped one. Taking the smaller of the two up front means the fallback is a
 * smaller dog at the proportions it was drawn at.
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
private fun DogField(modifier: Modifier = Modifier) {
    val field = AppTheme.colors.accentBrand.color
    val texture = ColorResource.White.withAlpha(FieldTextureAlpha).color

    val statusBar = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)

    BoxWithConstraints(modifier = modifier) {
        val dogSize = WelcomeDogSize
            .coerceAtMost(maxHeight - statusBar.asPaddingValues().calculateTopPadding())

        Spacer(
            modifier = Modifier
                .matchParentSize()
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
            // `Alive` and not `Settled`: hero weight, so 320px frames rather
            // than the board's 128px, and a changing repertoire rather than one
            // clip, which stops being seen after about three passes. This dog is
            // the screen.
            //
            // Nothing here reads the reduce-animations setting any more. `Dog`
            // does, which is the whole point of there being one of them.
            Dog(size = dogSize, motion = DogMotion.Alive)
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
 *
 * **The card paints its own amber**, in a box exactly its own size, and the
 * cream rounded rect goes on top of that. This looks redundant — the field
 * above is already amber — and it is the whole fix.
 *
 * A rounded corner is a hole. Whatever is behind the card shows through the two
 * wedges outside the arc, and the card is a *sibling* of the amber field, so
 * what is behind it is the page: the same cream as the card. First the curve was
 * invisible. Then the field was made to overdraw past its own bottom edge to
 * cover the wedges, and that traded an invisible curve for a worse artifact —
 * the overdraw ended partway down the arc, so each corner had a hard horizontal
 * line with amber above it and a grey shadow-on-cream wedge below. Measured on
 * device: amber to y=1515, cream from y=1570, 55px of dull grey in between.
 *
 * Two boxes in one layout node cannot disagree about where they end. There is no
 * distance to get wrong, no constraint to be clamped by, and nothing to keep in
 * sync when somebody changes the radius.
 */
@Composable
private fun WelcomeCard(
    state: OnboardingState,
    onAction: (OnboardingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // `shadow` rather than the `backgroundOverlay` BottomBar reaches for. Both
    // are black underneath so they render the same, but overlay means the scrim
    // behind a dialog, and a name that happens to resolve correctly is the kind
    // of thing that stops being true when someone retunes the palette.
    val lift = AppTheme.colors.shadow.withAlpha(CardShadowAlpha).color
    Box(modifier = modifier.fillMaxWidth().background(AppTheme.colors.accentBrand.color)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Cast upward, because the card meets the amber along its top edge
                // and nowhere else. `BottomBar` does the same thing for the same
                // reason; the only difference is the shape it is cast through, since
                // this one has corners and that one does not.
                //
                // Now that the amber is behind it, this lands on amber rather than on
                // cream, which is the difference between a shadow and a stain: a
                // neutral black over cream is a grey blob, over amber it is darker
                // amber.
                .dropShadow(CardShape) {
                    radius = CardShadowRadius
                    offset = Offset(0f, -CardShadowOffset)
                    color = lift
                }
                .background(AppTheme.colors.background.color, CardShape)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                // Roomier than the D800/D900/D700 this started at. The card is
                // the only thing on the screen with words in it, and at the old
                // padding the title sat close enough to the corner that the card
                // read as cramped precisely where it is meant to look like it
                // has room.
                //
                // Not derived from [CardCornerRadius], though an earlier note
                // here claimed it was. These held up unchanged when the radius
                // came down from 58dp to 24dp, which is the evidence that the
                // two are independent: the padding is set by the text, and the
                // radius by how separate the card should look.
                .padding(horizontal = Dimension.D1000)
                .padding(top = Dimension.D1100, bottom = Dimension.D900),
            ) {
            Text(
                text = stringResource(Res.string.app_name),
                // D1100 (34sp) rather than D1000 (28sp). It is a wordmark, not a
                // heading -- the only place the app says its own name -- and at
                // the heading size it sat level with the rules below it instead
                // of over them.
                typography = AppTheme.typography.Display.D1100,
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

            VerticalSpacerD500()

            LegalFooter(onAction = onAction)
        }
    }
}

/**
 * "By playing you agree to..." under the two buttons.
 *
 * There is no account here and nothing leaves the device on this screen, so
 * this is not a consent gate and deliberately does not behave like one: no
 * checkbox, no blocking, and both buttons work whether or not it is read. What
 * it does is form the agreement at the moment the player starts, which is worth
 * having in an app that sells a subscription, serves ads and posts scores to a
 * server.
 *
 * A Row of separate `Text`s rather than one `AnnotatedString` with link
 * annotations. The strings are already split per phrase for translation, and
 * clickable spans inside a single string would mean locating the span by index
 * in copy a translator is free to reorder.
 *
 * `FlowRow` because the four pieces do not fit on one line at larger font
 * scales, and the alternative is either an ellipsis in the middle of a legal
 * sentence or a link the player cannot reach.
 *
 * The links are underlined as well as coloured. Colour alone is not a link to
 * somebody who cannot see this one, and these are the two pieces of text on the
 * screen that have to be findable.
 */
@Composable
private fun LegalFooter(onAction: (OnboardingAction) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimension.D100, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val plain = AppTheme.typography.Caption.C300

        Text(
            text = stringResource(Res.string.onboarding_legal_prefix),
            typography = plain,
            color = AppTheme.colors.textSecondary,
        )
        LegalLink(
            text = stringResource(Res.string.onboarding_legal_terms),
            onClick = { onAction(OnboardingAction.OpenTerms) },
        )
        Text(
            text = stringResource(Res.string.onboarding_legal_and),
            typography = plain,
            color = AppTheme.colors.textSecondary,
        )
        LegalLink(
            text = stringResource(Res.string.onboarding_legal_privacy),
            onClick = { onAction(OnboardingAction.OpenPrivacy) },
        )
    }
}

/**
 * One of the two link words.
 *
 * `Role.Button` rather than a bare clickable: a screen reader user needs to be
 * told this is actionable, and these words are surrounded by text that is not.
 */
@Composable
private fun LegalLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        typography = AppTheme.typography.Caption.C300,
        color = AppTheme.colors.textSecondary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .semantics { role = Role.Button }
            .clickable(onClick = onClick),
    )
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
 * An ordinary [ButtonPrimary] on the [ButtonAccent.Brand] accent. It used to be
 * hand-built out of `DeepSurface`, the primitive the button ladder uses
 * internally, because there was no accent that meant "the amber" and recolouring
 * Primary would have repainted every button in the app.
 *
 * That was the wrong trade and it cost more than it looked like. The hand-built
 * version had to restate the disabled colours, the shape, the semantics role and
 * the text style, and it silently opted out of every later change to buttons --
 * including the one that made the press lip thicker, which is how it came up
 * again. A missing enum case is cheaper to add than a bespoke button is to keep.
 */
@Composable
private fun StartButton(enabled: Boolean, onClick: () -> Unit) {
    ButtonPrimary(
        onClick = onClick,
        accent = ButtonAccent.Brand,
        size = ButtonSize.Large,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(Res.string.onboarding_start_tutorial))
    }
}

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

/**
 * Square along the bottom, where the card runs off the edge of the screen.
 *
 * This card is the only thing between the amber and the buttons, so the curve is
 * what says it is a separate surface sitting in front rather than a colour
 * change halfway down the page. Square corners read as the latter.
 *
 * Tuned down from D1400 by eye. The argument for a generous radius was that a
 * small one stops reading as a separate surface, and that turned out to be true
 * only of a *very* small one: at 58dp the arc ate the top corners of the card
 * and crowded the title, and 24dp still reads as an edge sitting in front.
 */
private val CardCornerRadius = Dimension.D900

private val CardShape = RoundedCornerShape(
    topStart = CardCornerRadius,
    topEnd = CardCornerRadius,
)

/**
 * Soft and wide rather than tight and dark. The card is lifting a few
 * millimetres off a flat colour field, not floating over a photograph, so a hard
 * edge under it would read as a drawn line rather than as depth.
 *
 * The alpha is low for a reason particular to this screen: the shadow blurs out
 * past the card's rounded corners onto saturated amber, and neutral black over
 * amber goes grey-brown. At any strength that reads as a shadow on white it
 * reads as a smudge here.
 */
private const val CardShadowAlpha = 0.13f
private const val CardShadowRadius = 22f
private const val CardShadowOffset = 6f

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
