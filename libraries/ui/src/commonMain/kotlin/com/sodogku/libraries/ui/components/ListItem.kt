package com.sodogku.libraries.ui.components

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import org.jetbrains.compose.ui.tooling.preview.Preview

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
                    headlineContent = {
                        Text(
                            text = item.headlineText,
                            typography = ListItemDefaults.headlineTypography()
                        )
                    },
                    supportingContent = item.supportingText?.let { text ->
                        {
                            Text(
                                text = text,
                                typography = ListItemDefaults.supportingTypography()
                            )
                        }
                    },
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

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    headlineContent: @Composable () -> Unit,
    supportingContent: (@Composable () -> Unit)? = null,
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

    // The press animation is built here rather than through `bounceClick`, and
    // that is the whole reason this works.
    //
    // `Modifier.toggleable` merges its descendants, which is what folds the
    // headline into the row so the row can say its own name. Wrapped in a
    // `composed { }` helper it did not: the row came out of a `uiautomator` dump
    // checkable and unnamed with the text still in separate child nodes, and an
    // explicit `contentDescription` beside it became *another* child rather than
    // naming the row. `bounceClick`'s KDoc guessed `composed { }` was why; this
    // is that guess confirmed, since the same `toggleable` applied directly
    // merges.
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
                .thenIf(supportingContent != null) {
                    padding(vertical = Dimension.D400)
                }
                // Named here, one link *earlier* than the modifier that takes
                // the gesture, because that is the only position that reaches
                // the tree. `mergeDescendants` was tried first and does not
                // work even from here — the row came out of a dump still
                // checkable and still unnamed, which is the same trap
                // `bounceClick` documents. An explicit `contentDescription`
                // does work, and it is what `RuleChip` and `BoosterButton`
                // already do.
                //
                // The headline alone, not the supporting sentence. The
                // supporting text stays its own node and is read after; folding
                // it in would make the control's *name* a paragraph.
                //
                // No `stateDescription`: `Role.Switch` plus the toggleable value
                // already gives a reader "on" or "off" in its own words, and
                // saying it twice is worse than the house pattern in
                // `BoardCellLabels`, where nothing else supplies the state.
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
                    headlineContent()
                }

                if (supportingContent != null) {
                    VerticalSpacerD200()
                    ProvideTextConfig(
                        color = if (enabled) AppTheme.colors.onSurfaceSecondary else AppTheme.colors.onSurfaceDisabled
                    ) {
                        supportingContent()
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

        // `onCheckedChange = null` and no semantics of its own: the row owns
        // both the gesture and the state now, and a switch that kept either
        // would be the duplicate node this exists to remove. Still drawn, still
        // animates, still shows enabled or disabled.
        is ListItemAccessory.Switch ->
            Switch(
                checked = accessory.checked,
                onCheckedChange = null,
                enabled = enabled && accessory.enabled,
                modifier = Modifier.clearAndSetSemantics { },
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


