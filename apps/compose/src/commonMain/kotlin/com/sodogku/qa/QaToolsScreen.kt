package com.sodogku.qa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sodogku.libraries.ui.components.ListItem
import com.sodogku.libraries.ui.components.ListItemAccessory
import com.sodogku.libraries.core.BuildInfo
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
 * Mostly streak tools. They are here because the streak is the one thing in the
 * app that cannot be tested by playing: it needs days to pass. Anything else
 * that becomes untestable-without-waiting belongs on this screen too.
 *
 * The feedback switch is the exception, and it is here because this is the one
 * screen a tester can reach without the button they are switching off.
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
            modifier = Modifier
                .fillMaxSize()
                .screenContentPadding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimension.D400),
        ) {
            Text(text = "Feedback", typography = AppTheme.typography.Heading.H600)

            ListItem(
                headlineText = "Floating feedback button",
                supportingText = "Drag it anywhere. Tap it to file a directive.",
                accessory = ListItemAccessory.Switch(
                    checked = state.feedbackFabShown,
                    onCheckedChange = { onAction(QaToolsAction.ShowFeedbackFab(it)) },
                ),
            )

            VerticalSpacerD500()

            // Debug only, while the Feedback section above is not.
            //
            // The feedback button shows on `isTesterBuild`, which is debug *or*
            // TestFlight, so a TestFlight tester who wants it out of the way has
            // to be able to reach the switch. Everything below wipes or forges
            // streak history, which is not something to hand a tester with no
            // way to undo it: their play days are the only copy there is.
            if (!BuildInfo.isDebug) return@Column

            if (state.canShiftDay) {
                VerticalSpacerD500()

                Text(text = "Clock", typography = AppTheme.typography.Heading.H600)

                Text(
                    text = if (state.dayShift == 0) {
                        "Running on real time. Shifting the day moves the daily, its " +
                            "countdown, the streak and the calendar together, and survives " +
                            "a restart. Screens already open pick it up when they next " +
                            "reload — the streak page redraws on entry, the game screen on " +
                            "relaunch."
                    } else {
                        "Shifted ${state.dayShift.withSign()} days. " +
                            "Real time is untouched; only the date the app resolves has moved."
                    },
                    typography = AppTheme.typography.Body.B500,
                    color = AppTheme.colors.textSecondary,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D400)) {
                    listOf(-7, -1, 1, 7).forEach { days ->
                        ButtonSecondary(
                            onClick = { onAction(QaToolsAction.ShiftDay(days)) },
                            size = ButtonSize.Small,
                        ) {
                            Text("${days.withSign()}d")
                        }
                    }
                }

                ButtonSecondary(
                    onClick = { onAction(QaToolsAction.ClearDayShift) },
                    size = ButtonSize.Small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Back to real time")
                }
            }

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

private fun Int.withSign(): String = if (this < 0) toString() else "+$this"
