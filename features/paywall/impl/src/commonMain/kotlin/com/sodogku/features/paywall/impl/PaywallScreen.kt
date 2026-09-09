package com.sodogku.features.paywall.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.HorizontalDivider
import com.sodogku.libraries.ui.components.button.ButtonGhost
import com.sodogku.libraries.ui.components.dialog.bottomsheet.BottomSheet
import com.sodogku.libraries.ui.components.dialog.bottomsheet.BottomSheetState
import com.sodogku.libraries.ui.components.dialog.bottomsheet.BottomSheetValue
import com.sodogku.libraries.ui.components.dialog.bottomsheet.rememberBottomSheetState
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.game.drawPaw
import com.sodogku.libraries.ui.components.icon.Icon
import com.sodogku.libraries.ui.components.icon.IconButton
import com.sodogku.libraries.ui.components.icon.IconSize
import com.sodogku.libraries.ui.components.icon.Icons
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.system.DeepSurface
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.HorizontalSpacerD400
import com.sodogku.system.Radii
import com.sodogku.system.VerticalSpacerD300
import com.sodogku.system.VerticalSpacerD500
import com.sodogku.system.VerticalSpacerD800
import com.sodogku.system.VerticalSpacerD1200
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.common_close
import sodogku.libraries.resources.generated.resources.paywall_already_pro
import sodogku.libraries.resources.generated.resources.paywall_benefit_boosters
import sodogku.libraries.resources.generated.resources.paywall_benefit_free_helps
import sodogku.libraries.resources.generated.resources.paywall_benefit_jump
import sodogku.libraries.resources.generated.resources.paywall_benefit_no_ads
import sodogku.libraries.resources.generated.resources.paywall_benefit_offline
import sodogku.libraries.resources.generated.resources.paywall_buy
import sodogku.libraries.resources.generated.resources.paywall_headline
import sodogku.libraries.resources.generated.resources.paywall_nothing_to_restore
import sodogku.libraries.resources.generated.resources.paywall_not_now
import sodogku.libraries.resources.generated.resources.paywall_one_time
import sodogku.libraries.resources.generated.resources.paywall_purchase_failed
import sodogku.libraries.resources.generated.resources.paywall_restore
import sodogku.libraries.resources.generated.resources.paywall_stand_in_reason
import sodogku.libraries.resources.generated.resources.paywall_store_unavailable
import sodogku.libraries.resources.generated.resources.paywall_store_unreachable
import sodogku.libraries.resources.generated.resources.paywall_subtitle

/**
 * The Pro offer, SPEC 5.1.
 *
 * Five benefits, one price, and a way out. No countdown, no crossed-out
 * "regular price", no second screen that appears when you decline: this is a
 * non-consumable in a puzzle game, and the dark patterns that work on
 * subscriptions read as insulting here.
 *
 * The Restore control is not optional — Apple requires a visible one, and it
 * also lives in Settings. Having it here as well is what makes the paywall the
 * right screen to send a player to after a reinstall.
 *
 * ## It is a sheet, and it leaves like one
 *
 * The content lives inside [BottomSheet] rather than a `Screen`, and the caller
 * hands it the [BottomSheetState] its destination owns. Closing goes through
 * [BottomSheetState.dismiss] — the sheet slides down, *then* the route pops.
 * Popping first would tear the sheet off the screen with no animation at all,
 * and the previous arrangement (a full `screen<>` with a slide-up entrance) sent
 * it out through the top of the display on the way back, which is what a player
 * reported.
 */
