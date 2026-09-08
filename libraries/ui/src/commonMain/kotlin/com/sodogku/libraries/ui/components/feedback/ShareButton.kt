package com.sodogku.libraries.ui.components.feedback

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sodogku.libraries.sharing.ShareLabels
import com.sodogku.libraries.sharing.ShareResult
import com.sodogku.libraries.sharing.ShareText
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.button.ButtonSecondary
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.system.LocalShareSheet
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.share_cta

/**
 * Hands a finished board to the platform share sheet.
 *
 * The formatting happens here rather than at the call site so that every share
 * in the app is assembled the same way, and so a screen only has to supply the
 * two things it alone knows: what the board was, and what to call it. The
 * launcher comes from [LocalShareSheet], which is a no-op in previews and
 * tests.
 */
@Composable
fun ShareButton(
    result: ShareResult,
    labels: ShareLabels,
    modifier: Modifier = Modifier,
    onShared: () -> Unit = {},
) {
    val shareSheet = LocalShareSheet.current
    ButtonSecondary(
        onClick = {
            shareSheet.share(ShareText.format(result, labels))
            onShared()
        },
        modifier = modifier,
    ) {
        Text(stringResource(Res.string.share_cta))
    }
}

@Preview
@Composable
private fun ShareButtonPreview() {
    PreviewContent {
        ShareButton(
            result = ShareResult(
                size = 4,
                regions = listOf(
                    0, 0, 1, 1,
                    0, 2, 2, 1,
                    3, 3, 2, 1,
                    3, 3, 2, 1,
                ),
                timeMs = 102_000,
                score = 14_820,
                paws = 3,
                bonesRemaining = 2,
            ),
            labels = ShareLabels(title = "Sodogku · Level 12", footer = "sodogku.app"),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
