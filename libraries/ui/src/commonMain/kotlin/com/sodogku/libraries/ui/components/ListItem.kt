package com.sodogku.libraries.ui.components

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.bounceClick
import com.sodogku.libraries.ui.components.board.fill
import com.sodogku.libraries.ui.components.icon.Icon
import com.sodogku.libraries.ui.components.icon.IconResource
import com.sodogku.libraries.ui.components.icon.IconSize
import com.sodogku.libraries.ui.components.icon.Icons
import com.sodogku.libraries.ui.components.text.ProvideTextConfig
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.HorizontalSpacerD500
import com.sodogku.system.Radii
import com.sodogku.system.VerticalSpacerD200
import com.sodogku.system.thenIf
import com.sodogku.system.typography.TypographyResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.list_row_spoken_format

@Composable
fun ListSection(
    items: List<ListSectionItem>,
    modifier: Modifier = Modifier,
    title: String? = null,
    supportingText: String? = null,
    backgroundColor: ColorResource = AppTheme.colors.surfacePrimary,
    dividerColor: ColorResource = AppTheme.colors.border,
) {
    if (items.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        if (title != null) {
            Text(
                text = title,
                typography = AppTheme.typography.Heading.H700
            )
            VerticalSpacerD200()
        }

        if (supportingText != null) {
            Text(
                text = supportingText,
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.onSurfaceSecondary
            )
            VerticalSpacerD200()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Radii.Card.shape)
                .background(backgroundColor.color)

        ) {
            items.forEachIndexed { index, item ->
                ListItem(
                    modifier = Modifier,
                    leadingContent = item.leadingContent,
                    headlineText = item.headlineText,
                    supportingText = item.supportingText,
                    accessory = item.accessory,
                    enabled = item.enabled,
                    onClick = item.onClick,
                    showDivider = index != items.lastIndex,
                    dividerStartInset = item.dividerStartInset
                        ?: ListItemDefaults.dividerStartInset(item.leadingContent != null),
                    dividerColor = dividerColor
                )
            }
        }
    }
}

data class ListSectionItem(
    val headlineText: String,
    val supportingText: String? = null,
    val leadingContent: (@Composable () -> Unit)? = null,
    val accessory: ListItemAccessory = ListItemAccessory.Chevron,
    val onClick: (() -> Unit)? = null,
    val enabled: Boolean = true,
    val dividerStartInset: Dp? = null,
)

/**
 * The row takes `String`s rather than composable slots because a toggle row has
 * to be able to *say* its own name, and a slot can only be drawn. See the
 * `clearAndSetSemantics` below for why the row cannot borrow the name from the
 * text it contains.
 */
