package com.sodogku.libraries.ui.components

import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.inspectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import com.sodogku.system.Radius
import com.sodogku.system.color.ProvideContentColor
import com.sodogku.system.thenIf
import com.sodogku.system.thenIfNotNull
import com.sodogku.libraries.ui.Border
import com.sodogku.libraries.ui.Elevation
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.bounceClick
import com.sodogku.libraries.ui.inset
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.libraries.ui.components.text.Text
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A pressable surface: the shape, the fill, the border and the press.
 *
 * ### Why [contentDescription] is a parameter and not something you pass in [modifier]
 *
 * [bounceClick] ends in `clickable`, which merges everything below it, so a card
 * with three labels inside is read out as three fragments in a row. Clearing
 * first is what turns it into one sentence, and `clearAndSetSemantics` is the
 * only thing that does it — see [bounceClick]'s own doc for the two tidier
 * variants that were tried on a device and never reached the tree.
 *
 * A caller cannot do that clearing itself. Everything in [modifier] is applied
 * *above* the clickable, so a `clearAndSetSemantics` there clears the click
 * action along with the labels and the surface stops being a control. Given as a
 * parameter it is applied directly under the clickable, where the merge picks up
 * the name and the click action survives. That ordering is the whole point of
 * the parameter and `SurfaceSemanticsTest` holds it.
 *
 * [role] sits between the two for the same reason: below the clearing call it
 * would be cleared, above the clickable it lands on a node of its own.
 *
 * ### Why the press is applied before the fill
 *
 * A `graphicsLayer` only transforms what is drawn inside it, so a bounce applied
 * after the background scales the content and leaves the card holding still —
 * the text shrinks away from its own border. Applied first, the border, the
 * shadow and the fill scale with it, which is what every hand-rolled press in
 * the app was doing before this took it over.
 */
@Composable
@NonRestartableComposable
fun Surface(
    color: ColorResource?,
    contentColor: ColorResource,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    radius: Radius = Radii.Default,
    elevation: Elevation = Elevation.None,
    border: Border? = null,
    alpha: Float = 1f,
    onClick: () -> Unit,
    bounceScale: Float = 0.95f,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    indication: Indication? = null,
    role: Role? = null,
    contentDescription: String? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .bounceClick(
                enabled = enabled,
                scaleDown = bounceScale,
                indication = indication,
                mutableInteractionSource = interactionSource,
                onClick = onClick,
            )
            .thenIfNotNull(role) {
                semantics {
                    this.role = it
                }
            }
            .thenIfNotNull(contentDescription) {
                clearAndSetSemantics {
                    this.contentDescription = it
                }
            }
            .background(
                color = color,
                shape = radius.shape,
                elevation = elevation,
                clip = true,
                alpha = alpha,
                border = border
            )
            .padding(contentPadding),
        propagateMinConstraints = true
    ) {
        ProvideContentColor(contentColor, content)
    }
}

@Composable
@NonRestartableComposable
fun Surface(
    color: ColorResource?,
    contentColor: ColorResource,
    modifier: Modifier = Modifier,
    radius: Radius = Radii.Default,
    elevation: Elevation = Elevation.None,
    border: Border? = null,
    alpha: Float = 1f,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    /**
     * Whether content is cut off at the surface's edge. On by default, because
     * a rounded card whose contents square off its own corners is the reason
     * this clips at all.
     *
     * Pass `false` for a surface whose job is to be a background and whose
     * content is *meant* to hang over an edge — a bar with a badge above it,
     * a chip with a marker outside it. The shape has to be square for that to
     * be safe, and a clip is not free either, so it stays on unless a caller
     * says otherwise.
     */
    clip: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                color = color,
                shape = radius.shape,
                elevation = elevation,
                clip = clip,
                alpha = alpha,
                border = border
            )
            .semantics(mergeDescendants = false) {
                isTraversalGroup = true
            }
            // This prevents siblings that are underneath this surface from being receiving pointer events
            .pointerInput(Unit) {}
            .padding(contentPadding),
        propagateMinConstraints = true
    ) {
        ProvideContentColor(contentColor, content)
    }
}

private fun Modifier.background(
    color: ColorResource?,
    shape: Shape,
    elevation: Elevation,
    clip: Boolean,
    alpha: Float,
    border: Border?,
): Modifier = inspectable(
    androidx.compose.ui.platform.debugInspectorInfo {
        name = "background"
        properties["color"] = color
        properties["shape"] = shape
        properties["elevation"] = elevation.dp
        properties["clip"] = clip
        properties["alpha"] = alpha
        properties["border"] = border
    }
) {
    val backgroundShape = if (border == null || border.color.color.alpha < 0.99f) shape else shape.inset(border.width / 2f)
    this
        .thenIf(elevation > Elevation.None || alpha < 1f) {
            graphicsLayer {
                if (elevation > Elevation.None) {
                    shadowElevation = elevation.dp.toPx()
                    spotShadowColor = ColorResource.Black.color
                    ambientShadowColor = ColorResource.Black.color
                }
                this.alpha = alpha
                this.shape = shape
            }
        }
        .thenIfNotNull(border) {
            this.border(width = it.width, color = it.color.color, shape = shape)
        }
        .thenIfNotNull(color) {
            this.background(color = it.color, shape = shape)
        }
        .thenIf(clip) { clip(backgroundShape) }
}

@Preview
@Composable
private fun SurfacePreview() {
    PreviewContent {
        Surface(
            color = AppTheme.colors.background,
            contentColor = AppTheme.colors.text,
            contentPadding = PaddingValues(Dimension.D900)
        ) {
            Text("Hello")
        }
    }
}

@Preview
@Composable
private fun ClickableSurfacePreview() {
    PreviewContent {
        Surface(
            color = AppTheme.colors.background,
            contentColor = AppTheme.colors.text,
            radius = Radii.Banner,
            contentPadding = PaddingValues(Dimension.D900)
        ) {
            Text("Hello")
        }
    }
}

@Preview
@Composable
private fun ClickableSurfacePreviewNoColor() {
    PreviewContent(backgroundColor = null) {
        Surface(
            color = null,
            contentColor = AppTheme.colors.text,
            radius = Radii.Banner,
            contentPadding = PaddingValues(Dimension.D900)
        ) {
            Text("Hello")
        }
    }
}
