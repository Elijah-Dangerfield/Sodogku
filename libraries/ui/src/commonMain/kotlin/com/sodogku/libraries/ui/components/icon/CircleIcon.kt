package com.sodogku.libraries.ui.components.icon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import com.sodogku.system.thenIf
import com.sodogku.libraries.ui.Elevation
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.libraries.ui.components.Surface
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun CircleIcon(
    icon: IconResource?,
    iconSize: IconSize,
    modifier: Modifier = Modifier,
    padding: Dp = 0.dp,
    backgroundColor: ColorResource = AppTheme.colors.surfacePrimary,
    contentColor: ColorResource = AppTheme.colors.onSurfacePrimary,
    elevation: Elevation = Elevation.None,
    onClick: (() -> Unit)? = null
) {
    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        contentPadding = PaddingValues(padding),
        elevation = elevation,
        radius = Radii.Round,
        modifier = modifier
            .thenIf(onClick != null) {
                clickable { onClick?.invoke() }
            }
    ) {
        icon?.let {
            Icon(
                icon = icon,
                size = iconSize
            )
        } ?: Box(modifier = Modifier.size(iconSize.dp))
    }
}

@Preview
@Composable
private fun CircularIconPreview() {
    PreviewContent(backgroundColor = null) {
        com.sodogku.libraries.ui.components.icon.CircleIcon(
            icon = Icons.Check("Test"),
            iconSize = IconSize.Large,
            padding = Dimension.D400,
            backgroundColor = AppTheme.colors.background,
            contentColor = AppTheme.colors.onBackground
        )
    }
}
