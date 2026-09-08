package com.sodogku.libraries.achievements

/**
 * Stable identity for an achievement. Persisted by name, so renaming one throws
 * away everybody who earned it — add a new entry instead.
 *
 * **There is no display copy here on purpose.** A name and a description are
 * player-facing strings, they have to be translated, and this module cannot see
 * `:libraries:resources`. The UI maps an id to copy with an exhaustive `when`,
 * which means adding an entry here fails to compile until somebody writes the
 * words for it. A `nameKey: String` on the definition would have looked tidier
 * and would have failed at runtime, in the release build, as a badge captioned
 * `achievement_top_dog_name`.
 */
enum class AchievementId {
    // Campaign volume.
    FirstSteps,
    GoodDog,
    BestInShow,
    TopDog,

    // Clean play.
    PerfectForm,
    FlawlessTen,
    Comeback,
    NoHelpNeeded,

    // Speed and score.
    SpeedDemon,
    Blitz,
    HighRoller,
    ChainOfEight,

    // Mastery.
    ShowDog,
    Pedigree,
    GridSeven,
    GridTen,

    // Daily.
    FetchDaily,
    DailyDevotion,
    Faithful,

    // Hidden.
    NightOwl,
    EarlyBird,
}

/**
 * One achievement: a counter, and the value of it that earns the badge.
 *
 * Every criterion in the catalog is that same shape, which is a deliberate
 * simplification of the design this came from (a sealed `Criterion` hierarchy
 * plus a `Custom` escape hatch). Anything that cannot be phrased as "this stat
 * reached this number" becomes a new [Stat] in the fold rather than a new kind
 * of criterion — the reasoning about what counts then lives in exactly one
 * place, and progress-toward-unlock is a division rather than a special case.
 */
data class Achievement(
    val id: AchievementId,
    val stat: Stat,
    val target: Long,

    /**
     * Shown as a locked mystery until earned. For the ones that are a surprise
     * rather than a goal — "play at 3am" is a strange thing to ask of someone.
     */
    val hidden: Boolean = false,
) {
    init {
        require(target > 0) { "$id needs a positive target, was $target" }
    }

    fun isMet(counters: AchievementCounters): Boolean = counters[stat] >= target

    /** 0.0 to 1.0, for a progress bar. */
    fun progress(counters: AchievementCounters): Float =
        (counters[stat].toFloat() / target).coerceIn(0f, 1f)
}

/**
 * The catalog. Client-side and shipped in the binary, not served: every
 * achievement needs an icon and copy, so a new one costs a release regardless
 * (SPEC section 4.4).
 *
 * List order is display order. Grouped by what a player is doing when they earn
 * them, easiest first inside each group, so the badge grid reads as a set of
 * ladders rather than a bag.
 */
object Achievements {

    val catalog: List<Achievement> = listOf(
        Achievement(AchievementId.FirstSteps, Stat.LevelsCleared, target = 1),
        Achievement(AchievementId.GoodDog, Stat.LevelsCleared, target = 10),
        Achievement(AchievementId.BestInShow, Stat.LevelsCleared, target = 100),
        Achievement(AchievementId.TopDog, Stat.LevelsCleared, target = 500),

        Achievement(AchievementId.PerfectForm, Stat.FlawlessClears, target = 1),
        Achievement(AchievementId.FlawlessTen, Stat.BestFlawlessStreak, target = 10),
        Achievement(AchievementId.Comeback, Stat.ComebackClears, target = 1),
        Achievement(AchievementId.NoHelpNeeded, Stat.BoosterFreeClears, target = 25),

        Achievement(AchievementId.SpeedDemon, Stat.SprintClears, target = 1),
        Achievement(AchievementId.Blitz, Stat.BigBoardSprintClears, target = 1),
        // Reachable, and only on a big board: par on a 10x10 is a little over
        // 30,000 points, and about 6,400 on a 4x4. Grinding level 1 cannot earn
        // it, which is the point of a score achievement.
        Achievement(AchievementId.HighRoller, Stat.BestScore, target = 20_000),
        Achievement(AchievementId.ChainOfEight, Stat.BestCombo, target = 8),

        Achievement(AchievementId.ShowDog, Stat.ThreePawClears, target = 10),
        Achievement(AchievementId.Pedigree, Stat.ThreePawClears, target = 50),
        Achievement(AchievementId.GridSeven, Stat.LargestGridCleared, target = 7),
        Achievement(AchievementId.GridTen, Stat.LargestGridCleared, target = 10),

        Achievement(AchievementId.FetchDaily, Stat.DailiesCleared, target = 1),
        Achievement(AchievementId.DailyDevotion, Stat.BestDailyStreak, target = 7),
        Achievement(AchievementId.Faithful, Stat.BestDailyStreak, target = 30),

        Achievement(AchievementId.NightOwl, Stat.NightClears, target = 1, hidden = true),
        Achievement(AchievementId.EarlyBird, Stat.DawnClears, target = 1, hidden = true),
    )

    private val byId: Map<AchievementId, Achievement> = catalog.associateBy { it.id }

    operator fun get(id: AchievementId): Achievement = byId.getValue(id)
}