@Composable
fun PaywallScreen(
    state: PaywallState,
    sheetState: BottomSheetState,
    onDismissed: () -> Unit,
    onAction: (PaywallAction) -> Unit,
    modifier: Modifier = Modifier,
    standInNote: String = "",
    /**
     * Whether this sheet is here because an ad could not be filled.
     *
     * Passed from the route rather than derived from the dwell, which expires:
     * the sheet would stop explaining itself five seconds in, which is roughly
     * when somebody who was waiting for bones starts reading it.
     */
    isStandIn: Boolean = false,
) {
    val dwelling = state.secondsUntilDismissible > 0
    val cream = AppTheme.colors.background.color

    BottomSheet(
        onDismissRequest = onDismissed,
        state = sheetState,
        showDragHandle = false,
        contentAlignment = Alignment.Start,
        // The dwell holds every way *this sheet* offers to close, which now
        // includes the two a sheet adds: the drag and the scrim. The system back
        // gesture still gets through, on purpose — see the note on
        // `PaywallAction.Dismiss`. What the lock buys is a default, not a cage.
        sheetGesturesEnabled = !dwelling,
        shouldDismissOnClickOutside = !dwelling,
        // The sheet owns the scroll, and with it the sheet's height. See the
        // anchor note on `scrollableContent`: content-decided height is what let
        // a drag get snapped back to the top.
        scrollableContent = true,
        // The amber status bar, as a fill rather than a system call. The sheet's
        // container already runs to the top of the display; what sits under the
        // clock is that container, padded down by the status bar inset, which is
        // why the strip used to be cream. Colouring it is the whole feature, and
        // it costs nothing on the way out: no window flag is set, so there is
        // nothing to restore when the sheet leaves by the X, by back, by a drag,
        // by a purchase, or by not existing after a process death.
        backgroundColor = ProAmber,
        // The cream the container used to supply, painted back everywhere except
        // that strip. It lands on the sheet's own scrolling column, which is
        // pinned to the viewport, so it covers the sheet whether the content
        // fills it or not; painting the content instead left a slab of amber
        // under the last button on a tall phone.
        //
        // Drawn past the bottom edge because the sheet pads by the *bottom*
        // inset too, and that band is outside anything laid out in here. A draw
        // modifier ahead of the scroll in the chain escapes the scroll's clip,
        // and the sheet's own rounded-corner clip trims the overdraw at the
        // bottom of the display, where the sheet ends anyway.
        modifier = Modifier.drawBehind {
            drawRect(color = cream, size = Size(size.width, size.height * BottomInsetOverdraw))
        },
    ) {
        Column(modifier = modifier.fillMaxWidth()) {
            ProSlab(
                // Null rather than disabled: a close button that does nothing
                // reads as a broken sheet, and this one comes back the moment
                // the dwell is up.
                onClose = if (dwelling) null else ({ onAction(PaywallAction.Dismiss) }),
            )

            Column(modifier = Modifier.padding(horizontal = Dimension.D800)) {
                VerticalSpacerD500()

                // Why this appeared, in the player's words. Without it the sheet
                // is a decent Pro pitch that turns up unbidden after a booster
                // tap, which reads as the app selling at you rather than as a
                // substitute for something it could not deliver.
                if (isStandIn) {
                    Text(
                        text = stringResource(Res.string.paywall_stand_in_reason),
                        typography = AppTheme.typography.Body.B500,
                        color = AppTheme.colors.textSecondary,
                    )
                    VerticalSpacerD500()
                }

                if (standInNote.isNotEmpty()) {
                    Text(
                        text = standInNote,
                        typography = AppTheme.typography.Caption.C300,
                        color = AppTheme.colors.status.warning,
                    )
                    VerticalSpacerD300()
                }

                // Last thing before the list, and it has to stay last. It reads
                // as a lead-in to the bullets rather than as a pitch of its own,
                // so anything printed between it and the first paw orphans it.
                // The stand-in explanation used to land in exactly that gap.
                Text(
                    text = stringResource(Res.string.paywall_subtitle),
                    typography = AppTheme.typography.Body.B600,
                    color = AppTheme.colors.textSecondary,
                )

                VerticalSpacerD500()

                Benefit(stringResource(Res.string.paywall_benefit_no_ads))
                Benefit(stringResource(Res.string.paywall_benefit_offline))
                Benefit(stringResource(Res.string.paywall_benefit_free_helps))
                Benefit(stringResource(Res.string.paywall_benefit_boosters))
                Benefit(stringResource(Res.string.paywall_benefit_jump))

                VerticalSpacerD800()

                HorizontalDivider()

                VerticalSpacerD800()

                state.message?.let { message ->
                    Text(
                        text = stringResource(message.resource()),
                        typography = AppTheme.typography.Body.B500,
                        color = AppTheme.colors.textSecondary,
                    )
                    VerticalSpacerD500()
                }

                BuyButton(
                    priceLabel = state.priceLabel,
                    enabled = !state.isWorking && !state.isPro,
                    onClick = { onAction(PaywallAction.Buy) },
                )

                VerticalSpacerD500()

                Text(
                    text = stringResource(Res.string.paywall_one_time),
                    typography = AppTheme.typography.Caption.C300,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                VerticalSpacerD500()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ButtonGhost(
                        onClick = { onAction(PaywallAction.Restore) },
                        enabled = !state.isWorking,
                    ) {
                        Text(text = stringResource(Res.string.paywall_restore))
                    }

                    ButtonGhost(
                        onClick = { onAction(PaywallAction.Dismiss) },
                        enabled = !dwelling,
                    ) {
                        // A bare numeral while the dwell runs, the way every
                        // skippable ad counts itself down. Nothing to translate,
                        // and nothing that has to be read to be understood.
                        Text(
                            text = if (dwelling) "${state.secondsUntilDismissible}"
                            else stringResource(Res.string.paywall_not_now),
                        )
                    }
                }

                VerticalSpacerD1200()
            }
        }
    }
}

