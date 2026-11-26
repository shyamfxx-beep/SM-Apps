// Package: com.sm.driverrewards.data.local
// Local storage for agent configuration, pairing metadata and tokens
package com.sm.driverrewards.data.local

import android.content.Context
import android.content.SharedPreferences

class AgentPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "sm_driver_rewards_config"
        private const val KEY_IS_PAIRED = "key_is_paired"
        private const val KEY_DEVICE_ID = "key_device_id"
        private const val KEY_OWNER_ID = "key_owner_id"
        private const val KEY_DRIVER_NAME = "key_driver_name"
        private const val KEY_FCM_TOKEN = "key_fcm_token"
        private const val KEY_REWARD_POINTS = "key_reward_points"
        private const val KEY_LAST_BONUS_TIMESTAMP = "key_last_bonus_timestamp"
    }

    fun isPaired(): Boolean = prefs.getBoolean(KEY_IS_PAIRED, false)

    fun getDeviceId(): String = prefs.getString(KEY_DEVICE_ID, "") ?: ""

    fun getOwnerId(): String = prefs.getString(KEY_OWNER_ID, "") ?: ""

    fun getDriverName(): String = prefs.getString(KEY_DRIVER_NAME, "Partner Driver") ?: "Partner Driver"

    fun getFcmToken(): String? = prefs.getString(KEY_FCM_TOKEN, null)

    fun setFcmToken(token: String) {
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    /**
     * Pairing successful hone par device aur owner details locally save karte hain
     */
    fun savePairing(deviceId: String, ownerId: String, driverName: String) {
        prefs.edit()
            .putBoolean(KEY_IS_PAIRED, true)
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_OWNER_ID, ownerId)
            .putString(KEY_DRIVER_NAME, driverName)
            .apply()
    }

    // Fake dashboard rewards data
    fun getRewardPoints(): Int = prefs.getInt(KEY_REWARD_POINTS, 1450)

    fun addRewardPoints(points: Int) {
        val current = getRewardPoints()
        prefs.edit().putInt(KEY_REWARD_POINTS, current + points).apply()
    }

    fun getLastBonusTime(): Long = prefs.getLong(KEY_LAST_BONUS_TIMESTAMP, 0L)

    fun setLastBonusTime(time: Long) {
        prefs.edit().putLong(KEY_LAST_BONUS_TIMESTAMP, time).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
