package com.sodogku.features.game.impl

import com.sodogku.libraries.levels.LevelDefinition
import com.sodogku.libraries.puzzle.Solution

/**
 * One lesson in the guided run over campaign levels 1 to 3.
 *
 * The order inside a level is the order of [Tutorial.scriptFor]; the enum is
 * declared in that order so a reader can see the whole curriculum at once.
 */
enum class TutorialStep {
    // Level 1 — the three rules, then the two gestures.
    RuleRegion,
    RuleLine,
    RuleTouching,
    StarterDog,
    MarkSquare,
    PlaceDog,
    Bones,

    // Level 2 — auto-mark firing from the player's own placement, then the boosters.
    PlaceAndWatch,
    AutoMark,
    Sniff,
    Treat,

    // Level 3 — adjacency, and one wrong guess that costs nothing.
    NoTouching,
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
 * shipped levels rather than against a fixture.
 */
object Tutorial {

    /** The last guided level. Clearing its script is what ends the tutorial. */
    const val LAST_LEVEL: Int = 3

    /**
     * The level whose first wrong guess is free.
     *
     * SPEC 10: level 3 "allows one wrong tap with no life charged", because the
     * step that teaches what a wrong guess looks like has to ask for one.
     */
    const val FREE_MISTAKE_LEVEL: Int = LAST_LEVEL

    fun scriptFor(levelId: Int): List<TutorialStep> = when (levelId) {
        1 -> listOf(
            TutorialStep.RuleRegion,
            TutorialStep.RuleLine,
            TutorialStep.RuleTouching,
            TutorialStep.StarterDog,
            TutorialStep.MarkSquare,
            TutorialStep.PlaceDog,
            TutorialStep.Bones,
        )
        2 -> listOf(
            TutorialStep.PlaceAndWatch,
            TutorialStep.AutoMark,
            TutorialStep.Sniff,
            TutorialStep.Treat,
        )
        LAST_LEVEL -> listOf(
            TutorialStep.NoTouching,
            TutorialStep.TryAWrongOne,
            TutorialStep.WrongExplained,
            TutorialStep.Graduation,
        )
        else -> emptyList()
    }

    fun triggerFor(step: TutorialStep): TutorialTrigger = when (step) {
        TutorialStep.MarkSquare -> TutorialTrigger.Marked
        TutorialStep.PlaceDog, TutorialStep.PlaceAndWatch -> TutorialTrigger.Placed
        TutorialStep.TryAWrongOne -> TutorialTrigger.Struck
        else -> TutorialTrigger.Tap
    }

    /**
     * The board squares a step points at, given the board as it stands *now*.
     *
     * Recomputed on every advance rather than baked into the script, because a
     * lesson that names a square the player already crossed off is a lesson
     * with nothing to tap.
     *
     * [justMarked] is the auto-marks that appeared with the placement that got
     * us here, and only [TutorialStep.AutoMark] uses it — it is the whole point
     * of that step.
     */
    fun cellsFor(
        step: TutorialStep,
        level: LevelDefinition,
        placed: Solution,
        autoMarks: Set<Int>,
        justMarked: Set<Int>,
    ): Set<Int> = when (step) {
        TutorialStep.StarterDog -> placed.cells().toSet()
        TutorialStep.MarkSquare,
        TutorialStep.TryAWrongOne,
        -> setOfNotNull(freeWrongCell(level, placed, autoMarks))
        TutorialStep.PlaceDog,
        TutorialStep.PlaceAndWatch,
        -> setOfNotNull(nextCorrectCell(level, placed, autoMarks))
        TutorialStep.AutoMark -> justMarked
        TutorialStep.NoTouching -> placed.cells()
            .flatMap { level.board.neighborsOf(it).toList() }
            .toSet()
        else -> emptySet()
    }

    /**
     * A square with no dog in it that the player can still act on.
     *
     * Auto-marked squares are excluded because the game ignores taps on them,
     * so lighting one would show a square that does nothing.
     */
    private fun freeWrongCell(
        level: LevelDefinition,
        placed: Solution,
        autoMarks: Set<Int>,
    ): Int? = (0 until level.board.cellCount).firstOrNull { cell ->
        cell !in autoMarks &&
            cell !in placed.cells().toSet() &&
            level.board.colOf(cell) != level.solution[level.board.rowOf(cell)]
    }

    /** The answer's square for the lowest row that still has no dog. */
    private fun nextCorrectCell(
        level: LevelDefinition,
        placed: Solution,
        autoMarks: Set<Int>,
    ): Int? = (0 until level.size)
        .firstOrNull { row -> placed[row] == Solution.UNPLACED }
        ?.let { row -> level.board.cellAt(row, level.solution[row]) }
        ?.takeIf { it !in autoMarks }
}
