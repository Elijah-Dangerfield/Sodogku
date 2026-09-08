package com.sodogku.libraries.core

import kotlinx.coroutines.flow.Flow

interface ShakeDetector {
    val shakeEvents: Flow<ShakeEvent>

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
