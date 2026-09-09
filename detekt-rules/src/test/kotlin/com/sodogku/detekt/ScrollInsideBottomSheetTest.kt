package com.sodogku.detekt

import dev.detekt.api.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScrollInsideBottomSheetTest {

    private val rule get() = ScrollInsideBottomSheet(Config.empty)

    @Test
    fun `reports a verticalScroll in the sheet's content lambda`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Sheet() {
                BottomSheet(onDismissRequest = {}) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("tall")
                    }
                }
            }
            """
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `names scrollableContent so the reader knows what to do instead`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Sheet() {
                BottomSheet(onDismissRequest = {}) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {}
                }
            }
            """
        )

        assertTrue(
            findings.single().message.contains("scrollableContent = true"),
            "A message that only says \"don't\" gets suppressed: ${findings.single().message}",
        )
    }

    @Test
    fun `reports a scroll nested several composables deep inside the sheet`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Sheet() {
                BottomSheet(onDismissRequest = {}) {
                    Surface {
                        Box {
                            Column(modifier = Modifier.fillMaxWidth().verticalScroll(state)) {}
                        }
                    }
                }
            }
            """
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports a scroll passed as the sheet's own modifier`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Sheet() {
                BottomSheet(
                    onDismissRequest = {},
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    Text("tall")
                }
            }
            """
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports the design system's verticalScrollWithBar wrapper`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Sheet() {
                BottomSheet(onDismissRequest = {}) {
                    Column(modifier = Modifier.verticalScrollWithBar(rememberScrollState())) {}
                }
            }
            """
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports a vertically oriented Modifier scrollable`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Sheet() {
                BottomSheet(onDismissRequest = {}) {
                    Column(modifier = Modifier.scrollable(state, orientation = Orientation.Vertical)) {}
                }
            }
            """
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `allows a horizontally scrolling row of chips, which cannot fight the drag`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Sheet() {
                BottomSheet(onDismissRequest = {}) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        Chip("one")
                        Chip("two")
                    }
                    Column(modifier = Modifier.scrollable(state, orientation = Orientation.Horizontal)) {}
                }
            }
            """
        )

        assertTrue(findings.isEmpty(), "Horizontal scrolling in a sheet is fine: $findings")
    }

    @Test
    fun `allows the sheet that opts in properly`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Sheet() {
                BottomSheet(onDismissRequest = {}, scrollableContent = true) {
                    Column {
                        Text("tall")
                    }
                }
            }
            """
        )

        assertTrue(findings.isEmpty(), "$findings")
    }

    @Test
    fun `allows a vertical scroll on an ordinary screen`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun SettingsScreen() {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("settings")
                }
            }
            """
        )

        assertTrue(findings.isEmpty(), "$findings")
    }

    /**
     * The `verticalScroll` that implements `scrollableContent` lives inside
     * `fun BottomSheet`, not inside a `BottomSheet(...)` call. Matching on the
     * enclosing *function* rather than the enclosing call would flag the very
     * component that fixes the bug.
     */
    @Test
    fun `allows the scroll inside the BottomSheet declaration itself`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun BottomSheet(scrollableContent: Boolean = false, content: @Composable () -> Unit) {
                ModalBottomSheet(onDismissRequest = {}) {
                    Column(
                        modifier = if (scrollableContent) {
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        } else {
                            Modifier
                        },
                        content = content,
                    )
                }
            }
            """
        )

        assertTrue(findings.isEmpty(), "$findings")
    }

    /**
     * `BasicBottomSheet` does not forward `scrollableContent`, so telling its
     * caller to pass the flag would be advice that does not compile.
     */
    @Test
    fun `stays quiet on sheets that do not take scrollableContent`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Sheet() {
                BasicBottomSheet(onDismissRequest = {}) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {}
                }
            }
            """
        )

        assertTrue(findings.isEmpty(), "$findings")
    }

    /**
     * The limit worth writing down: the scroll is one call away and the rule
     * cannot follow it. If this test ever starts failing, the rule grew type
     * resolution and the class doc needs updating.
     */
    @Test
    fun `does not see a scroll hidden inside a composable the lambda calls`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Sheet() {
                BottomSheet(onDismissRequest = {}) {
                    TallSection()
                }
            }

            @Composable
            fun TallSection() {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {}
            }
            """
        )

        assertTrue(findings.isEmpty(), "$findings")
    }
}
