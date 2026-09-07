package com.sodogku.libraries.navigation.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.libraries.core.doNothing
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.Screen
import com.sodogku.libraries.ui.components.button.ButtonSecondary
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.VerticalSpacerD1600
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Blocking surface for the locked `403` access-denied envelope. The copy is
 * keyed off the machine-readable [reason] (the server never sends copy on the
 * wire); unknown tokens fall back to a generic block message. Back is
 * swallowed — a banned/suspended user has nowhere to go behind this.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun AccessDeniedScreen(
    reason: String,
    until: String?,
    appealUrl: String?,
    onAppeal: (String) -> Unit,
) {
    BackHandler { doNothing() }
    val (title, message) = when (reason) {
        "banned" -> "Account banned" to
            "This account has been permanently banned for violating our terms."
        "suspended" -> "Account suspended" to
            "This account has been temporarily suspended."
        else -> "Access denied" to
            "This account can't access the app right now."
    }
    Screen { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Dimension.D1000),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = title,
                typography = AppTheme.typography.Display.D1000,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(Dimension.D500))
            Text(
                text = message,
                typography = AppTheme.typography.Body.B400,
                textAlign = TextAlign.Center,
            )
            if (until != null) {
                Spacer(modifier = Modifier.height(Dimension.D400))
                Text(
                    text = "Access is restored on $until.",
                    typography = AppTheme.typography.Body.B500,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.weight(2f))

            if (appealUrl != null) {
                ButtonSecondary(
                    onClick = { onAppeal(appealUrl) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Appeal this decision")
                }
                VerticalSpacerD1600()
            }
        }
    }
}

@Preview
@Composable
private fun AccessDeniedScreenPreview_Banned() {
    PreviewContent {
        AccessDeniedScreen(
            reason = "banned",
            until = null,
            appealUrl = "https://example.com/appeal",
            onAppeal = {},
        )
    }
}

@Preview
@Composable
private fun AccessDeniedScreenPreview_Suspended_WithUntil() {
    PreviewContent {
        AccessDeniedScreen(
            reason = "suspended",
            until = "2026-08-01T00:00:00Z",
            appealUrl = null,
            onAppeal = {},
        )
    }
}
