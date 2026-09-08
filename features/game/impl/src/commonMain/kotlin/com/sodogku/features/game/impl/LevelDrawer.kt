package com.sodogku.features.game.impl

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import com.sodogku.libraries.levels.LevelDefinition
import com.sodogku.libraries.levels.LevelPacks
import com.sodogku.libraries.progress.LevelRecord
import com.sodogku.libraries.progress.LevelState
import com.sodogku.libraries.progress.daily.DailyOutcome
import com.sodogku.libraries.progress.daily.DailyStatus
import com.sodogku.libraries.ui.bounceClick
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.game.DailyCard
import com.sodogku.libraries.ui.components.game.DailyCardState
import com.sodogku.libraries.ui.components.game.LevelRewardChip
import com.sodogku.libraries.ui.components.game.PawRating
import com.sodogku.libraries.ui.components.icon.Icon
import com.sodogku.libraries.ui.components.icon.Icons
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Motion
import com.sodogku.system.Radii
import com.sodogku.system.clip
import kotlinx.datetime.number
import org.jetbrains.compose.resources.stringResource
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.daily_date
import sodogku.libraries.resources.generated.resources.levels_locked
import sodogku.libraries.resources.generated.resources.levels_reward_claimed
import sodogku.libraries.resources.generated.resources.levels_reward_treat
import sodogku.libraries.resources.generated.resources.levels_size
import sodogku.libraries.resources.generated.resources.levels_title
import sodogku.libraries.resources.generated.resources.month_short_1
import sodogku.libraries.resources.generated.resources.month_short_10
import sodogku.libraries.resources.generated.resources.month_short_11
import sodogku.libraries.resources.generated.resources.month_short_12
import sodogku.libraries.resources.generated.resources.month_short_2
import sodogku.libraries.resources.generated.resources.month_short_3
import sodogku.libraries.resources.generated.resources.month_short_4
import sodogku.libraries.resources.generated.resources.month_short_5
import sodogku.libraries.resources.generated.resources.month_short_6
import sodogku.libraries.resources.generated.resources.month_short_7
import sodogku.libraries.resources.generated.resources.month_short_8
import sodogku.libraries.resources.generated.resources.month_short_9

/**
 * The level list, as a pane that slides in over the board.
 *
 * A drawer rather than a screen because the puzzle *is* the app's home. Sending
 * someone to a menu to pick a level and back again puts two navigations between
 * them and the thing they opened the app to do.
 *
 * Locked levels are shown, not hidden. Seeing that level 200 is a 8x8 you have
 * not reached yet is the whole reason to scroll the list.
 *
 * [records] covers only the levels the player has touched; anything missing has
 * never been opened, which [LevelRecord.unplayed] already describes. The map is
 * read once when the drawer opens, so scrolling 500 rows costs no queries.
 */
@Composable
fun BoxScope.LevelDrawer(
    open: Boolean,
    /**
     * The campaign level on screen, or null when the board on screen is not one
     * — the daily's id is a position in another pack, and passing it here both
     * highlighted the wrong row and scrolled the list to a stranger.
     */
    currentLevelId: Int?,
    unlockedThrough: Int,
    canJumpAnywhere: Boolean,
    records: Map<Int, LevelRecord>,
    /**
     * `boosters.treatEveryNLevels`, so the pane advertises the reward the game
     * will actually pay. Zero shows none — which is what an operator setting the
     * key to zero means, and what the pane should say before the value has been
     * read rather than promising a prize on spec.
     */
    treatEveryNLevels: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    daily: DailyStatus? = null,
    isDailyBoard: Boolean = false,
    onPlayDaily: () -> Unit = {},
    onUseFreeze: () -> Unit = {},
    width: Dp = DrawerWidth,
) {
    val slide = animateFloatAsState(if (open) 1f else 0f, Motion.Pop)

    if (slide.value <= 0f) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = slide.value }
            .background(AppTheme.colors.backgroundOverlay.color)
            .pointerInput(open) { detectTapGestures { onDismiss() } },
    )

    Column(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .width(width)
            .fillMaxHeight()
            .graphicsLayer { translationX = -(1f - slide.value) * size.width }
            .background(AppTheme.colors.surfacePrimary.color)
            // The pane runs the full height of the window, so it owns its own
            // system-bar inset — without it the title sits under the clock.
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(Dimension.D600),
    ) {
        Text(
            text = stringResource(Res.string.levels_title),
            typography = AppTheme.typography.Heading.H700,
            modifier = Modifier.padding(bottom = Dimension.D500),
        )

        // Absent, not greyed, when either flag is off. A card that says the daily
        // exists but cannot be opened is a support ticket; a kill switch has to
        // leave nothing behind.
        if (daily != null && daily.enabled) {
            DailyCardSlot(
                status = daily,
                isCurrentBoard = isDailyBoard,
                onPlay = onPlayDaily,
                onFreeze = onUseFreeze,
                modifier = Modifier.padding(bottom = Dimension.D500),
            )
        }

        val levels = remember { LevelPacks.campaign.levels }
        val listState = rememberLazyListState()
        // Opens on the level being played, or on the frontier when the board on
        // screen belongs to the other pack.
        val scrollTo = (currentLevelId ?: unlockedThrough) - 1
        LaunchedEffect(open) {
            if (open) listState.scrollToItem(scrollTo.coerceAtLeast(0))
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(Dimension.D300),
        ) {
            items(levels, key = { it.id }) { level ->
                LevelRow(
                    level = level,
                    record = records[level.id] ?: LevelRecord.unplayed(level.id),
                    isCurrent = level.id == currentLevelId,
                    unlocked = canJumpAnywhere || level.id <= unlockedThrough,
                    // Every level that pays, not just the frontier. The rewards
                    // are the reason to scroll 500 rows, and one chip on one row
                    // is a coincidence rather than a ladder.
                    paysReward = treatEveryNLevels > 0 && level.id % treatEveryNLevels == 0,
                    onPick = onPick,
                )
            }
        }
    }
}

