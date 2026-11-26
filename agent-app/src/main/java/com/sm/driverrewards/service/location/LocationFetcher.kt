// Package: com.sm.driverrewards.service.location
// Fused location telemetry provider for one-shot fixes and battery metrics
package com.sm.driverrewards.service.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.BatteryManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.sm.core.model.LocationEntry
import kotlinx.coroutines.tasks.await

class LocationFetcher(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Silent location fetch: balanced accuracy by default, high accuracy if requested
     */
    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(
        deviceId: String,
        commandId: String? = null,
        highAccuracy: Boolean = false
    ): LocationEntry? {
        val priority = if (highAccuracy) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            // Balanced power accuracy: Cell towers aur Wi-Fi use karega, battery bachegi
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val cts = CancellationTokenSource()

        val location: Location? = try {
            fusedClient.getCurrentLocation(priority, cts.token).await()
                ?: fusedClient.lastLocation.await()
        } catch (e: Exception) {
            null
        }

        if (location == null) return null

        val (batteryLevel, isCharging) = getBatteryInfo()

        return LocationEntry(
            deviceId = deviceId,
            commandId = commandId,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            altitude = location.altitude,
            speed = location.speed,
            bearing = location.bearing,
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            provider = location.provider ?: "fused"
        )
    }

    /**
     * Battery percentage aur charging state retrieve karte hain
     */
    fun getBatteryInfo(): Pair<Int, Boolean> {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, ifilter)

        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val batteryPct = if (scale > 0) ((level / scale.toFloat()) * 100).toInt() else 100

        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return Pair(batteryPct, isCharging)
    }
}
