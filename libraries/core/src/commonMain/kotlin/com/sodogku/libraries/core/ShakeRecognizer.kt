package com.sodogku.libraries.core

import kotlin.math.sqrt

/**
 * Decides whether a stream of accelerometer samples is someone deliberately
 * shaking the phone.
 *
 * Both platform detectors feed this. They differ only in how their SDK hands
 * over samples and in what units it uses — CoreMotion reports multiples of
 * gravity, Android reports m/s² — so the adapters normalise to m/s² and the
 * gesture itself is defined in exactly one place.
 *
 * Two rules matter, and both come from a report of the shake dialog appearing
 * on its own after resuming from the background:
 *
 * - [reset] before the first sample of a listening session, and treat that first
 *   sample as the baseline rather than as motion. Otherwise the first sample
 *   after a resume is differenced against a vector captured before the phone
 *   went into a pocket, which is not a measurement of anything.
 * - One spike over the bar is not a shake. Lifting a phone off a table clears
 *   any single-sample threshold worth having. A shake is oscillatory — the hand
 *   reverses several times a second — so a real one produces qualifying samples
 *   repeatedly and a bump produces exactly one.
 *
 * The numbers were retuned after a report that the dialog appeared "way too"
 * often, having originally been carried over unchanged from the old Android
 * detector's `magnitude / timeDiffMs * 10000 > 800`.
 *
 * Two qualifying samples anywhere inside a whole second was the loose part, more
 * than the magnitude was. 8.0 m/s² per 100ms is under 1g of *change*, which
 * putting a phone down on a table clears comfortably, and two of those had a
 * full second to find each other. Four inside 700ms is a different question: it
 * asks for roughly 6Hz of sustained direction reversal, which is what a hand
 * shaking a phone does and what nothing else in normal handling does. Simulated
 * against the gestures that were firing it, a single pick-up and two separate
 * knocks now both produce nothing.
 */
class ShakeRecognizer {

    private var hasBaseline = false
    private var lastX = 0.0
    private var lastY = 0.0
    private var lastZ = 0.0
    private var lastSampleAtMs = 0L

    private var burstStartedAtMs = 0L
    private var burstCount = 0
    private var lastShakeAtMs: Long? = null

    /**
     * Drop the baseline so the next sample is treated as a fresh starting point.
     * Call this whenever sampling is (re)started.
     */
    fun reset() {
        hasBaseline = false
        burstCount = 0
    }

    fun onSample(x: Double, y: Double, z: Double, atMs: Long): Boolean {
        if (!hasBaseline) {
            recordSample(x, y, z, atMs)
            hasBaseline = true
            return false
        }

        val elapsedMs = atMs - lastSampleAtMs
        if (elapsedMs < SAMPLE_INTERVAL_MS) return false

        val deltaX = x - lastX
        val deltaY = y - lastY
        val deltaZ = z - lastZ
        recordSample(x, y, z, atMs)

        val changePer100Ms =
            sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) / elapsedMs * 100.0

        if (changePer100Ms < THRESHOLD_M_S2_PER_100MS) return false

        if (burstCount == 0 || atMs - burstStartedAtMs > BURST_WINDOW_MS) {
            burstStartedAtMs = atMs
            burstCount = 1
        } else {
            burstCount++
        }

        if (burstCount < SAMPLES_TO_CONFIRM) return false

        val previousShakeAtMs = lastShakeAtMs
        if (previousShakeAtMs != null && atMs - previousShakeAtMs < COOLDOWN_MS) return false

        lastShakeAtMs = atMs
        burstCount = 0
        return true
    }

    private fun recordSample(x: Double, y: Double, z: Double, atMs: Long) {
        lastX = x
        lastY = y
        lastZ = z
        lastSampleAtMs = atMs
    }

    private companion object {
        const val SAMPLE_INTERVAL_MS = 100L

        /**
         * Per 100ms of elapsed time, so it does not depend on sample spacing.
         * Above a firm set-down, well below a deliberate shake, which runs
         * 25-40.
         */
        const val THRESHOLD_M_S2_PER_100MS = 12.0

        /**
         * The knob that actually stopped the false positives. A bump can clear
         * any magnitude bar worth having; only a shake can clear one four times
         * in a row.
         */
        const val SAMPLES_TO_CONFIRM = 4

        /** Four samples in 700ms is about 6Hz: an oscillation, not a sequence of knocks. */
        const val BURST_WINDOW_MS = 700L

        const val COOLDOWN_MS = 1500L
    }
}
