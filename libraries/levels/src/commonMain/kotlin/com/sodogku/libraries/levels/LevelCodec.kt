package com.sodogku.libraries.levels

import com.sodogku.libraries.puzzle.Board
import com.sodogku.libraries.puzzle.Solution

/**
 * The on-disk form of a level: one pipe-delimited line.
 *
 * ```
 * 137|7|AABBCCDAABBCCD...|2461503|3
 * id  size regions          solution difficulty
 * ```
 *
 * Regions are one letter per cell, row-major. The solution is one digit per
 * row giving that row's column, which works because boards cap at
 * [Board.MAX_SIZE] and so columns are single digits.
 *
 * Packs are generated as Kotlin source rather than an asset file, so this codec
 * runs identically in a unit test, on Android and on iOS with no resource
 * loading in the way. That matters because the pack verification test is the
 * only thing standing between an unsolvable level and the store.
 */
object LevelCodec {

    fun encode(level: LevelDefinition): String = listOf(
        level.id.toString(),
        level.size.toString(),
        level.board.toString().replace("\n", ""),
        level.solution.columnByRow.joinToString(""),
        level.difficulty.toString(),
    ).joinToString(FIELD_SEPARATOR)

    fun decode(line: String): LevelDefinition {
        val fields = line.split(FIELD_SEPARATOR)
        require(fields.size == FIELD_COUNT) {
            "Expected $FIELD_COUNT fields in level line, got ${fields.size}: $line"
        }
        val (rawId, rawSize, regions, rawSolution, rawDifficulty) = fields

        val size = rawSize.toIntOrNull() ?: error("Bad size in: $line")
        require(regions.length == size * size) {
            "Level $rawId declares size $size but carries ${regions.length} region cells"
        }
        require(rawSolution.length == size) {
            "Level $rawId declares size $size but carries ${rawSolution.length} placements"
        }

        return LevelDefinition(
            id = rawId.toIntOrNull() ?: error("Bad id in: $line"),
            board = Board.parse(regions),
            solution = Solution(IntArray(size) { rawSolution[it].digitToInt() }),
            difficulty = rawDifficulty.toIntOrNull() ?: error("Bad difficulty in: $line"),
        )
    }

    fun decodePack(kind: PackKind, version: Int, lines: List<String>): LevelPack =
        LevelPack(kind, version, lines.map(::decode))

    private const val FIELD_SEPARATOR = "|"
    private const val FIELD_COUNT = 5
}

private operator fun <T> List<T>.component4(): T = this[3]

private operator fun <T> List<T>.component5(): T = this[4]
