package com.sodogku.features.paywall.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.Screen
import com.sodogku.libraries.ui.components.button.ButtonGhost
import com.sodogku.libraries.ui.components.button.ButtonPrimary
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.header.TopBar
import com.sodogku.libraries.ui.components.text.BulletRow
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.screenContentPadding
import com.sodogku.system.AppTheme
import com.sodogku.system.VerticalSpacerD300
import com.sodogku.system.VerticalSpacerD500
import com.sodogku.system.VerticalSpacerD800
import com.sodogku.system.VerticalSpacerD1200
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.paywall_already_pro
import sodogku.libraries.resources.generated.resources.paywall_benefit_boosters
import sodogku.libraries.resources.generated.resources.paywall_benefit_free_helps
import sodogku.libraries.resources.generated.resources.paywall_benefit_jump
import sodogku.libraries.resources.generated.resources.paywall_benefit_no_ads
import sodogku.libraries.resources.generated.resources.paywall_benefit_offline
import sodogku.libraries.resources.generated.resources.paywall_buy
import sodogku.libraries.resources.generated.resources.paywall_buy_priced
import sodogku.libraries.resources.generated.resources.paywall_nothing_to_restore
import sodogku.libraries.resources.generated.resources.paywall_not_now
import sodogku.libraries.resources.generated.resources.paywall_one_time
import sodogku.libraries.resources.generated.resources.paywall_purchase_failed
import sodogku.libraries.resources.generated.resources.paywall_restore
import sodogku.libraries.resources.generated.resources.paywall_store_unavailable
import sodogku.libraries.resources.generated.resources.paywall_store_unreachable
import sodogku.libraries.resources.generated.resources.paywall_subtitle
import sodogku.libraries.resources.generated.resources.paywall_title

/**
 * The Pro offer, SPEC 5.1.
 *
 * Five benefits, one price, and a way out. No countdown, no crossed-out
 * "regular price", no second screen that appears when you decline: this is a
 * $4.99 non-consumable in a puzzle game, and the dark patterns that work on
 * subscriptions read as insulting here.
 *
 * The Restore control is not optional — Apple requires a visible one, and it
 * also lives in Settings. Having it here as well is what makes the paywall the
 * right screen to send a player to after a reinstall.
 */
@Composable
fun PaywallScreen(
    state: PaywallState,
    onAction: (PaywallAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Screen(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopBar(
                title = stringResource(Res.string.paywall_title),
                onNavigateBack = { onAction(PaywallAction.Dismiss) },
                scrollState = scrollState,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .screenContentPadding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Dog(pose = DogPose.Solved)

            VerticalSpacerD500()

            Text(
                text = stringResource(Res.string.paywall_subtitle),
                typography = AppTheme.typography.Body.B600,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )

            VerticalSpacerD800()

            Column(modifier = Modifier.fillMaxWidth()) {
                Benefit(stringResource(Res.string.paywall_benefit_no_ads))
                Benefit(stringResource(Res.string.paywall_benefit_offline))
                Benefit(stringResource(Res.string.paywall_benefit_free_helps))
                Benefit(stringResource(Res.string.paywall_benefit_boosters))
                Benefit(stringResource(Res.string.paywall_benefit_jump))
            }

            VerticalSpacerD800()

            state.message?.let { message ->
                Text(
                    text = stringResource(message.resource()),
                    typography = AppTheme.typography.Body.B500,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                VerticalSpacerD500()
            }

            ButtonPrimary(
                onClick = { onAction(PaywallAction.Buy) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isWorking && !state.isPro,
                deep = true,
            ) {
                Text(
                    text = state.priceLabel
                        ?.let { stringResource(Res.string.paywall_buy_priced, it) }
                        ?: stringResource(Res.string.paywall_buy),
                )
            }

            VerticalSpacerD300()

            Text(
                text = stringResource(Res.string.paywall_one_time),
                typography = AppTheme.typography.Caption.C300,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )

            VerticalSpacerD500()

            ButtonGhost(
                onClick = { onAction(PaywallAction.Restore) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isWorking,
            ) {
                Text(text = stringResource(Res.string.paywall_restore))
            }

            ButtonGhost(
                onClick = { onAction(PaywallAction.Dismiss) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(Res.string.paywall_not_now))
            }

            VerticalSpacerD1200()
        }
    }
}

@Composable
private fun Benefit(text: String) {
    BulletRow(modifier = Modifier.fillMaxWidth()) {
        Text(text = text, typography = AppTheme.typography.Body.B500)
    }
    VerticalSpacerD300()
}

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
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun PaywallScreenPreview_NoPriceYet() {
    PreviewContent {
        PaywallScreen(
            state = PaywallState(isWorking = true, message = PaywallMessage.StoreUnreachable),
            onAction = {},
        )
    }
}
