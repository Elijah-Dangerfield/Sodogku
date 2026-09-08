package com.sodogku.libraries.sharing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two shares the win sheet actually produces.
 *
 * `ShareTextTest` pins the formatter. This pins the *contract with the game*:
 * which of the optional lines each mode passes, and what a real 7x7 clear comes
 * out looking like. The win sheet builds a [ShareResult] out of the board it
 * just finished and a [ShareLabels] out of resources it resolved, so these two
 * calls are the whole of what it does.
 */
class WinSheetShareTest {

    /**
     * A real region layout: seven regions on a 7x7, the shape the generator
     * produces for a mid-campaign board.
     *
     * ```
     * 0 0 0 1 1 1 1
     * 0 0 2 2 1 1 3
     * 4 0 2 2 2 3 3
     * 4 4 4 2 5 5 3
     * 4 4 6 5 5 5 3
     * 6 6 6 6 5 3 3
     * 6 6 6 6 6 6 3
     * ```
     */
    private val sevenBySeven = listOf(
        0, 0, 0, 1, 1, 1, 1,
        0, 0, 2, 2, 1, 1, 3,
        4, 0, 2, 2, 2, 3, 3,
        4, 4, 4, 2, 5, 5, 3,
        4, 4, 6, 5, 5, 5, 3,
        6, 6, 6, 6, 5, 3, 3,
        6, 6, 6, 6, 6, 6, 3,
    )

    /** What `GameState` holds at the moment the win sheet appears. */
    private val clear = ShareResult(
        size = 7,
        regions = sevenBySeven,
        timeMs = 148_000,
        score = 18_430,
        paws = 3,
        bonesRemaining = 2,
    )

    @Test
    fun aCampaignClearSharesWithNoStreakLine() {
        // There is no streak on a campaign level, and "🔥 0 day streak" under
        // one is worse than nothing at all.
        val share = ShareText.format(
            clear,
            ShareLabels(title = "Sodogku · Level 137", footer = "sodogku.app"),
        )

        val expected = """
            Sodogku · Level 137
            ⏱ 2:28   🏆 18,430   🐾🐾🐾   🦴🦴

            🟥🟥🟥🟧🟧🟧🟧
            🟥🟥🟨🟨🟧🟧🟩
            🟦🟥🟨🟨🟨🟩🟩
            🟦🟦🟦🟨🟪🟪🟩
            🟦🟦🟫🟪🟪🟪🟩
            🟫🟫🟫🟫🟪🟩🟩
            🟫🟫🟫🟫🟫🟫🟩

            sodogku.app
        """.trimIndent()

        assertEquals(expected, share)
        assertFalse(share.contains("streak"))
    }

    @Test
    fun theDailyIsTheOneThatCarriesTheStreak() {
        val share = ShareText.format(
            clear,
            ShareLabels(
                title = "Sodogku Daily · Sep 8",
                streak = "🔥 12 day streak",
                footer = "sodogku.app",
            ),
        )

        assertEquals("Sodogku Daily · Sep 8", share.lines().first())
        assertEquals("🔥 12 day streak", share.lines()[2])
        assertTrue(share.trimEnd().endsWith("sodogku.app"))
    }

    @Test
    fun bothModesShareTheSameGridForTheSameBoard() {
        // The grid is a property of the board, not of who played it or how.
        // Two players on the same daily produce byte-identical squares.
        val campaign = ShareText.format(clear, ShareLabels(title = "Sodogku · Level 137"))
        val daily = ShareText.format(
            clear,
            ShareLabels(title = "Sodogku Daily · Sep 8", streak = "🔥 1 day streak"),
        )

        assertEquals(gridOf(campaign), gridOf(daily))
    }

    /** The square rows: everything between the first blank line and the next. */
    private fun gridOf(share: String): List<String> =
        share.lines().dropWhile { it.isNotEmpty() }.drop(1).takeWhile { it.isNotEmpty() }
}
