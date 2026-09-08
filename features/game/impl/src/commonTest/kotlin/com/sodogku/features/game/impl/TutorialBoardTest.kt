package com.sodogku.features.game.impl

import com.sodogku.libraries.levels.LevelPacks
import com.sodogku.libraries.puzzle.PuzzleSolver
import com.sodogku.libraries.puzzle.Solution
import com.sodogku.libraries.puzzle.autoMarkedCells
import com.sodogku.libraries.puzzle.ruleViolations
import com.sodogku.libraries.puzzle.structuralProblems
import com.sodogku.libraries.progress.LevelRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The demo board is hand-authored, and hand-authored is exactly the property
 * that makes it worth checking with the shipped solver rather than by reading
 * it.
 *
 * A board with two answers would teach that a tap can be right and wrong at the
 * same time; a board with none would teach a rule the game does not have. Both
 * are invisible until a player meets them, because the tap mechanic answers from
 * the stored solution and never solves anything.
 *
 * `LevelPackVerificationTest` does this for the 500 generated levels. This board
 * is in no pack, so it needs its own.
 */
class TutorialBoardTest {

    private val level = TutorialBoard.level

    @Test
    fun theDemoBoardIsAWellFormedGrid() {
        assertEquals(TutorialBoard.SIZE, level.size)
        assertEquals(emptyList(), level.board.structuralProblems())
    }

    @Test
    fun theDemoBoardHasExactlyOneAnswer() {
        val solved = PuzzleSolver.uniqueSolutionOrNull(level.board)
        assertEquals(
            level.solution,
            assertNotNull(solved, "the demo board has no unique solution"),
            "the shipped answer is not the one the solver finds",
        )
    }

    @Test
    fun theShippedAnswerBreaksNoRule() {
        assertEquals(emptyList(), level.board.ruleViolations(level.solution))
    }

    @Test
    fun theDemoBoardIsNotACampaignLevel() {
        // The whole point of R3. If this id ever collided with a real level,
        // every guard in `GameViewModel` keyed on `rehearsing` would still hold,
        // but a stray `game.*` event would land on a level somebody plays.
        assertNull(LevelPacks.campaign.byId(TutorialBoard.LEVEL_ID))
        assertTrue(TutorialBoard.LEVEL_ID < LevelRecord.FIRST_LEVEL_ID)
        assertTrue(
            LevelPacks.campaign.levels.none { it.board == level.board },
            "the demo board is a copy of a campaign board",
        )
    }

    @Test
    fun everyLessonHasSomethingToPointAt() {
        // Walks the script over the board the way `TutorialRunner` does, and
        // asserts each step's squares exist. This is the check that would have
        // caught the first draft of the board, where three placements left the
        // deduction so tight that `TryAWrongOne` had no wrong square left to
        // light and the lesson silently disappeared.
        var placed = Solution.empty(level.size)
            .withPlacement(StarterRow, level.solution[StarterRow])
        var marks = level.board.autoMarkedCells(placed)
        var justMarked = emptySet<Int>()

        Tutorial.Script.forEach { step ->
            val cells = Tutorial.cellsFor(step, level, placed, marks, justMarked)
            if (Tutorial.triggerFor(step) != TutorialTrigger.Tap) {
                assertTrue(cells.isNotEmpty(), "$step asks for a gesture on nothing")
                val cell = cells.single()
                assertTrue(cell !in marks, "$step lit a square the board has crossed off")
                assertTrue(cell !in placed.cells().toSet(), "$step lit an occupied square")
            }
            when (Tutorial.triggerFor(step)) {
                TutorialTrigger.Placed -> {
                    val cell = cells.single()
                    assertTrue(isAnswer(cell), "$step asks for a placement on a wrong square")
                    val before = marks
                    placed = placed.withPlacement(
                        level.board.rowOf(cell),
                        level.board.colOf(cell),
                    )
                    marks = level.board.autoMarkedCells(placed)
                    justMarked = marks - before
                }
                TutorialTrigger.Struck ->
                    assertTrue(!isAnswer(cells.single()), "$step asks for a wrong guess on the answer")
                TutorialTrigger.Marked, TutorialTrigger.Tap -> Unit
            }
        }

        assertTrue(
            !placed.isComplete,
            "the script finishes the board, which would fire the win sheet over the last coach mark",
        )
    }

