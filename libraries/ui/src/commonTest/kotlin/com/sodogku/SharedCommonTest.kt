package com.sodogku

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Inherited from the template, and it asserts nothing about this app.
 *
 * One arithmetic assertion over literals. What it can actually fail on is the
 * source set around it: that `commonTest` in the design system module still
 * compiles and still has a runner attached on every target it builds for. A
 * module whose only test never ran would otherwise report green forever, which
 * is the same failure the source-scanning guards exist to catch elsewhere.
 *
 * That is a thin reason to keep a file, and it is written down so the next
 * person deciding whether to delete it is deciding rather than guessing. If
 * this module grows a test of its own, that test covers the same ground and
 * this one should go.
 *
 * ### Not here
 *
 * Anything about the design system. Real coverage of these components lives
 * beside each of them, for example `ModalDialogDefaultsTest` and
 * `RegionPaletteTest`.
 */
class SharedCommonTest {

    @Test
    fun example() {
        assertEquals(3, 1 + 2)
    }
}