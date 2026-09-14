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
 * ### Past level 500, where both axes are spent
 *
 * The size ladder finishes at ten. `docs/reference/large-boards-spike.md`
 * measured an 11x11 as a *longer* board rather than a harder one — reasoning per
 * square falls monotonically as the grid grows — costing 22 times as much
 * generator time per shipped tier-4 board and putting a colourblind glyph on a
 * 20.8dp square. Tier 5 is `BEYOND_DEDUCTION` and never ships. So there is no
 * axis left to climb, and pretending otherwise would mean either shipping boards
 * we cannot rate or a grid we cannot read.
 *
 * What the second five hundred are for is therefore *not repeating*. Meowdoku's
 * reviewers report its boards recurring around every hundred; every board here
 * is distinct up to the eight grid symmetries and any renaming of regions, and
 * `LevelPackVerificationTest` asserts that over the whole pack rather than
 * trusting the generator that produced it.
 *
 * The shape is five more 10x10 bands of a hundred, each twenty tier-3 boards
 * then eighty tier-4 ones. The dip at every hundredth level is the same
 * breather the sawtooth gives everywhere else, minus its usual excuse: no grid
 * grew. It stays because the alternative is five hundred consecutive boards at
 * the ceiling, which is the diet this curve already argues against, and because
 * a rhythm is the only structure left once size and tier are both spent.
 *
 * **Append only.** Progress is keyed on level id, so reordering or removing a
 * band silently reassigns everyone's completed levels to different boards.
 * Adding bands to the end does not: the generator draws band by band from one
 * sequential `Random`, so bands nobody touched come out byte for byte identical.
 * See [LevelPacks.PACK_VERSION].
 *
 * ### The daily curve
 *
 * Deliberately not a ramp, per `features.md#the-daily` — a daily is a
 * three-to-five-minute habit and every player meets the same board whatever
 * level they are on. It stays on mid-sized grids and is shuffled rather than
 * ordered.
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
        // Levels 501 to 1000. Written out five times rather than repeated by a
        // loop, because this list is the one human-readable record of the curve
        // and a `repeat(5)` in the middle of it is the thing a reader has to
        // evaluate instead of read. Each is an edit somebody could make.
        CurveBand(size = 10, runs = listOf(TierRun(3, 20), TierRun(4, 80))),
        CurveBand(size = 10, runs = listOf(TierRun(3, 20), TierRun(4, 80))),
        CurveBand(size = 10, runs = listOf(TierRun(3, 20), TierRun(4, 80))),
        CurveBand(size = 10, runs = listOf(TierRun(3, 20), TierRun(4, 80))),
        CurveBand(size = 10, runs = listOf(TierRun(3, 20), TierRun(4, 80))),
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
     * How far past the first level at its grid size [levelId] sits, counting
     * from zero, or null when no campaign band contains it.
     *
     * Counted from the first level at that *size* and not from the start of its
     * band. The two were the same thing until the campaign grew past 500, where
     * it keeps going without the grid growing with it: six consecutive 10x10
     * bands are one stretch as far as the player's eyes are concerned, and
     * counting from each band's start would restart the number five more times
     * for no reason the board shows.
     *
     * Null rather than a clamp for an id off either end of the curve. Every
     * caller here is asking "is this one of the opening levels of a size", and a
     * clamp would answer yes for level 0 and for a level past the end of the
     * pack, which are both nonsense rather than edges.
     */
    fun positionAtSize(levelId: Int): Int? {
        var firstInBand = 1
        var firstAtSize = 1
        var sizeSoFar = 0
        campaign.forEach { band ->
            if (band.size != sizeSoFar) {
                firstAtSize = firstInBand
                sizeSoFar = band.size
            }
            if (levelId in firstInBand until firstInBand + band.count) return levelId - firstAtSize
            firstInBand += band.count
        }
        return null
    }

    /**
     * Whether campaign level [levelId] opens with one dog already placed.
     *
     * Two windows, and a level inside either one gets the dog.
     *
     * [openingLevels] is `progression.starterDogOpeningLevels`: an unbroken run
     * from level 1, so the *first* board that opens empty is
     * `openingLevels + 1`. Per-band alone put it at level 4, three boards into a
     * first session, and a player who has not yet worked out what a region is
     * reads an empty grid as a board that failed to load rather than as a board
     * with a first move in it. The run is the answer to that: the empty start is
     * a thing the game asks for once the rules are known, not part of learning
     * them.
     *
     * [levelsPerBand] is `progression.starterDogLevelsPerBand`, counted from the
     * first level at a grid *size* rather than from an absolute id, because what
     * the free dog is for is the auto-mark cascade and the cascade is worth
     * watching again every time the grid grows. Keyed on one number it ran out
     * inside the 5x5 band and never came back for a player's first 7x7 or first
     * 10x10, which are the boards where it says the most.
     *
     * The grid is the trigger and the band is not, which only became a visible
     * difference when the campaign grew past 500 on a grid that had stopped
     * growing. See [positionAtSize].
     *
     * Zero or less switches a window off. Both at zero hands out no dogs at all,
     * which is how the whole head start is withdrawn from the console without
     * shipping a build. An id off the curve is never given one either way.
     */
    fun opensWithStarterDog(levelId: Int, levelsPerBand: Int, openingLevels: Int): Boolean {
        // Before either window, so that a level nobody generated — id 0, or one
        // past the end of the pack — cannot be handed a dog by the opening run
        // just for carrying a small number.
        val position = positionAtSize(levelId) ?: return false
        if (levelId <= openingLevels) return true
        if (levelsPerBand <= 0) return false
        return position < levelsPerBand
    }

    /**
     * Every daily level's declared shape, in no particular order — the daily
     * pool is shuffled after generation, so only the multiset is meaningful.
     */
    val dailyShape: List<LevelShape> = daily.flatMap { band ->
        band.tiers.map { tier -> LevelShape(band.size, tier) }
    }
}
