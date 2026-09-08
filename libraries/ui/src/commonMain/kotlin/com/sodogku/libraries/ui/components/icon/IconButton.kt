package com.sodogku.libraries.ui.components.icon

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.libraries.ui.system.LocalBuildInfo
import com.sodogku.libraries.ui.system.LocalContentColor
import com.sodogku.libraries.ui.Elevation
import com.sodogku.libraries.ui.elevation
import com.sodogku.system.Radii
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.libraries.ui.components.Surface
import com.sodogku.libraries.ui.components.icon.IconButton.Size
import com.sodogku.libraries.ui.components.text.Text
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A button that is nothing but a picture, which is why [icon] has to carry a
 * label.
 *
 * There is no text here for a screen reader to fall back on, so an
 * [Icons.decorative] icon makes a control that announces itself as an unnamed
 * button. Debug builds refuse it rather than letting it reach a store review —
 * the same bargain [Icons.Filled] strikes for a missing icon variant.
 */
@NonRestartableComposable
@Composable
fun IconButton(
    icon: IconResource,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    backgroundColor: ColorResource? = null,
    iconColor: ColorResource = LocalContentColor.current,
    size: Size = Size.Medium,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    if (icon.contentDescription == null && LocalBuildInfo.current.isDebug) {
        throw IllegalStateException(
            "IconButton(${icon.identifier}) has no content description. An icon button has no " +
                "text to fall back on, so pass Icons.${icon.identifier}(\"…\") with a label."
        )
    }
    val padding = size.padding
    val iconSize = size.iconSize

    @Composable
    fun Button(modifier: Modifier) {
        Surface(
            // The label is moved onto the button and off the picture inside it.
            // Left on the `Icon`, it lands on a node of its own two levels down
            // — measured in a `uiautomator` dump as a 28dp unfocusable child of
            // a 48dp focusable button with nothing on it — and whether a reader
            // finds it is up to the reader. Here it is on the thing being
            // pressed, which is the thing being described.
            // A shadow all the way round, but only on a button that has a fill.
            //
            // White circles on cream is the app's most common button and the
            // hardest to see: the gear and the menu on the board are a 4%
            // lightness step from the page they sit on, so they read as marks
            // printed on the background rather than as objects on top of it.
            // The shadow does two jobs at once, making the edge findable and
            // saying the thing is raised, which is the convention for "you can
            // press this".
            //
            // Skipped when there is no fill: a bare icon has no surface to lift
            // off the page, and a shadow under one is a shadow under a glyph.
            modifier = modifier
                .then(
                    if (backgroundColor != null) {
                        Modifier.elevation(Elevation.Button, Radii.IconButton.shape)
                    } else {
                        Modifier
                    },
                )
                .semantics(mergeDescendants = true) {
                    icon.contentDescription?.let { contentDescription = it }
                },
            contentPadding = PaddingValues(padding),
            color = backgroundColor,
            contentColor = iconColor,
            radius = Radii.IconButton,
            onClick = onClick,
            enabled = enabled,
            role = Role.Button,
            interactionSource = interactionSource
        ) {
            Icon(
                icon = icon.copy(contentDescription = null),
                size = iconSize
            )
        }
    }

    Button(modifier = modifier)

}

object IconButton {
    enum class Size {
        Smallest,
        Small,
        Medium,
        Large,
        Largest,
    }
}

internal val Size.padding: Dp
    get() = when (this) {
        Size.Smallest -> Dimension.D100
        Size.Small -> Dimension.D100
        Size.Medium -> Dimension.D100
        Size.Large -> Dimension.D200
        Size.Largest -> Dimension.D300
    }

internal val Size.iconSize: IconSize
    get() = when (this) {
        Size.Smallest -> IconSize.Smallest
        Size.Small -> IconSize.Small
        Size.Medium -> IconSize.Medium
        Size.Large -> IconSize.Large
        Size.Largest -> IconSize.Largest
    }

private val iconButtons = listOf(
    Icons.Check(""),
    Icons.Check(""),
    Icons.Check(""),
    Icons.Check(""),
    Icons.Check(""),
    Icons.Settings(""),
    Icons.Check(""),
)

@Suppress("MagicNumber")
@Preview
@Composable
private fun PreviewIconButtons() {
    PreviewContent() {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(com.sodogku.libraries.ui.components.icon.iconButtons) { icon ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(8.dp)
                ) {
                    com.sodogku.libraries.ui.components.icon.IconButton(
                        icon = icon,
                        modifier = Modifier.size(48.dp),
                        backgroundColor = null,
                        size = Size.Medium,
                        onClick = {}
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = icon::class.simpleName ?: "Err")
                }
            }
        }
    }
}

@Suppress("MagicNumber")
@Preview
@Composable
private fun PreviewIconButtonsBackground() {
    PreviewContent() {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(com.sodogku.libraries.ui.components.icon.iconButtons) { icon ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(8.dp)
                ) {
                    com.sodogku.libraries.ui.components.icon.IconButton(
                        icon = icon,
                        modifier = Modifier.size(48.dp),
                        backgroundColor = AppTheme.colors.onBackground,
                        iconColor = AppTheme.colors.background,
                        size = Size.Medium,
                        onClick = {}
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = icon::class.simpleName ?: "Err")
                }
            }
        }
    }
}