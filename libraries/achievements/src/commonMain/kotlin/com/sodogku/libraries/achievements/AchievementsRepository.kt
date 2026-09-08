package com.sodogku.libraries.achievements

import kotlinx.coroutines.flow.Flow

/**
 * The player's achievement history, and the only thing allowed to decide what
 * has been earned.
 *
 * Callers report a finished attempt and are told what that just unlocked. They
 * never write an unlock themselves, which is what keeps "announce a badge
 * exactly once" in one place.
 *
 * Device-local, like everything else in Sodogku: no accounts (see
 * `docs/decisions.md`), so this does not survive a reinstall and Settings says
 * so.
 *
 * The Settings toggle for achievements suppresses the *display* — the toast and
 * the tab — and deliberately does not reach this far. Recording keeps going
 * while they are switched off, so turning them back on shows real history
 * rather than a blank grid.
 */
interface AchievementsRepository {

    /** Current counters and unlocks, re-emitted whenever either changes. */
    fun observe(): Flow<AchievementState>

    suspend fun state(): AchievementState

    /**
     * Records one finished attempt and returns what it unlocked, in catalog
     * order. Empty is the normal answer.
     *
     * Recording the same attempt twice is a no-op: results are stored under
     * [LevelResult.key], so a retry of a failed write cannot double-count.
     */
    suspend fun record(result: LevelResult): List<Achievement>

    suspend fun reset()
}
