package com.sodogku.libraries.achievements

/**
 * A finished attempt that earns nothing but the opening badge.
 *
 * Every default here is deliberately just outside a threshold — one strike
 * (so not flawless, and not a comeback), one sniff spent, a minute on the clock
 * on the smallest grid, one paw, midday. A test that wants a counter to move
 * says so by overriding exactly the field that moves it, which is what makes
 * these assertions mean anything: if the fold ever started counting the neutral
 * result, half of the file would fail at once.
 */
fun result(
    levelId: Int = 1,
    mode: PlayMode = PlayMode.Campaign,
    size: Int = 4,
    completed: Boolean = true,
    score: Int = 0,
    paws: Int = 1,
    timeMs: Long = 60_000,
    strikes: Int = 1,
    bestCombo: Int = 0,
    sniffsUsed: Int = 1,
    treatsUsed: Int = 0,
    isFirstClear: Boolean = true,
    previousBestPaws: Int = 0,
    dailyStreakDays: Int = 0,
    localHour: Int = 12,
    finishedAt: Long = 0,
): LevelResult = LevelResult(
    levelId = levelId,
    mode = mode,
    size = size,
    completed = completed,
    score = score,
    paws = paws,
    timeMs = timeMs,
    strikes = strikes,
    bestCombo = bestCombo,
    sniffsUsed = sniffsUsed,
    treatsUsed = treatsUsed,
    isFirstClear = isFirstClear,
    previousBestPaws = previousBestPaws,
    dailyStreakDays = dailyStreakDays,
    localHour = localHour,
    finishedAt = finishedAt,
)

fun foldAll(vararg results: LevelResult): AchievementCounters =
    results.fold(AchievementCounters.Empty) { counters, next -> counters.fold(next) }

/**
 * A history that drives [stat] to exactly [value], and moves as little else as
 * it can. The `when` is exhaustive on purpose: a new [Stat] cannot be added
 * without deciding here how a player would actually earn it, which is the
 * question that catches a counter nothing in the game can ever increment.
 */
@Suppress("CyclomaticComplexMethod")
fun historyFor(stat: Stat, value: Int): List<LevelResult> = when (stat) {
    Stat.LevelsCleared -> clears(value) { result(levelId = it, isFirstClear = true) }
    Stat.DailiesCleared -> clears(value) { result(levelId = it, mode = PlayMode.Daily) }
    Stat.FlawlessClears,
    Stat.FlawlessStreak,
    Stat.BestFlawlessStreak,
    -> clears(value) { result(levelId = it, strikes = 0) }
    Stat.FlawlessBigBoardClears -> clears(value) { result(levelId = it, size = 7, strikes = 0) }
    Stat.ThreePawClears,
    Stat.ThreePawStreak,
    Stat.BestThreePawStreak,
    -> clears(value) { result(levelId = it, paws = 3, previousBestPaws = 0) }
    Stat.MaxBoardThreePawClears -> clears(value) {
        result(levelId = it, size = 10, paws = 3, previousBestPaws = 0)
    }
    Stat.ComebackClears -> clears(value) { result(levelId = it, strikes = 2) }
    Stat.BigBoardComebackClears -> clears(value) { result(levelId = it, size = 7, strikes = 2) }
    // The only history that is not one attempt per rung: a rematch needs the
    // loss that arms it.
    Stat.RedemptionClears -> rematches(value)
    Stat.BoosterFreeClears,
    Stat.BoosterFreeStreak,
    Stat.BestBoosterFreeStreak,
    -> clears(value) { result(levelId = it, sniffsUsed = 0, treatsUsed = 0) }
    Stat.AssistedClears -> clears(value) { result(levelId = it, sniffsUsed = 1, treatsUsed = 1) }
    Stat.SprintClears -> clears(value) { result(levelId = it, timeMs = 20_000) }
    Stat.FlashClears -> clears(value) { result(levelId = it, timeMs = 5_000) }
    Stat.BigBoardSprintClears -> clears(value) { result(levelId = it, size = 7, timeMs = 45_000) }
    Stat.MaxBoardSprintClears -> clears(value) { result(levelId = it, size = 10, timeMs = 120_000) }
    Stat.BigBoardClears -> clears(value) { result(levelId = it, size = 7) }
    Stat.MaxBoardClears -> clears(value) { result(levelId = it, size = 10) }
    Stat.NightClears -> clears(value) { result(levelId = it, localHour = 3) }
    Stat.DawnClears -> clears(value) { result(levelId = it, localHour = 6) }
    Stat.DailyFlawlessClears -> clears(value) {
        result(levelId = it, mode = PlayMode.Daily, strikes = 0)
    }
    Stat.DailyThreePawClears -> clears(value) {
        result(levelId = it, mode = PlayMode.Daily, paws = 3)
    }

    // A sum, so one long attempt is the shortest history that reaches it.
    Stat.MinutesPlayed -> highWater(value) { result(timeMs = value * MINUTE_MS) }

    // High-water marks are reached in one attempt, not accumulated.
    Stat.BestScore -> highWater(value) { result(score = value) }
    Stat.BestCombo -> highWater(value) { result(bestCombo = value) }
    Stat.LargestGridCleared -> highWater(value) { result(size = value) }
    Stat.BestDailyStreak -> highWater(value) {
        result(mode = PlayMode.Daily, dailyStreakDays = value)
    }
}

private fun clears(count: Int, build: (Int) -> LevelResult): List<LevelResult> =
    (1..count).map { build(it).copy(finishedAt = it.toLong()) }

private fun highWater(value: Int, build: () -> LevelResult): List<LevelResult> =
    if (value <= 0) emptyList() else listOf(build())

private fun rematches(count: Int): List<LevelResult> = (1..count).flatMap { level ->
    listOf(
        result(levelId = level, completed = false, paws = 0, strikes = 3, finishedAt = level * 2L),
        result(levelId = level, finishedAt = level * 2L + 1),
    )
}

private const val MINUTE_MS = 60_000L
