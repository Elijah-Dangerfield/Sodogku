package com.sodogku.features.settings.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.ListItemAccessory
import com.sodogku.libraries.ui.components.ListSection
import com.sodogku.libraries.ui.components.ListSectionItem
import com.sodogku.libraries.ui.components.Screen
import com.sodogku.libraries.ui.components.header.TopBar
import com.sodogku.libraries.ui.screenContentPadding
import com.sodogku.system.VerticalSpacerD500
import com.sodogku.system.VerticalSpacerD800
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.settings_achievements
import sodogku.libraries.resources.generated.resources.settings_achievements_show
import sodogku.libraries.resources.generated.resources.settings_achievements_show_body
import sodogku.libraries.resources.generated.resources.settings_colorblind
import sodogku.libraries.resources.generated.resources.settings_colorblind_body
import sodogku.libraries.resources.generated.resources.settings_feedback
import sodogku.libraries.resources.generated.resources.settings_haptics
import sodogku.libraries.resources.generated.resources.settings_haptics_body
import sodogku.libraries.resources.generated.resources.settings_privacy
import sodogku.libraries.resources.generated.resources.settings_progress_local_body
import sodogku.libraries.resources.generated.resources.settings_progress_local_title
import sodogku.libraries.resources.generated.resources.settings_reduce_animations
import sodogku.libraries.resources.generated.resources.settings_reduce_animations_body
import sodogku.libraries.resources.generated.resources.settings_rerun_tutorial
import sodogku.libraries.resources.generated.resources.settings_rerun_tutorial_body
import sodogku.libraries.resources.generated.resources.settings_section_about
import sodogku.libraries.resources.generated.resources.settings_section_play
import sodogku.libraries.resources.generated.resources.settings_terms
import sodogku.libraries.resources.generated.resources.settings_title
import sodogku.libraries.resources.generated.resources.settings_version_label

/**
 * Every player-facing setting in the app, and the legal links the stores
 * require. A pure render of [SettingsState] — nothing here decides anything.
 *
 * The rows are `ListSection` from the design system, so sizing, dividers, the
 * bounce on press and the switch styling come for free rather than being
 * re-decided here.
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Screen(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopBar(
                title = stringResource(Res.string.settings_title),
                onNavigateBack = { onAction(SettingsAction.Back) },
                scrollState = scrollState,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .screenContentPadding(padding),
        ) {
            VerticalSpacerD500()

            ListSection(
                title = stringResource(Res.string.settings_section_play),
                items = listOf(
                    toggleItem(
                        headline = stringResource(Res.string.settings_haptics),
                        supporting = stringResource(Res.string.settings_haptics_body),
                        checked = state.hapticsEnabled,
                        onToggle = { onAction(SettingsAction.ToggleHaptics) },
                    ),
                    toggleItem(
                        headline = stringResource(Res.string.settings_reduce_animations),
                        supporting = stringResource(Res.string.settings_reduce_animations_body),
                        checked = state.reduceAnimations,
                        onToggle = { onAction(SettingsAction.ToggleReduceAnimations) },
                    ),
                    toggleItem(
                        headline = stringResource(Res.string.settings_colorblind),
                        supporting = stringResource(Res.string.settings_colorblind_body),
                        checked = state.colorblindMode,
                        onToggle = { onAction(SettingsAction.ToggleColorblind) },
                    ),
                    // Sits with the playing settings rather than under About:
                    // it changes what happens on the board, and it is the one
                    // row here that navigates away from this screen.
                    ListSectionItem(
                        headlineText = stringResource(Res.string.settings_rerun_tutorial),
                        supportingText = stringResource(Res.string.settings_rerun_tutorial_body),
                        onClick = { onAction(SettingsAction.RerunTutorial) },
                    ),
                ),
            )

            VerticalSpacerD800()

            ListSection(
                title = stringResource(Res.string.settings_achievements),
                // The row that opens the grid disappears when badges are off,
                // matching what the toggle promises: nothing about them on
                // screen. The toggle itself stays, or there would be no way
                // back — unless `features.achievements` is off, which takes the
                // feature away rather than hiding it, so the toggle goes too.
                items = listOfNotNull(
                    ListSectionItem(
                        headlineText = stringResource(Res.string.settings_achievements),
                        onClick = { onAction(SettingsAction.OpenAchievements) },
                    ).takeIf { state.achievementsVisible && state.achievementsAvailable },
                    toggleItem(
                        headline = stringResource(Res.string.settings_achievements_show),
                        supporting = stringResource(Res.string.settings_achievements_show_body),
                        checked = state.achievementsVisible,
                        onToggle = { onAction(SettingsAction.ToggleAchievements) },
                    ).takeIf { state.achievementsAvailable },
                    ListSectionItem(
                        headlineText = stringResource(Res.string.settings_progress_local_title),
                        supportingText = stringResource(Res.string.settings_progress_local_body),
                        accessory = ListItemAccessory.None,
                    ),
                ),
            )

            VerticalSpacerD800()

            ListSection(
                title = stringResource(Res.string.settings_section_about),
                items = listOf(
                    ListSectionItem(
                        headlineText = stringResource(Res.string.settings_feedback),
                        onClick = { onAction(SettingsAction.OpenFeedback) },
                    ),
                    ListSectionItem(
                        headlineText = stringResource(Res.string.settings_terms),
                        onClick = { onAction(SettingsAction.OpenTerms) },
                    ),
                    ListSectionItem(
                        headlineText = stringResource(Res.string.settings_privacy),
                        onClick = { onAction(SettingsAction.OpenPrivacy) },
                    ),
                    ListSectionItem(
                        headlineText = stringResource(Res.string.settings_version_label),
                        accessory = ListItemAccessory.Text(state.appVersion),
                    ),
                ),
            )

            VerticalSpacerD800()
        }
    }
}

/**
 * The whole row toggles, not just the switch. A 12pt-wide control is a
 * needlessly small target when the label next to it means the same thing.
 */
private fun toggleItem(
    headline: String,
    supporting: String,
    checked: Boolean,
    onToggle: () -> Unit,
): ListSectionItem = ListSectionItem(
    headlineText = headline,
    supportingText = supporting,
    accessory = ListItemAccessory.Switch(
        checked = checked,
        onCheckedChange = { onToggle() },
    ),
    onClick = onToggle,
)

@Preview
@Composable
private fun SettingsScreenPreview() {
    PreviewContent {
        SettingsScreen(
            state = SettingsState(
                hapticsEnabled = true,
                reduceAnimations = false,
                colorblindMode = true,
                achievementsVisible = true,
                appVersion = "1.4.0 (204)",
            ),
            onAction = {},
        )
    }
}
