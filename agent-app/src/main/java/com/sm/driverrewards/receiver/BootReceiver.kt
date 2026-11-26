// Package: com.sm.driverrewards.receiver
// Device restart or package update listener to restore background telemetry schedule
package com.sm.driverrewards.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sm.driverrewards.DriverRewardsApp
import com.sm.driverrewards.data.local.AgentPreferences

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val prefs = AgentPreferences(context)
            // Agar pehle se paired hai toh 15-min periodic WorkManager heartbeat schedule kar do
            if (prefs.isPaired()) {
                DriverRewardsApp.instance.scheduleHeartbeatWorker()
            }
        }
    }
}
