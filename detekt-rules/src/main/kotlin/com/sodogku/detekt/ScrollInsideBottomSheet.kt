package com.sodogku.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.psiUtil.parents

/**
 * Flags a hand-rolled vertical scroll written inside a `BottomSheet { … }`
 * content lambda, where `scrollableContent = true` is the fix.
 *
 * Material derives the sheet's Expanded anchor from the sheet's *measured*
 * height (`Expanded at fullHeight - sheetSize.height`), recomputes the anchors
 * on every measure pass, and snaps to the recomputed target whenever the new
 * anchors differ from the old ones. A sheet whose height is decided by its
 * content can therefore be dragged, re-measured, and yanked back to Expanded
 * before the gesture finishes. From an iPhone that looked like: drag the sheet
 * down, and about ninety pixels in it jumps back to the top, over and over, and
 * will not close. Only tall content triggers it. Short content gives a stable
 * measured height and identical anchors on every pass.
 *
 * `BottomSheet`'s `scrollableContent` flag owns the scroll *and* pins the sheet
 * to the full height, so every recompute produces the same anchors and there is
 * nothing to snap to. A scroll modifier the caller writes inside the content
 * lambda gets the scrolling without the pinning, which is precisely the broken
 * combination.
 *
 * **What this rule can see, and what it cannot.** It is syntactic and runs
 * without type resolution, so it catches a scroll modifier written lexically
 * inside the sheet call, including one nested several composables deep, and one
 * passed as the sheet's own `modifier`. It cannot see a scroll hidden inside a
 * composable that the lambda merely *calls*; a `MyTallSection()` that scrolls
 * internally reintroduces the bug invisibly. The rule is a tripwire on the
 * common shape, not a proof.
 *
 * Deliberately narrow, because a noisy rule earns a blanket `@Suppress` and then
 * catches nothing:
 * - **Only vertical scrolling counts.** A horizontally scrolling row of chips
 *   inside a sheet is fine: it cannot fight the sheet's vertical drag, and it
 *   does not make the content taller than the screen, which is the actual
 *   trigger. `horizontalScroll` is never reported, and `Modifier.scrollable` is
 *   reported only when its `orientation` argument literally reads
 *   `Orientation.Vertical`. An orientation passed through a variable is left
 *   alone rather than guessed at.
 * - **Only [SHEET_CALLEES].** `BasicBottomSheet` has the same underlying
 *   problem but does not forward `scrollableContent`, so the advice this rule
 *   gives would not compile there. Add it here when it forwards the flag.
 * - **Previews are not exempt**, unlike [VerifyStrings] and [NoRawDesignValues].
 *   Sample copy in a preview is scaffolding; a sheet built the broken way in a
 *   preview is the thing somebody copies onto a real screen.
 */
class ScrollInsideBottomSheet(config: Config) : Rule(
    config,
    "A vertical scroll written inside a BottomSheet content lambda leaves the sheet's height " +
        "decided by its content, which lets Material snap the sheet back to Expanded mid-drag; " +
        "pass scrollableContent = true instead.",
) {
    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val callee = expression.calleeExpression?.text ?: return
        if (!expression.scrollsVertically(callee)) return
        val sheet = expression.enclosingSheetCallee() ?: return
        report(
            Finding(
                Entity.from(expression),
                "`$callee(...)` inside this $sheet(...) content. Pass `scrollableContent = true` to " +
                    "$sheet and drop the modifier. The sheet's own scroll also pins it to the full " +
                    "height; a sheet sized by its content has its drag anchors recomputed mid-gesture " +
                    "and snaps back to the top instead of closing.",
            ),
        )
    }

    /**
     * Whether this call is a modifier that scrolls on the vertical axis.
     *
     * `scrollable` takes its axis as an argument, so the argument list is read
     * as text, because there is no type resolution here, and an axis that isn't
     * spelled out at the call site is treated as not-vertical.
     */
    private fun KtCallExpression.scrollsVertically(callee: String): Boolean = when (callee) {
        in VERTICAL_SCROLL_MODIFIERS -> true
        SCROLLABLE_MODIFIER -> valueArguments.any { it.text?.contains(VERTICAL_ORIENTATION) == true }
        else -> false
    }

    /**
     * The name of the nearest enclosing sheet call, or null when this element
     * isn't inside one.
     *
     * Any lexical ancestor counts, not just the trailing lambda: a scroll passed
     * as the sheet's `modifier` argument lands on the same Column and breaks the
     * same way.
     */
    private fun KtCallExpression.enclosingSheetCallee(): String? = parents
        .filterIsInstance<KtCallExpression>()
        .mapNotNull { it.calleeExpression?.text }
        .firstOrNull { it in SHEET_CALLEES }

    private companion object {
        /**
         * The sheets that take `scrollableContent`. `BasicBottomSheet` is absent
         * on purpose; see the class doc.
         */
        val SHEET_CALLEES = setOf("BottomSheet")

        /**
         * Compose's vertical scroll modifier plus this project's wrappers around
         * it. A wrapper is the easiest place for this to hide, because the call
         * site stops looking like a Compose scroll. Add yours as you write them.
         */
        val VERTICAL_SCROLL_MODIFIERS = setOf("verticalScroll", "verticalScrollWithBar")

        const val SCROLLABLE_MODIFIER = "scrollable"

        const val VERTICAL_ORIENTATION = "Orientation.Vertical"
    }
}
