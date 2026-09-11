package com.sodogku.features.game.impl

import com.sodogku.libraries.levels.LevelDefinition
import com.sodogku.libraries.puzzle.Board
import com.sodogku.libraries.puzzle.Solution
import com.sodogku.libraries.ui.components.game.RuleDiagram
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * When the rule chips flash, as opposed to which one lights up.
 *
 * `brokenRule` answers *which*, and it answers the same thing twice when the
 * player breaks the same rule twice running — which is exactly the case the
 * chip used to sit out, because its flash was keyed on that answer alone.
 */
class RuleChipFlashKeyTest {

    // The 4x4 from BrokenRuleTest: region A is the four corners, region B is
    // everything else. Cell 10 touches the dog on 15 corner to corner and
    // shares nothing else with it, so it breaks adjacency and only adjacency.
    private val board = Board.parse("ABBABBBBBBBBABBA")

    private fun state(strike: Int, nonce: Int) = GameState(
        level = LevelDefinition(
            id = 1,
            board = board,
            solution = Solution.empty(board.size),
            difficulty = 1,
        ),
        placed = Solution(IntArray(board.size) { row -> if (row == 3) 3 else Solution.UNPLACED }),
        strikeCell = strike,
        strikeNonce = nonce,
    )

    @Test
    fun aSecondStrikeAgainstTheSameRuleChangesTheKey() {
        val first = state(strike = 10, nonce = 1)
        val again = state(strike = 10, nonce = 2)

        assertEquals(
            brokenRule(first),
            brokenRule(again),
            "The premise: the same rule, so the boolean the chip reads never moves",
        )
        assertNotEquals(
            ruleChipFlashKey(brokenRule(first), first.strikeNonce),
            ruleChipFlashKey(brokenRule(again), again.strikeNonce),
            "A second wrong guess against the same rule has to re-flash its chip",
        )
    }

    @Test
    fun theKeyIsTheStrikeNonceWhileARuleIsBroken() {
        assertEquals(4, ruleChipFlashKey(RuleDiagram.OnePerLine, strikeNonce = 4))
    }

    @Test
    fun aStrikeThatBreaksNoRuleLeavesTheKeyAlone() {
        // Three effects restarting to animate nothing is not free, and the
        // chips have nothing to say about a square that broke none of them.
        assertEquals(0, ruleChipFlashKey(broken = null, strikeNonce = 7))
    }
}
