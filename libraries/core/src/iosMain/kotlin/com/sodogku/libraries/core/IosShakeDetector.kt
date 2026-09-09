package com.sodogku.libraries.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.tatarka.inject.annotations.Inject
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSDate
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSTimeInterval
import platform.Foundation.timeIntervalSince1970
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@OptIn(ExperimentalForeignApi::class)
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosShakeDetector : ShakeDetector {

    private val motionManager = CMMotionManager()
    private val recognizer = ShakeRecognizer()

    private val events = MutableSharedFlow<ShakeEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val shakeEvents: SharedFlow<ShakeEvent> = events.asSharedFlow()

    override fun start() {
        if (!motionManager.accelerometerAvailable) return

        recognizer.reset()
        motionManager.accelerometerUpdateInterval = UPDATE_INTERVAL
        motionManager.startAccelerometerUpdatesToQueue(NSOperationQueue.mainQueue) { data, _ ->
            data?.acceleration?.useContents { handleSample(x, y, z) }
        }
    }

    override fun stop() {
        motionManager.stopAccelerometerUpdates()
    }

    private fun handleSample(xG: Double, yG: Double, zG: Double) {
        val now = (NSDate().timeIntervalSince1970 * 1000).toLong()
        val isShake = recognizer.onSample(
            x = xG * STANDARD_GRAVITY_M_S2,
            y = yG * STANDARD_GRAVITY_M_S2,
            z = zG * STANDARD_GRAVITY_M_S2,
            atMs = now,
        )

        if (isShake) events.tryEmit(ShakeEvent(now))
    }

    private companion object {
        /**
         * Half the recognizer's sample interval, matching Android — CoreMotion
         * delivers on the interval it is given, and the recognizer does its own
         * gating on top.
         */
        const val UPDATE_INTERVAL: NSTimeInterval = 0.05

        /** CoreMotion reports acceleration in g; the recognizer works in m/s². */
        const val STANDARD_GRAVITY_M_S2 = 9.80665
    }
}
