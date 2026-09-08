package com.sodogku.libraries.levels

/**
 * A run of consecutive levels at one difficulty tier.
 *
 * Declared as a run rather than derived from a percentage so the curve reads as
 * the thing a player experiences: twelve fives that need multi-region
 * reasoning, then six that need a contradiction.
 */
data class TierRun(val tier: Int, val count: Int)

/**
 * One stretch of a pack at a fixed grid size, and the tiers it is made of, in
 * the order they are played.
 *
 * Size and tier are separate axes. A band fixes the size and spends its levels
 * climbing the tier, so the campaign gets harder twice over — deeper reasoning
 * inside a band, a bigger grid at every band change — and neither has to
 * stand in for the other.
 */
data class CurveBand(val size: Int, val runs: List<TierRun>) {

    val count: Int = runs.sumOf { it.count }

    /** The tier of each level in the band, in order. */
    val tiers: List<Int> = runs.flatMap { run -> List(run.count) { run.tier } }

    init {
        require(runs.isNotEmpty()) { "band ${size}x$size declares no levels" }
        require(runs.all { it.count > 0 }) { "band ${size}x$size declares an empty run" }
        // Ascending and distinct: a band that dipped back to an easier tier
        // halfway through would read as a bug in the pack rather than as a
        // design choice, and `LevelPackVerificationTest` asserts the shipped
        // order matches this list exactly.
        val declared = runs.map { it.tier }
        require(declared == declared.sorted() && declared.distinct() == declared) {
            "band ${size}x$size must climb: $declared"
        }
    }
}

/** A level's shape: how big the grid is and how deep the reasoning goes. */
data class LevelShape(val size: Int, val difficulty: Int)

/**
 * What the shipped packs are made of.
 *
 * This lives here, next to the packs, rather than in `:tools:level-generator`,
 * because it is the only description of the curve that both the generator and
 * the verification test can read. When it was a private list inside the
 * generator the packs were the only record of the intended shape, so nothing
 * could tell a deliberate re-curve from a band that quietly came up short.
 *
 * ### The campaign curve
 *
 * Tier 5 is `Difficulty.BEYOND_DEDUCTION` — a board no chain of reasoning
 * solves — and never ships, so tier 4 is the ceiling and the whole ramp is 1 to
 * 4. That is a short ladder for 500 levels, which is the argument for climbing
 * it early rather than spreading it thin: the first version reached tier 3 at
 * level 38 and tier 4 at level 92, and 46% of the campaign was tier 2.
 *
 * The shape now is the one a casual puzzle game actually uses. Levels 1 to 10
 * teach on a 4x4 and the tutorial runs in front of them. Levels 11 to 22 build
 * confidence at the same tier on a slightly bigger grid. The ramp starts at 23
 * and reaches the ceiling at 35, so a player who is going to bounce off the
 * difficulty finds that out in their first session rather than an hour in.
 *
 * After that each band opens a tier below where the last one closed. That
 * sawtooth is deliberate and it is not a reset: the grid just grew, which is a
 * difficulty jump of its own, so the reasoning gets a breather while the player
 * learns to read a wider board. It also keeps roughly a quarter of the back
 * half at tier 3, which a solid diet of tier 4 would not.
 *
 * ### The daily curve
 *
 * Deliberately not a ramp, per SPEC Q3 — a daily is a three-to-five-minute
 * habit and every player meets the same board whatever level they are on. It
 * stays on mid-sized grids and is shuffled rather than ordered.
 *
 * What it does not do any more is draw whatever the generator happened to
 * produce, which was 11% tier 1 and 60% tier 2. That made the daily reliably
 * *easier* than the campaign level its player was on, which is the wrong signal
 * from the thing that exists to bring them back. The floor is now tier 2 and
 * the mode is tier 3, with a fifth of the pool at the ceiling, and the size
 * bands are unchanged so the time budget is too.
 */
object LevelCurve {

    val campaign: List<CurveBand> = listOf(
        // Guided by the tutorial. Nothing here may need more than a single
        // confinement step, and level 1 has to fall out on its own.
        CurveBand(size = 4, runs = listOf(TierRun(tier = 1, count = 2), TierRun(tier = 2, count = 8))),
        // The ramp. Tier 3 at level 23, tier 4 at level 35.
        CurveBand(
            size = 5,
            runs = listOf(TierRun(2, 12), TierRun(3, 12), TierRun(4, 6)),
        ),
        CurveBand(size = 6, runs = listOf(TierRun(2, 4), TierRun(3, 16), TierRun(4, 40))),
        CurveBand(size = 7, runs = listOf(TierRun(3, 24), TierRun(4, 56))),
        CurveBand(size = 8, runs = listOf(TierRun(3, 30), TierRun(4, 70))),
        CurveBand(size = 9, runs = listOf(TierRun(3, 30), TierRun(4, 80))),
        CurveBand(size = 10, runs = listOf(TierRun(3, 30), TierRun(4, 80))),
    )

    /** Two years of dailies, drawn from mid-sized boards at a steady mix. */
    val daily: List<CurveBand> = listOf(
        CurveBand(size = 6, runs = listOf(TierRun(2, 108), TierRun(3, 84), TierRun(4, 48))),
        CurveBand(size = 7, runs = listOf(TierRun(2, 112), TierRun(3, 88), TierRun(4, 50))),
        CurveBand(size = 8, runs = listOf(TierRun(2, 108), TierRun(3, 84), TierRun(4, 48))),
    )

    /** Every campaign level's declared shape, in level-id order. */
    val campaignShape: List<LevelShape> = campaign.flatMap { band ->
        band.tiers.map { tier -> LevelShape(band.size, tier) }
    }

    /**
     * Every daily level's declared shape, in no particular order — the daily
     * pool is shuffled after generation, so only the multiset is meaningful.
     */
    val dailyShape: List<LevelShape> = daily.flatMap { band ->
        band.tiers.map { tier -> LevelShape(band.size, tier) }
    }
}
