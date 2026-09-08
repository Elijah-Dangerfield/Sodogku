package com.sodogku.libraries.achievements

/**
 * Every number an achievement can be earned against.
 *
 * Typed rather than string-keyed (the shape this was modelled on used strings,
 * because its counters were a JSONB column a server wrote). Nothing here is
 * persisted — counters are folded from the stored [LevelResult] log on demand —
 * so there is no schema to migrate and no reason to give up the compiler.
 * Adding a stat forces a branch in [AchievementCounters.fold] and nothing else.
 */
enum class Stat {
    /** Distinct campaign levels cleared. A replay does not move it. */
    LevelsCleared,

    /** Daily boards cleared. One per day, so distinctness is free. */
    DailiesCleared,

    /** Clears that cost no bones at all. */
    FlawlessClears,

    /** Consecutive flawless clears, right now. Any other outcome resets it. */
    FlawlessStreak,

    /** The high-water mark of [FlawlessStreak]. */
    BestFlawlessStreak,

    /** Clears of a 7x7 or bigger that cost no bones. */
    FlawlessBigBoardClears,

    /** Levels taken to three paws, counted the first time each gets there. */
    ThreePawClears,

    /** Consecutive three-paw clears, right now. */
    ThreePawStreak,

    /** The high-water mark of [ThreePawStreak]. */
    BestThreePawStreak,

    /** Full-size boards taken to three paws, first time each. */
    MaxBoardThreePawClears,

    /** Clears that survived at least [AchievementCounters.COMEBACK_STRIKES]. */
    ComebackClears,

    /** The same, on a 7x7 or bigger. */
    BigBoardComebackClears,

    /**
     * Clears of a level the player had previously failed and not yet come back
     * to. The one counter that needs the log to be a *history* rather than a
     * tally: nothing about a single attempt says it is a rematch.
     */
    RedemptionClears,

    /** Clears with no sniff and no treat. */
    BoosterFreeClears,

    /** Consecutive clears with no sniff and no treat. */
    BoosterFreeStreak,

    /** The high-water mark of [BoosterFreeStreak]. */
    BestBoosterFreeStreak,

    /** Clears that spent both a sniff and a treat. */
    AssistedClears,

    /** Clears inside [AchievementCounters.SPRINT_MS]. */
    SprintClears,

    /** Clears inside [AchievementCounters.FLASH_MS]. */
    FlashClears,

    /** Clears of a 7x7 or bigger inside [AchievementCounters.BIG_BOARD_SPRINT_MS]. */
    BigBoardSprintClears,

    /** Clears of a full-size board inside [AchievementCounters.MAX_BOARD_SPRINT_MS]. */
    MaxBoardSprintClears,

    /** Clears of a 7x7 or bigger. */
    BigBoardClears,

    /** Clears at [AchievementCounters.MAX_BOARD_SIZE]. */
    MaxBoardClears,

    /** Clears finished in the small hours. */
    NightClears,

    /** Clears finished around sunrise. */
    DawnClears,

    /** Best single-level score. */
    BestScore,

    /** Longest run of consecutive correct placements, ever. */
    BestCombo,

    /** Largest grid dimension the player has cleared. */
    LargestGridCleared,

    /** Longest daily streak reached, as the daily feature reported it. */
    BestDailyStreak,

    /** Dailies cleared without losing a bone. */
    DailyFlawlessClears,

    /** Dailies taken to three paws. */
    DailyThreePawClears,

    /**
     * Whole minutes spent on boards, summed over every recorded attempt.
     *
     * SPEC section 8 turned down a "Marathon" badge because *session* length is
     * not a property of an attempt and nothing tracks it. Time on the boards is:
     * it is [LevelResult.timeMs] added up, and it is the honest version of the
     * same idea.
     */
    MinutesPlayed,
}

/**
 * A player's stats, folded from their [LevelResult] history in order.
 *
 * The fold is the single definition of "how a finished attempt moves the
 * numbers". Counters come in five shapes and only one of them is a sum, which
 * is exactly why the log is replayed in order rather than aggregated:
 *
 * - **accumulators** ([Stat.LevelsCleared], [Stat.FlawlessClears]) — `+1` per
 *   qualifying attempt;
 * - **high-water marks** ([Stat.BestScore], [Stat.LargestGridCleared]) —
 *   running max;
 * - **streaks** ([Stat.FlawlessStreak]) — reset-or-increment, in arrival order;
 * - **first-time-only counts** ([Stat.ThreePawClears]) — gated on what the
 *   level was worth *before* this attempt, so a replay cannot re-earn it;
 * - **counts that need what came earlier** ([Stat.RedemptionClears]) — answered
 *   from the two pieces of bookkeeping below.
 *
 * [playedMillis] and [unfinishedLevels] are that bookkeeping: they are not
 * stats, they are what the fold has to remember to compute two of them.
 * [Stat.MinutesPlayed] is truncated for display, so the milliseconds have to be
 * carried at full precision or a hundred short attempts would each round to
 * nothing.
 *
 * Every one of these is monotonic over a history, which is what lets
 * `AchievementEngine` treat an unlock as permanent. A badge hung off a *current*
 * streak would unlock and then disagree with its own counter on the next attempt.
 */
