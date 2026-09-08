package com.sodogku.libraries.ui.components.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.bounceClick
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.system.color.RegionPalette
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Motion
import com.sodogku.system.Radii
import com.sodogku.libraries.ui.system.DeepSurface
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sodogku.system.clip
import org.jetbrains.compose.resources.stringResource
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.booster_a11y
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalInspectionMode
import com.sodogku.libraries.ui.system.LocalReduceAnimations

/**
 * The lives left in this attempt, as bones.
 *
 * A lost bone fades and shrinks rather than vanishing, because the whole point
 * of the third strike is that the player saw the second one coming.
 */
@Composable
fun LifeRow(
    remaining: Int,
    modifier: Modifier = Modifier,
    total: Int = DefaultLives,
    size: Dp = Dimension.D900,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimension.D200),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        repeat(total) { index ->
            val spent = index >= remaining
            // Kept as State and read inside graphicsLayer rather than unwrapped
            // with `by`: unwrapping in composition recomposes this whole row on
            // every animation frame, three times over.
            val alpha = animateFloatAsState(if (spent) SpentAlpha else 1f, Motion.fade())
            val scale = animateFloatAsState(if (spent) SpentScale else 1f, Motion.Tap)
            Box(
                modifier = Modifier
                    .size(width = size * BoneAspect, height = size)
                    .graphicsLayer {
                        this.alpha = alpha.value
                        scaleX = scale.value
                        scaleY = scale.value
                    }
                    .drawBehind {
                        drawBone(
                            fill = if (spent) SpentBone else LiveBone,
                            edge = if (spent) SpentBoneEdge else LiveBoneEdge,
                        )
                    },
            )
        }
    }
}

/**
 * Paws earned, 0 to 3. Unearned paws stay visible in outline so the player can
 * see what they missed rather than just what they got.
 */
@Composable
fun PawRating(
    paws: Int,
    modifier: Modifier = Modifier,
    size: Dp = Dimension.D1100,
    /**
     * False draws the paws already earned, with no pop.
     *
     * The animation says "you just won these", which is a lie anywhere the
     * rating is being *recalled* rather than awarded. In the 500-row level list
     * it is also a nuisance: rows recycle as they scroll, so every completed
     * level pops each time it comes back on screen and the list appears to
     * twitch.
     */
    animated: Boolean = true,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimension.D300),
        modifier = modifier,
    ) {
        repeat(MaxPaws) { index ->
            val earned = index < paws
            val pop = remember { Animatable(if (animated) 0f else 1f) }
            LaunchedEffect(earned, animated) {
                if (earned && animated) {
                    pop.snapTo(0f)
                    pop.animateTo(Motion.PopOvershoot, Motion.Pop)
                    pop.animateTo(1f, Motion.Tap)
                }
            }
            Box(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        val scale = if (earned) pop.value else 1f
                        scaleX = scale
                        scaleY = scale
                    }
                    .drawBehind {
                        drawPaw(if (earned) EarnedPaw else UnearnedPaw, filled = earned)
                    },
            )
        }
    }
}

/**
 * The white lozenge the board's two counters sit in.
 *
 * There are exactly two of these and they are read as a pair, so the shape they
 * share is worth naming once rather than being two similar-looking `Row`s that
 * drift apart the first time one of them is adjusted.
 */
@Composable
fun HudPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimension.D300),
        modifier = modifier
            .clip(Radii.Round)
            .background(AppTheme.colors.surfacePrimary.color)
            .padding(horizontal = Dimension.D500, vertical = Dimension.D300),
        content = content,
    )
}

/**
 * The three permanent rule reminders, as one thing.
 *
 * Three loose pills read as three unrelated buttons and leave the eye deciding
 * whether they belong together; inside one card they read as what they are — a
 * rule sheet that happens to live on the board. It also gives the outline on a
 * [RuleChip] something to be an outline *within*, which is what makes
 * highlighting one of them mean "this one, of these three" rather than "this
 * one is on".
 */
@Composable
fun RuleChipGroup(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimension.D200),
        modifier = modifier
            .clip(Radii.Card)
            .background(AppTheme.colors.surfacePrimary.color)
            .padding(Dimension.D200),
        content = content,
    )
}

/**
 * One of the three permanent rule reminders, with a miniature board showing the
 * rule rather than describing it.
 *
 * These stay on screen for all 500 levels. A player who has internalised the
 * rules stops reading them, and a player who has not can glance without leaving
 * the board.
 *
 * [highlighted] outlines the chip rather than filling it. A filled chip in a row
 * of unfilled ones reads as *selected* — as though the player had turned that
 * rule on — and these are not controls. An outline reads as a pointer, which is
 * the only thing this ever means: the rule you just ran into.
 */
