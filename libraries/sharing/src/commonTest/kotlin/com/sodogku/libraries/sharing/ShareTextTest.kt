package com.sodogku.libraries.sharing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ShareTextTest {

    /**
     * ```
     * 0 0 1 1
     * 0 2 2 1
     * 3 3 2 1
     * 3 3 2 1
     * ```
     */
    private val fourByFour = listOf(
        0, 0, 1, 1,
        0, 2, 2, 1,
        3, 3, 2, 1,
        3, 3, 2, 1,
    )

    private fun run(
        regions: List<Int> = fourByFour,
        size: Int = 4,
        timeMs: Long = 102_000,
        score: Int = 14_820,
        paws: Int = 3,
        bonesRemaining: Int = 2,
    ) = ShareResult(size, regions, timeMs, score, paws, bonesRemaining)

    private val labels = ShareLabels(
        title = "Sodogku Daily · Sep 8",
        streak = "🔥 12 day streak",
        footer = "sodogku.app",
    )

    @Test
    fun formatsTheWholeShare() {
        val expected = """
            Sodogku Daily · Sep 8
            ⏱ 1:42   🏆 14,820   🐾🐾🐾   🦴🦴
            🔥 12 day streak

            🟥🟥🟧🟧
            🟥🟨🟨🟧
            🟩🟩🟨🟧
            🟩🟩🟨🟧

            sodogku.app
        """.trimIndent()

        assertEquals(expected, ShareText.format(run(), labels))
    }

    @Test
    fun theStreakAndFooterLinesAreOptional() {
        val expected = """
            Sodogku · Level 12
            ⏱ 1:42   🏆 14,820   🐾🐾🐾   🦴🦴

            🟥🟥🟧🟧
            🟥🟨🟨🟧
            🟩🟩🟨🟧
            🟩🟩🟨🟧
        """.trimIndent()

        assertEquals(expected, ShareText.format(run(), ShareLabels(title = "Sodogku · Level 12")))
    }

    @Test
    fun everyCellOfARegionRendersTheSameSquare() {
        // The spoiler test. A share that marked where the dogs went — the
        // obvious thing to build, and what the board itself shows — would put a
        // different glyph on exactly one cell of each region.
        val grid = ShareText.grid(run()).lines().joinToString("").chunkedEmoji()

        fourByFour.indices.groupBy { fourByFour[it] }.forEach { (region, cells) ->
            assertEquals(
                1,
                cells.map { grid[it] }.toSet().size,
                "region $region does not render uniformly, so one of its cells is singled out",
            )
        }
    }

    @Test
    fun twoSolutionsOfTheSameBoardShareIdentically() {
        // The property the format exists to have. It is guaranteed by the
        // signature — `ShareResult` has nowhere to put a solution — so this
        // stands as the regression guard for the day somebody adds one "just
        // for the win sheet". Its falsifiable companion is the test above.
        val onePlacement = listOf(1, 3, 0, 2)
        val another = listOf(2, 0, 3, 1)

        assertEquals(
            shareFor(onePlacement),
            shareFor(another),
            "a share must read the same whichever dogs the board turned out to hold",
        )
    }

    private fun shareFor(solution: List<Int>): String {
        // What a win sheet has in hand when it builds a share. The solution is
        // deliberately not passed on; it is here to show what is being withheld.
        check(solution.size == 4) { "a 4x4 has one dog per row" }
        return ShareText.format(run(), labels)
    }

    @Test
    fun theGridChangesWithTheBoard_andNotWithTheRun() {
        val otherBoard = listOf(
            0, 1, 1, 1,
            0, 0, 2, 1,
            3, 0, 2, 2,
            3, 3, 3, 2,
        )

        assertEquals(
            ShareText.grid(run()),
            ShareText.grid(run(timeMs = 15_000, score = 900, paws = 1, bonesRemaining = 0)),
            "how the run went belongs on the stats line, not in the grid",
        )
        assertTrue(ShareText.grid(run()) != ShareText.grid(run(regions = otherBoard)))
    }

    @Test
    fun aTenRegionBoardGetsTenDistinctSquares() {
        // Unicode has nine coloured-or-neutral squares and the top band needs
        // ten regions, so the tenth is the framed square. If that ever regresses
        // to a repeat, two regions merge into one shape on the biggest boards.
        val regions = (0 until 100).map { it % 10 }
        val squares = ShareText.grid(ShareResult(10, regions, 60_000, 1, 3, 3))
            .lines()
            .first()
            .chunkedEmoji()

        assertEquals(10, squares.toSet().size)
    }

    @Test
    fun timeReadsAsMinutesAndSeconds_andGrowsAnHourFieldWhenItHasTo() {
        assertTrue(ShareText.format(run(timeMs = 9_000), labels).contains("⏱ 0:09"))
        assertTrue(ShareText.format(run(timeMs = 615_000), labels).contains("⏱ 10:15"))
        assertTrue(ShareText.format(run(timeMs = 3_723_000), labels).contains("⏱ 1:02:03"))
    }

    @Test
    fun scoreIsGroupedInThousands() {
        assertTrue(ShareText.format(run(score = 7), labels).contains("🏆 7"))
        assertTrue(ShareText.format(run(score = 999), labels).contains("🏆 999"))
        assertTrue(ShareText.format(run(score = 1_000), labels).contains("🏆 1,000"))
        assertTrue(ShareText.format(run(score = 1_234_567), labels).contains("🏆 1,234,567"))
    }

    @Test
    fun pawsAndBonesAreDrawnAsManyTimesAsTheyWereEarned() {
        val statsOf: (ShareResult) -> String = { ShareText.format(it, labels).lines()[1] }

        assertTrue(statsOf(run(paws = 1, bonesRemaining = 3)).endsWith("🐾   🦴🦴🦴"))
        assertTrue(statsOf(run(paws = 3, bonesRemaining = 1)).endsWith("🐾🐾🐾   🦴"))
    }

    @Test
    fun anImpossibleRatingCannotStretchTheLine() {
        // Bone holdings are uncapped, so this is a real input rather than a
        // defensive flourish: a player can finish a level holding nine of them.
        val stats = ShareText.format(run(paws = 9, bonesRemaining = 40), labels).lines()[1]

        assertTrue(stats.endsWith("🐾🐾🐾   🦴🦴🦴🦴🦴"), "was: $stats")
    }

    @Test
    fun aRegionLayoutThatIsNotTheBoardIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            ShareResult(size = 5, regions = fourByFour, timeMs = 1, score = 1, paws = 1, bonesRemaining = 1)
        }
    }
}

/**
 * Splits a run of emoji into one string per glyph. The squares are astral-plane
 * code points, so `String.toList()` would cut them in half on the JVM.
 */
private fun String.chunkedEmoji(): List<String> {
    val glyphs = mutableListOf<String>()
    var index = 0
    while (index < length) {
        val width = if (this[index].isHighSurrogate()) 2 else 1
        glyphs += substring(index, index + width)
        index += width
    }
    return glyphs
}
