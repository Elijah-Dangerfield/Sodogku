package com.sodogku.libraries.navigation.floatingwindow

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.navigation.NavBackStackEntry
import com.sodogku.libraries.ui.components.dialog.bottomsheet.BottomSheetState
import com.sodogku.libraries.ui.components.dialog.bottomsheet.rememberDestinationBottomSheetState

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BottomSheetDestination(
    backStackEntry: NavBackStackEntry,
    content: @Composable (NavBackStackEntry, BottomSheetState) -> Unit
) {
    val sheetState = rememberDestinationBottomSheetState(backStackEntry.id)

    BackHandler(enabled = sheetState.isVisible) {
        sheetState.dismiss()
    }

    content(backStackEntry, sheetState)
}
