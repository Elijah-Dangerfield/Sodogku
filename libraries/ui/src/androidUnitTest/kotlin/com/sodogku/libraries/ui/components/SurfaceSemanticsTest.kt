package com.sodogku.libraries.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sodogku.libraries.ui.system.color.ColorResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * A named [Surface] is still something a player can press.
 *
 * This is a claim about the order two modifiers end up in, and the order is only
 * observable once they are both in a composition, so nothing below this tier can
 * hold it. The pairing is what matters: a card with several labels inside is read
 * out as several fragments unless something clears them, `clearAndSetSemantics`
 * is the only thing that does, and it takes the click action with it if it lands
 * on the wrong side of the clickable. Each half on its own looks fine.
 *
 * [aCallerThatClearsSemanticsItselfLosesTheClickAction] is the shape of the bug
 * rather than a test of `Surface`, and it is here on purpose: it is the reason
 * `contentDescription` is a parameter at all, and without it the first test
 * passes against an implementation that never needed writing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@Suppress("DEPRECATION")
class SurfaceSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aNamedSurfaceIsReadAsOneThingAndIsStillClickable() {
        var clicks = 0
        compose.setContent {
            LabelledSurface(contentDescription = Spoken, onClick = { clicks++ })
        }

        compose.onNodeWithContentDescription(Spoken).assertHasClickAction()
        compose.onNodeWithContentDescription(Spoken).performClick()
        assertEquals(1, clicks, "the surface's name survived but its click action did not")

        compose.onNodeWithText(FirstLabel).assertDoesNotExist()
        compose.onNodeWithText(SecondLabel).assertDoesNotExist()
    }

    /**
     * The guard against the guard. Every assertion above is about what a name
     * does to the tree, and all of them would pass against a `Surface` that had
     * quietly stopped composing its content at all.
     */
    @Test
    fun anUnnamedSurfaceReadsOutEveryLabelInside() {
        compose.setContent {
            LabelledSurface(contentDescription = null, onClick = {})
        }

        compose.onNodeWithText(FirstLabel).assertExists()
        compose.onNodeWithText(SecondLabel).assertExists()
    }

    /**
     * The same label, set by the caller one link earlier in the chain, which is
     * where a caller has to put it because that is all a caller can reach. It
     * names the card and stops it being a control.
     */
    @Test
    fun aCallerThatClearsSemanticsItselfLosesTheClickAction() {
        compose.setContent {
            Surface(
                color = ColorResource.White,
                contentColor = ColorResource.Black,
                modifier = Modifier.clearAndSetSemantics { contentDescription = Spoken },
                onClick = {},
            ) {
                Column {
                    BasicText(FirstLabel)
                    BasicText(SecondLabel)
                }
            }
        }

        compose.onNodeWithContentDescription(Spoken).assertHasNoClickAction()
    }

    @Composable
    private fun LabelledSurface(contentDescription: String?, onClick: () -> Unit) {
        Surface(
            color = ColorResource.White,
            contentColor = ColorResource.Black,
            onClick = onClick,
            contentDescription = contentDescription,
        ) {
            Column {
                BasicText(FirstLabel)
                BasicText(SecondLabel)
            }
        }
    }

    private companion object {
        const val Spoken = "First class, earned, 4 of 10"
        const val FirstLabel = "First class"
        const val SecondLabel = "4 / 10"
    }
}
