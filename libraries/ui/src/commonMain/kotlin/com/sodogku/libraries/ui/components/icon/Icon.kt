package com.sodogku.libraries.ui.components.icon

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension.D1000
import com.sodogku.system.Dimension.D1200
import com.sodogku.system.Dimension.D1300
import com.sodogku.system.Dimension.D600
import com.sodogku.system.Dimension.D800
import com.sodogku.system.Dimension.D850
import com.sodogku.system.Dimension.D900
import com.sodogku.system.DimensionResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun Icon(
    icon: IconResource,
    modifier: Modifier = Modifier,
    size: IconSize = IconSize.Small,
    color: ColorResource = com.sodogku.libraries.ui.system.LocalContentColor.current
) {
    androidx.compose.material3.Icon(
        imageVector = icon.imageVector,
        contentDescription = icon.contentDescription,
        modifier = modifier.size(size.dp),
        tint = color.color
    )
}


@Composable
fun SmallIcon(
    icon: IconResource,
    modifier: Modifier = Modifier,
    color: ColorResource = com.sodogku.libraries.ui.system.LocalContentColor.current
) {
    androidx.compose.material3.Icon(
        imageVector = icon.imageVector,
        contentDescription = icon.contentDescription,
        modifier = modifier.size(IconSize.Small.dp),
        tint = color.color
    )
}

@Composable
fun MediumIcon(
    icon: IconResource,
    modifier: Modifier = Modifier,
    color: ColorResource = com.sodogku.libraries.ui.system.LocalContentColor.current
) {
    androidx.compose.material3.Icon(
        imageVector = icon.imageVector,
        contentDescription = icon.contentDescription,
        modifier = modifier.size(IconSize.Medium.dp),
        tint = color.color
    )
}

@Composable
fun LargeIcon(
    icon: IconResource,
    modifier: Modifier = Modifier,
    color: ColorResource = com.sodogku.libraries.ui.system.LocalContentColor.current
) {
    androidx.compose.material3.Icon(
        imageVector = icon.imageVector,
        contentDescription = icon.contentDescription,
        modifier = modifier.size(IconSize.Large.dp),
        tint = color.color
    )
}

enum class IconSize(val dp: Dp) {
    Smallest(D600),
    Small(D800),
    AppBar(D850),
    Medium(D1000),
    Large(D1200),
    Largest(D1300),
}

@Preview
@Composable
private fun IconPreview() {
    PreviewContent() {
        LazyColumn {
            items(IconSize.entries.toTypedArray()) {
                com.sodogku.libraries.ui.components.icon.Icon(
                    size = it,
                    icon = Icons.Check.Outlined("check")
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${it.name} Icon", typography = AppTheme.typography.Body.B500
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

    }
}
