package com.sodogku.libraries.leaderboards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The board ids, which nothing else can check.
 *
 * They are typed by hand into App Store Connect and matched by string, so the
 * two mistakes available are a blank and a duplicate. Both are silent: a blank
 * id is a submission the platform rejects, and a shared id quietly merges two
 * boards into one that ranks scores against streaks. Neither shows up as a
 * failure anywhere else, which is the whole reason this file exists.
 *
 * The Play ids are held to a different standard, because empty is their correct
 * value today. The Play Console mints an opaque id when a board is created, so
 * an id can only be pasted in after the fact, and one of the three boards will
 * never have one at all. What is worth pinning is that a blank one never becomes
 * a duplicate, and that the weekly board stays out of Play entirely.
 *
 * Every assertion below is guarded by the count, so deleting the enum entries
 * fails the test rather than making it vacuously true over an empty list.
 */
class LeaderboardTest {

    @Test
    fun thereAreExactlyThreeBoards() {
        assertEquals(3, Leaderboard.entries.size)
    }

    @Test
    fun everyBoardHasAGameCenterId() {
        assertEquals(3, Leaderboard.entries.size)
        Leaderboard.entries.forEach { board ->
            assertTrue(board.appleId.isNotBlank(), "${board.name} has no Game Center id")
        }
    }

    @Test
    fun noTwoBoardsShareAGameCenterId() {
        assertEquals(3, Leaderboard.entries.size)
        assertEquals(
            Leaderboard.entries.size,
            Leaderboard.entries.map { it.appleId }.toSet().size,
        )
    }

    @Test
    fun theWeeklyBoardIsNotTheLifetimeBoardUnderAnotherName() {
        // A copied id would file every weekly submission into the all-time
        // board, where it would be dropped as no improvement and leave the
        // weekly board empty. The test above catches the duplicate; this one
        // says which pair it would be, because that is the copy-paste available.
        assertTrue(Leaderboard.WeeklyScore.appleId != Leaderboard.LifetimeScore.appleId)
    }

    @Test
    fun noTwoBoardsShareAPlayId() {
        // Blanks are excluded rather than counted, because more than one board
        // is legitimately blank. Two boards holding the same real id is the
        // paste error this catches, and on Play it is worse than on Game Center:
        // the ids are opaque, so nobody reading the file would spot it.
        val minted = Leaderboard.entries.mapNotNull { it.playId.ifBlank { null } }

        assertEquals(minted.size, minted.toSet().size, "two boards share a Play Games id")
    }

    @Test
    fun theWeeklyBoardHasNoPlayIdAndIsNotSupposedTo() {
        // Not a placeholder waiting to be filled in. Play Games has no recurring
        // board; it slices one board into daily, weekly and all-time views by
        // submission time, so the Android weekly standing is already a tab on
        // the lifetime board. A second board here would halve the room and rank
        // the same people twice.
        assertTrue(
            Leaderboard.WeeklyScore.playId.isBlank(),
            "Play Games has no recurring board for this to be",
        )
    }
}
