package com.sodogku.features.game.impl

import com.sodogku.libraries.levels.LevelDefinition
import com.sodogku.libraries.puzzle.Solution
import com.sodogku.system.Motion

/**
 * One lesson in the guided run over [TutorialBoard].
 *
 * The enum is declared in the order of [Tutorial.Script], so a reader can see
 * the whole curriculum at once.
 */
enum class TutorialStep {
    // The free dog, and then the three rules read off the board around it.
    StarterDog,
    RuleRegion,
    RuleLine,
    RuleTouching,

    // The two gestures, and what a wrong one costs.
    MarkSquare,
    PlaceDog,
    Bones,

    // Auto-mark firing from the player's own placement, then the boosters.
    PlaceAndWatch,
    AutoMark,
    Sniff,
    Treat,

    // One wrong guess that costs nothing, and the sign-off.
    TryAWrongOne,
    WrongExplained,
    Graduation,
}

/**
 * What moves a step along.
 *
 * [Tap] is the safe one: the scrim dismisses on a tap anywhere, so the step can
 * never trap the player. Every other trigger needs a real gesture on a lit
 * square, which is why [Tutorial.cellsFor] returning nothing for one of them is
 * treated as a step to skip rather than a step to show.
 */
enum class TutorialTrigger { Tap, Marked, Placed, Struck }

/**
 * The curriculum, as pure functions over a level and the board so far.
 *
 * Nothing here reads a clock, a cache or a ViewModel field, which is what lets
 * the "no step can strand the player" property be checked against the real
 * board rather than against a fixture.
 */
object Tutorial {

    /**
     * The whole lesson, on one board, in order.
     *
     * It used to be three scripts over three campaign levels. Merging them
     * dropped a step: `NoTouching` lit the ring around a dog on level 3, which
     * is exactly what [TutorialStep.RuleTouching] now does on the same board,
     * and teaching it twice was padding rather than reinforcement.
     */
    val Script: List<TutorialStep> = listOf(
        TutorialStep.StarterDog,
        TutorialStep.RuleRegion,
        TutorialStep.RuleLine,
        TutorialStep.RuleTouching,
        TutorialStep.MarkSquare,
        TutorialStep.PlaceDog,
        TutorialStep.Bones,
        TutorialStep.PlaceAndWatch,
        TutorialStep.AutoMark,
        TutorialStep.Sniff,
        TutorialStep.Treat,
        TutorialStep.TryAWrongOne,
        TutorialStep.WrongExplained,
        TutorialStep.Graduation,
    )

    /**
     * The curriculum for a player whose auto-mark setting is [autoMark].
     *
     * Two steps go when it is off, and both have to. [TutorialStep.AutoMark] is
     * a card saying "every square that dog rules out was crossed off for you"
     * pointed at squares that were not, and it would still show: its trigger is
     * `Tap`, so [TutorialRunner.frameFor] does not skip it for having nothing to
     * light. [TutorialStep.PlaceAndWatch] exists only to set that up — "then
     * watch what the board does", on a board that is about to do nothing —
     * and placing a dog was already taught by [TutorialStep.PlaceDog].
     *
     * A filter rather than a second list. The order and the copy are the same
     * lesson either way, and two scripts would drift.
     */
    fun scriptFor(autoMark: Boolean): List<TutorialStep> =
        if (autoMark) Script else Script - TaughtByAutoMark

    /** The lessons that have nothing to teach with auto-mark switched off. */
    private val TaughtByAutoMark = setOf(TutorialStep.PlaceAndWatch, TutorialStep.AutoMark)

    fun triggerFor(step: TutorialStep): TutorialTrigger = when (step) {
        TutorialStep.MarkSquare -> TutorialTrigger.Marked
        TutorialStep.PlaceDog, TutorialStep.PlaceAndWatch -> TutorialTrigger.Placed
        TutorialStep.TryAWrongOne -> TutorialTrigger.Struck
        else -> TutorialTrigger.Tap
    }

