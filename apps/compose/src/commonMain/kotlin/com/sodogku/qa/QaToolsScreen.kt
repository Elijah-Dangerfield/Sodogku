package com.sodogku.qa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sodogku.libraries.ui.components.Screen
import com.sodogku.libraries.ui.components.header.TopBar
import com.sodogku.libraries.ui.components.button.ButtonSecondary
import com.sodogku.libraries.ui.components.button.ButtonSize
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.screenContentPadding
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.VerticalSpacerD500

/**
 * Debug-only tools, reached from Settings or from the shake dialog.
 *
 * Dev-facing, so every string here is a hardcoded constant rather than a string
 * resource. Putting these in `strings.xml` would ask a translator to localise
 * "Seed 6 days" and would put developer tooling in the shipped resource table.
 * `ShakeDialog` already does this for its network inspector button, for the same
 * reason.
 *
 * Streak tools only, for now. They are here because the streak is the one thing
 * in the app that cannot be tested by playing: it needs days to pass. Anything
 * else that becomes untestable-without-waiting belongs on this screen too.
 */
@Composable
fun QaToolsScreen(
    state: QaToolsState,
    onAction: (QaToolsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Screen(
        modifier = modifier.fillMaxSize(),
        topBar = { TopBar(title = "QA tools", onNavigateBack = { onAction(QaToolsAction.Back) }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().screenContentPadding(padding),
            verticalArrangement = Arrangement.spacedBy(Dimension.D400),
        ) {
            Text(text = "Streak", typography = AppTheme.typography.Heading.H600)

            Text(
                text = if (state.loaded) {
                    "Today is ${state.today}. Streak ${state.streak}, longest ${state.longest}, " +
                        "${state.daysRecorded} days recorded. " +
                        if (state.playedToday) "Today already counts." else "Today does not count yet."
                } else {
                    "Reading..."
                },
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
            )

            VerticalSpacerD500()

            // Seeds end *yesterday*, so the next board you finish is the one
            // that bumps the streak and fires the celebration. That is the thing
            // you came here to watch.
            Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D400)) {
                listOf(1, 6, 29).forEach { days ->
                    ButtonSecondary(
                        onClick = { onAction(QaToolsAction.SeedStreak(days)) },
                        size = ButtonSize.Small,
                    ) {
                        Text("Seed $days")
                    }
                }
            }

            ButtonSecondary(
                onClick = { onAction(QaToolsAction.MarkTodayPlayed) },
                size = ButtonSize.Small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Mark today played")
            }

            ButtonSecondary(
                onClick = { onAction(QaToolsAction.ResetPrompts) },
                size = ButtonSize.Small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reset streak prompts (intention + celebrations)")
            }

            ButtonSecondary(
                onClick = { onAction(QaToolsAction.ClearPlayDays) },
                size = ButtonSize.Small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear every played day")
            }
        }
    }
}
