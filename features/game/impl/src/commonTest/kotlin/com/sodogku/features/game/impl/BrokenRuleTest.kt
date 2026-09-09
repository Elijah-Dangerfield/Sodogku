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

    /**
     * A second 4x4, shaped so two dogs can conflict with the same square from
     * the same distance by different rules — which is the only situation the
     * rule ordering actually decides, and one the board above cannot produce.
     *
     *   B B B B      0  1  2  3
     *   B B B B      4  5  6  7
     *   B B A B      8  9 10 11
     *   A B B B     12 13 14 15
     *
     * Against a strike on 12: the dog on 4 is two squares up its column, and the
     * dog on 10 is two squares away sharing region A. Neither is touching and
     * both are equally near.
     */
    private val tieBoard = Board.parse("BBBBBBBBBBABABBB")

    private fun state(strike: Int?, placed: Set<Int>, on: Board = board) = GameState(
        level = LevelDefinition(
            id = 1,
            board = on,
            solution = Solution.empty(on.size),
            difficulty = 1,
        ),
        placed = Solution(IntArray(on.size) { row ->
            placed.firstOrNull { it / on.size == row }?.rem(on.size) ?: Solution.UNPLACED
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
    fun theNearestDogDecidesEvenWhenAFurtherOneBreaksATighterRule() {
        // Cell 12 is a corner, so region A. Two dogs object to it: cell 3, the
        // opposite corner, on the colour rule from three squares away; and cell
        // 4, two squares up its own column, on the line rule.
        //
        // Asking each rule in turn whether *any* dog broke it answered "colour",
        // pointing at the far corner. The player is looking at the square they
        // tapped, and the dog two above it is what explains it.
        assertEquals(
            RuleDiagram.OnePerLine,
            brokenRule(state(strike = 12, placed = setOf(3, 4))),
        )
    }

    @Test
    fun twoDogsEquallyNearAreSplitByWhichRuleIsTighter() {
        // Nothing about distance can choose between these two, so the ordering
        // is the whole answer — and without one it would fall to whatever order
        // the placements happen to iterate in, which is the arbitrariness this
        // is here to rule out. The colour blob is a bounded area the player can
        // take in; the column runs the height of the board.
        assertEquals(
            RuleDiagram.OnePerRegion,
            brokenRule(state(strike = 12, placed = setOf(4, 10), on = tieBoard)),
        )
    }

    @Test
    fun oneDogBreakingTwoRulesNamesTheTighterOne() {
        // Cell 9 shares both a column and a region with cell 1, two rows clear
        // of it. The colour blob is a bounded area; the column runs the height
        // of the board.
        assertEquals(
            RuleDiagram.OnePerRegion,
            brokenRule(state(strike = 1, placed = setOf(9))),
        )
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
