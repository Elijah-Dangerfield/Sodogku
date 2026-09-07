package com.sodogku.libraries.ui.components.text

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.sodogku.system.AppTheme
import com.sodogku.system.HorizontalSpacerD200
import com.sodogku.libraries.ui.system.color.ColorResource

@Composable
fun AsteriskText(text: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        text()
        HorizontalSpacerD200()
        Text(
            text = "*",
            typography = AppTheme.typography.Display.D800,
            color = ColorResource.Red500
        )
    }
}