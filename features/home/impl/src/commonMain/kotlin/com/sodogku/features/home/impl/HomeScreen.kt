package com.sodogku.features.home.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sodogku.libraries.ui.components.Screen
import com.sodogku.libraries.ui.components.button.Button
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.VerticalSpacerD500
import com.sodogku.system.VerticalSpacerD800
import org.jetbrains.compose.resources.stringResource
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.home_temporary_launcher

/**
 * One entry per band, so the board can be checked at the sizes that actually
 * differ: the smallest grid, a mid one, and the largest.
 */
private val LaunchLevels = listOf(
    "Level 1 (4x4)" to 1,
    "Level 101 (7x7)" to 101,
    "Level 391 (10x10)" to 391,
)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToFeedback: () -> Unit,
    onNavigateToBugReport: () -> Unit,
    onPlay: (levelId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    Screen(modifier = modifier) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(Dimension.D900),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Welcome to Sodogku",
                typography = AppTheme.typography.Heading.H700,
                color = AppTheme.colors.text,
                textAlign = TextAlign.Center,
            )

            VerticalSpacerD800()

            Text(
                text = stringResource(Res.string.home_temporary_launcher),
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )

            VerticalSpacerD800()

            LaunchLevels.forEach { (label, levelId) ->
                Button(onClick = { onPlay(levelId) }) {
                    Text(label)
                }
                VerticalSpacerD500()
            }

            Button(onClick = onNavigateToFeedback) {
                Text("Send Feedback")
            }

            VerticalSpacerD500()

            Button(onClick = onNavigateToBugReport) {
                Text("Report a Bug")
            }
        }
    }
}