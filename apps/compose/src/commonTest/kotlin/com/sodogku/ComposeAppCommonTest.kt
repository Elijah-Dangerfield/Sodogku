package com.sodogku

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Inherited from the template, and it asserts nothing about this app.
 *
 * One arithmetic assertion over literals. The only thing it can fail on is the
 * source set around it: that the entry-point module still has a `commonTest`
 * that compiles and a runner attached on every target. That is worth slightly
 * more here than in a library, because this module assembles the whole
 * dependency injection graph and a build that stopped running its tests at all
 * would look identical to one where they pass.
 *
 * Still a thin reason, written down so the next person deciding whether to
 * delete it is deciding rather than guessing. `DevFeedbackViewModelTest` beside
 * it already proves the source set runs, so if anything here earns deletion it
 * is this file.
 *
 * ### Not here
 *
 * Everything. The graph itself is exercised by `:apps:integration`, which
 * constructs real view models over a real client stack.
 */
class ComposeAppCommonTest {

    @Test
    fun example() {
        assertEquals(3, 1 + 2)
    }
}