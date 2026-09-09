package com.sodogku.libraries.ui.components.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The score roll takes longer for a bigger jump, and the header's copy of the
 * number is short enough to sit in a header.
 *
 * It was a flat 420ms whatever the delta. A placement pays a few hundred points
 * and a level clear pays a few thousand, so the same duration covered a tenfold
 * range: the clear blurred and the number simply appeared to change, which is
 * precisely what the animation exists to avoid.
 *
 * These test the shape rather than the constants. A tuning pass should be able
 * to move the floor, the ceiling and the slope without rewriting the tests, and
 * still be caught if it makes the duration constant again or lets it run away.
 */
class ScoreCounterTest {

    @Test
    fun aBiggerJumpRollsForLonger() {
        val placement = countUpMillis(PLACEMENT_POINTS)
        val levelClear = countUpMillis(LEVEL_CLEAR_POINTS)

        assertTrue(
            levelClear > placement,
            "a $LEVEL_CLEAR_POINTS point clear rolls for ${levelClear}ms and a " +
                "$PLACEMENT_POINTS point placement for ${placement}ms, so the duration is not scaling",
        )
    }

    @Test
    fun aPlacementStillFeelsImmediate() {
        // The floor matters as much as the scaling. A roll that takes half a
        // second for 200 points would make the board feel laggy on every tap.
        assertTrue(
            countUpMillis(PLACEMENT_POINTS) < IMMEDIATE_CEILING,
            "a placement rolls for ${countUpMillis(PLACEMENT_POINTS)}ms, which reads as lag",
        )
    }

    @Test
    fun anEnormousJumpIsCapped() {
        // Lifetime score is unbounded, so without a ceiling a player returning
        // after a long absence could watch the counter for a full minute.
        val huge = countUpMillis(HUGE_POINTS)
        val absurd = countUpMillis(HUGE_POINTS * 1000)

        assertEquals(huge, absurd, "the duration keeps growing with the delta")
        assertTrue(absurd < HOLDS_THE_SCREEN, "${absurd}ms holds the screen too long")
    }

    @Test
    fun aDropRollsRatherThanFreezing() {
        // The lifetime score only climbs today, but `ScoreCounter` is handed a
        // plain Int and a negative delta computed as a duration would be
        // negative, which `tween` rejects at runtime rather than at compile
        // time. Cheap to make impossible.
        assertTrue(countUpMillis(-LEVEL_CLEAR_POINTS) > 0, "a downward jump produced a non-positive duration")
        assertEquals(
            countUpMillis(LEVEL_CLEAR_POINTS),
            countUpMillis(-LEVEL_CLEAR_POINTS),
            "a jump down should roll for as long as the same jump up",
        )
    }

    @Test
    fun noJumpStillHasAPositiveDuration() {
        // `ScoreCounter` returns early on a zero delta, so this never runs in
        // practice. It is here because a zero duration is the one value `tween`
        // treats as "snap", and a refactor that removed that early return would
        // otherwise silently kill the animation.
        assertTrue(countUpMillis(0) > 0)
    }

    @Test
    fun aSmallScoreIsDrawnExactly() {
        // `0.8K` is longer than `845` and less true. There is nothing to save
        // below a thousand.
        assertEquals("845", abbreviateScore(845))
        assertEquals("0", abbreviateScore(0))
        assertEquals("999", abbreviateScore(999))
    }

    @Test
    fun thousandsCarryOneDecimal() {
        // The reported case: level 29's lifetime total, six digits wide in a
        // header beside a level number.
        assertEquals("130.5K", abbreviateScore(130_450))
        assertEquals("1.2K", abbreviateScore(1_234))
        assertEquals("1K", abbreviateScore(1_000))
    }

    @Test
    fun aWholeNumberDropsItsDecimal() {
        // `12.0K` is a decimal place spent saying nothing.
        assertEquals("12K", abbreviateScore(12_000))
        assertEquals("12K", abbreviateScore(12_004), "the rounding stopped short of a whole number")
    }

    @Test
    fun theDecimalIsWhatKeepsTheRollWorthWatching() {
        // The counter rolls between these two, and without a decimal place both
        // would read `130K` — a level clear worth 400 points would animate
        // between two identical strings.
        assertNotEquals(abbreviateScore(130_050), abbreviateScore(130_450))
    }

    @Test
    fun millionsGetTheirOwnSuffix() {
        assertEquals("1.2M", abbreviateScore(1_234_567))
        assertEquals("1M", abbreviateScore(1_000_000))
    }

    @Test
    fun theTopOfTheThousandsPromotesRatherThanRoundingToFourDigits() {
        // 999,999 rounds to `1000.0K`, which is longer than the number it is
        // abbreviating and reads as a bug.
        assertEquals("1M", abbreviateScore(999_999))
        assertEquals("999.9K", abbreviateScore(999_949), "the promotion reached too far down")
    }

    private companion object {
        /** About what one placement pays on a mid-size board. */
        const val PLACEMENT_POINTS = 200

        /** About what finishing a board pays, bonus included. */
        const val LEVEL_CLEAR_POINTS = 2400

        const val HUGE_POINTS = 50_000

        /** Above this a per-tap animation reads as the app being slow. */
        const val IMMEDIATE_CEILING = 500

        /** Above this the player is waiting on an animation rather than watching one. */
        const val HOLDS_THE_SCREEN = 2000
    }
}
