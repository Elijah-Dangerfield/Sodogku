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
    // The campaign, and the boards getting bigger.
    FirstSteps,
    GoodDog,
    OffTheLeash,
    WellTrained,
    BestInShow,
    SeasonedSnout,
    TopDog,
    GridSeven,
    GridTen,
    BigLeague,
    HeavyLifting,
    TenByTenClub,

    // Clean play.
    PerfectForm,
    Spotless,
    SqueakyClean,
    HatTrick,
    FlawlessTen,
    Untouchable,
    CleanSheet,
    IronNose,

    // Doing it the hard way.
    Comeback,
    NeverSayDie,
    ToughCookie,
    NervesOfSteel,
    NoHelpNeeded,
    SelfTaught,
    AllYourOwnWork,
    SoloRun,
    LongWayRound,

    // Speed.
    SpeedDemon,
    QuickPaws,
    BlurOfFur,
    Blitz,
    RocketRecall,
    TenInFive,

    // Score and combos.
    TreatMoney,
    HighRoller,
    Jackpot,
    ChainOfFive,
    ChainOfEight,
    Unbroken,

    // Paws.
    ThreePawsUp,
    ShowDog,
    Pedigree,
    BlueRibbon,
    HallOfFame,
    OnARoll,
    InTheZone,
    PerfectTen,
    MasterOfTheGrid,

    // The daily.
    FetchDaily,
    CreatureOfHabit,
    PartOfTheRoutine,
    RegularAsClockwork,
    ThreeInARow,
    DailyDevotion,
    Faithful,
    HundredDays,
    SpotlessDaily,
    NoBonesAboutIt,
    Showstopper,

    // Time on the boards.
    HourWithTheDogs,
    TenHoursDeep,
    FiftyHoursIn,

    // Secrets.
    SecondWind,
    GrudgeMatch,
    NoShameInIt,
    TenSecondDog,
    BlinkAndMissIt,
    NightOwl,
    MidnightShift,
    EarlyBird,
    SunriseRitual,
}

/**
 * Which shelf of the trophy case a badge sits on.
 *
 * At twenty-one badges the grouping could stay implicit in list order. At
 * seventy-three it cannot: an ungrouped wall of tiles is a bag, and the whole
 * reason to open the screen is to find the ladder you are part-way up.
 *
 * [Secrets] is where every hidden badge lives, rather than each one sitting in
 * the group its criterion belongs to. A mystery tile under "Speed" has already
 * given away the half of the surprise worth keeping.
 */
enum class AchievementGroup {
    Campaign,
    CleanPlay,
    HardWay,
    Speed,
    Score,
    Paws,
    Daily,
    Dedication,
    Secrets,
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

/** One shelf: a group and the badges on it, easiest first. */
data class AchievementSection(
    val group: AchievementGroup,
    val achievements: List<Achievement>,
)

/**
 * The catalog. Client-side and shipped in the binary, not served: every
 * achievement needs an icon and copy, so a new one costs a release regardless
 * (SPEC section 4.4).
 *
 * [sections] is the source of truth and [catalog] is its flattening, so display
 * order and grant order cannot disagree and no badge can be left off the grid by
 * being forgotten in a second list.
 *
 * Targets that describe *the game* rather than the player — a score, a combo
 * length, a board size, a campaign length — are checked against the shipped
 * packs and the scoring formula by `AchievementReachabilityTest`. A badge nobody
 * can ever earn is worse than no badge, and it looks exactly like a working one.
 */
object Achievements {

