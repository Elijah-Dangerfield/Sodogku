package com.sodogku.features.game.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.libraries.ui.components.button.ButtonGhost
import com.sodogku.libraries.ui.components.button.ButtonPrimary
import com.sodogku.libraries.ui.components.button.ButtonSecondary
import com.sodogku.libraries.ui.components.dialog.Dialog
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import org.jetbrains.compose.resources.stringResource
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.booster_bone_body
import sodogku.libraries.resources.generated.resources.booster_bone_title
import sodogku.libraries.resources.generated.resources.booster_have
import sodogku.libraries.resources.generated.resources.booster_not_now
import sodogku.libraries.resources.generated.resources.booster_sniff_body
import sodogku.libraries.resources.generated.resources.booster_sniff_title
import sodogku.libraries.resources.generated.resources.booster_treat_body
import sodogku.libraries.resources.generated.resources.booster_treat_title
import sodogku.libraries.resources.generated.resources.booster_use
import sodogku.libraries.resources.generated.resources.booster_watch_ad

/**
 * What a booster is and what it costs, before it costs anything.
 *
 * Shown on the first tap of each booster whatever the count, and on every tap
 * once the count is zero. Spending a consumable cannot be undone, so the first
 * time someone taps an unfamiliar button they find out what happens before it
 * happens — after that the button just works.
 */
@Composable
fun BoosterPrompt(
    consumable: Consumable,
    held: Int,
    /**
     * What an ad tops this booster up to, from `boosters.refillTo`.
     *
     * Passed in rather than read from the compile-time constant, because the
     * refill itself is config-driven now: with the constant here, raising the
     * key above three would have had the button promise less than the tap
     * delivers, and lowering it would have had the button lie.
     */
    refillTo: Int,
    onUse: () -> Unit,
    onWatchAd: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimension.D500),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Dog(pose = consumable.pose())

            Text(
                text = stringResource(consumable.title()),
                typography = AppTheme.typography.Heading.H700,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(consumable.body()),
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.booster_have, held),
                typography = AppTheme.typography.Caption.C300,
                color = AppTheme.colors.textSecondary,
            )

            // "Use one" only appears when there is one to use. Offering it at
            // zero and failing silently is how a button teaches distrust.
            if (held > 0 && consumable != Consumable.Bone) {
                ButtonPrimary(onClick = onUse, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.booster_use))
                }
                ButtonSecondary(onClick = onWatchAd, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.booster_watch_ad, refillTo))
                }
            } else {
                ButtonPrimary(onClick = onWatchAd, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.booster_watch_ad, refillTo))
                }
            }

            ButtonGhost(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.booster_not_now))
            }
        }
    }
}

private fun Consumable.pose(): DogPose = when (this) {
    Consumable.Bone -> DogPose.HardMode
    Consumable.Sniff -> DogPose.Focused
    Consumable.Treat -> DogPose.Solved
}

private fun Consumable.title() = when (this) {
    Consumable.Bone -> Res.string.booster_bone_title
    Consumable.Sniff -> Res.string.booster_sniff_title
    Consumable.Treat -> Res.string.booster_treat_title
}

private fun Consumable.body() = when (this) {
    Consumable.Bone -> Res.string.booster_bone_body
    Consumable.Sniff -> Res.string.booster_sniff_body
    Consumable.Treat -> Res.string.booster_treat_body
}
