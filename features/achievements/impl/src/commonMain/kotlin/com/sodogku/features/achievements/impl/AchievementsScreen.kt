package com.sodogku.features.achievements.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.features.achievements.AchievementCopy
import com.sodogku.libraries.achievements.AchievementGroup
import com.sodogku.libraries.achievements.AchievementId
import com.sodogku.libraries.ui.Border
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.border
import com.sodogku.libraries.ui.bounceClick
import com.sodogku.libraries.ui.components.FullScreenLoader
import com.sodogku.libraries.ui.components.NonLazyVerticalGrid
import com.sodogku.libraries.ui.components.ProgressRow
import com.sodogku.libraries.ui.components.Screen
import com.sodogku.libraries.ui.components.button.ButtonPrimary
import com.sodogku.libraries.ui.components.dialog.Dialog
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.header.TopBar
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.screenContentPadding
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import com.sodogku.system.VerticalSpacerD300
import com.sodogku.system.VerticalSpacerD500
import com.sodogku.system.VerticalSpacerD800
import com.sodogku.system.clip
import com.sodogku.system.thenIf
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.achievements_earned
import sodogku.libraries.resources.generated.resources.achievements_hidden_body
import sodogku.libraries.resources.generated.resources.achievements_hidden_name
import sodogku.libraries.resources.generated.resources.achievements_locked
import sodogku.libraries.resources.generated.resources.achievements_off_body
import sodogku.libraries.resources.generated.resources.achievements_off_title
import sodogku.libraries.resources.generated.resources.achievements_progress
import sodogku.libraries.resources.generated.resources.achievements_summary
import sodogku.libraries.resources.generated.resources.achievements_title
import sodogku.libraries.resources.generated.resources.common_close

/**
 * Every badge in the catalog, earned or not.
 *
 * Locked badges are **shown**, with how far along they are. A grid that only
 * held what a player already has is a trophy case; the ones they have not got
 * are the reason to open it. The hidden badges are the exception, and they are
 * hidden rather than absent — a grey card with "???" says there is something
 * there to find, which is the whole point of a surprise.
 *
 * The grid is drawn shelf by shelf. At twenty-one badges the catalog's ordering
 * carried the grouping on its own; at seventy-three it does not, and an
 * unlabelled wall of tiles is a bag rather than a set of ladders.
 */
@Composable
fun AchievementsScreen(
    state: AchievementsState,
    onAction: (AchievementsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Screen(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopBar(
                title = stringResource(Res.string.achievements_title),
                onNavigateBack = { onAction(AchievementsAction.Back) },
                scrollState = scrollState,
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.loading -> FullScreenLoader()
                !state.visible -> BadgesOff(
                    modifier = Modifier
                        .fillMaxSize()
                        .screenContentPadding(padding),
                )
                else -> BadgeGrid(
                    state = state,
                    onAction = onAction,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .screenContentPadding(padding),
                )
            }

            // Renders nothing in place: the design system's dialog registers
            // itself with the host mounted in `App.kt` and is drawn over the
            // whole window, top bar included.
            state.selected?.let { badge ->
                BadgeDetailDialog(
                    badge = badge,
                    onDismiss = { onAction(AchievementsAction.CloseDetail) },
                )
            }
        }
    }
}

@Composable
private fun BadgeGrid(
    state: AchievementsState,
    onAction: (AchievementsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        VerticalSpacerD500()

        Dog(pose = DogPose.Solved, size = Dimension.D1900)

        Text(
            text = stringResource(
                Res.string.achievements_summary,
                state.earnedCount.toString(),
                state.totalCount.toString(),
            ),
            typography = AppTheme.typography.Heading.H600,
        )

        state.sections.forEach { section ->
            VerticalSpacerD800()

            Text(
                text = stringResource(AchievementCopy.groupName(section.group)),
                typography = AppTheme.typography.Heading.H700,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            VerticalSpacerD300()

            NonLazyVerticalGrid(
                columns = GridColumns,
                data = section.badges,
                verticalSpacing = Dimension.D400,
                horizontalSpacing = Dimension.D400,
            ) { _, badge ->
                BadgeTile(badge = badge, onClick = { onAction(AchievementsAction.Select(badge.id)) })
            }
        }

        VerticalSpacerD800()
    }
}

/**
 * One card in the grid.
 *
 * Locked badges keep their shape and lose their colour, so the grid reads as
 * one set at a glance rather than as two.
 */
@Composable
private fun BadgeTile(badge: Badge, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            // Before the clip and the background, or only the label scales.
            .bounceClick(onClick = onClick)
            .fillMaxWidth()
            .aspectRatio(1f)
            // The earned outline is in the card's own shape and sits *outside*
            // the clip. Both halves matter: a square border on a rounded card
            // loses its corners, and a border drawn inside the clip has its
            // outer edge shaved off by it.
            .thenIf(badge.unlocked) { border(Border(AppTheme.colors.accentPrimary), Radii.Card) }
            .clip(Radii.Card)
            .background(
                if (badge.unlocked) {
                    AppTheme.colors.surfacePrimary.color
                } else {
                    AppTheme.colors.surfaceSecondary.color
                },
            )
            .padding(Dimension.D300),
    ) {
        // Colour separates the two states weakly here — the locked surface and
        // the page behind it are two steps apart on the grey ramp — so the
        // glyph carries it: earned badges are lit, the rest are faded. Read in
        // a graphicsLayer lambda rather than in composition, per the DS rule.
        Text(
            text = badge.face(),
            typography = AppTheme.typography.Display.D1000,
            modifier = Modifier.graphicsLayer {
                alpha = if (badge.unlocked) 1f else LockedGlyphAlpha
            },
        )
        VerticalSpacerD300()
        Text(
            text = badge.displayName(),
            typography = AppTheme.typography.Caption.C300,
            color = if (badge.unlocked) {
                AppTheme.colors.text
            } else {
                AppTheme.colors.textSecondary
            },
            textAlign = TextAlign.Center,
            maxLines = MaxNameLines,
        )
        // Only on a badge that is genuinely part-way there. A bar sitting at
        // zero under fifteen unstarted cards is furniture that says nothing.
        if (!badge.unlocked && badge.progress > 0f) {
            VerticalSpacerD300()
            ProgressRow(
                progressPercent = badge.progress,
                modifier = Modifier.padding(horizontal = Dimension.D300),
            )
        }
    }
}

