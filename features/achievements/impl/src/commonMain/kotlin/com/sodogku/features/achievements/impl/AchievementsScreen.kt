package com.sodogku.features.achievements.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.features.achievements.AchievementCopy
import com.sodogku.libraries.achievements.AchievementGroup
import com.sodogku.libraries.achievements.AchievementId
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.FullScreenLoader
import com.sodogku.libraries.ui.components.ProgressBar
import com.sodogku.libraries.ui.components.Screen
import com.sodogku.libraries.ui.components.SectionHeader
import com.sodogku.libraries.ui.components.achievements.BadgeProgress
import com.sodogku.libraries.ui.components.achievements.BadgeSpotlightCard
import com.sodogku.libraries.ui.components.achievements.BadgeSpotlightSpec
import com.sodogku.libraries.ui.components.achievements.BadgeTileGrid
import com.sodogku.libraries.ui.components.achievements.BadgeTileSpec
import com.sodogku.libraries.ui.components.achievements.BadgeTileState
import com.sodogku.libraries.ui.components.button.ButtonPrimary
import com.sodogku.libraries.ui.components.button.ButtonSize
import com.sodogku.libraries.ui.components.dialog.Dialog
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.game.StatChipRow
import com.sodogku.libraries.ui.components.game.StatChipSpec
import com.sodogku.libraries.ui.components.header.TopBar
import com.sodogku.libraries.ui.components.text.CountUpNumber
import com.sodogku.libraries.ui.components.text.DisplayNumeralStrokeWidth
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.screenContentPadding
import com.sodogku.libraries.ui.system.LocalReduceAnimations
import com.sodogku.libraries.ui.system.color.BadgeSetStyle
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.VerticalSpacerD300
import com.sodogku.system.VerticalSpacerD500
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.achievements_card_fraction
import sodogku.libraries.resources.generated.resources.achievements_closest_to_done
import sodogku.libraries.resources.generated.resources.achievements_earned
import sodogku.libraries.resources.generated.resources.achievements_empty_body
import sodogku.libraries.resources.generated.resources.achievements_hero_label
import sodogku.libraries.resources.generated.resources.achievements_hidden_body
import sodogku.libraries.resources.generated.resources.achievements_hidden_name
import sodogku.libraries.resources.generated.resources.achievements_just_earned
import sodogku.libraries.resources.generated.resources.achievements_locked
import sodogku.libraries.resources.generated.resources.achievements_off_body
import sodogku.libraries.resources.generated.resources.achievements_off_title
import sodogku.libraries.resources.generated.resources.achievements_progress
import sodogku.libraries.resources.generated.resources.achievements_section_count
import sodogku.libraries.resources.generated.resources.achievements_see_all
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
 * Every badge in the catalog, earned or not, rebuilt to the 2026-09 handoff's
 * row 10 around what the player is closest to earning.
 *
 * One scrolling page, not two screens. The handoff draws the top and the full
 * grid as separate frames, and `SEE ALL` between them; here the grid is the
 * rest of the same column and the button scrolls to it. A second route would
 * have been a page whose only content was a link to the page under it.
 *
 * Locked badges are **shown**, with how far along they are. A grid that only
 * held what a player already has is a trophy case; the ones they have not got
 * are the reason to open it. Sets carry a colour, and the rung the player is on
 * keeps it while the rest of the shelf goes grey, so a shelf reads as one thing
 * to do next and a row of things after it.
 *
 * The hidden badges are the exception, and they are hidden rather than absent:
 * a card with "???" says there is something there to find, which is the whole
 * point of a surprise.
 */
