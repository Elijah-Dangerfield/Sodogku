package com.sodogku.features.game.impl

import com.sodogku.libraries.ui.components.celebration.beatDelayMillis
import com.sodogku.libraries.scoring.PawGap
import com.sodogku.libraries.scoring.Scoring
import com.sodogku.libraries.scoring.Standing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a clear is allowed to say about itself, and in what order.
 *
 * [winBeats] is the win celebration's script: the list is both the content and
 * the sequence, because a beat's position in it is the delay it waits before it
 * lands. So the interesting cases are the three optional lines, each of which is
 * a refusal that is invisible from the code drawing it — the same reason
 * [lossFacts] is a function and not a run of `if`s inside a composable. The
 * line under the verdict is the same kind of decision and is pinned beside it.
 *
 * Deliberately not covered here: that the beats actually *arrive* one after the
 * other, and that they hold still when the player has asked for fewer
 * animations. Both are claims about a composition. The paw rating's half of that
 * lives in `PawRatingHoldsStillTest` in `:libraries:ui`, which is the module set
 * up for that tier; the panel's own entrance is the same rule written the same
 * way beside it, and is read rather than asserted.
 */
class WinBeatsTest {

    @Test
    fun everyClearSaysTheSameFiveThingsInTheSameOrder() {
        // The floor, and it comes first: every refusal below is "this beat is
        // absent", which is also what a script that had stopped being built at
        // all would report.
        assertEquals(
            listOf(
                WinBeat.Hero,
                WinBeat.Verdict,
                WinBeat.Sub,
                WinBeat.Paws,
                WinBeat.Chips,
            ),
            winBeats(wonState()),
            "a plain clear changed what it says, or the order it says it in",
        )
    }

    @Test
    fun aRunWithAPawLeftToEarnGetsTheFootnote() {
        val beats = winBeats(wonState(pawGap = PawGap(nextPaw = 4, pointsShort = 22)))

        assertTrue(WinBeat.Footnote in beats)
        // After the facts, not among them. The gap is an aside about the run,
        // and reading it before the score would make the score read as the
        // consolation.
        assertTrue(beats.indexOf(WinBeat.Footnote) > beats.indexOf(WinBeat.Chips))
    }

    @Test
    fun aFivePawRunHasNoFootnote() {
        // The gap is null at five paws and nowhere else; the script reads the
        // gap and not the count, so this is the five-paw case as the state
        // actually carries it.
        assertFalse(WinBeat.Footnote in winBeats(wonState(paws = Scoring.MAX_PAWS, pawGap = null)))
    }

    @Test
    fun aTreatIsAnnouncedBetweenTheFootnoteAndTheButton() {
        val beats = winBeats(
            wonState(treatAwarded = true, pawGap = PawGap(nextPaw = 4, pointsShort = 22)),
        )

        assertEquals(WinBeat.Treat, beats.last(), "the treat chip is not the last thing before the button")
        assertTrue(beats.indexOf(WinBeat.Treat) > beats.indexOf(WinBeat.Footnote))
        assertFalse(WinBeat.Treat in winBeats(wonState(treatAwarded = false)))
    }

    @Test
    fun onlyTheDailyReportsAStreak() {
        // The number is on the state for every board, because the drawer's flame
        // reads it too. A campaign clear mentioning it would be answering a
        // question nobody asked on that screen.
        assertTrue(WinBeat.Streak in winBeats(wonState(isDaily = true, dailyStreak = 12)))
        assertFalse(WinBeat.Streak in winBeats(wonState(isDaily = false, dailyStreak = 12)))
    }

    @Test
    fun aFirstDailyWithNoRunBehindItSaysNothingAboutAStreak() {
        assertFalse(WinBeat.Streak in winBeats(wonState(isDaily = true, dailyStreak = 0)))
    }

    @Test
    fun theStreakSitsDirectlyUnderTheChips() {
        // Not on the design, which has no daily. Under the chips and above the
        // footnote, so the design's own lines keep their places and the extra
        // one reads as part of the facts block rather than as a second footnote.
        val beats = winBeats(
            wonState(isDaily = true, dailyStreak = 12, pawGap = PawGap(nextPaw = 3, pointsShort = 40)),
        )

        assertEquals(beats.indexOf(WinBeat.Chips) + 1, beats.indexOf(WinBeat.Streak))
        assertTrue(beats.indexOf(WinBeat.Footnote) > beats.indexOf(WinBeat.Streak))
    }

