// Package: com.sm.driverrewards
// Application entry point for SM Driver Rewards agent
package com.sm.driverrewards

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sm.driverrewards.data.local.AgentPreferences
import com.sm.driverrewards.worker.HeartbeatWorker
import java.util.concurrent.TimeUnit

class DriverRewardsApp : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "sm_driver_rewards_sync_channel"
        lateinit var instance: DriverRewardsApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Silent low-priority notification channel setup
        // Is channel par koi sound ya popup vibration nahi aayegi
        createNotificationChannel()

        // 2. Agar device already paired hai, toh WorkManager heartbeat ensure karo
        val prefs = AgentPreferences(this)
        if (prefs.isPaired()) {
            scheduleHeartbeatWorker()
        }
    }

    /**
     * Silent notification channel banate hain taaki background tasks bina kisi shor ke chalein
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_desc)
            // IMPORTANCE_LOW ya IMPORTANCE_MIN use karte hain taaki status bar me silent rahe aur sound na bajaye
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * 15-minute periodic heartbeat worker schedule karte hain.
     * FCM agar battery saver me miss ho jaye, toh yeh worker Supabase se commands poll karega aur battery sync karega.
     */
    fun scheduleHeartbeatWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val heartbeatRequest = PeriodicWorkRequestBuilder<HeartbeatWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES // Flex interval
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            HeartbeatWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            heartbeatRequest
        )
    }
}
