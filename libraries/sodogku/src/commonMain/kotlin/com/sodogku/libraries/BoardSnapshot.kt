package com.sodogku.libraries.sodogku

import kotlinx.serialization.Serializable

/**
 * An attempt in progress, saved so closing the app does not throw it away.
 *
 * The one that was missing. Booster spends were persisted the moment they
 * happened and the board they paid for was not, so a force-quit mid-puzzle kept
 * the charge and lost the reasoning — which is the worst possible half to save.
 *
 * It holds what the player *did*, not what the game derived. Auto-marks are
 * recomputed from [placements] on restore, because they are a function of it and
 * a stored copy is a second source of truth that can disagree. Everything else
 * here is either a choice ([manualMarks]) or a cost already paid
 * ([wrongGuesses], [strikesTaken], [score]) and cannot be recovered any other
 * way.
 *
 * Deliberately on [AppData] rather than in Room. It is one small blob, there is
 * only ever one of it, and nothing queries it — a table would buy a migration
 * and no capability.
 */
@Serializable
data class BoardSnapshot(
    val levelId: Int,
    /** True when [levelId] indexes the daily pack, which shares a number line with the campaign. */
    val isDaily: Boolean,

    /** Column per row, `-1` for a row with no dog yet. Mirrors `Solution.columnByRow`. */
    val placements: List<Int>,

    /** The player's own crosses. Auto-marks are derived and not stored. */
    val manualMarks: Set<Int>,

    /** Squares that cost a bone. These stay red, so they have to survive too. */
    val wrongGuesses: Set<Int>,

    /**
     * Wrong guesses this attempt has made, which is **not** the same question as
     * how many bones are left.
     *
     * Bones are one count held in [AppData.bones] and spent across every board,
     * so the holding is already on disk and does not belong here twice. What a
     * snapshot has to carry is the per-attempt number the completion bonus and
     * the achievement log are priced on — resume without it and a board finished
     * after a relaunch scores as if it had been cleared cleanly.
     *
     * Defaulted rather than required, so a snapshot written before bones went
     * global decodes as an attempt with a clean sheet instead of failing to
     * restore at all.
     */
    val strikesTaken: Int = 0,
    val score: Int,
    val combo: Int,
    val bestCombo: Int,
    val placementCount: Int,

    /**
     * Elapsed play time in millis at the moment of the save.
     *
     * Stored rather than derived from a start timestamp on purpose: a wall-clock
     * start would count the hours the app spent closed, and a player who resumed
     * the next morning would find a level they had lost on time they never spent.
     */
    val elapsedMs: Long,

    /** Which attempt this is, so the telemetry keeps counting across a restart. */
    val attemptNumber: Int,

    val sniffsUsed: Int,
    val treatsUsed: Int,
) {
    /**
     * True for a snapshot with nothing in it worth restoring.
     *
     * An untouched board is not worth resuming — restoring one is
     * indistinguishable from starting it, and keeping it would mean a player who
     * glanced at level 40 and left is offered level 40 forever.
     *
     * **Spent help counts as something.** A sniff leaves nothing on the board:
     * it highlights squares and the highlight is transient, so a board where the
     * player has only sniffed looks untouched by the first three terms. Dropping
     * it there means the attempt comes back having taken no help, which banks a
     * bigger score than was earned and under-reports `sniffs_used`. A treat is
     * caught by the placements it makes, but it is named here too rather than
     * left to that coincidence.
     */
    val isEmpty: Boolean
        get() = placements.none { it != UNPLACED } &&
            manualMarks.isEmpty() &&
            wrongGuesses.isEmpty() &&
            sniffsUsed == 0 &&
            treatsUsed == 0

    companion object {
        /** Matches `Solution.UNPLACED`; duplicated so this module needs no puzzle dependency. */
        const val UNPLACED: Int = -1
    }
}
