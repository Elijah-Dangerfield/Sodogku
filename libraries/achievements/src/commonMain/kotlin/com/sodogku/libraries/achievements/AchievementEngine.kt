package com.sodogku.libraries.achievements

/**
 * Everything the player has done, as far as achievements are concerned: the
 * folded [counters], and what they have earned with them.
 *
 * [unlocked] maps an id to when it was earned, and the timestamp comes from the
 * attempt that crossed the threshold rather than from a clock read at the time
 * of the fold. That is what makes a replay of the log reproduce the same dates
 * it produced live, including for an achievement added long after the fact.
 */
data class AchievementState(
    val counters: AchievementCounters = AchievementCounters.Empty,
    val unlocked: Map<AchievementId, Long> = emptyMap(),
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
                unlocked = state.unlocked + newlyUnlocked.associate { it.id to result.finishedAt },
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
