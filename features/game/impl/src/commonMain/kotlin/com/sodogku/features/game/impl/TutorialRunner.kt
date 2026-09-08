package com.sodogku.features.game.impl

import com.sodogku.libraries.core.logging.Logger
import com.sodogku.libraries.core.logging.logEvent
import com.sodogku.libraries.levels.LevelDefinition
import com.sodogku.libraries.puzzle.Solution

/**
 * Where the guided run is up to.
 *
 * Pulled out of `GameViewModel` because it is the one responsibility in there
 * with a whole state machine of its own — a script, a position in it, and a
 * record of which levels have already been guided — and those three fields sat
 * next to fifteen others that had nothing to do with them.
 *
 * It deliberately owns no `GameState`. Deciding *which* coach mark is showing is
 * this class's business; putting it on screen is the ViewModel's, because only
 * the ViewModel can call `updateState`. So every method here returns a
 * [TutorialFrame] and changes nothing the player can see.
 */
class TutorialRunner(private val logger: Logger) {

    private var active = false
    private var script: List<TutorialStep> = emptyList()
    private var index = 0

    /**
     * Levels whose script has already run to the end.
     *
     * Without it, replaying level 1 after finishing the tutorial re-teaches the
     * three rules to somebody who has just proved they know them.
     */
    private val guided = mutableSetOf<Int>()

    /** True while a script is showing, which is what suppresses the board's own overlays. */
    val isRunning: Boolean get() = script.isNotEmpty()

    /**
     * The step the board is waiting on, or null between steps.
     *
     * Read rather than computed, so the ViewModel can ask without the answer
     * moving underneath it.
     */
    val currentStep: TutorialStep? get() = script.getOrNull(index)

    /**
     * Whether the guided run should happen at all.
     *
     * Set once from the persisted flag rather than re-read per level: the flag is
     * written the moment the tutorial ends, and the next level's setup runs
     * before that write has any chance to land.
     */
    fun arm(hasCompletedTutorial: Boolean, isDaily: Boolean) {
        active = !isDaily && !hasCompletedTutorial
    }

    /** Loads the script for [levelId], or nothing when this level is not guided. */
    fun beginLevel(levelId: Int) {
        script = if (active && levelId !in guided) Tutorial.scriptFor(levelId) else emptyList()
        index = 0
    }

    /** The frame a freshly opened board should show. */
    fun openingFrame(
        level: LevelDefinition,
        placed: Solution,
        autoMarks: Set<Int>,
    ): TutorialFrame {
        if (script.isEmpty()) return TutorialFrame.None
        val frame = frameFor(level, placed, autoMarks, justMarked = emptySet())
        if (frame.step == null) guided += level.id
        return frame
    }

    /**
     * The next frame after the player did something, skipping any step whose
     * squares no longer exist.
     *
     * A step that lights nothing is a step the player cannot complete, and
     * leaving one up is how a tutorial dead-ends.
     */
    fun frameFor(
        level: LevelDefinition,
        placed: Solution,
        autoMarks: Set<Int>,
        justMarked: Set<Int>,
    ): TutorialFrame {
        while (index < script.size) {
            val step = script[index]
            val cells = Tutorial.cellsFor(step, level, placed, autoMarks, justMarked)
            if (Tutorial.triggerFor(step) == TutorialTrigger.Tap || cells.isNotEmpty()) {
                logger.logEvent("tutorial.step_viewed", "step" to step.name, "level_id" to level.id)
                return TutorialFrame(step, cells)
            }
            index++
        }
        return TutorialFrame.None
    }

    /** Moves past the current step. Returns true once this level's script is spent. */
    fun advance(levelId: Int): Boolean {
        index++
        val finished = index >= script.size
        if (finished) guided += levelId
        return finished
    }

    /** Abandons the whole guided run, not just this level. */
    fun stop() {
        active = false
        script = emptyList()
        index = 0
    }
}
