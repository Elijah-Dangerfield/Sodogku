package com.sodogku.features.achievements.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.features.achievements.AchievementCopy
import com.sodogku.libraries.achievements.AchievementGroup
import com.sodogku.libraries.achievements.AchievementId
import com.sodogku.libraries.ui.Border
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.FullScreenLoader
import com.sodogku.libraries.ui.components.NonLazyVerticalGrid
import com.sodogku.libraries.ui.components.ProgressRow
import com.sodogku.libraries.ui.components.Screen
import com.sodogku.libraries.ui.components.Surface
import com.sodogku.libraries.ui.components.button.ButtonPrimary
import com.sodogku.libraries.ui.components.dialog.Dialog
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.game.Stat
import com.sodogku.libraries.ui.components.game.StatPills
import com.sodogku.libraries.ui.components.header.TopBar
import com.sodogku.libraries.ui.components.icon.Icon
import com.sodogku.libraries.ui.components.icon.IconSize
import com.sodogku.libraries.ui.components.icon.Icons
import com.sodogku.libraries.ui.components.text.CountUpNumber
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.screenContentPadding
import com.sodogku.libraries.ui.system.LocalReduceAnimations
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Motion
import com.sodogku.system.Radii
import com.sodogku.system.VerticalSpacerD300
import com.sodogku.system.VerticalSpacerD500
import com.sodogku.system.VerticalSpacerD700
import com.sodogku.system.VerticalSpacerD800
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.achievements_earned
import sodogku.libraries.resources.generated.resources.achievements_empty_body
import sodogku.libraries.resources.generated.resources.achievements_hero_label
import sodogku.libraries.resources.generated.resources.achievements_hidden_body
import sodogku.libraries.resources.generated.resources.achievements_hidden_name
import sodogku.libraries.resources.generated.resources.achievements_just_earned
import sodogku.libraries.resources.generated.resources.achievements_locked
import sodogku.libraries.resources.generated.resources.achievements_next_up
import sodogku.libraries.resources.generated.resources.achievements_off_body
import sodogku.libraries.resources.generated.resources.achievements_off_title
import sodogku.libraries.resources.generated.resources.achievements_progress
import sodogku.libraries.resources.generated.resources.achievements_spoken_earned
import sodogku.libraries.resources.generated.resources.achievements_spoken_hidden
import sodogku.libraries.resources.generated.resources.achievements_spoken_new
import sodogku.libraries.resources.generated.resources.achievements_spoken_progress
import sodogku.libraries.resources.generated.resources.achievements_stat_earned
import sodogku.libraries.resources.generated.resources.achievements_stat_earned_spoken
import sodogku.libraries.resources.generated.resources.achievements_stat_sets
import sodogku.libraries.resources.generated.resources.achievements_stat_sets_spoken
import sodogku.libraries.resources.generated.resources.achievements_stat_sets_value
import sodogku.libraries.resources.generated.resources.achievements_stat_to_go
import sodogku.libraries.resources.generated.resources.achievements_stat_to_go_spoken
import sodogku.libraries.resources.generated.resources.achievements_title
import sodogku.libraries.resources.generated.resources.common_close

/**
 * Every badge in the catalog, earned or not.
 *
 * Built out of the same three pieces the rest of the app celebrates with, so it
 * reads as the same product: the hero number and its count-up-then-thump are
 * `CountUpNumber`, lifted out of `StreakHero`; the facts underneath are the win
 * sheet's `StatPills`; the register throughout is the win sheet's, which is
 * measurements rather than encouragement.
 *
 * Locked badges are **shown**, with how far along they are. A grid that only
 * held what a player already has is a trophy case; the ones they have not got
 * are the reason to open it. They sit on the same card as an earned badge for
 * the same reason — the version this replaced drew them on a surface two steps
 * from the page colour, so seventy-three of them read as empty slots rather than
 * as things worth having.
 *
 * The hidden badges are the exception, and they are hidden rather than absent —
 * a card with "???" says there is something there to find, which is the whole
 * point of a surprise.
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

        CollectionHero(state = state)

        state.spotlight?.let { spotlight ->
            VerticalSpacerD800()
            SpotlightShelf(
                spotlight = spotlight,
                onSelect = { onAction(AchievementsAction.Select(it)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

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
 * How many, how far, out of what.
 *
 * The number is the page, the way the streak is the streak page. It climbs only
 * when the page is holding news, which is the same rule and for the same reason
 * — see [AchievementsState.countUpFrom].
 *
 * The pills repeat the earned count that the hero just said, deliberately. The
 * win sheet does exactly this with the score: the big number is the moment, the
 * pill is the fact, and a row of three reads in one look where three sentences
 * would each have to be read.
 */