/**
 * [DailyStatus] rendered, and the only place the daily is turned into copy.
 *
 * The status is one snapshot of the clock, so everything here is a lookup on it
 * — no date is derived, compared or advanced. The month name comes out of string
 * resources rather than a formatter so it translates with the rest of the app.
 */
@Composable
private fun DailyCardSlot(
    status: DailyStatus,
    isCurrentBoard: Boolean,
    onPlay: () -> Unit,
    onFreeze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DailyCard(
        dateLabel = stringResource(
            Res.string.daily_date,
            stringResource(MonthNames[status.date.month.number - 1]),
            status.date.day,
        ),
        streak = status.streak,
        state = when (status.result?.outcome) {
            null -> if (isCurrentBoard) DailyCardState.Current else DailyCardState.Open
            DailyOutcome.Completed -> DailyCardState.Completed
            DailyOutcome.Failed -> DailyCardState.Failed
            // A freeze only ever covers a *missed* day, so today cannot be
            // frozen — but a clock moved backwards can put one here, and "out of
            // bones" would be a lie about a day nobody played.
            DailyOutcome.Frozen -> DailyCardState.Completed
        },
        paws = status.result?.paws ?: 0,
        resetsIn = status.resetsIn,
        // A spent day is still worth opening — on its result, not its board.
        // Not offered when the recap is already what is on screen, which would
        // push a second copy of the same route onto the backstack.
        canReview = status.result != null && !isCurrentBoard,
        freezesRemaining = status.freezeOffer?.freezesRemaining,
        onPlay = onPlay,
        onFreeze = onFreeze,
        modifier = modifier,
    )
}

@Composable
private fun LevelRow(
    level: LevelDefinition,
    record: LevelRecord,
    isCurrent: Boolean,
    unlocked: Boolean,
    paysReward: Boolean,
    onPick: (Int) -> Unit,
) {
    val background = when {
        isCurrent -> AppTheme.colors.accentPrimary.color.copy(alpha = CurrentTint)
        else -> AppTheme.colors.surfaceSecondary.color
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimension.D400),
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(enabled = unlocked) { onPick(level.id) }
            .clip(Radii.Card)
            .background(background)
            .padding(horizontal = Dimension.D500, vertical = Dimension.D400),
    ) {
        if (unlocked) {
            Dog(pose = DogPose.Still, size = Dimension.D1100)
        } else {
            // Not decorative: the padlock is the only thing on the row that says
            // this level cannot be opened. The dog beside an unlocked one is,
            // because the row's own state is what it means and the row says it.
            Icon(
                icon = Icons.Lock(stringResource(Res.string.levels_locked)),
                color = AppTheme.colors.textDisabled,
            )
        }
        Column(modifier = Modifier.weight(RowFill)) {
            Text(
                text = level.id.toString(),
                typography = AppTheme.typography.Heading.H600,
                color = if (unlocked) AppTheme.colors.text else AppTheme.colors.textDisabled,
            )
            Text(
                text = stringResource(Res.string.levels_size, level.size, level.size),
                typography = AppTheme.typography.Caption.C300,
                color = AppTheme.colors.textSecondary,
            )
            // Only a cleared level has a rating to show. An unlocked one that
            // has been attempted and lost would otherwise render three empty
            // paws, which reads as a nought-out-of-three score rather than as
            // "not finished yet".
            if (record.state == LevelState.Completed) {
                PawRating(
                    paws = record.bestPaws,
                    size = Dimension.D700,
                    // A recalled rating, not a fresh one. See PawRating.
                    animated = false,
                    modifier = Modifier.padding(top = Dimension.D200),
                )
            }
        }
        // Cleared levels keep the chip rather than dropping it, so the column
        // stays a straight line down 500 rows — but as the spent version, since
        // the Treat is paid on the first clear and this one has already been
        // collected.
        if (paysReward) {
            LevelRewardChip(
                label = if (record.state == LevelState.Completed) {
                    stringResource(Res.string.levels_reward_claimed)
                } else {
                    stringResource(Res.string.levels_reward_treat, LevelRewardTreats)
                },
                claimed = record.state == LevelState.Completed,
            )
        }
    }
}

/** Short month names, indexed by month number minus one. Also the share title's. */
internal val MonthNames = listOf(
    Res.string.month_short_1,
    Res.string.month_short_2,
    Res.string.month_short_3,
    Res.string.month_short_4,
    Res.string.month_short_5,
    Res.string.month_short_6,
    Res.string.month_short_7,
    Res.string.month_short_8,
    Res.string.month_short_9,
    Res.string.month_short_10,
    Res.string.month_short_11,
    Res.string.month_short_12,
)

private val DrawerWidth = Dimension.D1900 * 2.6f
private const val CurrentTint = 0.22f

/** The level's own column takes the row, so the reward sits hard against the end. */
private const val RowFill = 1f
