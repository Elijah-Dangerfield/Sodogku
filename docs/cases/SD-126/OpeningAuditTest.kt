package com.sodogku.libraries.levels

import com.sodogku.libraries.puzzle.Board
import com.sodogku.libraries.puzzle.CandidateGrid
import com.sodogku.libraries.puzzle.Deduction
import com.sodogku.libraries.puzzle.DeductionEngine
import kotlin.test.Test

class OpeningAuditTest {

    private fun smallestRegion(level: LevelDefinition): Int =
        (0 until level.size).minOf { level.board.cellsInRegion(it).size }

    private fun firstStep(level: LevelDefinition): Deduction? =
        DeductionEngine.nextStep(CandidateGrid(level.board))

    private fun openingRun(level: LevelDefinition): Triple<Int, Int, Int> {
        val grid = CandidateGrid(level.board)
        var deepestTier = 0
        var steps = 0
        var crosses = 0
        while (true) {
            val step = DeductionEngine.nextStep(grid) ?: break
            deepestTier = maxOf(deepestTier, step.technique.tier)
            steps++
            if (step is Deduction.Place) break
            (step as Deduction.Eliminate).cells.forEach { if (grid.eliminate(it)) crosses++ }
        }
        return Triple(deepestTier, steps, crosses)
    }

    private fun report(name: String, levels: List<LevelDefinition>) {
        println("=== $name (${levels.size} boards) ===")
        println("sizes:        " + levels.groupingBy { it.size }.eachCount().toSortedMap())
        println("difficulties: " + levels.groupingBy { it.difficulty }.eachCount().toSortedMap())
        println("smallest region size: " + levels.groupingBy { smallestRegion(it) }.eachCount().toSortedMap())

        val empty = levels.filter { smallestRegion(it) > 1 }
        println("no single-cell region: ${empty.size} / ${levels.size}")
        println("no first step at all:  " + levels.count { firstStep(it) == null } + " / ${levels.size}")
        println("first step technique:  " + levels.groupingBy { firstStep(it)?.technique }.eachCount())
        println(
            "of the no-single-cell boards, first step is tier <= 2: " +
                empty.count { firstStep(it)!!.technique.tier <= 2 } + " / " + empty.size,
        )

        val runs = levels.map { openingRun(it) }
        println("deepest tier before the first dog: " + runs.groupingBy { it.first }.eachCount().toSortedMap())
        println("steps before the first dog:        " + runs.groupingBy { it.second }.eachCount().toSortedMap())
        val emptyCrosses = levels.zip(runs).filter { smallestRegion(it.first) > 1 }.map { it.second.third }
        println(
            "crosses available before the first dog (no-single-cell boards): " +
                "min=${emptyCrosses.min()} max=${emptyCrosses.max()} mean=${"%.1f".format(emptyCrosses.average())}",
        )
    }

    @Test
    fun audit() {
        report("DAILY", LevelPacks.daily.levels)
        report("CAMPAIGN", LevelPacks.campaign.levels)

        // 2026-09-12, the day the report was filed. daily.poolOffset ships at 0.
        val reported = LevelPacks.dailyFor(epochDay = 20708L)
        println("=== the reported board ===")
        println(
            "daily id ${reported.id}, ${reported.size}x${reported.size}, tier ${reported.difficulty}, " +
                "smallest region ${smallestRegion(reported)}",
        )
        println(reported.board)
        println("solution, column by row: ${reported.solution.columnByRow.toList()}")

        val grid = CandidateGrid(reported.board)
        repeat(4) { index ->
            val step = DeductionEngine.nextStep(grid) ?: return@repeat
            fun name(cell: Int) =
                "r${reported.board.rowOf(cell)}c${reported.board.colOf(cell)}" +
                    "(${Board.REGION_LETTERS[reported.board.regionAt(cell)]})"
            println(
                "step ${index + 1}: ${step.technique} -> " + when (step) {
                    is Deduction.Place -> "PLACE " + name(step.cell)
                    is Deduction.Eliminate -> "CROSS " + step.cells.joinToString(" ", transform = ::name)
                },
            )
            when (step) {
                is Deduction.Place -> grid.place(step.cell)
                is Deduction.Eliminate -> step.cells.forEach { grid.eliminate(it) }
            }
        }
    }
}