@Composable
fun AchievementsScreen(
    state: AchievementsState,
    onAction: (AchievementsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var viewport by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Screen(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopBar(
                title = stringResource(Res.string.achievements_title),
                typographyToken = AppTheme.typography.Display.D1000,
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
                else -> BadgePage(
                    state = state,
                    onAction = onAction,
                    scrollTo = { target ->
                        // The header's place in the viewport plus what is already
                        // scrolled past is its place in the content, whatever
                        // the frame it was measured in.
                        viewport?.let { it.localPositionOf(target, Offset.Zero).y + scrollState.value }
                    },
                    scrollState = scrollState,
                    modifier = Modifier
                        .fillMaxSize()
                        // Before the scroll in the chain, so these are the
                        // coordinates of the window the content moves inside.
                        .onGloballyPositioned { viewport = it }
                        .verticalScroll(scrollState)
                        .screenContentPadding(padding, includeHorizontalInsets = false)
                        .padding(horizontal = ScreenGutter),
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
private fun BadgePage(
    state: AchievementsState,
    onAction: (AchievementsAction) -> Unit,
    scrollTo: (LayoutCoordinates) -> Float?,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    var firstShelf by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val still = LocalReduceAnimations.current || LocalInspectionMode.current
    val scope = rememberCoroutineScope()
    val headroom = with(LocalDensity.current) { ShelfHeadroom.toPx() }

    Column(modifier = modifier) {
        Spacer(Modifier.height(HeroTop))
        CollectionHero(state = state)

        Spacer(Modifier.height(ChipsTop))
        StatChipRow(chips = collectionStats(state))

        state.spotlight?.let { spotlight ->
            Spacer(Modifier.height(SpotlightTop))
            SpotlightShelf(
                spotlight = spotlight,
                onSelect = { onAction(AchievementsAction.Select(it)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.sections.isNotEmpty()) {
            Spacer(Modifier.height(ButtonTop))
            ButtonPrimary(
                size = ButtonSize.Hero,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val target = firstShelf?.let(scrollTo) ?: return@ButtonPrimary
                    val offset = (target - headroom).toInt().coerceAtLeast(0)
                    scope.launch {
                        if (still) scrollState.scrollTo(offset) else scrollState.animateScrollTo(offset)
                    }
                },
            ) {
                Text(stringResource(Res.string.achievements_see_all, state.totalCount.toString()))
            }
        }

        state.sections.forEachIndexed { index, section ->
            Spacer(Modifier.height(ShelfTop))
            Shelf(
                section = section,
                onSelect = { onAction(AchievementsAction.Select(it)) },
                modifier = if (index == 0) Modifier.onGloballyPositioned { firstShelf = it } else Modifier,
            )
        }

        Spacer(Modifier.height(PageBottom))
    }
}

/**
 * How many, out of what, beside the dog rather than under it.
 *
 * The number is the page, the way the streak is the streak page. It climbs only
 * when the page is holding news, which is the same rule and for the same reason:
 * see [AchievementsState.countUpFrom].
 */
@Composable
private fun CollectionHero(state: AchievementsState, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimension.D700),
            modifier = Modifier.padding(horizontal = HeroInset),
        ) {
            Dog(pose = DogPose.Solved, size = HeroDogSize)

            Column {
                CountUpNumber(
                    value = state.earnedCount,
                    countUpFrom = state.countUpFrom,
                    typography = AppTheme.typography.Display.D1400,
                    color = AppTheme.colors.accentBrand,
                    strokeColor = AppTheme.colors.textOutline,
                    strokeWidth = DisplayNumeralStrokeWidth,
                )
                Text(
                    text = stringResource(Res.string.achievements_hero_label, state.totalCount.toString()),
                    typography = AppTheme.typography.Label.L750.Bold,
                    color = AppTheme.colors.accentBrandInk,
                )
            }
        }

        // Only on a genuinely empty page. Explaining where badges come from to
        // somebody who already has some is noise, and the streak page settled
        // the same question the same way.
        if (state.earnedCount == 0) {
            VerticalSpacerD500()
            Text(
                text = stringResource(Res.string.achievements_empty_body),
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = HeroInset),
            )
        }
    }
}

/**
 * The three chips repeat the earned count the hero just said, deliberately.
 * The board-cleared screen does exactly this with the score: the big number is
 * the moment, the chip is the fact, and a row of three reads in one look where
 * three sentences would each have to be read.
 */
@Composable
private fun collectionStats(state: AchievementsState): List<StatChipSpec> = listOf(
    StatChipSpec(
        label = stringResource(Res.string.achievements_stat_earned),
        value = state.earnedCount.toString(),
        tint = AppTheme.colors.accentBrand,
        labelInk = AppTheme.colors.onAccentBrand,
        valueInk = AppTheme.colors.accentBrandInk,
        spoken = stringResource(Res.string.achievements_stat_earned_spoken, state.earnedCount.toString()),
    ),
    StatChipSpec(
        label = stringResource(Res.string.achievements_stat_to_go),
        value = state.lockedCount.toString(),
        tint = AppTheme.colors.accentSecondary,
        labelInk = AppTheme.colors.onAccentSecondary,
        valueInk = AppTheme.colors.accentSecondary,
        spoken = stringResource(Res.string.achievements_stat_to_go_spoken, state.lockedCount.toString()),
    ),
    StatChipSpec(
        label = stringResource(Res.string.achievements_stat_sets),
        value = stringResource(
            Res.string.achievements_stat_sets_value,
            state.completedSetCount.toString(),
            state.sections.size.toString(),
        ),
        tint = AppTheme.colors.status.okay,
        labelInk = AppTheme.colors.onAccentPrimary,
        valueInk = AppTheme.colors.status.okayInk,
        spoken = stringResource(
            Res.string.achievements_stat_sets_spoken,
            state.completedSetCount.toString(),
            state.sections.size.toString(),
        ),
    ),
)

/**
 * The rows pinned above the grid: either what just landed, or what is nearly in
 * reach.
 *
 * This is the part that makes an unlock an *event*. A badge earned while the
 * page is open used to change the colour of one tile, possibly four shelves
 * below the fold; now it is lifted to the top, under a heading that says so,
 * while the hero counts up to include it.
 */
@Composable
private fun SpotlightShelf(
    spotlight: Spotlight,
    onSelect: (AchievementId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val celebrated = spotlight.kind == SpotlightKind.JustEarned

    Column(modifier = modifier) {
        Text(
            text = when (spotlight.kind) {
                SpotlightKind.JustEarned -> stringResource(Res.string.achievements_just_earned)
                SpotlightKind.NextUp -> stringResource(Res.string.achievements_closest_to_done)
            },
            typography = AppTheme.typography.Heading.H800,
            color = AppTheme.colors.text,
            modifier = Modifier.padding(horizontal = HeroInset),
        )
        Spacer(Modifier.height(Dimension.D600))

        Column(
            verticalArrangement = Arrangement.spacedBy(Dimension.D500),
            modifier = Modifier.fillMaxWidth(),
        ) {
            spotlight.badges.forEachIndexed { index, badge ->
                StaggeredEntry(index = index, animated = celebrated) {
                    BadgeSpotlightCard(
                        spec = badge.spotlightSpec(celebrated = celebrated),
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
 * Three badges landing in the same second is the normal case, not the edge one:
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

/** One chapter of the grid: its header, and its badges three across. */
@Composable
private fun Shelf(
    section: BadgeSection,
    onSelect: (AchievementId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nearest = section.nearest?.id
    val earnedLabel = stringResource(Res.string.achievements_earned)

    Column(modifier = modifier) {
        SectionHeader(
            title = stringResource(AchievementCopy.groupName(section.group)),
            dot = section.group.set().fill,
            count = stringResource(
                Res.string.achievements_section_count,
                section.badges.count { it.unlocked }.toString(),
                section.badges.size.toString(),
            ),
            modifier = Modifier.padding(horizontal = HeroInset),
        )
        Spacer(Modifier.height(Dimension.D600))
        BadgeTileGrid(
            tiles = section.badges.map { it.tileSpec(upNext = it.id == nearest) },
            earnedLabel = earnedLabel,
            onSelect = { onSelect(section.badges[it].id) },
        )
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
 * font pushes the Close button off the bottom of a card that cannot move; the
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
                    AppTheme.colors.accentBrandInk
                } else {
                    AppTheme.colors.textSecondary
                },
            )
            // No bar on a mystery badge: its numbers are zeroed upstream, and a
            // 0/1 bar would still say "one clear does it".
            if (!badge.unlocked && !badge.mystery) {
                Text(text = badge.progressLabel(), typography = AppTheme.typography.Body.B600)
                ProgressBar(progress = badge.progress, fill = badge.group.set().fill)
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
 * It says recording carried on, because the alternative reading, that turning
 * them off threw the history away, is the one that stops somebody turning them
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

@Composable
private fun Badge.tileSpec(upNext: Boolean): BadgeTileSpec = BadgeTileSpec(
    face = face(),
    name = displayName(),
    state = when {
        unlocked -> BadgeTileState.Earned
        mystery -> BadgeTileState.Mystery
        else -> BadgeTileState.Locked(
            progress = BadgeProgress(fraction = progress, label = progressLabel()),
            upNext = upNext,
        )
    },
    set = group.set(),
    spoken = spoken(),
)

@Composable
private fun Badge.spotlightSpec(celebrated: Boolean): BadgeSpotlightSpec = BadgeSpotlightSpec(
    face = face(),
    name = displayName(),
    kicker = if (celebrated) {
        stringResource(Res.string.achievements_earned)
    } else {
        stringResource(AchievementCopy.groupName(group))
    },
    set = group.set(),
    progress = if (celebrated) {
        null
    } else {
        BadgeProgress(
            fraction = progress,
            label = stringResource(Res.string.achievements_card_fraction, current.toString(), target.toString()),
        )
    },
    spoken = spoken(),
)

@Composable
private fun AchievementGroup.set(): BadgeSetStyle = AppTheme.colors.badgeSets[this]

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
    AchievementCopy.describe(id)
}

@Composable
private fun Badge.progressLabel(): String =
    stringResource(Res.string.achievements_progress, current.toString(), target.toString())

/**
 * The whole badge as one sentence, for a screen reader.
 *
 * Name, state, and how far along: the three things a sighted player gets from
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

private const val MysteryGlyph = "❓"

/** One beat between rows. Short enough that three still land as one moment. */
private const val StaggerMillis = 120L

private const val MaxStaggerMillis = 600L

/*
 * The page's measurements, from the handoff's row 10. Everything on the scale
 * is a rung; everything off it is the nearest rung plus the difference, the
 * way the board-cleared screen writes its own, rather than snapped.
 */

/** The handoff's 16 gutter; the cards and the button run edge to edge inside it. */
private val ScreenGutter = Dimension.D700

/** Headings, the hero and the section headers sit 20 in, four further than the cards. */
private val HeroInset = Dimension.D100

/** The 78 dog: the 70 rung plus 8. */
private val HeroDogSize = Dimension.D1500 + Dimension.D300

private val HeroTop = Dimension.D850
private val ChipsTop = Dimension.D850

/** `padding: 26px 20px 0` over "Closest to done", and over each shelf. Off the scale by two. */
private val SpotlightTop = Dimension.D900 + Dimension.D50
private val ShelfTop = Dimension.D900 + Dimension.D50

/** The button follows the last card at the same gap the cards keep between themselves, doubled. */
private val ButtonTop = Dimension.D900

/** What `SEE ALL` leaves above the first shelf so its header is not flush with the top bar. */
private val ShelfHeadroom = Dimension.D700

private val PageBottom = Dimension.D1100

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