@Composable
private fun CollectionHero(state: AchievementsState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Dog(pose = DogPose.Solved, size = Dimension.D1900)

        VerticalSpacerD300()

        CountUpNumber(value = state.earnedCount, countUpFrom = state.countUpFrom)

        Text(
            text = stringResource(Res.string.achievements_hero_label, state.totalCount.toString()),
            typography = AppTheme.typography.Heading.H600,
            color = AppTheme.colors.accentBrand,
            textAlign = TextAlign.Center,
        )

        // Only on a genuinely empty page. Explaining where badges come from to
        // somebody who already has some is noise, and the streak page settled
        // the same question the same way.
        if (state.earnedCount == 0) {
            VerticalSpacerD500()
            Text(
                text = stringResource(Res.string.achievements_empty_body),
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }

        VerticalSpacerD700()

        StatPills(stats = collectionStats(state))
    }
}

@Composable
private fun collectionStats(state: AchievementsState): List<Stat> = listOf(
    Stat(
        caption = stringResource(Res.string.achievements_stat_earned),
        value = state.earnedCount.toString(),
        tint = AppTheme.colors.accentPrimary.color,
        spoken = stringResource(Res.string.achievements_stat_earned_spoken, state.earnedCount.toString()),
    ),
    Stat(
        caption = stringResource(Res.string.achievements_stat_to_go),
        value = state.lockedCount.toString(),
        tint = AppTheme.colors.accentSecondary.color,
        spoken = stringResource(Res.string.achievements_stat_to_go_spoken, state.lockedCount.toString()),
    ),
    Stat(
        caption = stringResource(Res.string.achievements_stat_sets),
        value = stringResource(
            Res.string.achievements_stat_sets_value,
            state.completedSetCount.toString(),
            state.sections.size.toString(),
        ),
        tint = AppTheme.colors.status.okay.color,
        spoken = stringResource(
            Res.string.achievements_stat_sets_spoken,
            state.completedSetCount.toString(),
            state.sections.size.toString(),
        ),
    ),
)

/**
 * The pinned row above the grid: either what just landed, or what is nearly in
 * reach.
 *
 * This is the part that makes an unlock an *event*. A badge earned while the
 * page is open used to change the colour of one tile, possibly four shelves
 * below the fold; now it is lifted to the top, under a heading that says so,
 * while the hero counts up to include it.
 *
 * Full-width rows rather than more grid cells, on purpose. Three detailed rows
 * above seventy-three scannable squares is a hierarchy; three slightly different
 * squares among seventy-six is not.
 */
@Composable
private fun SpotlightShelf(
    spotlight: Spotlight,
    onSelect: (AchievementId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = when (spotlight.kind) {
                SpotlightKind.JustEarned -> stringResource(Res.string.achievements_just_earned)
                SpotlightKind.NextUp -> stringResource(Res.string.achievements_next_up)
            },
            typography = AppTheme.typography.Heading.H700,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        VerticalSpacerD300()

        Column(
            verticalArrangement = Arrangement.spacedBy(Dimension.D400),
            modifier = Modifier.fillMaxWidth(),
        ) {
            spotlight.badges.forEachIndexed { index, badge ->
                StaggeredEntry(index = index, animated = spotlight.kind == SpotlightKind.JustEarned) {
                    SpotlightCard(
                        badge = badge,
                        celebrated = spotlight.kind == SpotlightKind.JustEarned,
                        onClick = { onSelect(badge.id) },
                    )
                }
            }
        }
    }
}

/**
 * Lets a row arrive a beat after the one above it.
 *
 * Three badges landing in the same second is the normal case, not the edge one —
 * a first clear can earn three at once. Dropped in together they read as one
 * lump; dropped in a beat apart they read as three things, which is what they
 * are. The same slide the unlock toast uses, so it is recognisably the same
 * event arriving in a different place.
 *
 * Entry only. A row that has arrived never leaves, so an exit transition would
 * only ever be dead code.
 */
