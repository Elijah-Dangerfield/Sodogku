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
 * The magnitude bar is deliberately unchanged from what shipped: the old Android
 * detector compared `magnitude / timeDiffMs * 10000` against 800, which is the
 * same bar as 8.0 m/s² per 100ms. What changed is that it is now reached the
 * same way regardless of how far apart two samples happened to land, and that
 * clearing it once is no longer enough.
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
        const val THRESHOLD_M_S2_PER_100MS = 8.0
        const val SAMPLES_TO_CONFIRM = 2
        const val BURST_WINDOW_MS = 1000L
        const val COOLDOWN_MS = 1500L
    }
}
