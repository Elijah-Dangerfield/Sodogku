package com.sodogku.server.http

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Picking a string for a request whose language the catalogue may not have.
 *
 * The whole file is about degrading, since the interesting inputs are the ones
 * with no exact answer. A tag is matched case insensitively, and subtags are
 * stripped one at a time until something matches, so a phone asking for
 * `zh-Hant-TW` still gets the `zh` entry rather than nothing.
 *
 * Preference order is the part that is easy to get backwards. The list is
 * walked in order and the first tag that matches *anything* wins, so a first
 * preference the catalogue has never heard of falls through to the second
 * rather than ending the search.
 *
 * Two last resorts, in order: English, then any value at all. The second is
 * deliberately unordered, and the test asserts only that the result came from
 * the catalogue. Pinning which one would be pinning map iteration order, which
 * is not a promise and would fail on an unrelated change.
 *
 * ### Not here
 *
 * How the preference list is parsed out of an `Accept-Language` header, and
 * which routes consult this, are the route tests.
 */
class LocaleMatchTest {

    private val sample = mapOf(
        "en" to "Hello",
        "es" to "Hola",
        "fr" to "Bonjour",
    )

    @Test
    fun exactMatchReturned() {
        assertEquals("Hola", pickLocalized(sample, listOf("es")))
    }

    @Test
    fun caseInsensitiveMatch() {
        assertEquals("Hola", pickLocalized(sample, listOf("ES")))
        assertEquals("Hola", pickLocalized(sample, listOf("Es-MX")))
    }

    @Test
    fun stripsSubtagToFindParent() {
        // catalog has `en`, request asks for `en-US` → falls back to `en`.
        assertEquals("Hello", pickLocalized(sample, listOf("en-US")))
    }

    @Test
    fun walksMultiSubtagChain() {
        val map = mapOf("zh" to "你好")
        assertEquals("你好", pickLocalized(map, listOf("zh-Hant-TW")))
    }

    @Test
    fun firstPreferenceWins() {
        // both `de` and `es` are in the preferences, but `de` is first.
        // Catalog only has `es`. Since `de` doesn't match anything, falls
        // through to `es` on the next preference.
        assertEquals("Hola", pickLocalized(sample, listOf("de", "es")))
    }

    @Test
    fun fallsBackToEnglishWhenNoPreferenceMatches() {
        assertEquals("Hello", pickLocalized(sample, listOf("ja", "ko")))
    }

    @Test
    fun fallsBackToFirstValueWhenNoEnglish() {
        val noEnglish = mapOf("ja" to "こんにちは", "ko" to "안녕하세요")
        // No `en`, no preference match — picks an arbitrary value (the first
        // in iteration order).
        val result = pickLocalized(noEnglish, listOf("de"))
        assertEquals(true, result in noEnglish.values)
    }

    @Test
    fun emptyMapReturnsDefault() {
        assertEquals("fallback", pickLocalized(emptyMap(), listOf("en"), default = "fallback"))
    }
}