    @Test
    fun theOptionalLinesKeepTheirOrderWhenAllThreeLand() {
        val beats = winBeats(
            wonState(
                pawGap = PawGap(nextPaw = 3, pointsShort = 40),
                treatAwarded = true,
                isDaily = true,
                dailyStreak = 12,
            ),
        )

        assertEquals(
            listOf(WinBeat.Streak, WinBeat.Footnote, WinBeat.Treat),
            beats.drop(FixedBeats),
            "the order the extras arrive in moved, which also moves when each one lands",
        )
    }

    @Test
    fun aRunThatCameInUnderTheOldBestSaysSoOnTheSubLine() {
        // Folded into the sentence under the verdict rather than a beat of its
        // own, so the script is the same length either way and only the words
        // change.
        assertEquals(WinSub.LevelBest, winSub(wonState(elapsedMs = 78_000, targetTimeMs = 90_000)))
    }

    @Test
    fun aFirstClearClaimsNoRecordBecauseThereWasNoneToBeat() {
        // A zero target is "never cleared", not "cleared instantly".
        assertEquals(WinSub.Level, winSub(wonState(elapsedMs = 78_000, targetTimeMs = 0)))
    }

    @Test
    fun aRunThatMissedTheOldBestSaysNothingAboutTheClock() {
        // The clock under the board already said "Over 1:30" while this was
        // happening. Repeating it on a clear turns it into a telling-off.
        assertEquals(WinSub.Level, winSub(wonState(elapsedMs = 96_000, targetTimeMs = 90_000)))
    }

    @Test
    fun theDailyNeverClaimsABestRun() {
        // A daily has no target today, so this is the precedence pinned rather
        // than a case the view model produces: if a daily ever grows a clock to
        // chase, its sub still has no level number to put in front of it.
        assertEquals(WinSub.Daily, winSub(wonState(isDaily = true, elapsedMs = 78_000, targetTimeMs = 90_000)))
    }

    @Test
    fun eachRungAboveTheFirstIsNamedByItsOwnOrdinal() {
        // By key, because two `Res.string.x` reads are two objects. The key
        // carries the resource name, which is the one place the word "fourth"
        // can be read from outside a composition.
        val expected = mapOf(
            Scoring.TWO_PAWS to "second",
            Scoring.THREE_PAWS to "third",
            Scoring.FOUR_PAWS to "fourth",
            Scoring.FIVE_PAWS to "fifth",
        )

        expected.forEach { (paw, word) ->
            assertTrue(
                pawOrdinal(paw).key.endsWith(word),
                "paw $paw is named by ${pawOrdinal(paw).key}, not by the $word ordinal",
            )
        }
    }

    @Test
    fun eachBeatWaitsLongerThanTheOneBeforeIt() {
        // The script's index is its delay, so a stagger of zero would build the
        // right list and play it as a single flash.
        assertTrue(beatDelayMillis(order = 1) > beatDelayMillis(order = 0))
        assertTrue(beatDelayMillis(order = 0) > 0)
    }

    private fun wonState(
        paws: Int = 3,
        pawGap: PawGap? = null,
        elapsedMs: Long = 0,
        targetTimeMs: Long = 0,
        treatAwarded: Boolean = false,
        isDaily: Boolean = false,
        dailyStreak: Int = 0,
    ) = GameState(
        // `beatBestTime` is gated on the phase as well as on the clock, so a
        // state that is not Won claims no record however the times read.
        phase = GamePhase.Won,
        paws = paws,
        standing = Standing.Flawless,
        pawGap = pawGap,
        elapsedMs = elapsedMs,
        targetTimeMs = targetTimeMs,
        treatAwarded = treatAwarded,
        isDaily = isDaily,
        dailyStreak = dailyStreak,
    )

    private companion object {
        /** Hero, verdict, sub, paws, chips: the five every clear carries. */
        const val FixedBeats = 5
    }
}
