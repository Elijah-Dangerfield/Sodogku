package com.sodogku.features.game.impl

import com.sodogku.libraries.puzzle.Technique
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The half of the technique-to-copy map the compiler cannot check.
 *
 * The `when` in [reasonOf] is exhaustive, so a sixth technique stops the build
 * until somebody writes its sentence. What compiles perfectly well is the same
 * sentence pointed at two techniques, which is what a copied branch looks like,
 * and on screen it looks like a hint confidently explaining itself with the
 * wrong rule.
 */
class HintSentenceTest {

    @Test
    fun everyTechniqueHasItsOwnSentence() {
        val sentences = Technique.entries.map { reasonOf(it) }

        assertEquals(
            Technique.entries.size,
            sentences.distinct().size,
            "two techniques share a sentence: ${Technique.entries.zip(sentences)}",
        )
    }
}