@Composable
private fun StaggeredEntry(index: Int, animated: Boolean, content: @Composable () -> Unit) {
    // A pending `delay` is not an idle composition, so a preview or a screenshot
    // capture would wait for a frame that never settles.
    val still = !animated || LocalReduceAnimations.current || LocalInspectionMode.current
    var shown by remember(index, still) { mutableStateOf(still) }

    LaunchedEffect(index, still) {
        if (still) return@LaunchedEffect
        // Capped, because the count here is however many badges have landed
        // since the last look. Three is the common case; a catalog that grew in
        // an update can hand this twenty, and twenty beats is not a reveal.
        delay((index * StaggerMillis).coerceAtMost(MaxStaggerMillis))
        shown = true
    }

    AnimatedVisibility(
        visible = shown,
        enter = slideInVertically { -it } + fadeIn(),
        exit = ExitTransition.None,
    ) {
        content()
    }
}

/**
 * One pinned row: the badge, what it is, and either that it is won or how much
 * of it is left.
 *
 * The bar lives here and nowhere else on the page. Seventy-three of them would
 * be a dashboard; three of them, above the fold, on the badges a player can
 * actually reach, is the only place the extra weight buys anything. Everywhere
 * else progress is a number, which says the same thing in a line of caption type.
 */
@Composable
private fun SpotlightCard(badge: Badge, celebrated: Boolean, onClick: () -> Unit) {
    val spoken = badge.spoken()

    Surface(
        color = AppTheme.colors.surfacePrimary,
        contentColor = AppTheme.colors.onSurfacePrimary,
        modifier = Modifier.fillMaxWidth(),
        radius = Radii.Card,
        border = if (celebrated) Border(AppTheme.colors.accentPrimary, Dimension.D50) else null,
        onClick = onClick,
        bounceScale = Motion.PressScale,
        // A parameter rather than a `clearAndSetSemantics` on the modifier
        // above: passed in, it would clear the click action along with the
        // labels. See `Surface`.
        contentDescription = spoken,
        contentPadding = PaddingValues(
            horizontal = Dimension.D600,
            vertical = Dimension.D500,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimension.D500),
        ) {
            Text(text = badge.face(), typography = AppTheme.typography.Display.D800)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (celebrated) {
                        stringResource(Res.string.achievements_earned)
                    } else {
                        stringResource(AchievementCopy.groupName(badge.group))
                    },
                    typography = AppTheme.typography.Caption.C300,
                    color = AppTheme.colors.accentPrimary,
                )
                Text(text = badge.displayName(), typography = AppTheme.typography.Body.B600)

                if (!celebrated) {
                    VerticalSpacerD300()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimension.D400),
                    ) {
                        ProgressRow(
                            progressPercent = badge.progress,
                            shape = Radii.Progress.shape,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = badge.progressLabel(),
                            typography = AppTheme.typography.Caption.C300,
                            color = AppTheme.colors.textSecondary,
                        )
                    }
                }
            }

            // The same trophy the unlock toast puts at the trailing edge of
            // every pill, and for the same reason: the glyph on the left
            // varies, so nothing else on the row says "you earned something"
            // rather than "here is a thing".
            if (celebrated) {
                Icon(
                    icon = Icons.Trophy.decorative,
                    size = IconSize.Small,
                    color = AppTheme.colors.accentPrimary,
                )
            }
        }
    }
}

/**
 * One card in the grid.
 *
 * Earned and locked share a surface and differ in the two places that carry
 * meaning: the ring, and the line under the name. A locked badge that is drawn
 * on the page colour with a faded glyph and nothing else is an empty slot, and a
 * wall of empty slots is not a collection anybody wants to fill.
 */
