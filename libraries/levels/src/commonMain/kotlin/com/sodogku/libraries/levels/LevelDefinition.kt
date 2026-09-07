package com.sodogku.libraries.levels

import com.sodogku.libraries.puzzle.Board
import com.sodogku.libraries.puzzle.Solution

/** Which pack a level belongs to. */
enum class PackKind { Campaign, Daily }

/**
 * One shipped level: the board, its verified answer, and how hard it is.
 *
 * The solution ships with the level. A determined player can unzip the app and
 * read it, which for a single-player game with no leaderboard is worth nothing,
 * and having it makes checking a tap an array lookup instead of a solve on the
 * cold path of every single move.
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