/**
 * The amber band across the top, with the dog breaking out of its bottom edge.
 *
 * The overhang is the whole idea of the header, so it is built out of layout
 * rather than an offset: the slab reserves [DogOverhang] of bottom padding
 * *outside* its own background, and the dog is aligned to the bottom of the box
 * they share. Half the dog therefore lands on the amber and half on the cream,
 * and neither half can be clipped, because nothing here clips.
 *
 */
@Composable
private fun ProSlab(onClose: (() -> Unit)?, modifier: Modifier = Modifier) {
    val texture = ColorResource.White.withAlpha(SlabTextureAlpha).color

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = DogOverhang)
                .background(ProAmber.color, SlabShape)
                .drawBehind { drawSlabPaws(texture) }
                .padding(horizontal = Dimension.D800)
                .padding(top = Dimension.D500, bottom = Dimension.D1200),
        ) {
            // An X, not a chevron. A chevron says there is a page behind this
            // one to go back to; this is a sheet that slid up over the board and
            // the only thing it can do is go away again.
            //
            // Reserved whether or not the button is there, so the slab does not
            // change height, and the dog does not jump, when the dwell ends.
            Box(modifier = Modifier.height(CloseRowHeight), contentAlignment = Alignment.CenterStart) {
                if (onClose != null) {
                    IconButton(
                        icon = Icons.X(stringResource(Res.string.common_close)),
                        onClick = onClose,
                        iconColor = AppTheme.colors.text,
                        size = IconButton.Size.Large,
                    )
                }
            }

            VerticalSpacerD500()

            Text(
                text = stringResource(Res.string.paywall_headline),
                typography = AppTheme.typography.Display.D1100,
                color = AppTheme.colors.text,
                // Two lines is the shape the headline was written for. Left to
                // itself it wraps to three on a narrow phone and the dog's ears
                // land in the middle of the last one.
                modifier = Modifier.fillMaxWidth(HeadlineWidthFraction),
            )
        }

        // Not [DogPose.Solved]. That one is holding a trophy, which is the pose
        // the win sheet uses, and on a screen asking for money it congratulates
        // the player for a purchase they have not made. Thinking is alert, it is
        // tilted back toward the headline it sits beside, and the pencil behind
        // its ear is the only prop in the set that belongs to a puzzle rather
        // than to a result.
        Dog(
            pose = DogPose.Thinking,
            size = SlabDogSize,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Dimension.D700),
        )
    }
}

/**
 * One promise, bulleted with the app's own mark.
 *
 * A paw rather than a tick because a tick is a form control and this is a list
 * of things a dog does for you. Decorative, and unlabelled on purpose: the paw
 * says nothing the sentence beside it does not.
 *
 * Drawn through [Icons.Paw] rather than straight onto a canvas, so the bullet is
 * the same shape as every other paw in the app and picks up the icon system's
 * sizing. It is [com.sodogku.system.color.Colors.textSecondary] and not the
 * amber: the amber the slab and the button are made of measures **1.6:1** on the
 * sheet's cream, which is a bullet you cannot see. Brown-700 measures 7.0:1 on
 * the same cream and still sits a step behind the sentence it marks.
 */
@Composable
private fun Benefit(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(
            icon = Icons.Paw.decorative,
            size = PawBulletSize,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.padding(top = Dimension.D100),
        )
        HorizontalSpacerD400()
        Text(text = text, typography = AppTheme.typography.Body.B500)
    }
    VerticalSpacerD500()
}

/**
 * The offer, with the price on the right of it.
 *
 * **The right half is empty when the store has not answered**, and that is the
 * designed state rather than a hole in the layout. The price is per-storefront
 * and only the store knows it (SPEC 4.3); today no product exists at all, so
 * this is what every build renders. A placeholder price that turns out to be
 * wrong in a currency nobody thought about is worse than no price, and a button
 * that reads "Get Pro" on its own is a complete sentence.
 */
@Composable
private fun BuyButton(priceLabel: String?, enabled: Boolean, onClick: () -> Unit) {
    val face = if (enabled) ProAmber else AppTheme.colors.surfaceDisabled
    val ink = if (enabled) AppTheme.colors.text else AppTheme.colors.onSurfaceDisabled

    DeepSurface(
        color = face.color,
        shape = Radii.Button,
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics { role = Role.Button },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimension.D800, vertical = Dimension.D700),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.paywall_buy),
                typography = AppTheme.typography.Label.L600,
                color = ink,
            )
            priceLabel?.let {
                Text(text = it, typography = AppTheme.typography.Label.L600, color = ink)
            }
        }
    }
}