    val sections: List<AchievementSection> = listOf(
        AchievementSection(
            AchievementGroup.Campaign,
            listOf(
                Achievement(AchievementId.FirstSteps, Stat.LevelsCleared, target = 1),
                Achievement(AchievementId.GoodDog, Stat.LevelsCleared, target = 10),
                Achievement(AchievementId.OffTheLeash, Stat.LevelsCleared, target = 25),
                Achievement(AchievementId.WellTrained, Stat.LevelsCleared, target = 50),
                Achievement(AchievementId.BestInShow, Stat.LevelsCleared, target = 100),
                Achievement(AchievementId.SeasonedSnout, Stat.LevelsCleared, target = 250),
                Achievement(AchievementId.TopDog, Stat.LevelsCleared, target = 500),
                Achievement(AchievementId.GridSeven, Stat.LargestGridCleared, target = 7),
                Achievement(AchievementId.GridTen, Stat.LargestGridCleared, target = 10),
                Achievement(AchievementId.BigLeague, Stat.BigBoardClears, target = 10),
                Achievement(AchievementId.HeavyLifting, Stat.BigBoardClears, target = 50),
                Achievement(AchievementId.TenByTenClub, Stat.MaxBoardClears, target = 25),
            ),
        ),
        AchievementSection(
            AchievementGroup.CleanPlay,
            listOf(
                Achievement(AchievementId.PerfectForm, Stat.FlawlessClears, target = 1),
                Achievement(AchievementId.Spotless, Stat.FlawlessClears, target = 25),
                Achievement(AchievementId.SqueakyClean, Stat.FlawlessClears, target = 100),
                Achievement(AchievementId.HatTrick, Stat.BestFlawlessStreak, target = 3),
                Achievement(AchievementId.FlawlessTen, Stat.BestFlawlessStreak, target = 10),
                Achievement(AchievementId.Untouchable, Stat.BestFlawlessStreak, target = 25),
                Achievement(AchievementId.CleanSheet, Stat.FlawlessBigBoardClears, target = 1),
                Achievement(AchievementId.IronNose, Stat.FlawlessBigBoardClears, target = 10),
            ),
        ),
        AchievementSection(
            AchievementGroup.HardWay,
            listOf(
                Achievement(AchievementId.Comeback, Stat.ComebackClears, target = 1),
                Achievement(AchievementId.NeverSayDie, Stat.ComebackClears, target = 10),
                Achievement(AchievementId.ToughCookie, Stat.ComebackClears, target = 50),
                Achievement(AchievementId.NervesOfSteel, Stat.BigBoardComebackClears, target = 1),
                Achievement(AchievementId.NoHelpNeeded, Stat.BoosterFreeClears, target = 25),
                Achievement(AchievementId.SelfTaught, Stat.BoosterFreeClears, target = 100),
                Achievement(AchievementId.AllYourOwnWork, Stat.BoosterFreeClears, target = 250),
                Achievement(AchievementId.SoloRun, Stat.BestBoosterFreeStreak, target = 20),
                Achievement(AchievementId.LongWayRound, Stat.BestBoosterFreeStreak, target = 50),
            ),
        ),
        AchievementSection(
            AchievementGroup.Speed,
            listOf(
                Achievement(AchievementId.SpeedDemon, Stat.SprintClears, target = 1),
                Achievement(AchievementId.QuickPaws, Stat.SprintClears, target = 25),
                Achievement(AchievementId.BlurOfFur, Stat.SprintClears, target = 100),
                Achievement(AchievementId.Blitz, Stat.BigBoardSprintClears, target = 1),
                Achievement(AchievementId.RocketRecall, Stat.BigBoardSprintClears, target = 10),
                Achievement(AchievementId.TenInFive, Stat.MaxBoardSprintClears, target = 1),
            ),
        ),
        AchievementSection(
            AchievementGroup.Score,
            listOf(
                // The ladder is pinned to what the formula can actually pay. Par
                // on the biggest, hardest shipped board is a little under 32,000
                // and par is also the ceiling, so 26,000 is a fast clean run on a
                // 10x10 and nothing else — while 5,000 is one good 4x4.
                Achievement(AchievementId.TreatMoney, Stat.BestScore, target = 5_000),
                Achievement(AchievementId.HighRoller, Stat.BestScore, target = 20_000),
                Achievement(AchievementId.Jackpot, Stat.BestScore, target = 26_000),
                Achievement(AchievementId.ChainOfFive, Stat.BestCombo, target = 5),
                Achievement(AchievementId.ChainOfEight, Stat.BestCombo, target = 8),
                // A combo cannot outlive the board: ten placements is the whole
                // of a 10x10, so this is the last rung that exists.
                Achievement(AchievementId.Unbroken, Stat.BestCombo, target = 10),
            ),
        ),
        AchievementSection(
            AchievementGroup.Paws,
            listOf(
                Achievement(AchievementId.ThreePawsUp, Stat.ThreePawClears, target = 1),
                Achievement(AchievementId.ShowDog, Stat.ThreePawClears, target = 10),
                Achievement(AchievementId.Pedigree, Stat.ThreePawClears, target = 50),
                Achievement(AchievementId.BlueRibbon, Stat.ThreePawClears, target = 150),
                Achievement(AchievementId.HallOfFame, Stat.ThreePawClears, target = 300),
                Achievement(AchievementId.OnARoll, Stat.BestThreePawStreak, target = 5),
                Achievement(AchievementId.InTheZone, Stat.BestThreePawStreak, target = 20),
                Achievement(AchievementId.PerfectTen, Stat.MaxBoardThreePawClears, target = 1),
                Achievement(AchievementId.MasterOfTheGrid, Stat.MaxBoardThreePawClears, target = 10),
            ),
        ),
        AchievementSection(
            AchievementGroup.Daily,
            listOf(
                Achievement(AchievementId.FetchDaily, Stat.DailiesCleared, target = 1),
                Achievement(AchievementId.CreatureOfHabit, Stat.DailiesCleared, target = 10),
                Achievement(AchievementId.PartOfTheRoutine, Stat.DailiesCleared, target = 50),
                Achievement(AchievementId.RegularAsClockwork, Stat.DailiesCleared, target = 150),
                Achievement(AchievementId.ThreeInARow, Stat.BestDailyStreak, target = 3),
                Achievement(AchievementId.DailyDevotion, Stat.BestDailyStreak, target = 7),
                Achievement(AchievementId.Faithful, Stat.BestDailyStreak, target = 30),
                Achievement(AchievementId.HundredDays, Stat.BestDailyStreak, target = 100),
                Achievement(AchievementId.SpotlessDaily, Stat.DailyFlawlessClears, target = 1),
                Achievement(AchievementId.NoBonesAboutIt, Stat.DailyFlawlessClears, target = 10),
                Achievement(AchievementId.Showstopper, Stat.DailyThreePawClears, target = 10),
            ),
        ),
        AchievementSection(
            AchievementGroup.Dedication,
            listOf(
                Achievement(AchievementId.HourWithTheDogs, Stat.MinutesPlayed, target = 60),
                Achievement(AchievementId.TenHoursDeep, Stat.MinutesPlayed, target = 600),
                Achievement(AchievementId.FiftyHoursIn, Stat.MinutesPlayed, target = 3_000),
            ),
        ),
        AchievementSection(
            AchievementGroup.Secrets,
            listOf(
                Achievement(AchievementId.SecondWind, Stat.RedemptionClears, target = 1, hidden = true),
                Achievement(AchievementId.GrudgeMatch, Stat.RedemptionClears, target = 10, hidden = true),
                Achievement(AchievementId.NoShameInIt, Stat.AssistedClears, target = 1, hidden = true),
                Achievement(AchievementId.TenSecondDog, Stat.FlashClears, target = 1, hidden = true),
                Achievement(AchievementId.BlinkAndMissIt, Stat.FlashClears, target = 10, hidden = true),
                Achievement(AchievementId.NightOwl, Stat.NightClears, target = 1, hidden = true),
                Achievement(AchievementId.MidnightShift, Stat.NightClears, target = 10, hidden = true),
                Achievement(AchievementId.EarlyBird, Stat.DawnClears, target = 1, hidden = true),
                Achievement(AchievementId.SunriseRitual, Stat.DawnClears, target = 10, hidden = true),
            ),
        ),
    )

    /** Every badge, in display order. */
    val catalog: List<Achievement> = sections.flatMap { it.achievements }

    private val byId: Map<AchievementId, Achievement> = catalog.associateBy { it.id }

    private val groupById: Map<AchievementId, AchievementGroup> =
        sections.flatMap { section -> section.achievements.map { it.id to section.group } }.toMap()

    operator fun get(id: AchievementId): Achievement = byId.getValue(id)

    fun groupOf(id: AchievementId): AchievementGroup = groupById.getValue(id)
}
