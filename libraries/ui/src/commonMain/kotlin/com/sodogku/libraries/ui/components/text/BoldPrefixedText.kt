package com.sodogku.libraries.ui.components.text

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.system.AppTheme
import com.sodogku.system.typography.TypographyResource
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.makeBold
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun BoldPrefixedText(
    modifier: Modifier = Modifier,
    boldText: String,
    regularText: String,
    textAlign: TextAlign = TextAlign.Start,
    typography: TypographyResource = AppTheme.typography.Body.B700
) {
    val string = "$boldText $regularText".makeBold(boldText)

    Row(modifier = modifier) {
       Text(
           textAlign = textAlign,
           text = string,
           typography = typography
       )
    }
}

@Preview
@Composable
fun BoldPrefixedTextPreview() {
    PreviewContent {
        com.sodogku.libraries.ui.components.text.BoldPrefixedText(
            boldText = "Role: ",
            regularText = "Spy",
            typography = AppTheme.typography.Body.B700
        )
    }
}