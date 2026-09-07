package com.sodogku.libraries.levels

import com.sodogku.libraries.puzzle.Board
import com.sodogku.libraries.puzzle.Solution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LevelCodecTest {

    private val level = LevelDefinition(
        id = 7,
        board = Board.parse("BAAABBCADDCCDDDD"),
        solution = Solution(intArrayOf(2, 0, 3, 1)),
        difficulty = 2,
    )

    @Test
    fun encode_producesThePipeDelimitedLine() {
        assertEquals("7|4|BAAABBCADDCCDDDD|2031|2", LevelCodec.encode(level))
    }

    @Test
    fun decode_readsItBack() {
        assertEquals(level, LevelCodec.decode("7|4|BAAABBCADDCCDDDD|2031|2"))
    }

    @Test
    fun decode_rejectsAWrongFieldCount() {
        assertFailsWith<IllegalArgumentException> { LevelCodec.decode("7|4|BAAABBCADDCCDDDD|2031") }
    }

    @Test
    fun decode_rejectsRegionsThatDoNotMatchTheDeclaredSize() {
        // The size field and the region string are independently written, so a
        // generator bug could disagree between them. Catch it at the boundary
        // rather than as a confusing out-of-bounds later.
        assertFailsWith<IllegalArgumentException> { LevelCodec.decode("7|4|BAAABB|2031|2") }
    }

    @Test
    fun decode_rejectsASolutionThatDoesNotMatchTheDeclaredSize() {
        assertFailsWith<IllegalArgumentException> {
            LevelCodec.decode("7|4|BAAABBCADDCCDDDD|203|2")
        }
    }

    @Test
    fun decode_rejectsAnUnknownRegionLetter() {
        assertFailsWith<IllegalArgumentException> {
            LevelCodec.decode("7|4|BAAABBCADDCCDDDZ|2031|2")
        }
    }
}
