package com.sodogku.features.game.impl

import com.sodogku.libraries.levels.LevelDefinition
import com.sodogku.libraries.puzzle.Board
import com.sodogku.libraries.puzzle.Solution
import com.sodogku.libraries.ui.components.game.RuleDiagram
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which rule chip the board outlines after a wrong guess.
 *
 * The interesting cases are the ones where the answer is *nothing*: a chip
 * pointing at the wrong rule teaches a player something untrue about a game
 * they are already finding hard.
 */
class BrokenRuleTest {

    // 4x4. Region A is the four corners, region B is everything else — an
    // awkward partition on purpose. With compact regions every square that
    // shares a region also touches its neighbours, so there is no board shape
    // that can tell the colour rule and the touching rule apart.
    //
    //   A B B A      0  1  2  3
    //   B B B B      4  5  6  7
    //   B B B B      8  9 10 11
    //   A B B A     12 13 14 15
    //
    // The dog sits on 15 throughout.
    private val board = Board.parse("ABBABBBBBBBBABBA")

    private val dog = 15

    private fun state(strike: Int?, placed: Set<Int>) = GameState(
        level = LevelDefinition(
            id = 1,
            board = board,
            solution = Solution.empty(board.size),
            difficulty = 1,
        ),
        placed = Solution(IntArray(board.size) { row ->
            placed.firstOrNull { it / board.size == row }?.rem(board.size) ?: Solution.UNPLACED
        }),
        strikeCell = strike,
        strikeNonce = if (strike == null) 0 else 1,
    )

    @Test
    fun aDiagonalNeighbourNamesTheTouchingRule() {
        // Cell 10 touches the dog corner to corner and shares nothing else with
        // it — not its row, not its column, not its region. Adjacency is the
        // only rule it breaks, which is what makes it the case that proves the
        // check runs at all.
        assertEquals(RuleDiagram.NoTouching, brokenRule(state(strike = 10, placed = setOf(dog))))
    }

    @Test
    fun anOrthogonalNeighbourAlsoNamesTheTouchingRule() {
        // Cell 11 is directly above the dog, so it breaks the column rule too.
        // Adjacency is the subtler fact and the one worth surfacing.
        assertEquals(RuleDiagram.NoTouching, brokenRule(state(strike = 11, placed = setOf(dog))))
    }

    @Test
    fun aSecondSquareInTheSameRegionNamesTheColourRule() {
        // The opposite corner: region A again, but three rows and three columns
        // away, so nothing else objects to it.
        assertEquals(RuleDiagram.OnePerRegion, brokenRule(state(strike = 0, placed = setOf(dog))))
    }

    @Test
    fun aDistantSquareInTheSameColumnNamesTheLineRule() {
        // Cell 7: the dog's column, two rows clear of it, region B.
        assertEquals(RuleDiagram.OnePerLine, brokenRule(state(strike = 7, placed = setOf(dog))))
    }

    @Test
    fun aSquareThatBreaksNothingVisibleNamesNoRule() {
        // Cell 4 shares no line, no region and no edge with the dog. It is
        // simply not where a dog goes — and nothing on screen says so yet.
        assertNull(brokenRule(state(strike = 4, placed = setOf(dog))))
    }

    @Test
    fun theFirstGuessOfAnEmptyBoardNamesNoRule() {
        assertNull(brokenRule(state(strike = 5, placed = emptySet())))
    }

    @Test
    fun noStrikeNamesNoRule() {
        assertNull(brokenRule(state(strike = null, placed = setOf(dog))))
    }
}