/**
 * The texture in the slab: a few paws in the top-right, a shade lighter than the
 * amber they sit on.
 *
 * Fixed positions rather than a random scatter. A seed drawn per composition
 * would reshuffle the texture on every recomposition, and at four paws nobody
 * can tell a fixed arrangement from a random one anyway.
 */
private fun DrawScope.drawSlabPaws(color: Color) {
    SlabPaws.forEach { spot ->
        val extent = size.height * spot.scale
        val left = size.width * spot.x
        val top = size.height * spot.y
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

private class PawSpot(val x: Float, val y: Float, val scale: Float, val turn: Float)

private val SlabPaws = listOf(
    PawSpot(x = 0.58f, y = 0.06f, scale = 0.30f, turn = 18f),
    PawSpot(x = 0.79f, y = 0.26f, scale = 0.22f, turn = -24f),
    PawSpot(x = 0.90f, y = 0.02f, scale = 0.17f, turn = 6f),
    PawSpot(x = 0.68f, y = 0.44f, scale = 0.14f, turn = -8f),
)

/**
 * The one amber in the palette.
 *
 * It is reached through the status token because that is where the ramp put it,
 * not because anything here is a warning. Named once so the slab, the paw
 * bullets and the buy button cannot drift apart.
 *
 * The type on it is [com.sodogku.system.color.Colors.text] rather than white.
 * The mockup asked for white and white on this amber is 1.8:1, which is below
 * every WCAG threshold there is; brown-900 on it measures 6.5:1, and the header
 * still reads as an amber slab with a headline on it.
 */
private val ProAmber: ColorResource
    @ReadOnlyComposable
    @Composable
    get() = AppTheme.colors.status.warning

/** Square across the top, where the sheet's own corners already round it. */
private val SlabShape = RoundedCornerShape(
    bottomStart = Dimension.D1000,
    bottomEnd = Dimension.D1000,
)

/**
 * How far the texture paws lift off the amber they sit on.
 *
 * Raised from 0.14, which was a paw you had to be told was there: white at that
 * alpha over amber-600 lands on `#FFBE24`, **1.08:1** against the slab and 2.7
 * points of CIE L*. At 0.28 it is `#FFC847`, **1.16:1** and 5.3 points of L*.
 * About double the step, and still nowhere near the 3:1 that would turn a
 * texture into a pattern of spots competing with the headline.
 */
private const val SlabTextureAlpha = 0.28f
private const val HeadlineWidthFraction = 0.72f

/**
 * How far past the sheet's viewport the cream is painted, as a multiple of it.
 *
 * Only the bottom system-bar inset is actually being covered, which is 24dp on
 * gesture navigation and 48dp with three buttons. A whole extra viewport is
 * cheap, needs no inset lookup, and cannot come up short on a device whose bars
 * are taller than either.
 */
private const val BottomInsetOverdraw = 2f

/** How far the dog hangs below the slab. Half of it, so half of it is on cream. */
private val DogOverhang: Dp = Dimension.D1500
private val SlabDogSize: Dp = Dimension.D1900 + Dimension.D1500
private val CloseRowHeight: Dp = Dimension.D1300
private val PawBulletSize = IconSize.Small

private fun PaywallMessage.resource() = when (this) {
    PaywallMessage.AlreadyPro -> Res.string.paywall_already_pro
    PaywallMessage.PurchaseFailed -> Res.string.paywall_purchase_failed
    PaywallMessage.StoreUnavailable -> Res.string.paywall_store_unavailable
    PaywallMessage.NothingToRestore -> Res.string.paywall_nothing_to_restore
    PaywallMessage.StoreUnreachable -> Res.string.paywall_store_unreachable
}

@Preview
@Composable
private fun PaywallScreenPreview() {
    PreviewContent {
        PaywallScreen(
            state = PaywallState(priceLabel = "$4.99"),
            sheetState = rememberBottomSheetState(BottomSheetValue.Expanded),
            onDismissed = {},
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun PaywallScreenPreview_StandingInForAnAd() {
    PreviewContent {
        PaywallScreen(
            state = PaywallState(priceLabel = "$4.99", secondsUntilDismissible = 5),
            sheetState = rememberBottomSheetState(BottomSheetValue.Expanded),
            onDismissed = {},
            onAction = {},
            standInNote = "booster_grant · no_fill",
            isStandIn = true,
        )
    }
}

@Preview
@Composable
private fun PaywallScreenPreview_NoPriceYet() {
    PreviewContent {
        PaywallScreen(
            state = PaywallState(message = PaywallMessage.StoreUnreachable),
            sheetState = rememberBottomSheetState(BottomSheetValue.Expanded),
            onDismissed = {},
            onAction = {},
        )
    }
}
