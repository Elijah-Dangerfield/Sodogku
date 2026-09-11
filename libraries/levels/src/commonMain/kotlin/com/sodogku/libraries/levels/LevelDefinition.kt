package com.sodogku.libraries.levels

import com.sodogku.libraries.puzzle.Board
import com.sodogku.libraries.puzzle.Solution

/** Which pack a level belongs to. */
enum class PackKind { Campaign, Daily }

/**
 * One shipped level: the board, its verified answer, and how hard it is.
 *
 * The solution ships with the level. A determined player can unzip the app and
 * read it, and having it makes checking a tap an array lookup instead of a
 * solve on the cold path of every single move.
 *
 * That trade was originally argued from "no leaderboard", and there are three
 * now, so the honest version is narrower: the boards are not defensible and are
 * not claimed to be. The score a leaderboard ranks is a fraction of par that
 * still has to be *played*, on a clock, without losing bones, so knowing the
 * answer skips the reasoning and not the pace. A cheat that mattered would need
 * a modified client, which no client-side secret survives anyway. See
 * `docs/decisions.md`.
 *
 * There is deliberately no par score or paw threshold here. Those are derived at
 * runtime from [size] and [difficulty] with coefficients that live in remote
 * config, so retuning what counts as a three-paw clear is a config change rather
 * than a regenerated pack and an app release.
 */
data class LevelDefinition(
    val id: Int,
    val board: Board,
    val solution: Solution,
    val difficulty: Int,
) {
    val size: Int get() = board.size
}

/** An ordered, versioned run of levels. */
data class LevelPack(
    val kind: PackKind,
    val version: Int,
    val levels: List<LevelDefinition>,
) {
    val size: Int get() = levels.size

    operator fun get(index: Int): LevelDefinition = levels[index]

    fun byId(id: Int): LevelDefinition? = levels.firstOrNull { it.id == id }
}
