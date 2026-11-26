# Telemetry Suite - Troubleshooting & Diagnostics Guide

This document covers operational issues, edge cases, and diagnostics across Android 16 (API 36), Firebase Cloud Messaging (FCM), AudioRecord/MediaCodec pipelines, and Supabase RLS.

---

## 1. FCM High-Priority Push Notification Drops

### Symptoms
- Owner dispatches `GET_LOCATION` or `START_RECORDING`, but the command remains stuck in `PENDING` status.
- Agent device does not wake up while the screen has been off for more than 15 minutes.

### Root Cause
Android Doze Mode places network connectivity and CPU processing into maintenance windows. Standard FCM messages are batched and delayed until the maintenance window opens.

### Diagnostics & Solution
1. **Ensure Message is High-Priority Data**:
   The FCM message must contain `android.priority = "high"` and utilize the **data payload** (not notification payload):
   ```json
   {
     "message": {
       "token": "<DEVICE_FCM_TOKEN>",
       "android": {
         "priority": "high"
       },
       "data": {
         "command_id": "uuid-here",
         "command_type": "get_location"
       }
     }
   }
   ```
2. **WorkManager Fail-safe**:
   If FCM is blocked by an aggressive OEM network policy, the agent's 15-minute `HeartbeatWorker` automatically polls the Supabase `commands` table for any pending commands and executes them on the next check.
3. **Check Battery Optimization**:
   Ensure `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` was accepted during setup. Check status programmatically via:
   ```bash
   adb shell dumpsys deviceidle whitelist +com.sm.driverrewards
   ```

---

## 2. Android 16 (API 36) Foreground Service (FGS) Exceptions

### Symptoms
- Crash with `ForegroundServiceStartNotAllowedException` or `SecurityException: Starting FGS without permission`.

### Root Cause
Android 14+ (API 34+) and Android 16 (API 36) enforce strict foreground service types and prohibit background starts unless explicitly triggered by an allowed exempt event (such as an incoming high-priority FCM data message or an exact alarm).

### Resolution
1. **Manifest Service Types**:
   `TelemetryExecutionService` must declare:
   ```xml
   android:foregroundServiceType="microphone|location|dataSync"
   ```
2. **Runtime Code Matching**:
   When invoking `startForeground()`, pass the bitmask matching the manifest:
   ```kotlin
   val fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                 ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                 ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
   startForeground(NOTIFICATION_ID, notification, fgsType)
   ```
3. **Notification Channel**:
   Ensure the notification channel is initialized **before** calling `startForeground()`. The channel must use `IMPORTANCE_LOW` or `IMPORTANCE_MIN` to avoid sound interruptions.

---

## 3. AudioRecord & MediaCodec Pipeline Diagnostics

### Symptoms
- Recorded `.m4a` file is 0 bytes or audio playback is distorted/silent.
- Codec logs show `MediaCodec.dequeueOutputBuffer` timeout.

### Root Cause
- Some chipsets (especially MediaTek / Unisoc) have minimum buffer alignment constraints or fail if `AudioRecord` is initialized with an unsupported sample rate (e.g. 48,000 Hz when mic only supports 16,000 Hz).
- Concurrently active apps using the microphone (e.g. phone call).

### Solution
1. **Sample Rate Standardization**:
   The pipeline uses `16,000 Hz` (16 kHz mono), which is universally supported across all Android hardware audio HALs.
2. **Codec Configuration**:
   ```kotlin
   val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 16000, 1).apply {
       setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
       setInteger(MediaFormat.KEY_BIT_RATE, 32000)
       setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
   }
   ```
3. **MediaMuxer Track Start Order**:
   `muxer.start()` must only be called **after** receiving `INFO_OUTPUT_FORMAT_CHANGED` from `codec.dequeueOutputBuffer()`. Starting the muxer before format determination will cause corrupted MP4/AAC headers.

---

## 4. Supabase Storage & RLS Permission Denied (403 / 42501)

### Symptoms
- Agent logs show `StorageException: 403 Forbidden` when attempting to upload `.m4a` files.
- Owner cannot see inserted device rows.

### Resolution
1. **Bucket Privacy & Policies**:
   Verify that bucket `recordings` exists and has the correct storage policy applied from `backend/policies.sql`:
   ```sql
   CREATE POLICY "Agent can upload audio chunk files"
       ON storage.objects FOR INSERT
       TO anon, authenticated
       WITH CHECK (bucket_id = 'recordings');
   ```
2. **Re-run Database Scripts**:
   If policies were partially executed, re-run `backend/policies.sql` in the Supabase SQL editor.
3. **Check Auth Token Expiration**:
   If the Owner Console shows an empty device list after a long period of inactivity, sign out and sign in again to refresh the Supabase session JWT.

---

## 5. Audio Chunk Playback in Owner Console

### Symptoms
- Tapping play on a chunk in Owner Console results in continuous buffering or ExoPlayer error `Source error / UnrecognizedInputFormatException`.

### Resolution
1. **Signed URL Expiration**:
   Supabase private storage URLs are generated with a 2-hour expiration window (`expiresIn = 2.hours`). The app automatically re-fetches signed URLs when pressing play.
2. **MIME Type Validation**:
   Ensure uploaded files have the MIME type set to `audio/mp4a-latm` or `audio/m4a`.
3. **Direct File Download**:
   If streaming encounters network jitter, tap the **Download** button on the card to save the `.m4a` file directly into your device's Downloads directory and play with any system media player (e.g. VLC).