@Composable
fun ListItem(
    headlineText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
    accessory: ListItemAccessory = ListItemAccessory.Chevron,
    contentPadding: PaddingValues = ListItemDefaults.contentPadding(),
    showDivider: Boolean = false,
    dividerStartInset: Dp = ListItemDefaults.dividerStartInset(leadingContent != null),
    dividerColor: ColorResource = AppTheme.colors.borderSecondary,
) {
    // A switch accessory takes the whole row over. `Modifier.toggleable` puts
    // the role and the on/off state on the row itself, so the row is one node
    // that says its own name and its own state.
    //
    // Before this, the switch was independently checkable *and* the row was
    // clickable, so the same setting appeared twice in the traversal: once as
    // the label, then again as a bare "on" with no name, because `bounceClick`
    // contributes an action and a role and nothing else. `toggleItem` in
    // Settings passes `onClick` and `onCheckedChange` doing the same thing, so
    // collapsing them loses no behaviour. Any caller that gives a switch row an
    // `onClick` meaning something *different* has written two controls in one
    // row, and this deliberately does not support that.
    val toggle = accessory as? ListItemAccessory.Switch
    val toggleEnabled = enabled && toggle?.enabled != false

    val spokenFormat = stringResource(Res.string.list_row_spoken_format)
    val spokenLabel = remember(spokenFormat, headlineText, supportingText) {
        spokenRowLabel(spokenFormat, headlineText, supportingText)
    }

    // The press animation is built here rather than through `bounceClick`
    // because `bounceClick` is a `composed { }` helper and cannot carry the
    // toggle's role or state. See its KDoc.
    val toggleInteraction = remember { MutableInteractionSource() }
    val pressScale = remember { Animatable(1f) }
    LaunchedEffect(toggleInteraction) {
        toggleInteraction.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> pressScale.animateTo(BounceScaleDown)
                is PressInteraction.Release -> pressScale.animateTo(1f)
                is PressInteraction.Cancel -> pressScale.animateTo(1f)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Dimension.D1400)
                .thenIf(supportingText != null) {
                    padding(vertical = Dimension.D400)
                }
                .then(
                    when {
                        toggle != null -> Modifier
                            // Read in the draw phase, never in composition.
                            .graphicsLayer {
                                val scale = pressScale.value
                                scaleX = scale
                                scaleY = scale
                            }
                            .toggleable(
                                value = toggle.checked,
                                enabled = toggleEnabled,
                                interactionSource = toggleInteraction,
                                indication = null,
                                role = Role.Switch,
                                onValueChange = { toggle.onCheckedChange(it) },
                            )
                            // What makes the row one node instead of two.
                            //
                            // `toggleable` alone is not enough, and the reason
                            // is specific. It sets `mergeDescendants`, and
                            // Compose's Android bridge then *skips* assigning
                            // `contentDescription` to any merging node that
                            // still has children, to avoid clobbering them: it
                            // hangs the description off a synthetic extra child
                            // instead. So the row arrives in a `uiautomator`
                            // dump checkable with no name, whether or not a
                            // `contentDescription` was set on it, which is
                            // exactly the shape SD-3 reports. `clearAndSetSemantics`
                            // drops the children, and with no children left the
                            // bridge puts the name on the row itself.
                            //
                            // That is also why the label has to carry the
                            // supporting sentence: nothing under this row is
                            // reachable any more, so anything left out of
                            // [spokenRowLabel] is not read at all.
                            //
                            // Peers on one layout node collapse rather than
                            // overwrite, so `toggleable`'s role, click action
                            // and on/off state all survive this and supply the
                            // state half of the label. No `stateDescription`
                            // here: the platform speaks `Role.Switch` plus the
                            // toggled value in the reader's own system
                            // language, which an app-supplied string would
                            // override with English. `BoardCellLabels` has to
                            // set one because a crossed-off square has no
                            // platform state to borrow.
                            .clearAndSetSemantics { contentDescription = spokenLabel }

                        onClick != null -> Modifier.bounceClick(
                            enabled = enabled,
                            onClick = onClick
                        )

                        else -> Modifier
                    }
                )
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingContent != null) {
                Box(Modifier.padding(end = Dimension.D500)) {
                    leadingContent()
                }
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                ProvideTextConfig(
                    color = if (enabled) AppTheme.colors.onSurfacePrimary else AppTheme.colors.onSurfaceDisabled
                ) {
                    Text(
                        text = headlineText,
                        typography = ListItemDefaults.headlineTypography()
                    )
                }

                if (supportingText != null) {
                    VerticalSpacerD200()
                    ProvideTextConfig(
                        color = if (enabled) AppTheme.colors.onSurfaceSecondary else AppTheme.colors.onSurfaceDisabled
                    ) {
                        Text(
                            text = supportingText,
                            typography = ListItemDefaults.supportingTypography()
                        )
                    }
                }
            }

            val hasAccessory = accessory !is ListItemAccessory.None

            if (hasAccessory) {
                HorizontalSpacerD500()
                Accessory(accessory = accessory, enabled = enabled)
            }
        }

        if (showDivider) {
            HorizontalDivider(
                color = dividerColor,
                modifier = Modifier.padding(start = dividerStartInset)
            )
        }
    }
}

sealed interface ListItemAccessory {
    data object None : ListItemAccessory
    data object Chevron : ListItemAccessory
    data class Icon(
        val icon: IconResource,
        val tint: ColorResource? = null,
        val size: IconSize = IconSize.Small,
    ) : ListItemAccessory

    data class Switch(
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
        val enabled: Boolean = true,
    ) : ListItemAccessory

    data class Text(
        val text: String,
        val typography: TypographyResource? = null,
        val color: ColorResource? = null,
    ) : ListItemAccessory

    class Custom(
        val content: @Composable () -> Unit
    ) : ListItemAccessory
}

/** Matches `bounceClick`'s default, so a toggle row presses like every other row. */
private const val BounceScaleDown = 0.90f

/**
 * Everything a toggle row has to say, in one string.
 *
 * A toggle row clears its children's semantics so that the row itself can be
 * named, which means this is the only thing a screen reader gets: a sentence
 * left out here is a sentence nobody hears. The headline comes first because it
 * is what the control *is*; the supporting line is detail and can be skipped
 * past.
 *
 * Assembled through a translatable format rather than concatenated, for the
 * same reason `board_cell_format` is: a translation may want the two the other
 * way round, or joined by something other than a full stop.
 */
