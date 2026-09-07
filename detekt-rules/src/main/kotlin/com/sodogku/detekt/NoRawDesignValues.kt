package com.sodogku.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Fails on a feature reaching past the design system for a value it should be
 * asking for by name: a raw `dp` / `sp` literal instead of a `Dimension` token,
 * or a Material import instead of the DS component.
 *
 * The point is not tidiness, it is that a new screen should look right *by
 * default*. Every raw `16.dp` is a decision made once, at a call site, that
 * nobody will revisit when the spacing scale changes — and every direct
 * `androidx.compose.material3.Button` is a control that will not bounce, will
 * not pick up the theme, and will drift from the rest of the app.
 *
 * Deliberately allows:
 * - anything inside `:libraries:ui` (the design system has to define the values
 *   somewhere, and that somewhere is there);
 * - `0.dp` and `1.dp`, which are structural rather than spacing decisions;
 * - `@Preview` functions, where a literal is scaffolding for the preview frame.
 *
 * Configure `allowedMaterialImports` in `detekt.yml` for the handful of Material
 * types the DS legitimately re-exports (shapes, window insets, and similar).
 */
class NoRawDesignValues(config: Config) : Rule(
    config,
    "Features must use design-system tokens and components, not raw dp/sp literals or Material imports.",
) {

    private val allowedMaterialImports: List<String> by config(emptyList<String>())

    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)
        val path = importDirective.importedFqName?.asString() ?: return
        if (!path.startsWith(MATERIAL_PACKAGE)) return
        if (allowedMaterialImports.any { path.startsWith(it) }) return

        report(
            Finding(
                Entity.from(importDirective),
                "Direct Material import `$path` — use the design-system equivalent in " +
                    ":libraries:ui so the component picks up the theme and press feedback.",
            ),
        )
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        val selector = expression.selectorExpression?.text ?: return
        if (selector != "dp" && selector != "sp") return

        val literal = expression.receiverExpression.text
        if (literal !in STRUCTURAL_LITERALS && literal.toFloatOrNull() == null) return
        if (literal in STRUCTURAL_LITERALS) return
        if (expression.isInsidePreview()) return

        report(
            Finding(
                Entity.from(expression),
                "Raw `$literal.$selector` — use a `Dimension` token (or an `AppTheme.typography` " +
                    "style for text) so the scale stays in one place.",
            ),
        )
    }

    private fun KtDotQualifiedExpression.isInsidePreview(): Boolean {
        val function = getParentOfType<KtNamedFunction>(strict = true) ?: return false
        return function.annotationEntries.any { it.shortName?.asString() == "Preview" }
    }

    private companion object {
        const val MATERIAL_PACKAGE = "androidx.compose.material"

        /** Structure, not spacing: a hairline border or an explicit zero. */
        val STRUCTURAL_LITERALS = setOf("0", "1")
    }
}