/**
 * The badge, full size, with what it takes to earn it.
 *
 * The design system's [Dialog], not a hand-rolled scrim. The first version was
 * a `Box` with a background inside the screen's content slot, and it paid for
 * that three times over: no entrance animation (the card and its scrim simply
 * appeared, a single frame apart from nothing), no back-press handling, and a
 * scrim that stopped at the top bar because it could only cover its own
 * sibling. A new surface should get all of that from the design system without
 * its author knowing the rules exist.
 */
@Composable
private fun BadgeDetailDialog(badge: Badge, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimension.D500),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = badge.face(), typography = AppTheme.typography.Display.D1400)
            Text(
                text = badge.displayName(),
                typography = AppTheme.typography.Heading.H700,
                textAlign = TextAlign.Center,
            )
            Text(
                text = badge.displayDescription(),
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (badge.unlocked) {
                    stringResource(Res.string.achievements_earned)
                } else {
                    stringResource(Res.string.achievements_locked)
                },
                typography = AppTheme.typography.Label.L400,
                color = if (badge.unlocked) {
                    AppTheme.colors.accentPrimary
                } else {
                    AppTheme.colors.textSecondary
                },
            )
            // No bar on a mystery badge: its numbers are zeroed upstream, and a
            // 0/1 bar would still say "one clear does it".
            if (!badge.unlocked && !badge.mystery) {
                Text(
                    text = stringResource(
                        Res.string.achievements_progress,
                        badge.current.toString(),
                        badge.target.toString(),
                    ),
                    typography = AppTheme.typography.Body.B600,
                )
                if (badge.progress > 0f) {
                    ProgressRow(progressPercent = badge.progress)
                }
            }
            ButtonPrimary(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.common_close))
            }
        }
    }
}

/**
 * What the screen says when the player has switched badges off.
 *
 * It says recording carried on, because the alternative reading — that turning
 * them off threw the history away — is the one that stops somebody turning them
 * back on.
 */
@Composable
private fun BadgesOff(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(horizontal = Dimension.D800),
    ) {
        Dog(pose = DogPose.Thinking)
        VerticalSpacerD500()
        Text(
            text = stringResource(Res.string.achievements_off_title),
            typography = AppTheme.typography.Heading.H700,
            textAlign = TextAlign.Center,
        )
        VerticalSpacerD300()
        Text(
            text = stringResource(Res.string.achievements_off_body),
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

private fun Badge.face(): String =
    if (mystery) MysteryGlyph else AchievementCopy.glyph(id)

@Composable
private fun Badge.displayName(): String = if (mystery) {
    stringResource(Res.string.achievements_hidden_name)
} else {
    stringResource(AchievementCopy.name(id))
}

@Composable
private fun Badge.displayDescription(): String = if (mystery) {
    stringResource(Res.string.achievements_hidden_body)
} else {
    stringResource(AchievementCopy.description(id))
}

/** Three across fits a two-word badge name on the narrowest phone we support. */
private const val GridColumns = 3

private const val MaxNameLines = 2

private const val MysteryGlyph = "❓"

/** Faded, not hidden: the shape of an unearned badge is half the invitation. */
private const val LockedGlyphAlpha = 0.35f

@Preview
@Composable
private fun AchievementsScreenPreview() {
    PreviewContent {
        AchievementsScreen(state = previewState(), onAction = {})
    }
}

@Preview
@Composable
private fun BadgeDetailPreview() {
    PreviewContent {
        AchievementsScreen(
            state = previewState().copy(selectedId = AchievementId.GoodDog),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun AchievementsOffPreview() {
    PreviewContent {
        AchievementsScreen(
            state = previewState().copy(visible = false),
            onAction = {},
        )
    }
}

private fun previewState(): AchievementsState = AchievementsState(
    loading = false,
    badges = listOf(
        badge(
            id = AchievementId.FirstSteps,
            group = AchievementGroup.Campaign,
            unlocked = true,
            progress = 1f,
            current = 1L,
        ),
        badge(
            id = AchievementId.GoodDog,
            group = AchievementGroup.Campaign,
            progress = 0.4f,
            current = 4L,
            target = 10L,
        ),
        badge(id = AchievementId.BestInShow, group = AchievementGroup.Campaign, target = 100L),
        badge(
            id = AchievementId.SpeedDemon,
            group = AchievementGroup.Speed,
            unlocked = true,
            progress = 1f,
            current = 1L,
        ),
        badge(
            id = AchievementId.ChainOfEight,
            group = AchievementGroup.Score,
            progress = 0.75f,
            current = 6L,
            target = 8L,
        ),
        badge(id = AchievementId.NightOwl, group = AchievementGroup.Secrets, mystery = true),
    ),
)

private fun badge(
    id: AchievementId,
    group: AchievementGroup,
    unlocked: Boolean = false,
    mystery: Boolean = false,
    progress: Float = 0f,
    current: Long = 0L,
    target: Long = 1L,
): Badge = Badge(
    id = id,
    group = group,
    unlocked = unlocked,
    mystery = mystery,
    progress = progress,
    current = current,
    target = target,
)
