package com.sodogku.features.onboarding.impl

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The one thing on the welcome screen that a number can get wrong.
 *
 * Nearly all of this screen is layout and copy: the dog's position is decided by
 * the layout tree, the two of it agree with the splash because there is only one
 * of them, and the only honest check on how any of it looks is a screenshot.
 * [FieldPaws] is the exception. It is nine hand-placed coordinates, the texture
 * is drawn behind the dog rather than clipped around it, and a paw dropped in
 * the middle would be half-hidden under the dog's chin and read as a smudge on
 * the art rather than as a mistake in a list.
 *
 * So this pins the one rule the list has to keep: the middle belongs to the dog.
 * It is not a rendering test and does not pretend to be one.
 */
class WelcomeFieldPawsTest {

    @Test
    fun `no paw is placed where the dog stands`() {
        val trespassers = FieldPaws.filter { it.x in DogColumn && it.y in DogRow }

        assertTrue(
            trespassers.isEmpty(),
            "Paws inside the dog's box: " +
                trespassers.joinToString { "(${it.x}, ${it.y})" },
        )
    }

    @Test
    fun `every paw is inside the field it is drawn on`() {
        val strays = FieldPaws.filter { it.x !in Unit01 || it.y !in Unit01 }

        assertTrue(
            strays.isEmpty(),
            "Paws outside the field: " + strays.joinToString { "(${it.x}, ${it.y})" },
        )
    }

    private companion object {
        /**
         * Where the dog's drawn head can reach, as a fraction of the field.
         *
         * The head fills 106 by 77 of the sprite's 128px frame, and the frame is
         * capped at the field's height, so the head is at most 60% of the field
         * tall and about 55% of a phone's width across, centred. These are the
         * outer bounds of that, rounded outwards.
         */
        val DogColumn = 0.22f..0.78f
        val DogRow = 0.20f..0.80f

        val Unit01 = 0f..1f
    }
}
