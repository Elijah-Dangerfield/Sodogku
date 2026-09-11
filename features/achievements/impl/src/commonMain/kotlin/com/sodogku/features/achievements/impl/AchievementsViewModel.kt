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
        // Read *before* `markSeen` moves it, and then frozen for as long as this
        // screen is alive. It is the line between "you have seen this badge" and
        // "this is news", and the page celebrates everything on the far side of
        // it. Re-reading it after the write would be a page that congratulates
        // nobody, which is exactly the bug the watermark exists to avoid.
        val seenAt = Catching { appCache.get().achievementsSeenAt }
            .logOnFailure { "Failed to read the achievements watermark" }
            .getOrNull()
            ?: 0L

        updateState {
            it.copy(
                loading = false,
                visible = visible,
                seenAt = seenAt,
                badges = history.toBadges(seenAt),
            )
        }
        // Only what was actually put in front of somebody. With badges switched
        // off this screen is an explanation rather than a grid, and calling that
        // "seen" would bury every badge earned while they were off on the day
        // they are switched back on.
        if (visible) markSeen(history)
    }

    /**
     * Puts the watermark the board's trophy counts against at the newest badge
     * on screen, so the badge clears when the grid is opened and not before.
     *
     * The newest unlock rather than "now", because this module has no clock and
     * does not need one: the question the trophy asks is whether anything was
     * unlocked *after* the last look, and the unlock times are the only side of
     * that comparison. A "now" read from a device clock running ahead of the one
     * that stamped the unlocks would swallow a badge earned later.
     *
     * Never lowers the watermark, so the order two screens happen to write in
     * cannot uncount a badge.
     */
    private suspend fun markSeen(history: AchievementState) {
        val newest = history.unlocked.values.maxOrNull() ?: return
        Catching {
            appCache.update { data ->
                data.copy(achievementsSeenAt = maxOf(data.achievementsSeenAt, newest))
            }
        }.logOnFailure { "Failed to mark badges as seen" }
    }

    /**
     * A badge landing while the grid is open.
     *
     * [AchievementsState.seenAt] deliberately does not move, so the badge that
     * just arrived reads as new and the page celebrates it. The *watermark on
     * disk* does move, because the player has now genuinely seen it and the
     * board's trophy counts against that number rather than against this state.
     * Leaving that write out is how a badge earned in front of the player leaves
     * the trophy lit for the rest of the install.
     */
    private suspend fun AchievementsAction.onHistory(history: AchievementState) {
        updateState { it.copy(loading = false, badges = history.toBadges(it.seenAt)) }
        // Only once the first load has settled: the observer's first emission can
        // beat `Load` through the action queue, and marking seen off the back of
        // it would count a grid nobody has been shown yet.
        if (state.seenAt != null && state.visible) markSeen(history)
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
private fun AchievementState.toBadges(seenAt: Long?): List<Badge> = Achievements.catalog.map { achievement ->
    val unlockedAt = unlocked[achievement.id]
    val mystery = achievement.hidden && unlockedAt == null
    Badge(
        id = achievement.id,
        group = Achievements.groupOf(achievement.id),
        unlocked = unlockedAt != null,
        mystery = mystery,
        isNew = unlockedAt != null && seenAt != null && unlockedAt > seenAt,
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

    /**
     * Earned since the last time this page was looked at, which is what the page
     * celebrates. Always false while a badge is locked.
     */
    val isNew: Boolean = false,

    /** 0.0 to 1.0. */
    val progress: Float,
    val current: Long,
    val target: Long,
)

/** What the pinned card at the top of the page is for right now. */
enum class SpotlightKind {
    /** Badges earned since the last look. An unlock is an event, so it gets a stage. */
    JustEarned,

    /** The nearest unearned badges. What a page with nothing on it yet is about. */
    NextUp,
}

/** The pinned card at the top: a reason to have opened the page. */
data class Spotlight(
    val kind: SpotlightKind,
    val badges: List<Badge>,
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

    /**
     * The unlock watermark as it stood when this screen opened, or null before
     * the first load has finished. Frozen: see [AchievementsViewModel].
     */
    val seenAt: Long? = null,

    /** The badge whose detail sheet is open. */
    val selectedId: AchievementId? = null,
) {
    val earnedCount: Int get() = badges.count { it.unlocked }

    val totalCount: Int get() = badges.size

    val lockedCount: Int get() = totalCount - earnedCount

    val selected: Badge? get() = badges.firstOrNull { it.id == selectedId }

    /** Everything earned since the last look, in catalog order. */
    val justEarned: List<Badge> get() = badges.filter { it.isNew }

    /**
     * What the hero number climbs from, or null when there is nothing to
     * celebrate.
     *
     * The same rule the streak page settled on: a page you went looking for
     * should not perform at you. Here the trigger is not *how* the page was
     * opened but whether it is holding news, so three badges landing at once is
     * one climb of three rather than three separate performances.
     */
    val countUpFrom: Int? get() = (earnedCount - justEarned.size).takeIf { justEarned.isNotEmpty() }

    /** Whole shelves finished, which is the only fact on the page about the *set*. */
    val completedSetCount: Int
        get() = sections.count { section -> section.badges.all { it.unlocked } }

    /**
     * The grid, one shelf at a time. Grouped rather than declared twice, so a
     * badge cannot be in the catalog and off the screen: `groupBy` keeps
     * encounter order and [badges] is already in catalog order, which is the
     * order the sections were flattened in.
     */
    val sections: List<BadgeSection>
        get() = badges.groupBy { it.group }.map { (group, list) -> BadgeSection(group, list) }

    /**
     * The pinned card at the top of the page.
     *
     * News first, and all of it: a backfill that lands eleven badges at once
     * gets eleven cards, because capping the celebration at three would make the
     * heading a lie while the hero counted up by eleven.
     *
     * Otherwise the nearest unearned badges, which is the answer to the state
     * nobody designs. A brand-new player's page is seventy-three things they
     * have not done; these three are the ones within reach, and with every
     * counter at zero "nearest" settles on the cheapest targets, one from each
     * shelf. One per group rather than three rungs of the same ladder, so the
     * row reads as a set of ways to play instead of as one task repeated.
     *
     * Mystery badges are never here. Naming one would give away the half of the
     * surprise worth keeping, and "Next up: ???" is not a goal anybody can act on.
     */
    val spotlight: Spotlight?
        get() {
            if (justEarned.isNotEmpty()) return Spotlight(SpotlightKind.JustEarned, justEarned)

            val nearest = badges.asSequence()
                .filter { !it.unlocked && !it.mystery }
                // A stable sort, so catalog order breaks every tie. That is the
                // whole of what a fresh install needs: every counter is zero, so
                // every candidate ties, and `AchievementSection` declares each
                // shelf easiest first. Sorting on the target as well looked like
                // insurance and turned out to be unreachable — a shelf whose
                // cheapest badge is unearned always has it first anyway.
                .sortedByDescending { it.progress }
                .distinctBy { it.group }
                .take(NextUpSize)
                .toList()

            return if (nearest.isEmpty()) null else Spotlight(SpotlightKind.NextUp, nearest)
        }
}

/** Three fits across a phone without the row becoming a second grid. */
private const val NextUpSize = 3

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
