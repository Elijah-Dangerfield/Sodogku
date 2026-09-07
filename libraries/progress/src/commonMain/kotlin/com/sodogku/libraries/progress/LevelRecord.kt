package com.sodogku.libraries.progress

/**
 * Where a level sits in the campaign.
 *
 * These are not ranked by declaration order — [Skipped] is a *better* outcome
 * than [Unlocked] because the player moved past the level, and [Completed]
 * beats both. `ProgressRepository` owns that ranking; nothing outside it should
 * infer an ordering from the enum.
 */
enum class LevelState { Locked, Unlocked, Completed, Skipped }

/**
 * Everything the map and the win sheet need to know about one level.
 *
 * Every field is a *best*, not a last: a replay that goes worse leaves the
 * record alone, so a player can never lose a three-paw clear by trying for a
 * faster one. [attempts] is the exception and counts every start.
 *
 * A level the player has never touched has no row on disk, so callers always
 * get a record — [unplayed] — rather than a null they'd have to interpret.
 */
data class LevelRecord(
    val levelId: Int,
    val state: LevelState,
    val bestScore: Int,
    val bestPaws: Int,
    /** Lower is better, and `0` means "never cleared" rather than "instant". */
    val bestTimeMs: Long,
    val attempts: Int,
    /** Epoch millis, `0` = never. */
    val firstCompletedAt: Long,
    val lastPlayedAt: Long,
) {
    companion object {
        /**
         * The campaign has to start somewhere, and nothing can unlock the first
         * level because nothing precedes it — so it is open by construction
         * rather than by a row written at first launch.
         */
        const val FIRST_LEVEL_ID: Int = 1

        fun unplayed(levelId: Int): LevelRecord = LevelRecord(
            levelId = levelId,
            state = if (levelId <= FIRST_LEVEL_ID) LevelState.Unlocked else LevelState.Locked,
            bestScore = 0,
            bestPaws = 0,
            bestTimeMs = 0L,
            attempts = 0,
            firstCompletedAt = 0L,
            lastPlayedAt = 0L,
        )
    }
}
