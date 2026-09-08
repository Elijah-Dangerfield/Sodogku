package com.sodogku.libraries.ui.components.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.color.ProvideContentColor
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.libraries.ui.components.button.Button
import com.sodogku.libraries.ui.components.button.ButtonSize
import com.sodogku.libraries.ui.components.button.ProvideButtonConfig
import com.sodogku.libraries.ui.components.text.ProvideTextConfig
import com.sodogku.libraries.ui.components.text.Text
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun ModalContent(
    modifier: Modifier = Modifier,
    backgroundColor: ColorResource = AppTheme.colors.surfacePrimary,
    contentColor: ColorResource = AppTheme.colors.onSurfacePrimary,
    topContent: @Composable () -> Unit = {},
    content: @Composable () -> Unit = {},
    bottomContent: @Composable (() -> Unit)? = null,
) {

    Column(
        modifier = modifier.background(backgroundColor.color)
    ) {

        ProvideContentColor(color = contentColor) {

            ProvideTextConfig(AppTheme.typography.Display.D1000) {
                topContent()
            }

            Spacer(modifier = Modifier.height(Dimension.D600))

            ProvideTextConfig(AppTheme.typography.Body.B700) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)

                ) {
                    content()
                }
            }

            if (bottomContent != null) {
                Spacer(modifier = Modifier.height(Dimension.D1000))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor.color),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ProvideButtonConfig(size = ButtonSize.Small) {
                        bottomContent()
                    }
                }
            }
        }
    }
}

@Composable
@Preview
private fun PreviewModalContent() {
    PreviewContent {
        ModalContent(
            modifier = Modifier,
            topContent = { Text(text = "Top Content") },
            content = {
                Column {
                    Text(text = "context".repeat(50))
                }
            },
            bottomContent = {
                Button(onClick = { }) {
                    Text(text = "Bottom Content")
                }
            },
        )
    }
}

@Composable
@Preview
private fun PreviewModalContentLong() {
    PreviewContent {
        ModalContent(
            modifier = Modifier,
            topContent = { Text(text = "Top Content") },
            content = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(text = "This is a bunch of words that take sus space".repeat(100))
                }
            },
            bottomContent = {
                Button(onClick = { }) {
                    Text(text = "Bottom Content")
                }
            },
        )
    }
}