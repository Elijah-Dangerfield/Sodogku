package com.sodogku.integration.copy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The two boosters are named backwards in code, and the copy has to survive it.
 *
 * `Consumable.Sniff` paints crosses on squares a dog cannot be on, which is a
 * **hint**. `Consumable.Treat` runs `HintFinder` and puts a dog on the board,
 * which is **locating** one. The button labels carry the correction, because
 * renaming the enum would mean migrating persisted holdings, config keys and
 * analytics events.
 *
 * The cost of that trade is that every string keyed `*_sniff_*` is one slip away
 * from saying "Locate", and the slip is invisible in review: the key says treat,
 * the copy says hint, and both words are real words that belong to this feature.
 *
 * **This has gone wrong three times.** First the two button labels were
 * transposed. Fixing those left the prompt titles transposed, so tapping Locate
 * opened a sheet headed "Use a hint". Fixing *those* left the tutorial
 * transposed, which was the worst of the three: the coach mark for the Treat
 * step was titled "Hint" while pointing at the button labelled Locate, and the
 * level reward chip promised "+1 Hint" for a treat.
 *
 * So the rule is not "do not write these words". `booster_sniff_body` says "your
 * dog sniffs around", which is the dog doing something rather than the name of a
 * control, and `achievement_treat_money_name` is a pun. Both are fine. What is
 * never fine is a string filed under one booster naming the *other* booster's
 * button.
 *
 * The labels are read from the file rather than hardcoded here, so relabelling a
 * booster updates the rule instead of breaking it.
 */
class BoosterNamingTest {

    @Test
    fun noStringNamesTheOtherBoostersButton() {
        val all = strings()
        val labels = boosterLabels(all)

        val offenders = all.mapNotNull { (name, body) ->
            val booster = name.boosterKey() ?: return@mapNotNull null
            val otherLabel = labels.getValue(if (booster == SNIFF) TREAT else SNIFF)
            if (!body.mentions(otherLabel)) return@mapNotNull null
            "  $name is filed under $booster but says \"$otherLabel\": $body"
        }

        assertTrue(
            offenders.isEmpty(),
            "Sniff is the Hint button and Treat is the Locate button. These have it backwards:\n" +
                offenders.joinToString("\n"),
        )
    }

    @Test
    fun everyBoosterStringNamesItsOwnButtonOrNoButtonAtAll() {
        // The weaker half of the pair, and it earns its place: the first test
        // passes for a string that names neither button, which is what a title
        // reading "Sniff" did. A title has to name the control it is teaching.
        val all = strings()
        val labels = boosterLabels(all)

        val offenders = all.mapNotNull { (name, body) ->
            val booster = name.boosterKey() ?: return@mapNotNull null
            if (!name.endsWith("_title")) return@mapNotNull null
            if (body.mentions(labels.getValue(booster))) return@mapNotNull null
            "  $name should name the ${labels.getValue(booster)} button: $body"
        }

        assertTrue(
            offenders.isEmpty(),
            "A booster's title has to name the button it is about:\n" + offenders.joinToString("\n"),
        )
    }

    @Test
    fun theScanReadsLabelsAndFindsBoosterStrings() {
        // The guard against the guard. Both tests above are written as "find the
        // offenders", so a scan that matched nothing would report success in
        // exactly the same words as a scan that found nothing wrong.
        val all = strings()
        val labels = boosterLabels(all)

        assertTrue(labels.getValue(SNIFF).isNotBlank(), "no label read for sniff")
        assertTrue(labels.getValue(TREAT).isNotBlank(), "no label read for treat")
        assertTrue(
            labels.getValue(SNIFF) != labels.getValue(TREAT),
            "both boosters read as the same label, so the scan cannot tell them apart",
        )

        // Counted per booster rather than in total, which is the version that
        // works. A single total passed happily with the sniff branch of
        // `boosterKey` broken, because the nine treat strings cleared the floor
        // on their own and the five sniff strings were silently unscanned.
        for (booster in listOf(SNIFF, TREAT)) {
            val keyed = all.filter { (name, _) -> name.boosterKey() == booster }
            assertTrue(
                keyed.size >= MIN_STRINGS_PER_BOOSTER,
                "only ${keyed.size} strings matched $booster, so that half of the key match is broken",
            )
            assertTrue(
                keyed.any { (name, _) -> name.endsWith("_title") },
                "no $booster title found, so the title rule is checking nothing for it",
            )
        }
    }

    /** Which booster a string is filed under, by key, or null if it is not a booster string. */
    private fun String.boosterKey(): String? = when {
        // Anchored to a word boundary so `game_booster_sniff` matches and a
        // hypothetical `sniffle_body` does not.
        Regex("""(^|_)$SNIFF(_|$)""") in this -> SNIFF
        Regex("""(^|_)$TREAT(_|$)""") in this -> TREAT
        else -> null
    }

    /**
     * The words on the two buttons, read from the file.
     *
     * Read rather than hardcoded because the point of this test is that the
     * labels are the source of truth and the code names are not. A hardcoded
     * "Hint" would make relabelling a booster fail this test rather than update
     * it.
     */
    private fun boosterLabels(all: List<Pair<String, String>>): Map<String, String> {
        val byName = all.toMap()
        return mapOf(
            SNIFF to byName.getValue("game_booster_sniff").trim(),
            TREAT to byName.getValue("game_booster_treat").trim(),
        )
    }

    /** Case-insensitive and whole-word, so "Locate" matches "locate" but not "located". */
    private fun String.mentions(label: String): Boolean =
        Regex("""\b${Regex.escape(label)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(this)

    /** Every `<string name="...">body</string>` in the shared resources. */
    private fun strings(): List<Pair<String, String>> {
        val file = File(
            repoRoot(),
            "libraries/resources/src/commonMain/composeResources/values/strings.xml",
        )
        assertTrue(file.isFile, "no strings at ${file.absolutePath}")

        return Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(file.readText())
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()
    }

    private fun repoRoot(): String =
        System.getProperty("sodogku.repoRoot")
            ?: error("sodogku.repoRoot is unset — apps/integration/build.gradle.kts supplies it")

    private operator fun Regex.contains(text: String): Boolean = containsMatchIn(text)

    private companion object {
        const val SNIFF = "sniff"
        const val TREAT = "treat"

        /**
         * A floor per booster, so a key match broken on one side reports "found
         * nothing" rather than passing on the other side's strings. Sniff has
         * five and treat has nine as of writing.
         */
        const val MIN_STRINGS_PER_BOOSTER = 4
    }
}
