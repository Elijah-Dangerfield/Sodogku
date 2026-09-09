package com.sodogku.libraries.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = ShakeDetector::class)
class AndroidShakeDetector(
    private val context: Context,
) : ShakeDetector, SensorEventListener {

    private val sensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private val accelerometer by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private val recognizer = ShakeRecognizer()

    private val events = MutableSharedFlow<ShakeEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val shakeEvents: SharedFlow<ShakeEvent> = events.asSharedFlow()

    override fun start() {
        val sensor = accelerometer ?: return
        recognizer.reset()
        sensorManager.registerListener(this, sensor, SAMPLING_PERIOD_US)
    }

    override fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val now = System.currentTimeMillis()
        val isShake = recognizer.onSample(
            x = event.values[0].toDouble(),
            y = event.values[1].toDouble(),
            z = event.values[2].toDouble(),
            atMs = now,
        )

        if (isShake) events.tryEmit(ShakeEvent(now))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        /**
         * Half the recognizer's sample interval, so a sample is always available
         * close to when the interval elapses. `SENSOR_DELAY_UI` is ~66ms, which
         * put consecutive accepted samples ~133ms apart and made the gesture's
         * sensitivity depend on where the delivery cadence happened to fall.
         */
        const val SAMPLING_PERIOD_US = 50_000
    }
}
