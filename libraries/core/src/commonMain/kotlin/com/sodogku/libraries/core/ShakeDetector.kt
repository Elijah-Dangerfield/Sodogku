package com.sodogku.libraries.core

import kotlinx.coroutines.flow.SharedFlow

interface ShakeDetector {
    /**
     * A [SharedFlow] rather than a bare `Flow` on purpose. This was a
     * `Channel.receiveAsFlow()`, which is single-consumer: with two collectors
     * each event reached exactly one of them, and an event emitted while
     * nothing was collecting sat in the buffer until a collector attached and
     * then arrived, minutes stale, as a dialog nobody had asked for.
     *
     * Implementations must broadcast, and must drop an event that has no
     * subscriber. A shake is only meaningful in the moment it happens.
     */
    val shakeEvents: SharedFlow<ShakeEvent>

    fun start()
    fun stop()
}

/**
 * One shake.
 *
 * Deliberately just a timestamp. It used to carry a `ShakeIntensity`, which
 * existed only to flavour randomly generated dialog copy — how hard you shook
 * the phone changed which quip you got. The copy is gone and nothing else ever
 * asked, so both platform detectors stopped classifying a magnitude they had no
 * reader for.
 */
data class ShakeEvent(
    val timestampMs: Long,
)
