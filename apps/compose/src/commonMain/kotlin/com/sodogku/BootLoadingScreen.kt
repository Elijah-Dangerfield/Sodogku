package com.sodogku

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.BoneLoader
import com.sodogku.system.AppTheme
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.splash_loading

/**
 * What is on screen between the platform splash handing off and the app being
 * ready to draw a board.
 *
 * The same bone the iOS splash shows, on the same cream, so launch reads as one
 * loader rather than as a handover between two ideas about what loading looks
 * like. On Android the system splash comes first and this is the whole of it.
 *
 * **This used to be a still dog with a spinner that faded in after five
 * seconds**, on the reasoning that a fast boot should not be told it is slow and
 * a spinner in the first second is usually a lie. That reasoning was right and
 * it is what a sweeping bone already does: it claims motion without claiming
 * progress, from the first frame, so there is nothing to delay and nothing to
 * reserve space for. The delayed spinner, the reserved row that stopped the dog
 * jumping when it appeared, and the two previews for the two states all go with
 * it.
 */
@Composable
fun BootLoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.background.color),
        contentAlignment = Alignment.Center,
    ) {
        BoneLoader(label = stringResource(Res.string.splash_loading))
    }
}

@Preview
@Composable
private fun BootLoadingScreenPreview() {
    PreviewContent {
        BootLoadingScreen()
    }
}
