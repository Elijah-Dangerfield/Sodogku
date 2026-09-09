package com.sodogku.libraries.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The gesture rules: the baseline reset across a stop/start, the sample-interval
 * gate, the magnitude bar, the requirement that a shake be sustained rather than
 * a single spike, and the cooldown.
 *
 * These rules lived inside two platform detectors that disagreed with each other
 * — Android divided by elapsed time and iOS did not, Android measured m/s² and
 * iOS measured g — and neither could be tested, because building a `SensorEvent`
 * or a `CMAccelerometerData` is not something a unit test does. That is why the
 * baseline was never reset on resume for as long as it wasn't.
 *
 * NOT covered here: the platform detectors themselves, which are now only
 * register/unregister and a unit conversion. Whether a shake reaches the dialog
 * is `ShakeHandlerTest` in `:apps:compose`.
 */
class ShakeRecognizerTest {

    @Test
    fun theFirstSampleIsABaselineAndNeverMotion() {
        val recognizer = ShakeRecognizer()

        // A phone lying flat reads 9.8 on z. That first reading is where the
        // phone was when we started looking, not a 9.8 m/s² change from nothing.
        assertFalse(recognizer.onSample(0.0, 0.0, 9.8, atMs = 100))
        assertFalse(recognizer.onSample(0.0, 0.0, 0.0, atMs = 200))
    }

    @Test
    fun theBarIsAChangePerHundredMillisecondsNotPerSample() {
        val recognizer = ShakeRecognizer()

        recognizer.onSample(0.0, 0.0, 0.0, atMs = 0)

        // Samples 400ms apart. 12 m/s² of drift over that long is 3.0 per
        // 100ms — turning the phone over, not shaking it.
        assertFalse(recognizer.onSample(0.0, 0.0, 12.0, atMs = 400))
        assertFalse(recognizer.onSample(0.0, 0.0, 0.0, atMs = 800))
        assertFalse(recognizer.onSample(0.0, 0.0, 12.0, atMs = 1_200))
    }

    @Test
    fun resetMakesTheNextSampleABaselineRatherThanMotion() {
        val recognizer = ShakeRecognizer()

        recognizer.onSample(0.0, 0.0, 9.8, atMs = 0)

        // The app went away and came back. Without dropping the baseline, the
        // vector from before the phone was pocketed is differenced against the
        // one after it is picked up, and two of those is a "shake" nobody made.
        recognizer.reset()

        assertFalse(recognizer.onSample(0.0, 0.0, -9.8, atMs = 200))
        assertFalse(recognizer.onSample(0.0, 0.0, 9.8, atMs = 400))
    }

    @Test
    fun samplesArrivingFasterThanTheSampleIntervalAreIgnored() {
        val recognizer = ShakeRecognizer()

        recognizer.onSample(0.0, 0.0, 0.0, atMs = 0)

        // Each of these clears the bar when measured over the 50ms that actually
        // elapsed. Only the reading a full interval after the baseline counts.
        assertFalse(recognizer.onSample(0.0, 0.0, 5.0, atMs = 50))
        assertFalse(recognizer.onSample(0.0, 0.0, 10.0, atMs = 100))
    }

    @Test
    fun sustainedMotionUnderTheBarIsNeverAShake() {
        val recognizer = ShakeRecognizer()

        recognizer.onSample(0.0, 0.0, 0.0, atMs = 0)

        repeat(20) { index ->
            val step = index + 1
            val z = if (step % 2 == 0) 0.0 else 7.0
            assertFalse(
                recognizer.onSample(0.0, 0.0, z, atMs = step * 100L),
                "7.0 m/s² per 100ms is under the 8.0 bar, however long it goes on",
            )
        }
    }

    @Test
    fun oneSpikeOverTheBarIsNotAShake() {
        val recognizer = ShakeRecognizer()

        recognizer.onSample(0.0, 0.0, 0.0, atMs = 0)

        // Lifting the phone off a table: one large change, then stillness.
        assertFalse(recognizer.onSample(0.0, 0.0, 12.0, atMs = 100))
        assertFalse(recognizer.onSample(0.0, 0.0, 12.0, atMs = 200))
        assertFalse(recognizer.onSample(0.0, 0.0, 12.0, atMs = 300))
    }

    @Test
    fun twoQualifyingSamplesInsideTheBurstWindowAreAShake() {
        val recognizer = ShakeRecognizer()

        recognizer.onSample(0.0, 0.0, 0.0, atMs = 0)

        assertFalse(recognizer.onSample(0.0, 0.0, 12.0, atMs = 100))
        assertTrue(recognizer.onSample(0.0, 0.0, 0.0, atMs = 200))
    }

    @Test
    fun qualifyingSamplesSpacedWiderThanTheBurstWindowDoNotAccumulate() {
        val recognizer = ShakeRecognizer()

        recognizer.onSample(0.0, 0.0, 0.0, atMs = 0)

        assertFalse(recognizer.onSample(0.0, 0.0, 12.0, atMs = 100))

        // Held still for over a second, then knocked once more. Two isolated
        // knocks a second and a half apart are two knocks, not a shake.
        (2..14).forEach { step ->
            assertFalse(recognizer.onSample(0.0, 0.0, 12.0, atMs = step * 100L))
        }
        assertFalse(recognizer.onSample(0.0, 0.0, 0.0, atMs = 1_500))
    }

    @Test
    fun aSecondShakeInsideTheCooldownIsSuppressed() {
        val recognizer = ShakeRecognizer()

        recognizer.onSample(0.0, 0.0, 0.0, atMs = 0)
        assertFalse(recognizer.onSample(0.0, 0.0, 12.0, atMs = 100))
        assertTrue(recognizer.onSample(0.0, 0.0, 0.0, atMs = 200))

        assertFalse(recognizer.onSample(0.0, 0.0, 12.0, atMs = 300))
        assertFalse(recognizer.onSample(0.0, 0.0, 0.0, atMs = 400))
    }

    @Test
    fun continuedShakingFiresAgainOnlyOnceTheCooldownHasPassed() {
        val recognizer = ShakeRecognizer()

        recognizer.onSample(0.0, 0.0, 0.0, atMs = 0)

        val firedAt = (1..40).mapNotNull { step ->
            val atMs = step * 100L
            val z = if (step % 2 == 0) 0.0 else 12.0
            atMs.takeIf { recognizer.onSample(0.0, 0.0, z, atMs = atMs) }
        }

        assertEquals(listOf(200L, 1_700L, 3_200L), firedAt)
    }
}
