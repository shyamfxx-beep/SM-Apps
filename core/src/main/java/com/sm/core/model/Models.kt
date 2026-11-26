// Package: com.sm.core.model
// Data models for Supabase Postgres serialization and network transfer
package com.sm.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Status of device agent
 */
object DeviceStatus {
    const val PENDING_PAIR = "pending_pair"
    const val ONLINE = "online"
    const val OFFLINE = "offline"
    const val BUSY = "busy"
}

/**
 * Commands types supported by the system
 */
object CommandType {
    const val GET_LOCATION = "get_location"
    const val START_RECORDING = "start_recording"
    const val STOP_RECORDING = "stop_recording"
    const val LIVE_TRACK = "live_track"
}

/**
 * Lifecycle states of a command
 */
object CommandStatus {
    const val PENDING = "pending"
    const val DELIVERED = "delivered"
    const val EXECUTING = "executing"
    const val COMPLETED = "completed"
    const val FAILED = "failed"
}

/**
 * Device table mapping
 * Agent aur Owner dono isi structure ko Supabase se query/update karte hain.
 */
@Serializable
data class Device(
    val id: String = "",
    @SerialName("owner_id") val ownerId: String = "",
    @SerialName("pairing_code") val pairingCode: String = "",
    val name: String = "",
    val model: String = "Android Device",
    @SerialName("android_version") val androidVersion: String = "16",
    @SerialName("battery_level") val batteryLevel: Int = 100,
    @SerialName("is_charging") val isCharging: Boolean = false,
    @SerialName("fcm_token") val fcmToken: String? = null,
    val status: String = DeviceStatus.PENDING_PAIR,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/**
 * Parameters contained inside command.payload jsonb
 */
@Serializable
data class CommandPayload(
    @SerialName("duration_minutes") val durationMinutes: Int = 5,
    @SerialName("high_accuracy") val highAccuracy: Boolean = false,
    @SerialName("interval_seconds") val intervalSeconds: Int = 10
)

/**
 * Command table mapping
 * Owner app command insert karta hai, agent FCM/Realtime se uthata hai.
 */
@Serializable
data class Command(
    val id: String = "",
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("owner_id") val ownerId: String = "",
    val type: String = "",
    val payload: String = "{}",
    val status: String = CommandStatus.PENDING,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/**
 * Location row mapping
 * Silent telemetry coordinates pushed by agent.
 */
@Serializable
data class LocationEntry(
    val id: String? = null,
    @SerialName("device_id") val deviceId: String,
    @SerialName("command_id") val commandId: String? = null,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float = 0f,
    val altitude: Double = 0.0,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    @SerialName("battery_level") val batteryLevel: Int = 100,
    @SerialName("is_charging") val isCharging: Boolean = false,
    val provider: String = "fused",
    @SerialName("captured_at") val capturedAt: String? = null
)

/**
 * Recording metadata row
 * Har 60 second ke audio chunk ki detail Supabase mein store hoti hai.
 */
@Serializable
data class RecordingEntry(
    val id: String? = null,
    @SerialName("device_id") val deviceId: String,
    @SerialName("command_id") val commandId: String? = null,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("duration_seconds") val durationSeconds: Int = 60,
    @SerialName("file_size_bytes") val fileSizeBytes: Long = 0,
    val format: String = "audio/mp4a-latm",
    @SerialName("chunk_index") val chunkIndex: Int = 0,
    @SerialName("recorded_at") val recordedAt: String? = null
)

/**
 * Result returned by pair_device_agent stored procedure
 */
@Serializable
data class PairingRpcResponse(
    val success: Boolean = false,
    val error: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("owner_id") val ownerId: String? = null,
    @SerialName("device_name") val deviceName: String? = null
)
