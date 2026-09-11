package com.sodogku.libraries.achievements

/**
 * One badge the player has, and the two times attached to it. They hold the
 * same number for almost every badge, and they answer different questions:
 *
 * - [unlockedAt] is when the player crossed the line, taken from the attempt
 *   that did it rather than from a clock read at the time of the fold. It is
 *   what makes a replay of the log reproduce the dates it produced live, and it
 *   is the honest answer to "when did I earn this".
 * - [announcedAt] is when the badge was put in front of them. For a badge
 *   granted as it happens these coincide. For one added to the catalog later
 *   and back-filled from history they do not: crossed weeks ago, told today.
 *
 * Anything asking "is this news" wants [announcedAt]. Comparing [unlockedAt]
 * against the seen-watermark buries every back-filled badge under a date from
 * before the player last looked.
 */
data class Unlock(
    val unlockedAt: Long,
    val announcedAt: Long = unlockedAt,
)

/**
 * Everything the player has done, as far as achievements are concerned: the
 * folded [counters], and what they have earned with them.
 */
data class AchievementState(
    val counters: AchievementCounters = AchievementCounters.Empty,
    val unlocked: Map<AchievementId, Unlock> = emptyMap(),
) {
    fun isUnlocked(id: AchievementId): Boolean = id in unlocked

    companion object {
        val Empty: AchievementState = AchievementState()
    }
}

/** The next state, and what to celebrate on the way to it. */
data class AchievementUpdate(
    val state: AchievementState,
    /** In catalog order, and empty when nothing crossed. */
    val newlyUnlocked: List<Achievement>,
)

/**
 * Turns finished attempts into badges.
 *
 * A pure fold with no clock, no storage and no hidden state: `apply(state, result)`
 * is a function of its two arguments, so the same history always produces the
 * same badges in the same order, and it can be run over the whole log to
 * back-fill an achievement that did not exist when the level was played.
 *
 * Having no clock also means it cannot know when a badge was *announced*: the
 * log records attempts, not the releases that changed the catalog between them,
 * so the fold can only report the crossing attempt for both halves of [Unlock].
 * The stored unlock table is the authority on announcement, and the repository
 * overrides [Unlock.announcedAt] when it writes a row.
 *
 * **It cannot grant the same achievement twice**, because an id already in
 * [AchievementState.unlocked] is filtered out before anything is announced —
 * a re-run reports nothing new no matter how the counters move. Counters are a
 * separate concern: applying the *same* result twice would double-count them,
 * and that is prevented one layer out, where the fact log dedupes on
 * [LevelResult.key].
 */
object AchievementEngine {

    fun apply(
        state: AchievementState,
        result: LevelResult,
        catalog: List<Achievement> = Achievements.catalog,
    ): AchievementUpdate {
        val counters = state.counters.fold(result)
        val newlyUnlocked = catalog.filter { !state.isUnlocked(it.id) && it.isMet(counters) }
        return AchievementUpdate(
            state = AchievementState(
                counters = counters,
                unlocked = state.unlocked + newlyUnlocked.associate { it.id to Unlock(result.finishedAt) },
            ),
            newlyUnlocked = newlyUnlocked,
        )
    }

    /**
     * Folds a whole history from nothing. Order matters — streaks are defined by
     * it — so [results] must be in the order the attempts happened.
     */
    fun replay(
        results: Iterable<LevelResult>,
        catalog: List<Achievement> = Achievements.catalog,
    ): AchievementState = results.fold(AchievementState.Empty) { state, result ->
        apply(state, result, catalog).state
    }
}
