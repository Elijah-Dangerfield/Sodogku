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
import com.sodogku.libraries.ui.bounceClick
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.game.PawRating
import com.sodogku.libraries.ui.components.icon.Icon
import com.sodogku.libraries.ui.components.icon.Icons
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Motion
import com.sodogku.system.Radii
import com.sodogku.system.clip
import org.jetbrains.compose.resources.stringResource
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.levels_reward
import sodogku.libraries.resources.generated.resources.levels_size
import sodogku.libraries.resources.generated.resources.levels_title

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
    currentLevelId: Int,
    unlockedThrough: Int,
    canJumpAnywhere: Boolean,
    records: Map<Int, LevelRecord>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
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

        val levels = remember { LevelPacks.campaign.levels }
        val listState = rememberLazyListState()
        LaunchedEffect(open) {
            if (open) listState.scrollToItem((currentLevelId - 1).coerceAtLeast(0))
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
                    // The frontier: the furthest level that has opened. Showing
                    // what it pays out is the pull down the list.
                    showsReward = level.id == unlockedThrough,
                    onPick = onPick,
                )
            }
        }
    }
}

@Composable
private fun LevelRow(
    level: LevelDefinition,
    record: LevelRecord,
    isCurrent: Boolean,
    unlocked: Boolean,
    showsReward: Boolean,
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
            Icon(icon = Icons.Lock(null), color = AppTheme.colors.textDisabled)
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
        // A placeholder glyph until the reward art exists. It lives in
        // strings.xml so swapping it for a real asset is one call site.
        if (showsReward) {
            Text(
                text = stringResource(Res.string.levels_reward),
                typography = AppTheme.typography.Heading.H600,
            )
        }
    }
}

private val DrawerWidth = Dimension.D1900 * 2.6f
private const val CurrentTint = 0.22f

/** The level's own column takes the row, so the reward sits hard against the end. */
private const val RowFill = 1f
