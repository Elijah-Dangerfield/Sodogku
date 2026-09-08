package com.sodogku.features.achievements.impl

import androidx.lifecycle.viewModelScope
import com.sodogku.libraries.achievements.Achievement
import com.sodogku.libraries.achievements.AchievementCounters
import com.sodogku.libraries.achievements.AchievementGroup
import com.sodogku.libraries.achievements.AchievementId
import com.sodogku.libraries.achievements.AchievementState
import com.sodogku.libraries.achievements.Achievements
import com.sodogku.libraries.achievements.AchievementsRepository
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.flowroutines.SEAViewModel
import com.sodogku.libraries.flowroutines.collectIn
import com.sodogku.libraries.sodogku.AppCache
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import me.tatarka.inject.annotations.Inject

/**
 * The badge grid.
 *
 * Reads two independent things and keeps them apart on purpose. The *history*
 * comes from [AchievementsRepository], which knows nothing about settings and
 * never stops recording. Whether the player wants to look at it comes from
 * [AppCache], and reaches no further than [AchievementsState.visible] — turning
 * badges off hides this screen's contents and the unlock toast, and does not
 * touch a single row of the fact log. That is what makes turning them back on
 * show real history rather than a blank grid.
 */
@Inject
class AchievementsViewModel(
    private val achievements: AchievementsRepository,
    private val appCache: AppCache,
) : SEAViewModel<AchievementsState, AchievementsEvent, AchievementsAction>(
    initialStateArg = AchievementsState(),
) {

    init {
        takeAction(AchievementsAction.Load)
        // Observed rather than read once: the fold is re-run whenever a fact or
        // an unlock lands, so a badge earned while this screen is open (from a
        // background write) shows up without a refresh.
        achievements.observe().collectIn(viewModelScope) {
            takeAction(AchievementsAction.HistoryChanged(it))
        }
        appCache.updates
            .map { it.achievementsVisible }
            .distinctUntilChanged()
            .collectIn(viewModelScope) { takeAction(AchievementsAction.VisibilityChanged(it)) }
    }

    override suspend fun handleAction(action: AchievementsAction) {
        when (action) {
            AchievementsAction.Load -> action.load()
            is AchievementsAction.HistoryChanged -> action.onHistory(action.state)
            is AchievementsAction.VisibilityChanged -> action.updateState {
                it.copy(visible = action.visible)
            }
            AchievementsAction.Back -> sendEvent(AchievementsEvent.NavigateBack)
            is AchievementsAction.Select -> action.updateState { it.copy(selectedId = action.id) }
            AchievementsAction.CloseDetail -> action.updateState { it.copy(selectedId = null) }
        }
    }

    /**
     * The first paint.
     *
     * Both the visibility flag and the history are read here as well as
     * observed, because `Cache.updates` is a change feed — on a launch where
     * nothing writes to `AppData`, waiting for it would leave the screen
     * loading forever.
     */
    private suspend fun AchievementsAction.load() {
        val visible = Catching { appCache.get().achievementsVisible }
            .logOnFailure { "Failed to read the achievements setting" }
            .getOrNull()
            ?: true
        val history = Catching { achievements.state() }
            .logOnFailure { "Failed to read achievement history" }
            .getOrNull()
            ?: AchievementState.Empty

        updateState {
            it.copy(
                loading = false,
                visible = visible,
                badges = history.toBadges(),
            )
        }
    }

    private suspend fun AchievementsAction.onHistory(history: AchievementState) {
        val badges = history.toBadges()
        updateState { it.copy(loading = false, badges = badges) }
    }
}

/**
 * Catalog order, with each badge's progress resolved against the player's
 * counters.
 *
 * A hidden badge that has not been earned reports **no** progress. It is the
 * one place where showing a real number would be a leak: "4am clears: 0 / 1" on
 * a mystery badge tells the reader exactly what to go and try.
 */
private fun AchievementState.toBadges(): List<Badge> = Achievements.catalog.map { achievement ->
    val earned = isUnlocked(achievement.id)
    val mystery = achievement.hidden && !earned
    Badge(
        id = achievement.id,
        group = Achievements.groupOf(achievement.id),
        unlocked = earned,
        mystery = mystery,
        progress = if (mystery) 0f else achievement.progress(counters),
        current = if (mystery) 0L else achievement.currentFor(counters),
        target = achievement.target,
    )
}

/**
 * Where the player is on this badge's counter, clamped to the target.
 *
 * Clamped because the raw counter keeps climbing after an achievement is
 * earned, and "500 / 10" under a badge you got two months ago reads as a bug.
 */
private fun Achievement.currentFor(counters: AchievementCounters): Long =
    counters[stat].coerceAtMost(target)

/** One badge, as the grid and the detail sheet need it. */
data class Badge(
    val id: AchievementId,
    val group: AchievementGroup,
    val unlocked: Boolean,

    /** Hidden and not yet earned: rendered as a mystery, with no progress. */
    val mystery: Boolean,

    /** 0.0 to 1.0. */
    val progress: Float,
    val current: Long,
    val target: Long,
)

data class AchievementsState(
    val loading: Boolean = true,

    /**
     * The Settings toggle. Display only — see [AchievementsViewModel]. False
     * means the grid is replaced by an explanation, not that anything stopped
     * being recorded.
     */
    val visible: Boolean = true,
    val badges: List<Badge> = emptyList(),

    /** The badge whose detail sheet is open. */
    val selectedId: AchievementId? = null,
) {
    val earnedCount: Int get() = badges.count { it.unlocked }

    val totalCount: Int get() = badges.size

    val selected: Badge? get() = badges.firstOrNull { it.id == selectedId }

    /**
     * The grid, one shelf at a time. Grouped rather than declared twice, so a
     * badge cannot be in the catalog and off the screen: `groupBy` keeps
     * encounter order and [badges] is already in catalog order, which is the
     * order the sections were flattened in.
     */
    val sections: List<BadgeSection>
        get() = badges.groupBy { it.group }.map { (group, list) -> BadgeSection(group, list) }
}

/** One heading and the badges under it. */
data class BadgeSection(
    val group: AchievementGroup,
    val badges: List<Badge>,
)

sealed interface AchievementsEvent {
    data object NavigateBack : AchievementsEvent
}

sealed interface AchievementsAction {
    data object Load : AchievementsAction
    data class HistoryChanged(val state: AchievementState) : AchievementsAction
    data class VisibilityChanged(val visible: Boolean) : AchievementsAction
    data object Back : AchievementsAction
    data class Select(val id: AchievementId) : AchievementsAction
    data object CloseDetail : AchievementsAction
}
