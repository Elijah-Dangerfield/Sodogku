package com.sodogku.libraries.achievements

/** Which pack the attempt was against. Stored by name, never by ordinal. */
enum class PlayMode { Campaign, Daily }

/**
 * The raw record of one finished attempt, from the player's point of view.
 *
 * This — not a set of counters — is what gets stored, append-only. Keeping the
 * facts is what makes every number in [AchievementCounters] re-derivable: an
 * achievement added in a later release back-fills from a player's history the
 * first time they finish anything, because the fold runs over the whole log
 * rather than over a tally somebody has to remember to migrate.
 *
 * Every field here is something the game already knows at the moment an attempt
 * ends. Nothing is a projection and nothing is a running total — streaks and
 * high-water marks are the *fold's* business, so a reinstall can never hand the
 * store a pre-computed streak of zero and clobber a real one.
 */
data class LevelResult(
    val levelId: Int,
    val mode: PlayMode,

    /** Grid dimension, 4 to 10. */
    val size: Int,

    /** False for an attempt that ran out of bones. Failures are recorded too. */
    val completed: Boolean,

    val score: Int,

    /** 0 to 3, and 0 for a failed attempt. */
    val paws: Int,

    val timeMs: Long,

    /** Wrong guesses that cost a bone. Zero is a flawless clear. */
    val strikes: Int,

    /** Longest run of consecutive correct placements in this attempt. */
    val bestCombo: Int,

    val sniffsUsed: Int,
    val treatsUsed: Int,

    /**
     * True when this is the first time the level has ever been cleared. Read
     * from `ProgressRepository` *before* the clear is written, and the reason
     * "levels cleared" counts levels rather than clears — a replayed level must
     * not walk the counter up a second time.
     */
    val isFirstClear: Boolean,

    /**
     * The level's best paw rating before this attempt, for the same reason:
     * a three-paw counter that a replay could re-increment counts nothing.
     */
    val previousBestPaws: Int,

    /**
     * The daily streak this attempt landed on, as the daily feature computes it
     * (C6). Zero for campaign levels and for a daily that broke the streak.
     *
     * Deliberately reported rather than derived here: the streak has a freeze
     * mechanic, and a fold that re-derived it from the dates in the log would
     * quietly disagree with the number on the daily card. One owner, watermarked
     * here.
     */
    val dailyStreakDays: Int = 0,

    /** Local hour of day, 0 to 23. The time-of-day achievements read this. */
    val localHour: Int,

    /** Epoch millis. Also the timestamp an achievement this attempt unlocks gets. */
    val finishedAt: Long,
) {
    /**
     * Stable identity for this attempt, so recording it twice is a no-op rather
     * than a double count. Two attempts at one level cannot end in the same
     * millisecond, and an attempt replayed from the log carries its original
     * timestamp.
     */
    val key: String get() = "${mode.name}:$levelId:$finishedAt"
}
