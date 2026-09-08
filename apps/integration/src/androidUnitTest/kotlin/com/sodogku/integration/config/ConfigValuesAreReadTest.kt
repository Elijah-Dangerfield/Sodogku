package com.sodogku.integration.config

import com.sodogku.libraries.config.values.SodogkuConfigValues
import com.sodogku.libraries.config.impl.model.BasicMapAppConfig
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every declared config value has to be injected somewhere.
 *
 * This closes the third instance of one failure. A `ConfiguredValue` is declared,
 * it lands in the fallback map, it passes the completeness test, it is
 * transcribed into the admin registry, it renders in the console with an editor
 * and a type, an operator changes it — and nothing happens, because no code ever
 * asked for it. Every check we had was a check that the key was *declared
 * consistently*, and a key can be declared perfectly and be inert.
 *
 * The three:
 * - `app.minSupportedVersion` was the client's name for a key the admin console
 *   edited as `upgrade.minSupportedVersionCode`. The kill switch, pointed at
 *   nothing.
 * - `telemetry.*` was fine, but only because the module that declares those keys
 *   is also the module that reads them.
 * - All fourteen `scoring.*` keys, found by a review: `GameViewModel` calls
 *   `Scoring.placement`/`complete`/`paws` and lets the `config` parameter default
 *   to `ScoringConfig.Default`, so the console's scoring page was decorative.
 *
 * **The method is a text search, and that is a real limitation.** It proves a
 * class is *named* outside its own declaration, not that the value it resolves
 * reaches a decision. A class injected into a constructor and then ignored still
 * passes. That is a much smaller hole than the one it closes: forgetting to
 * inject is the mistake people make, and using an injected value is the reason
 * you injected it.
 *
 * When this fails, the fix is one of two things: inject and use the value, or
 * delete it. [UNWIRED] is a debt list, not an escape hatch — see its KDoc.
 */
class ConfigValuesAreReadTest {

    @Test
    fun noNewConfigValueIsDeclaredWithoutAReader() {
        val unread = unreadClassNames()

        assertTrue(
            unread.all { it in UNWIRED },
            "These config values are declared and nothing outside :libraries:config names them, " +
                "so changing them in the admin console does nothing:\n" +
                unread.filterNot { it in UNWIRED }.joinToString("\n") { "  $it" } +
                "\n\nInject and use it, or delete it. Do not add it to UNWIRED.",
        )
    }

    @Test
    fun theDebtListShrinksAndNeverGrowsStale() {
        // The half that makes UNWIRED a debt list rather than a place things go
        // to be forgotten. Wiring a value up and leaving its name here would
        // silently re-open the hole for the next one added under the same name.
        val unread = unreadClassNames().toSet()

        assertTrue(
            UNWIRED.all { it in unread },
            "These are listed as unwired but something now reads them. Delete them from UNWIRED:\n" +
                UNWIRED.filterNot { it in unread }.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun theScanCanActuallyFail() {
        // The guard against the guard. If the scan silently matched nothing —
        // wrong root, wrong extension, a build layout that moved — every value
        // would read as unwired and the debt list would look complete. If it
        // matched everything, nothing would ever be reported. Pin both ends.
        val declared = declaredClassNames()
        val unread = unreadClassNames()

        assertTrue(unread.isNotEmpty(), "nothing at all reported unwired, so the scan matches everything")
        assertTrue(
            unread.size < declared.size,
            "every declared value reported unwired, so the scan matches nothing",
        )
        assertTrue(
            "NoSuchConfigValueExistsAnywhere" !in unread,
            "a name that does not exist is not evidence of anything",
        )
    }

    /** Declared class names that no source file outside `:libraries:config` mentions. */
    private fun unreadClassNames(): List<String> {
        val sources = sourceFiles()
        assertTrue(sources.size > MIN_SOURCE_FILES, "only found ${sources.size} source files to scan")

        // A mention inside the config module is the declaration itself, or the
        // area list next to it, and proves nothing about a reader.
        val outsideConfig = sources
            .filter { file -> file.path.split(File.separator).none { it == "config" } }
            .map { it.readText() }

        return declaredClassNames().filterNot { name -> outsideConfig.any { it.contains(name) } }
    }

    private fun declaredClassNames(): List<String> =
        SodogkuConfigValues.all(BasicMapAppConfig(emptyMap<String, Any>())).map { it::class.simpleName!! }

    private fun sourceFiles(): List<File> {
        val root = System.getProperty(REPO_ROOT_PROPERTY)
            ?: error("$REPO_ROOT_PROPERTY is unset — apps/integration/build.gradle.kts should supply it")
        return listOf("libraries", "features", "apps")
            .map { File(root, it) }
            .flatMap { dir -> dir.walkTopDown().filter { it.isSourceFile() }.toList() }
    }

    /** Generated output and test doubles both mention names without reading them. */
    private fun File.isSourceFile(): Boolean {
        if (!isFile || extension != "kt") return false
        val parts = path.split(File.separator)
        return "build" !in parts && "commonTest" !in parts &&
            "androidUnitTest" !in parts && "test" !in parts
    }

    private companion object {
        const val REPO_ROOT_PROPERTY = "sodogku.repoRoot"

        /** A floor, so a broken scan reports "found nothing" rather than passing. */
        const val MIN_SOURCE_FILES = 200

        /**
         * The debt. It is now 6 names.
         *
         * It was 39 when this test was written, not the 37 the decisions entry
         * and the KDoc above both claim — the prose miscounted and the set is
         * the thing that runs, so trust the set.
         *
         * This is **not** a place to add things. Two tests hold it in place —
         * one fails if a name appears that is not listed here, the other fails
         * if a listed name gains a reader and is left behind. So the list can
         * only ever shrink, and it shrinks by wiring a value up and deleting its
         * line.
         *
         * What is left divides cleanly in two, and the difference matters. Some
         * of these are keys whose *feature* does not exist — a skip button, a
         * level map, a maintenance screen — and wiring one of those means
         * building the feature, not finding the call site. The rest name a
         * screen or hook that is genuinely missing. Neither is fixed by
         * searching harder, which is why each line below says which it is.
         */
        val UNWIRED = setOf(
            // Ads. The banner needs a component on a level map we do not have,
            // and app-open needs both an `AdPlacement` and a cold-start hook —
            // `AdFormat.AppOpen` reaches the SDK but no gate ever asks for it,
            // so the cooldown has nothing to space out. Both formats default
            // off, so these are stubs rather than gaps.
            "AdsAppOpenEnabled",
            "AdsBannerOnLevelMap",
            "AdsAppOpenCooldownHours",

            // `LOCK` is an arm that was never built: nothing anywhere locks a
            // level after a third strike, and `RealAdGate` documents that it
            // does not consult this key on the reward path on purpose. Wiring
            // it would mean inventing the harsher half of an A/B test.
            "AdsFailureMode",

            // What is left of the economy. The level map with silhouettes does
            // not exist — the level drawer shows every level, locked ones
            // included, deliberately — so there is no disclosure window for
            // `lookaheadCount` to widen. And nothing counts rewarded booster
            // grants per day: that is ad bookkeeping, it belongs next to the
            // other five numbers in `AdStateCache`, and `RealAdGate` has no
            // per-day counter to hang it on yet.
            "ProgressionLookaheadCount",
            "BoostersAdGrantsPerDay",
        )
    }
}
