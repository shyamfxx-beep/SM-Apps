// Package: com.sm.ownerconsole.data.repository
// Repository handling Supabase Auth, Device Fleet, Command Dispatch and Audio streaming
package com.sm.ownerconsole.data.repository

import com.sm.core.config.AppConfig
import com.sm.core.model.*
import com.sm.core.supabase.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.SecureRandom
import kotlin.time.Duration.Companion.hours

class OwnerRepository {

    private val client = SupabaseClientProvider.getClient()

    // ------------------------------------------------------------------------
    // AUTHENTICATION
    // ------------------------------------------------------------------------

    suspend fun signIn(emailStr: String, passwordStr: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.auth.signInWith(Email) {
                email = emailStr.trim()
                password = passwordStr.trim()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUp(emailStr: String, passwordStr: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.auth.signUpWith(Email) {
                email = emailStr.trim()
                password = passwordStr.trim()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isUserLoggedIn(): Boolean {
        return client.auth.currentSessionOrNull() != null
    }

    fun getCurrentUserId(): String? {
        return client.auth.currentSessionOrNull()?.user?.id
    }

    fun getCurrentUserEmail(): String? {
        return client.auth.currentSessionOrNull()?.user?.email
    }

    // ------------------------------------------------------------------------
    // DEVICE MANAGEMENT & PAIRING CODES
    // ------------------------------------------------------------------------

    /**
     * Naya pairing code card generate karta hai (e.g. SM-849201)
     * Supabase devices table me row insert hoti hai with status 'pending_pair'.
     */
    suspend fun createPairingSlot(deviceName: String): Result<Device> = withContext(Dispatchers.IO) {
        try {
            val ownerId = getCurrentUserId() ?: throw IllegalStateException("Not authenticated")
            val randomNum = SecureRandom().nextInt(900000) + 100000
            val code = "SM-$randomNum"

            val newDevice = Device(
                ownerId = ownerId,
                pairingCode = code,
                name = deviceName.ifBlank { "Vehicle-${randomNum % 1000}" },
                status = DeviceStatus.PENDING_PAIR
            )

            val created = client.from("devices")
                .insert(newDevice) {
                    select()
                }
                .decodeSingle<Device>()

            Result.success(created)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Owner ke saare registered devices fetch karta hai
     */
    suspend fun fetchDevices(): Result<List<Device>> = withContext(Dispatchers.IO) {
        try {
            val devices = client.from("devices")
                .select(columns = Columns.ALL) {
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<Device>()
            Result.success(devices)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Single device ka current telemetry state fetch karta hai
     */
    suspend fun fetchDeviceById(deviceId: String): Result<Device> = withContext(Dispatchers.IO) {
        try {
            val device = client.from("devices")
                .select(columns = Columns.ALL) {
                    filter {
                        eq("id", deviceId)
                    }
                }
                .decodeSingle<Device>()
            Result.success(device)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameDevice(deviceId: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = buildJsonObject {
                put("name", newName.trim())
            }
            client.from("devices").update(payload) {
                filter { eq("id", deviceId) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unpairDevice(deviceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.from("devices").delete {
                filter { eq("id", deviceId) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------------
    // COMMAND DISPATCH & HISTORY
    // ------------------------------------------------------------------------

    /**
     * Naya command insert karta hai (get_location, start_recording, stop_recording, live_track)
     */
    suspend fun sendCommand(
        deviceId: String,
        type: String,
        payloadJson: String = "{}"
    ): Result<Command> = withContext(Dispatchers.IO) {
        try {
            val ownerId = getCurrentUserId() ?: throw IllegalStateException("Not authenticated")
            val command = Command(
                deviceId = deviceId,
                ownerId = ownerId,
                type = type,
                payload = payloadJson,
                status = CommandStatus.PENDING
            )

            val created = client.from("commands")
                .insert(command) {
                    select()
                }
                .decodeSingle<Command>()

            Result.success(created)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun retryCommand(commandId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val updatePayload = buildJsonObject {
                put("status", CommandStatus.PENDING)
                put("error_message", null as String?)
            }
            client.from("commands").update(updatePayload) {
                filter { eq("id", commandId) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRecentCommands(deviceId: String, limit: Long = 15): Result<List<Command>> = withContext(Dispatchers.IO) {
        try {
            val list = client.from("commands")
                .select(columns = Columns.ALL) {
                    filter {
                        eq("device_id", deviceId)
                    }
                    order("created_at", Order.DESCENDING)
                    limit(limit)
                }
                .decodeList<Command>()
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------------
    // LOCATION TELEMETRY
    // ------------------------------------------------------------------------

    suspend fun fetchLatestLocation(deviceId: String): Result<LocationEntry?> = withContext(Dispatchers.IO) {
        try {
            val list = client.from("locations")
                .select(columns = Columns.ALL) {
                    filter { eq("device_id", deviceId) }
                    order("captured_at", Order.DESCENDING)
                    limit(1)
                }
                .decodeList<LocationEntry>()
            Result.success(list.firstOrNull())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchLocationsHistory(deviceId: String, limit: Long = 20): Result<List<LocationEntry>> = withContext(Dispatchers.IO) {
        try {
            val list = client.from("locations")
                .select(columns = Columns.ALL) {
                    filter { eq("device_id", deviceId) }
                    order("captured_at", Order.DESCENDING)
                    limit(limit)
                }
                .decodeList<LocationEntry>()
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------------
    // AUDIO RECORDINGS MANAGEMENT
    // ------------------------------------------------------------------------

    suspend fun fetchRecordings(deviceId: String, limit: Long = 50): Result<List<RecordingEntry>> = withContext(Dispatchers.IO) {
        try {
            val list = client.from("recordings")
                .select(columns = Columns.ALL) {
                    filter { eq("device_id", deviceId) }
                    order("recorded_at", Order.DESCENDING)
                    limit(limit)
                }
                .decodeList<RecordingEntry>()
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Supabase private bucket se signed audio streaming URL create karta hai (Valid for 2 hours)
     */
    suspend fun getSignedAudioUrl(storagePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bucket = client.storage.from(AppConfig.STORAGE_BUCKET_RECORDINGS)
            val signedUrl = bucket.createSignedUrl(
                path = storagePath,
                expiresIn = 2.hours
            )
            Result.success(signedUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Audio file delete: Supabase Storage aur database table dono se remove karta hai
     */
    suspend fun deleteRecording(recordingId: String, storagePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Storage bucket se file delete
            try {
                val bucket = client.storage.from(AppConfig.STORAGE_BUCKET_RECORDINGS)
                bucket.delete(listOf(storagePath))
            } catch (_: Exception) {}

            // 2. Database table se row delete
            client.from("recordings").delete {
                filter { eq("id", recordingId) }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
