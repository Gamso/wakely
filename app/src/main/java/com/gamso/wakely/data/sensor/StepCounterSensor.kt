package com.gamso.wakely.data.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.gamso.wakely.domain.repository.StepSensor
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reads the hardware step counter once (it keeps counting without the app). The sensor reports the steps since the
 * phone booted, as soon as a listener is registered; no foreground service and no continuous listener are needed.
 */
class StepCounterSensor(private val context: Context) : StepSensor {

    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? get() = manager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    override fun isAvailable(): Boolean = sensor != null

    override fun hasPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
        PackageManager.PERMISSION_GRANTED

    override suspend fun readSteps(): Long? {
        val manager = manager ?: return null
        val sensor = sensor ?: return null
        if (!hasPermission()) return null
        return withTimeoutOrNull(READ_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        manager.unregisterListener(this)
                        if (continuation.isActive) continuation.resume(event.values.firstOrNull()?.toLong())
                    }

                    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
                }
                val registered = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                if (!registered && continuation.isActive) continuation.resume(null)
                continuation.invokeOnCancellation { manager.unregisterListener(listener) }
            }
        }
    }

    private companion object {
        const val READ_TIMEOUT_MS = 3_000L
    }
}
