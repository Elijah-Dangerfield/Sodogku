package com.sodogku.libraries.ui.system.color

import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The cross is drawn arm by arm as the player makes it, and the staging is the
 * part worth pinning: an implementation that simply drew the whole cross would
 * look almost right in a screenshot and wrong in the hand.
 */
class BoardMarkTest {

    private val cell = Size(100f, 100f)

    @Test
    fun theCrossCoversSixtyPercentOfTheSquare() {
        assertEquals(60f, BoardMark.extent(cell), Tolerance)
    }

    @Test
    fun theSquaresShorterSideDecidesTheSize() {
        assertEquals(BoardMark.extent(Size(100f, 40f)), BoardMark.extent(Size(40f, 100f)), Tolerance)
        assertEquals(24f, BoardMark.extent(Size(100f, 40f)), Tolerance)
    }

    @Test
    fun theArmsAreThickEnoughToBeSeenAcrossAHundredSquares() {
        // Twelve percent of the square. Chosen against the reference art, and
        // asserted because "thick" is the entire point of the mark: a hairline
        // cross in white on a pastel is invisible, and nothing else here would
        // fail if the stroke quietly went back to a line.
        assertEquals(12f, BoardMark.strokeWidth(cell), Tolerance)
    }

    @Test
    fun nothingIsDrawnBeforeTheGestureStarts() {
        assertEquals(emptyList<MarkStroke>(), BoardMark.strokes(cell, progress = 0f))
    }

    @Test
    fun theFirstArmFinishesBeforeTheSecondBegins() {
        val justBefore = BoardMark.strokes(cell, progress = BoardMark.StrokeSplit - 0.01f)
        assertEquals(1, justBefore.size, "the second arm started early")

        val justAfter = BoardMark.strokes(cell, progress = BoardMark.StrokeSplit + 0.01f)
        assertEquals(2, justAfter.size, "the second arm never started")
    }

    @Test
    fun eachArmGrowsFromItsOwnCornerAsProgressRuns() {
        val quarter = BoardMark.strokes(cell, progress = BoardMark.StrokeSplit / 2f).single()
        val whole = BoardMark.strokes(cell, progress = 1f).first()

        assertEquals(whole.start, quarter.start, "the arm moved instead of growing")
        assertTrue(
            quarter.length() < whole.length() / 2f + Tolerance,
            "the arm was already ${quarter.length()} long at half of its own stage",
        )
    }

    @Test
    fun aFinishedCrossIsTwoFullDiagonalsThroughTheCentre() {
        val strokes = BoardMark.strokes(cell, progress = 1f)
        assertEquals(2, strokes.size)

        val diagonal = BoardMark.extent(cell) * sqrt(2f)
        strokes.forEach { assertEquals(diagonal, it.length(), Tolerance) }

        strokes.forEach { stroke ->
            val midX = (stroke.start.x + stroke.end.x) / 2f
            val midY = (stroke.start.y + stroke.end.y) / 2f
            assertEquals(cell.width / 2f, midX, Tolerance)
            assertEquals(cell.height / 2f, midY, Tolerance)
        }

        // Opposite diagonals, or the "cross" is two parallel slashes.
        val (first, second) = strokes
        assertTrue(
            first.slope() * second.slope() < 0f,
            "both arms run the same way; that is a slash, not a cross",
        )
    }

    @Test
    fun progressPastTheEndIsTheSameAsTheEnd() {
        assertEquals(BoardMark.strokes(cell, 1f), BoardMark.strokes(cell, 1.4f))
    }

    private fun MarkStroke.length(): Float {
        val dx = end.x - start.x
        val dy = end.y - start.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun MarkStroke.slope(): Float = (end.y - start.y) / (end.x - start.x)

    private fun assertEquals(expected: Float, actual: Float, tolerance: Float) {
        assertTrue(
            abs(expected - actual) <= tolerance,
            "expected $expected but was $actual",
        )
    }

    private companion object {
        const val Tolerance = 0.01f
    }
}
