package com.sodogku.libraries.config.impl

import com.sodogku.libraries.config.getValueForPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * How a malformed remote value resolves.
 *
 * The rule the whole fail-open guarantee rests on: a value the client cannot
 * parse must resolve to **null**, so the caller falls back to its declared
 * default. A parse that invents an answer is worse than no answer at all —
 * the default is chosen deliberately and written down; the invented value is
 * whatever the coercion happened to produce.
 */
class MapExtTest {

    @Test
    fun anUnparseableBooleanFallsBackRatherThanReadingAsFalse() {
        // `"banana".toBoolean()` is `false`. That is the bug: a string typed
        // into a boolean flag in the admin console would have disabled the
        // feature on every device that fetched it, with no log and no fallback.
        listOf("banana", "", "1", "yes", "off", "null").forEach { junk ->
            assertNull(
                mapOf("ads" to mapOf("enabled" to junk)).getValueForPath<Boolean>("ads.enabled"),
                "\"$junk\" must not resolve to a boolean",
            )
        }
    }

    @Test
    fun realBooleansStillResolveInEitherCase() {
        // The companion assertion. A guard that rejected everything would pass
        // the test above and break every flag in the app.
        fun read(raw: Any) = mapOf("ads" to mapOf("enabled" to raw)).getValueForPath<Boolean>("ads.enabled")

        assertEquals(true, read(true))
        assertEquals(false, read(false))
        assertEquals(true, read("true"))
        assertEquals(false, read("false"))
        // The admin console lets an operator type a raw value, and casing is
        // not a mistake worth punishing.
        assertEquals(true, read("True"))
        assertEquals(false, read("FALSE"))
    }

    @Test
    fun unparseableNumbersAlreadyFellBackAndStillDo() {
        val map = mapOf("scoring" to mapOf("comboStep" to "banana"))

        assertNull(map.getValueForPath<Int>("scoring.comboStep"))
        assertNull(map.getValueForPath<Double>("scoring.comboStep"))
        assertNull(map.getValueForPath<Long>("scoring.comboStep"))
    }

    @Test
    fun aMissingPathIsNullRatherThanAnError() {
        val map = mapOf("ads" to mapOf("enabled" to true))

        assertNull(map.getValueForPath<Boolean>("ads.somethingElse"))
        assertNull(map.getValueForPath<Boolean>("nothing.here"))
        // A leaf where a branch was expected must not throw either.
        assertNull(map.getValueForPath<Boolean>("ads.enabled.deeper"))
    }
}