    /**
     * How long a step holds still after the player has done what it asked, so
     * the mark they just made finishes drawing before the spotlight moves.
     *
     * Without this the coach mark advanced in the same frame as the tap: the
     * hole in the scrim jumped to the next square while the cross was two
     * strokes in, so the one thing the lesson had just asked for was the one
     * thing the player never saw. The numbers are the design system's own —
     * they are how long the thing being waited on actually takes.
     */
    fun settleMillis(trigger: TutorialTrigger): Long = when (trigger) {
        TutorialTrigger.Tap -> 0L
        TutorialTrigger.Marked -> Motion.MarkDrawMillis.toLong()
        TutorialTrigger.Placed -> Motion.PlacementPulseMillis.toLong()
        TutorialTrigger.Struck -> Motion.ShakeMillis.toLong()
    }

    /**
     * The board squares a step points at, given the board as it stands *now*.
     *
     * Recomputed on every advance rather than baked into the script, because a
     * lesson that names a square the player already crossed off is a lesson
     * with nothing to tap.
     *
     * The three rule steps light the squares that rule ruled out rather than
     * the chip that names it. A chip is a picture of the rule; the column with
     * a dog at the top of it and four crosses under it *is* the rule, on the
     * board the player is looking at.
     *
     * [justMarked] is the auto-marks that appeared with the placement that got
     * us here, and only [TutorialStep.AutoMark] uses it — it is the whole point
     * of that step.
     *
     * [visibleMarks] is `GameState.visibleAutoMarks` and not the deduction behind
     * it. Every use of it here is about what the square *looks* like: a lesson
     * must not light a square that already reads as crossed off, and with
     * auto-mark switched off none of them do.
     */
    fun cellsFor(
        step: TutorialStep,
        level: LevelDefinition,
        placed: Solution,
        visibleMarks: Set<Int>,
        justMarked: Set<Int>,
    ): Set<Int> = when (step) {
        TutorialStep.StarterDog -> setOfNotNull(starterCell(placed))
        TutorialStep.RuleRegion -> starterCell(placed)
            ?.let { level.board.cellsInRegion(level.board.regionAt(it)).toSet() }
            .orEmpty()
        TutorialStep.RuleLine -> starterCell(placed)?.let { cell ->
            val row = level.board.rowOf(cell)
            val col = level.board.colOf(cell)
            (0 until level.size).flatMap {
                listOf(level.board.cellAt(row, it), level.board.cellAt(it, col))
            }.toSet()
        }.orEmpty()
        TutorialStep.RuleTouching -> starterCell(placed)
            ?.let { level.board.neighborsOf(it).toSet() }
            .orEmpty()
        TutorialStep.MarkSquare,
        TutorialStep.TryAWrongOne,
        -> setOfNotNull(freeWrongCell(level, placed, visibleMarks))
        TutorialStep.PlaceDog,
        TutorialStep.PlaceAndWatch,
        -> setOfNotNull(nextCorrectCell(level, placed, visibleMarks))
        TutorialStep.AutoMark -> justMarked
        else -> emptySet()
    }

    /**
     * The dog the board opened with, which is what the three rule lessons are
     * read off.
     *
     * The first placement by row order, not a constant: it is the free dog on
     * row 0, and every rule step runs before the player has placed anything of
     * their own. Null on a board that opened empty, which lights nothing — safe,
     * because all four steps that ask for it dismiss on a tap anywhere.
     */
    private fun starterCell(placed: Solution): Int? = placed.cells().firstOrNull()

    /**
     * A square with no dog in it that the player can still act on.
     *
     * Squares that already show a cross are excluded: both steps that ask for
     * one ask the player to *make a mark there*, and lighting a square that is
     * already marked is an instruction with nothing to do.
     */
    private fun freeWrongCell(
        level: LevelDefinition,
        placed: Solution,
        visibleMarks: Set<Int>,
    ): Int? = (0 until level.board.cellCount).firstOrNull { cell ->
        cell !in visibleMarks &&
            cell !in placed.cells().toSet() &&
            level.board.colOf(cell) != level.solution[level.board.rowOf(cell)]
    }

    /** The answer's square for the lowest row that still has no dog. */
    private fun nextCorrectCell(
        level: LevelDefinition,
        placed: Solution,
        visibleMarks: Set<Int>,
    ): Int? = (0 until level.size)
        .firstOrNull { row -> placed[row] == Solution.UNPLACED }
        ?.let { row -> level.board.cellAt(row, level.solution[row]) }
        ?.takeIf { it !in visibleMarks }
}