@Composable
private fun BadgeTile(badge: Badge, onClick: () -> Unit) {
    val spoken = badge.spoken()

    Surface(
        color = AppTheme.colors.surfacePrimary,
        contentColor = AppTheme.colors.onSurfacePrimary,
        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        radius = Radii.Card,
        // The earned outline is in the card's own shape and sits *outside* the
        // clip. Both halves matter: a square border on a rounded card loses its
        // corners, and a border drawn inside the clip has its outer edge shaved
        // off by it. `Surface` does both.
        border = if (badge.unlocked) Border(AppTheme.colors.accentPrimary, Dimension.D50) else null,
        onClick = onClick,
        bounceScale = Motion.PressScale,
        // See `SpotlightCard`: the tile's three labels would otherwise be read
        // one after another.
        contentDescription = spoken,
        contentPadding = PaddingValues(Dimension.D300),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The glyph still carries the state, but it is dimmed rather than
            // ghosted: a locked badge has to look like something to want. Read
            // in a graphicsLayer lambda rather than in composition, per the DS
            // rule.
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
                color = if (badge.unlocked) AppTheme.colors.text else AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = MaxNameLines,
            )
            // A number, not a bar. "4 / 10" is the information a bar was
            // carrying, in one line of caption type, and it is on every locked
            // badge rather than only the ones that happen to have started — a
            // line that appears and disappears down a grid is what made the old
            // page look ragged.
            //
            // The same C300 the spotlight rows, the stat pills and the win
            // sheet use for a line of metadata. It was a step below that, which
            // made the smallest text on the page the one carrying the only
            // number on the tile.
            //
            // Nothing on a mystery badge: its numbers are zeroed upstream, and
            // even "0 / 1" would say "one clear does it".
            if (!badge.mystery) {
                Text(
                    text = if (badge.unlocked) {
                        stringResource(Res.string.achievements_earned)
                    } else {
                        badge.progressLabel()
                    },
                    typography = AppTheme.typography.Caption.C300,
                    color = if (badge.unlocked) {
                        AppTheme.colors.accentPrimary
                    } else {
                        AppTheme.colors.textSecondary
                    },
                    maxLines = 1,
                )
            }
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
 *
 * Scrolls, like `GameDialogHost` does. `Dialog` sizes itself to its content and
 * then stops at the window, so a long badge description at the largest system
 * font pushes the Close button off the bottom of a card that cannot move — the
 * player is left looking at a dialog they can only leave by pressing back.
 */
@Composable
private fun BadgeDetailDialog(badge: Badge, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimension.D500),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
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
                Text(text = badge.progressLabel(), typography = AppTheme.typography.Body.B600)
                ProgressRow(progressPercent = badge.progress, shape = Radii.Progress.shape)
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

@Composable
private fun Badge.progressLabel(): String =
    stringResource(Res.string.achievements_progress, current.toString(), target.toString())

/**
 * The whole badge as one sentence, for a screen reader.
 *
 * Name, state, and how far along — the three things a sighted player gets from
 * the ring, the glyph and the counter in a single glance, and which arrive as
 * disconnected fragments if each label is left to speak for itself.
 */
@Composable
private fun Badge.spoken(): String {
    val name = displayName()
    return when {
        mystery -> stringResource(Res.string.achievements_spoken_hidden)
        isNew -> stringResource(Res.string.achievements_spoken_new, name)
        unlocked -> stringResource(Res.string.achievements_spoken_earned, name)
        else -> stringResource(
            Res.string.achievements_spoken_progress,
            name,
            current.toString(),
            target.toString(),
        )
    }
}

/** Three across fits a two-word badge name on the narrowest phone we support. */
private const val GridColumns = 3

private const val MaxNameLines = 2

private const val MysteryGlyph = "❓"

/** Dimmed, not ghosted: the shape of an unearned badge is half the invitation. */
private const val LockedGlyphAlpha = 0.45f

/** One beat between rows. Short enough that three still land as one moment. */
private const val StaggerMillis = 120L

private const val MaxStaggerMillis = 600L

@Preview
@Composable
private fun AchievementsScreenPreview() {
    PreviewContent {
        AchievementsScreen(state = previewState(), onAction = {})
    }
}

@Preview
@Composable
private fun AchievementsJustEarnedPreview() {
    PreviewContent {
        AchievementsScreen(
            state = previewState().copy(
                badges = previewState().badges.map {
                    if (it.id == AchievementId.SpeedDemon) it.copy(isNew = true) else it
                },
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun AchievementsEmptyPreview() {
    PreviewContent {
        AchievementsScreen(
            state = previewState().copy(
                badges = previewState().badges.map {
                    it.copy(unlocked = false, isNew = false, progress = 0f, current = 0L)
                },
            ),
            onAction = {},
        )
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
    seenAt = 0L,
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
