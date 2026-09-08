package com.sodogku.features.gate.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.features.gate.BlockingGate
import com.sodogku.libraries.core.doNothing
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.Screen
import com.sodogku.libraries.ui.components.button.ButtonGhost
import com.sodogku.libraries.ui.components.button.ButtonPrimary
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.VerticalSpacerD500
import com.sodogku.system.VerticalSpacerD1600
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.gate_legal_accept
import sodogku.libraries.resources.generated.resources.gate_legal_body
import sodogku.libraries.resources.generated.resources.gate_legal_privacy
import sodogku.libraries.resources.generated.resources.gate_legal_terms
import sodogku.libraries.resources.generated.resources.gate_legal_title
import sodogku.libraries.resources.generated.resources.gate_maintenance_title
import sodogku.libraries.resources.generated.resources.gate_update_action
import sodogku.libraries.resources.generated.resources.gate_update_body
import sodogku.libraries.resources.generated.resources.gate_update_title

/**
 * The wall. One of the three launch gates, drawn instead of the whole app.
 *
 * `App.kt` renders this **in place of** the nav host rather than navigating to
 * it, which is what makes "blocking" mean something: there is no back stack entry
 * to pop, and a deep link arriving while this is up is dropped rather than
 * queued. Back is swallowed on top of that, the same way `OfflineBlockScreen` and
 * `AccessDeniedScreen` swallow it — a wall you can leave with the system gesture
 * is a suggestion.
 *
 * The maintenance body is the operator's own words, straight from
 * `upgrade.maintenanceMessage`, and that is the one piece of copy in the app that
 * is deliberately not a string resource. See the KDoc on `AppMaintenanceMessage`:
 * an incident is worded during the incident, and a resource key can only ever
 * resolve to something written before it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LaunchGateScreen(
    gate: BlockingGate,
    onAction: (LaunchGateAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { doNothing() }

    Screen(modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimension.D1000),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            when (gate) {
                BlockingGate.ForceUpdate -> ForceUpdateBody()
                is BlockingGate.Maintenance -> MaintenanceBody(message = gate.message)
                is BlockingGate.ReacceptLegal -> ReacceptLegalBody(onAction = onAction)
            }

            Spacer(modifier = Modifier.weight(2f))

            GateActions(gate = gate, onAction = onAction)

            VerticalSpacerD1600()
        }
    }
}

@Composable
private fun ForceUpdateBody() {
    Dog(pose = DogPose.HardMode)
    VerticalSpacerD500()
    GateTitle(text = stringResource(Res.string.gate_update_title))
    VerticalSpacerD500()
    GateBody(text = stringResource(Res.string.gate_update_body))
}

@Composable
private fun MaintenanceBody(message: String) {
    Dog(pose = DogPose.Paused)
    VerticalSpacerD500()
    GateTitle(text = stringResource(Res.string.gate_maintenance_title))
    VerticalSpacerD500()
    GateBody(text = message)
}

@Composable
private fun ReacceptLegalBody(onAction: (LaunchGateAction) -> Unit) {
    Dog(pose = DogPose.Thinking)
    VerticalSpacerD500()
    GateTitle(text = stringResource(Res.string.gate_legal_title))
    VerticalSpacerD500()
    GateBody(text = stringResource(Res.string.gate_legal_body))
    VerticalSpacerD500()

    ButtonGhost(onClick = { onAction(LaunchGateAction.OpenTerms) }) {
        Text(text = stringResource(Res.string.gate_legal_terms))
    }
    ButtonGhost(onClick = { onAction(LaunchGateAction.OpenPrivacy) }) {
        Text(text = stringResource(Res.string.gate_legal_privacy))
    }
}

/**
 * Maintenance has no button on purpose. There is nothing the player can do about
 * it and nothing for a "try again" to retry — config refreshes on every
 * foreground, so leaving and coming back is already the retry, and the screen
 * takes itself down the moment the mode goes back to `off`.
 */
@Composable
private fun GateActions(gate: BlockingGate, onAction: (LaunchGateAction) -> Unit) {
    when (gate) {
        BlockingGate.ForceUpdate -> ButtonPrimary(
            onClick = { onAction(LaunchGateAction.OpenStore) },
            modifier = Modifier.fillMaxWidth(),
            deep = true,
        ) {
            Text(text = stringResource(Res.string.gate_update_action))
        }

        is BlockingGate.ReacceptLegal -> ButtonPrimary(
            // The versions the screen was drawn with, not a fresh read: a config
            // refresh landing between this frame and the tap would otherwise
            // record consent to something nobody was shown.
            onClick = {
                onAction(
                    LaunchGateAction.AcceptLegal(
                        termsVersion = gate.termsVersion,
                        privacyVersion = gate.privacyVersion,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
            deep = true,
        ) {
            Text(text = stringResource(Res.string.gate_legal_accept))
        }

        is BlockingGate.Maintenance -> doNothing()
    }
}

@Composable
private fun GateTitle(text: String) {
    Text(
        text = text,
        typography = AppTheme.typography.Display.D1000,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun GateBody(text: String) {
    Text(
        text = text,
        typography = AppTheme.typography.Body.B500,
        color = AppTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
    )
}

@Preview
@Composable
private fun LaunchGateScreenPreview_ForceUpdate() {
    PreviewContent {
        LaunchGateScreen(gate = BlockingGate.ForceUpdate, onAction = {})
    }
}

@Preview
@Composable
private fun LaunchGateScreenPreview_Maintenance() {
    PreviewContent {
        LaunchGateScreen(
            gate = BlockingGate.Maintenance(
                "We are moving the level packs to a new home. Sodogku will be back " +
                    "in about an hour, and nothing you have finished is going anywhere.",
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun LaunchGateScreenPreview_ReacceptLegal() {
    PreviewContent {
        LaunchGateScreen(
            gate = BlockingGate.ReacceptLegal(termsVersion = 2, privacyVersion = 2),
            onAction = {},
        )
    }
}