@Composable
fun RuleChip(
    diagram: RuleDiagram,
    label: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onClick: () -> Unit = {},
) {
    val diagramColor = AppTheme.colors.text.color
    val regionColors = RuleChipRegionColors
    val resting = AppTheme.colors.surfaceSecondary.color
    val alert = AppTheme.colors.danger.color
    val still = LocalReduceAnimations.current || LocalInspectionMode.current

    // One flash that settles, rather than a border that switches on.
    //
    // The chip is naming the rule a wrong guess just broke, so it wants to catch
    // the eye at the moment it changes and then stop competing with the board.
    // A dark outline did neither: it did not move when it appeared, and it
    // stayed exactly as loud for as long as it was up.
    //
    // It settles to a tint rather than to nothing, because the answer is still
    // useful after the flash. Somebody who looks down a second later should
    // still be able to see which rule they hit.
    val flash = remember { Animatable(0f) }
    LaunchedEffect(highlighted, still) {
        if (!highlighted) {
            flash.animateTo(0f, tween(RuleChipFadeMillis))
            return@LaunchedEffect
        }
        if (still) {
            flash.snapTo(RuleChipRest)
            return@LaunchedEffect
        }
        flash.snapTo(1f)
        flash.animateTo(RuleChipRest, tween(RuleChipSettleMillis))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimension.D300),
        // bounceClick before the fill, so the press scales the whole chip
        // rather than shrinking the label inside a stationary pill.
        modifier = modifier
            .semantics { contentDescription = label }
            .bounceClick(onClick = onClick)
            .clip(Radii.Card)
            // Fill and outline are drawn here rather than set as modifiers, so
            // the flash is read in the draw phase. Read in composition it would
            // recompose all three chips on every frame of the settle.
            .drawBehind {
                val lit = flash.value
                drawRect(color = lerp(resting, alert.copy(alpha = RuleChipTint), lit))
                if (lit > 0f) {
                    drawRect(
                        color = alert.copy(alpha = lit),
                        style = Stroke(width = RuleChipOutline.toPx()),
                    )
                }
            }
            .padding(horizontal = Dimension.D400, vertical = Dimension.D300),
    ) {
        Box(
            modifier = Modifier
                .size(Dimension.D1000)
                .drawBehind { drawRuleDiagram(diagram, diagramColor, regionColors) },
        )
        Text(
            text = label,
            typography = AppTheme.typography.Caption.C300,
            color = AppTheme.colors.textSecondary,
        )
    }
}

/**
 * A booster, with its holding as a corner badge.
 *
 * The badge shows even at zero, in the muted colour. A count that disappears
 * when it runs out makes the button look broken rather than empty, and empty is
 * the state that should invite a tap — that is where the ad offer lives.
 */
@Composable
fun BoosterButton(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
    /**
     * The button's own colour. Each booster keeps one, so a player learns
     * "the green one" long before they read the word — which is the only way a
     * booster row works once there are three of them and a thumb over two.
     */
    color: Color = AppTheme.colors.accentPrimary.color,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    val boosterLabel = stringResource(Res.string.booster_a11y, label, count)
    Box(modifier = modifier) {
        DeepSurface(
            // A face on a lip, the same treatment every filled button in the app
            // gets. The gradient-and-sheen this replaced read as a shadow blob
            // under a pill rather than as a thing with thickness.
            color = if (enabled) color else AppTheme.colors.surfaceDisabled.color,
            enabled = enabled,
            onClick = onClick,
            modifier = Modifier
                .padding(BadgeInset)
                // The count belongs in the name: "Sniff" alone does not say
                // whether tapping it will do anything.
                .semantics { contentDescription = boosterLabel },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(
                    horizontal = Dimension.D800,
                    vertical = Dimension.D500,
                ),
            ) {
                Text(
                    text = label,
                    typography = AppTheme.typography.Body.B600,
                    color = if (enabled) {
                        AppTheme.colors.onAccentPrimary
                    } else {
                        AppTheme.colors.onSurfaceDisabled
                    },
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(Radii.Round)
                .background(
                    if (count > 0) AppTheme.colors.danger.color else AppTheme.colors.textDisabled.color,
                )
                .padding(horizontal = Dimension.D400, vertical = Dimension.D100),
        ) {
            Text(
                text = count.toString(),
                typography = AppTheme.typography.Caption.C300,
                color = AppTheme.colors.onAccentPrimary,
            )
        }
    }
}

/** How many bones an attempt starts with. Mirrors `ScoringConfig.MAX_LIVES`. */
const val DefaultLives: Int = 3

private const val MaxPaws = 3
private const val SpentAlpha = 0.28f
private const val SpentScale = 0.82f

/** Bones are wider than they are tall, or they read as a lump. */
private const val BoneAspect = 1.45f

/** Heavy enough to be an outline rather than a hairline, at chip size. */
private val RuleChipOutline = Dimension.D100

/** How lit the chip stays once the flash has settled. Visible, not shouting. */
private const val RuleChipRest = 0.35f

/** The alert colour's strength in the fill at full flash. A wash, not a block. */
private const val RuleChipTint = 0.22f

private const val RuleChipSettleMillis = 520
private const val RuleChipFadeMillis = 260

/** Room for the badge to overhang the button's corner. */
private val BadgeInset = Dimension.D400

/** Three palette fills, so the colour rule's miniature matches the real board. */
private val RuleChipRegionColors =
    listOf(RegionPalette[0].fill, RegionPalette[3].fill, RegionPalette[7].fill)

/**
 * Warm gold, not cream. The first pass used a pale bone colour that washed out
 * against a light background and read as beige rather than as a thing worth
 * keeping.
 */
private val LiveBone = Color(0xFFF5C043)
private val LiveBoneEdge = Color(0xFFC8871B)

/** Spent bones stay bone-shaped but go colourless, so the loss is legible at a glance. */
private val SpentBone = Color(0xFFDEDAD6)
private val SpentBoneEdge = Color(0xFFB4AEA8)
private val EarnedPaw = Color(0xFFF5B93D)
private val UnearnedPaw = Color(0x33000000)

@Preview
@Composable
private fun GameHudPreview() {
    PreviewContent {
        Column(verticalArrangement = Arrangement.spacedBy(Dimension.D500)) {
            LifeRow(remaining = 2)
            PawRating(paws = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D400)) {
                BoosterButton(label = "Sniff", count = 3)
                BoosterButton(label = "Treat", count = 0)
            }
        }
    }
}