internal fun spokenRowLabel(
    format: String,
    headlineText: String,
    supportingText: String?,
): String = if (supportingText == null) headlineText else format.fill(headlineText, supportingText)

object ListItemDefaults {
    private val HorizontalPadding = Dimension.D500
    private val VerticalPadding = Dimension.D0

    fun contentPadding(): PaddingValues = PaddingValues(
        horizontal = HorizontalPadding,
        vertical = VerticalPadding
    )

    fun dividerStartInset(
        hasLeadingContent: Boolean,
        leadingContentWidth: Dp = IconSize.Small.dp
    ): Dp {
        val leadingInset = if (hasLeadingContent) {
            leadingContentWidth + Dimension.D500
        } else {
            0.dp
        }
        return Dimension.D500 + leadingInset
    }

    @Composable
    fun headlineTypography(): TypographyResource = AppTheme.typography.Body.B700.SemiBold

    @Composable
    fun supportingTypography(): TypographyResource = AppTheme.typography.Body.B500
}

@Composable
private fun Accessory(
    accessory: ListItemAccessory,
    enabled: Boolean
) {
    when (accessory) {
        ListItemAccessory.None -> Unit

        // Decorative, because the row is what you activate and it already says
        // where it goes. Labelled "Navigate", this was a second node after every
        // such row, so a reader heard the destination and then the word
        // "Navigate" with nothing attached to it.
        ListItemAccessory.Chevron ->
            Icon(
                icon = Icons.ChevronRight.decorative,
                color = AppTheme.colors.onSurfacePrimary,
                size = IconSize.Small
            )

        is ListItemAccessory.Icon ->
            Icon(
                icon = accessory.icon,
                color = accessory.tint ?: AppTheme.colors.onSurfacePrimary,
                size = accessory.size
            )

        // `onCheckedChange = null`: the row owns the gesture and the state now,
        // and a switch that kept either would be the duplicate node this exists
        // to remove. Still drawn, still animates, still shows enabled or
        // disabled. The row clears this subtree anyway, so this is about not
        // handing the switch a second tap target rather than about semantics.
        is ListItemAccessory.Switch ->
            Switch(
                checked = accessory.checked,
                onCheckedChange = null,
                enabled = enabled && accessory.enabled,
            )


        is ListItemAccessory.Text ->
            Text(
                text = accessory.text,
                typography = accessory.typography ?: AppTheme.typography.Body.B600,
                color = accessory.color
                    ?: if (enabled) AppTheme.colors.onSurfaceSecondary else AppTheme.colors.onSurfaceDisabled
            )

        is ListItemAccessory.Custom -> accessory.content()
    }
}

@Preview
@Composable
private fun ListSectionPreview() {
    PreviewContent {
        Box(Modifier.padding(horizontal = Dimension.D500)) {
            ListSection(
                title = "General",
                items = listOf(
                    ListSectionItem(
                        headlineText = "Edit Name",
                        leadingContent = { Icon(Icons.Pencil("Charity")) },
                        onClick = {}
                    ),
                    ListSectionItem(
                        headlineText = "Edit Donation",
                        supportingText = "Change the charity you support",
                        leadingContent = { Icon(Icons.Charity("Charity")) },
                        onClick = {}
                    ),
                    ListSectionItem(
                        headlineText = "Tip Jar",
                        leadingContent = { Icon(Icons.TipJar("Tip Jar")) },
                        accessory = ListItemAccessory.None,
                        onClick = {}
                    )
                )
            )
        }
    }
}

@Preview
@Composable
private fun ListSectionPreviewWithSwitches() {
    PreviewContent {
        var encouragement by remember { mutableStateOf(true) }
        var violations by remember { mutableStateOf(false) }

        Box(Modifier.padding(horizontal = Dimension.D500)) {
            ListSection(
                title = "Notifications",
                items = listOf(
                    ListSectionItem(
                        headlineText = "Encouragement",
                        accessory = ListItemAccessory.Switch(
                            checked = encouragement,
                            onCheckedChange = { encouragement = it }
                        )
                    ),
                    ListSectionItem(
                        headlineText = "Violations",
                        accessory = ListItemAccessory.Switch(
                            checked = violations,
                            onCheckedChange = { violations = it }
                        )
                    )
                )
            )
        }
    }
}


