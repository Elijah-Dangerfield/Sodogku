package com.sodogku.features.game.impl

import com.sodogku.libraries.levels.LevelCodec
import com.sodogku.libraries.levels.LevelDefinition

/**
 * The board the tutorial teaches on. It is in no pack, it is worth no points,
 * and finishing it is not a thing that can happen.
 *
 * It used to teach on campaign levels 1 to 3, which meant the lessons had to
 * take whatever those boards happened to look like — the square a rule pointed
 * at was wherever the generator put it, and a player's first three real levels
 * were spent under a scrim. This one is hand-authored so that every lesson has
 * the example it wants:
 *
 * ```
 * A A B B C        The dog opens on row 0, column 2. From there its own colour,
 * A B B C C        its row and column, and the ring of squares it touches are
 * A B D C E        three different shapes on the board — so "one per colour",
 * A D D C E        "one per row and column" and "dogs keep their distance" can
 * D D E E E        each light the squares that rule actually crossed off.
 * ```
 *
 * The starter dog is deliberately not in a corner. A corner dog rules out three
 * neighbours, and a three-square ring reads as an accident rather than as the
 * rule; from the middle of the top row it is five, which reads as a ring.
 *
 * **Every claim above is checked, not asserted.** `TutorialBoardTest` runs
 * `PuzzleSolver.uniqueSolutionOrNull` over it — a demo board with two answers
 * or none would teach that a tap can be right and wrong at the same time — and
 * walks the script asserting each lesson still has squares to point at.
 */
object TutorialBoard {

    /**
     * Not a campaign level id, and not [com.sodogku.libraries.progress.LevelRecord.FIRST_LEVEL_ID].
     * Nothing keyed on level id may ever be written for this board; zero is the
     * value that makes a leak obvious in a log rather than silently crediting
     * level 1.
     */
    const val LEVEL_ID: Int = 0

    const val SIZE: Int = 5

    val level: LevelDefinition = LevelCodec.decode(
        "$LEVEL_ID|$SIZE|AABBCABBCCABDCEADDCEDDEEE|20314|1",
    )
}
