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
 * Every assertion below is guarded by the count, so deleting the enum entries
 * fails the test rather than making it vacuously true over an empty list.
 */
class LeaderboardTest {

    @Test
    fun thereAreExactlyThreeBoards() {
        assertEquals(3, Leaderboard.entries.size)
    }

    @Test
    fun everyBoardHasAnId() {
        assertEquals(3, Leaderboard.entries.size)
        Leaderboard.entries.forEach { board ->
            assertTrue(board.id.isNotBlank(), "${board.name} has no leaderboard id")
        }
    }

    @Test
    fun noTwoBoardsShareAnId() {
        assertEquals(3, Leaderboard.entries.size)
        assertEquals(
            Leaderboard.entries.size,
            Leaderboard.entries.map { it.id }.toSet().size,
        )
    }

    @Test
    fun theWeeklyBoardIsNotTheLifetimeBoardUnderAnotherName() {
        // A copied id would file every weekly submission into the all-time
        // board, where it would be dropped as no improvement and leave the
        // weekly board empty. The test above catches the duplicate; this one
        // says which pair it would be, because that is the copy-paste available.
        assertTrue(Leaderboard.WeeklyScore.id != Leaderboard.LifetimeScore.id)
    }
}