data class AchievementCounters(
    private val values: Map<Stat, Long> = emptyMap(),
    private val playedMillis: Long = 0L,
    private val unfinishedLevels: Set<String> = emptySet(),
) {

    operator fun get(stat: Stat): Long = values[stat] ?: 0L

    /** Folds one finished attempt in and returns the next snapshot. Pure. */
    @Suppress("CyclomaticComplexMethod")
    fun fold(result: LevelResult): AchievementCounters {
        val next = values.toMutableMap()
        fun inc(stat: Stat) { next[stat] = (next[stat] ?: 0L) + 1L }
        fun setMax(stat: Stat, candidate: Long) { next[stat] = maxOf(next[stat] ?: 0L, candidate) }
        fun streak(current: Stat, best: Stat, held: Boolean) {
            val run = if (held) this[current] + 1L else 0L
            next[current] = run
            setMax(best, run)
        }

        // Time on the boards is the one thing a failed attempt still buys.
        val played = playedMillis + result.timeMs.coerceAtLeast(0L)
        next[Stat.MinutesPlayed] = played / MINUTE_MS

        // Judged on every attempt, not just clears: running out of bones is the
        // opposite of flawless, so a failure has to break the streak. The same
        // goes for the other two runs — a streak you can pause by losing is not
        // a streak.
        val flawless = result.completed && result.strikes == 0
        val threePaws = result.completed && result.paws >= THREE_PAWS
        val unaided = result.completed && result.sniffsUsed == 0 && result.treatsUsed == 0
        streak(Stat.FlawlessStreak, Stat.BestFlawlessStreak, flawless)
        streak(Stat.ThreePawStreak, Stat.BestThreePawStreak, threePaws)
        streak(Stat.BoosterFreeStreak, Stat.BestBoosterFreeStreak, unaided)

        // Everything past here describes a level the player finished. An
        // abandoned or failed attempt is still worth storing — it breaks
        // streaks, it arms a rematch, and a later release can ask it new
        // questions — but it earns nothing.
        if (!result.completed) {
            return AchievementCounters(next, played, unfinishedLevels + result.levelKey)
        }

        // The level is dropped from the set on the way out, so a rematch is
        // spent when it is won. Losing the same board again re-arms it, which
        // is farmable only by deliberately throwing away three bones first.
        if (result.levelKey in unfinishedLevels) inc(Stat.RedemptionClears)

        val bigBoard = result.size >= BIG_BOARD_SIZE
        val maxBoard = result.size >= MAX_BOARD_SIZE

        when (result.mode) {
            PlayMode.Daily -> {
                inc(Stat.DailiesCleared)
                setMax(Stat.BestDailyStreak, result.dailyStreakDays.toLong())
                if (flawless) inc(Stat.DailyFlawlessClears)
                if (threePaws) inc(Stat.DailyThreePawClears)
            }
            // Levels, not clears: the campaign is 500 boards, and replaying one
            // of them is not progress through it.
            PlayMode.Campaign -> if (result.isFirstClear) inc(Stat.LevelsCleared)
        }

        if (flawless) {
            inc(Stat.FlawlessClears)
            if (bigBoard) inc(Stat.FlawlessBigBoardClears)
        }
        if (result.strikes >= COMEBACK_STRIKES) {
            inc(Stat.ComebackClears)
            if (bigBoard) inc(Stat.BigBoardComebackClears)
        }
        if (unaided) inc(Stat.BoosterFreeClears)
        if (result.sniffsUsed > 0 && result.treatsUsed > 0) inc(Stat.AssistedClears)

        if (threePaws && result.previousBestPaws < THREE_PAWS) {
            inc(Stat.ThreePawClears)
            if (maxBoard) inc(Stat.MaxBoardThreePawClears)
        }

        if (bigBoard) inc(Stat.BigBoardClears)
        if (maxBoard) inc(Stat.MaxBoardClears)

        // A zero duration means the attempt was recorded without a clock, not
        // that it was instant. `LevelRecord` reads 0 the same way.
        if (result.timeMs in 1..SPRINT_MS) inc(Stat.SprintClears)
        if (result.timeMs in 1..FLASH_MS) inc(Stat.FlashClears)
        if (bigBoard && result.timeMs in 1..BIG_BOARD_SPRINT_MS) inc(Stat.BigBoardSprintClears)
        if (maxBoard && result.timeMs in 1..MAX_BOARD_SPRINT_MS) inc(Stat.MaxBoardSprintClears)

        if (result.localHour in NIGHT_HOURS) inc(Stat.NightClears)
        if (result.localHour in DAWN_HOURS) inc(Stat.DawnClears)

        setMax(Stat.BestScore, result.score.toLong())
        setMax(Stat.BestCombo, result.bestCombo.toLong())
        setMax(Stat.LargestGridCleared, result.size.toLong())

        return AchievementCounters(next, played, unfinishedLevels - result.levelKey)
    }

    companion object {
        val Empty: AchievementCounters = AchievementCounters()

        /** Bones lost and still cleared, for [Stat.ComebackClears]. */
        const val COMEBACK_STRIKES: Int = 2

        const val THREE_PAWS: Int = 3

        /** "Any level under 30 seconds", per SPEC section 8. */
        const val SPRINT_MS: Long = 30_000

        /** Fast enough that only a small board and a solved-it-already run get there. */
        const val FLASH_MS: Long = 10_000

        /** The band where a fast clear stops being a 4x4 formality. */
        const val BIG_BOARD_SIZE: Int = 7
        const val BIG_BOARD_SPRINT_MS: Long = 60_000

        /**
         * The largest board the game ships. Named here rather than taken from
         * `Board.MAX_SIZE` because this module deliberately depends on nothing;
         * `AchievementReachabilityTest` pins the two together against the real
         * packs so the copy cannot drift.
         */
        const val MAX_BOARD_SIZE: Int = 10
        const val MAX_BOARD_SPRINT_MS: Long = 300_000

        /** 1am to 4:59am. */
        val NIGHT_HOURS: IntRange = 1..4

        /** 5am to 7:59am, so the two windows cannot both fire. */
        val DAWN_HOURS: IntRange = 5..7

        private const val MINUTE_MS: Long = 60_000
    }
}