    @Test
    fun theStarterDogIsNotInACorner() {
        // A corner dog touches three squares, and three squares do not read as a
        // ring — which is the whole of the `RuleTouching` lesson. Off the corner
        // on the top row it touches five.
        val column = level.solution[StarterRow]
        assertTrue(column > 0 && column < level.size - 1, "the starter dog is in a corner")
        val starter = level.board.cellAt(StarterRow, column)
        assertEquals(EdgeRingSize, level.board.neighborsOf(starter).size)
    }

    @Test
    fun eachRuleLessonLightsSomethingDifferent() {
        // Three rules, three highlights. If two of them resolved to the same
        // squares the player would be shown the same picture twice and told it
        // meant two different things.
        val placed = Solution.empty(level.size)
            .withPlacement(StarterRow, level.solution[StarterRow])
        val marks = level.board.autoMarkedCells(placed)
        val lit = listOf(
            TutorialStep.RuleRegion,
            TutorialStep.RuleLine,
            TutorialStep.RuleTouching,
        ).map { Tutorial.cellsFor(it, level, placed, marks, emptySet()) }

        assertEquals(lit.size, lit.toSet().size, "two rule lessons light the same squares")
        lit.forEach { cells ->
            assertTrue(cells.isNotEmpty())
            // Every square a rule lesson points at is already crossed off, by
            // that rule, because of the dog in the middle of it. That is what
            // makes the highlight an explanation rather than a decoration.
            assertTrue(
                cells.all { it in marks || it in placed.cells().toSet() },
                "a rule lesson lit a square that rule did not rule out",
            )
        }
    }

    @Test
    fun theCurriculumForAPuristStillHasSomethingToPointAt() {
        // The same walk as `everyLessonHasSomethingToPointAt`, over the shorter
        // script and with no marks at all — because with auto-mark off nothing
        // is drawn, so `visibleMarks` is empty for the whole run. Dropping two
        // steps changes which squares the remaining ones resolve to, and the
        // failure of getting that wrong is the one that walk exists to catch: a
        // gated lesson with nothing to tap, which strands the player.
        val script = Tutorial.scriptFor(autoMark = false)
        assertTrue(
            script.size < Tutorial.Script.size,
            "the purist script is the full one, so this walks the same ground twice",
        )
        assertTrue(TutorialStep.Graduation in script, "the run has no ending")

        var placed = Solution.empty(level.size)
            .withPlacement(StarterRow, level.solution[StarterRow])
        var gated = 0

        script.forEach { step ->
            val cells = Tutorial.cellsFor(step, level, placed, emptySet(), emptySet())
            if (Tutorial.triggerFor(step) == TutorialTrigger.Tap) return@forEach
            gated++
            assertTrue(cells.isNotEmpty(), "$step asks for a gesture on nothing")
            val cell = cells.single()
            assertTrue(cell !in placed.cells().toSet(), "$step lit an occupied square")
            when (Tutorial.triggerFor(step)) {
                TutorialTrigger.Placed -> {
                    assertTrue(isAnswer(cell), "$step asks for a placement on a wrong square")
                    placed = placed.withPlacement(level.board.rowOf(cell), level.board.colOf(cell))
                }
                TutorialTrigger.Struck ->
                    assertTrue(!isAnswer(cell), "$step asks for a wrong guess on the answer")
                TutorialTrigger.Marked, TutorialTrigger.Tap -> Unit
            }
        }

        assertTrue(gated > 0, "no step was checked, so the walk asserts nothing")
        assertTrue(
            !placed.isComplete,
            "the script finishes the board, which would fire the win sheet over the last coach mark",
        )
    }

    private fun isAnswer(cell: Int): Boolean =
        level.board.colOf(cell) == level.solution[level.board.rowOf(cell)]

    private companion object {
        /** `GameViewModel.startAttempt` always gives the free dog to row 0. */
        const val StarterRow = 0

        /** Eight neighbours, less the three that fall off the top edge. */
        const val EdgeRingSize = 5
    }
}
