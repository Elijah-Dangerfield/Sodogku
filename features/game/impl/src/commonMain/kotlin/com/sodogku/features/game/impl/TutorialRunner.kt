package com.sodogku.features.game.impl

import com.sodogku.libraries.core.logging.Logger
import com.sodogku.libraries.core.logging.logEvent
import com.sodogku.libraries.levels.LevelDefinition
import com.sodogku.libraries.puzzle.Solution

/**
 * Where the guided run is up to.
 *
 * Pulled out of `GameViewModel` because it is the one responsibility in there
 * with a whole state machine of its own — a script, a position in it, and
 * whether the run is still wanted — and those sat next to fifteen others that
 * had nothing to do with them.
 *
 * It deliberately owns no `GameState`. Deciding *which* coach mark is showing is
 * this class's business; putting it on screen is the ViewModel's, because only
 * the ViewModel can call `updateState`. So every method here returns a
 * [TutorialFrame] and changes nothing the player can see.
 */
class TutorialRunner(private val logger: Logger) {

    private var armed = false
    private var script: List<TutorialStep> = emptyList()
    private var index = 0

    /** True while a script is showing, which is what suppresses the board's own overlays. */
    val isRunning: Boolean get() = script.isNotEmpty()

    /**
     * Whether the rehearsal board should open in front of the level the route
     * asked for.
     *
     * Read *before* the board is chosen, which is why it is separate from
     * [isRunning]: the ViewModel has to know which board to open before there is
     * a script to run on it.
     */
    val shouldRehearse: Boolean get() = armed

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
     * Set once from the persisted flag rather than re-read per board: the flag is
     * written the moment the tutorial ends, and the real level's setup runs
     * before that write has any chance to land.
     *
     * [onFirstLevel] is false for a daily, and for a campaign level somebody
     * jumped to. The rehearsal hands the player back to the board the route
     * asked for when it is done, and that is only a sensible thing to do when
     * the route was the start of the campaign.
     */
    fun arm(hasCompletedTutorial: Boolean, onFirstLevel: Boolean) {
        armed = onFirstLevel && !hasCompletedTutorial
    }

    /**
     * Loads the script, or nothing when the run is not armed.
     *
     * [autoMark] is the player's setting, and it decides which curriculum runs
     * — see [Tutorial.scriptFor]. Read here rather than held from [arm] because
     * a replay from Settings is the one way a player reaches this having
     * already turned auto-mark off, and that is precisely the run that must not
     * teach a feature they switched off.
     */
    fun begin(autoMark: Boolean) {
        script = if (armed) Tutorial.scriptFor(autoMark) else emptyList()
        index = 0
    }

    /** The frame a freshly opened board should show. */
    fun openingFrame(
        level: LevelDefinition,
        placed: Solution,
        visibleMarks: Set<Int>,
    ): TutorialFrame {
        if (script.isEmpty()) return TutorialFrame.None
        return frameFor(level, placed, visibleMarks, justMarked = emptySet())
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
        visibleMarks: Set<Int>,
        justMarked: Set<Int>,
    ): TutorialFrame {
        while (index < script.size) {
            val step = script[index]
            val cells = Tutorial.cellsFor(step, level, placed, visibleMarks, justMarked)
            if (Tutorial.triggerFor(step) == TutorialTrigger.Tap || cells.isNotEmpty()) {
                logger.logEvent("tutorial.step_viewed", "step" to step.name)
                return TutorialFrame(step, cells)
            }
            index++
        }
        return TutorialFrame.None
    }

    /** Moves past the current step. Returns true once the script is spent. */
    fun advance(): Boolean {
        index++
        return index >= script.size
    }

    /** Abandons the guided run for good. */
    fun stop() {
        armed = false
        script = emptyList()
        index = 0
    }
}
