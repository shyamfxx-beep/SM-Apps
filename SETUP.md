# Telemetry Suite - Deployment & OEM Configuration Guide

This guide details the step-by-step provisioning flow for target Android devices running **SM Driver Rewards** (`com.sm.driverrewards`). Because aggressive vendor battery management algorithms kill background services, specific settings must be toggled on each OEM brand to guarantee seamless FCM wake-up and location/audio execution.

---

## 1. Initial Device Provisioning Flow

1. **Install APK**:
   - Transfer `SM-DriverRewards-release.apk` to the target device.
   - Install using Package Installer.
2. **Launch Application**:
   - Open **SM Driver Rewards** from the home screen / app drawer.
   - The app opens directly to the **SM Driver Partner Activation** screen.
3. **Fill Partner Details**:
   - Enter driver name (e.g., `Driver Partner`).
   - Enter the 6-digit **Fleet Pairing Code** generated in the Owner Console (e.g., `SM-849201`).
4. **Grant Core Android Permissions**:
   - **Notifications**: Tap "Grant" -> Allow.
   - **Voice Notes & Audio**: Tap "Grant" -> Allow while using the app.
   - **Precise Location**: Tap "Grant" -> Allow while using the app (Precise).
   - **Background Location**: Tap "Grant" -> Choose **"Allow all the time"** in system settings.
   - **Battery Unrestricted**: Tap "Grant" -> Disable battery optimization for the app.
5. **Activate**:
   - Tap **"Connect & Activate Rewards"**.
   - Upon successful verification with Supabase, the app transitions immediately to the **Rewards Dashboard**.

---

## 2. Per-OEM Permission Checklist

### 1. Xiaomi / Redmi / POCO (MIUI / HyperOS)
MIUI employs aggressive background freezes that block FCM broadcast receivers if not whitelisted.

1. **Autostart Permission**:
   - Open `Settings` -> `Apps` -> `Manage Apps` -> `SM Driver Rewards`.
   - Enable **Autostart** toggle.
   - Tap **Autostart settings** -> Enable **Allow app to be started by other apps**.
2. **Battery Saver Configuration**:
   - Under App Info, tap **Battery Saver**.
   - Change selection from *MIUI Battery Saver (recommended)* to **No Restrictions**.
3. **Background Pop-ups**:
   - In App Info, tap **Other Permissions**.
   - Set **Display pop-up windows while running in the background** to **Always Allow**.
   - Set **Show on Lock screen** to **Allow**.
4. **Lock in Recent Apps**:
   - Open Recent Apps (swipe up and hold).
   - Long press on **SM Driver Rewards** card and tap the **Padlock** icon so it remains locked in memory.

---

### 2. Oppo / Realme (ColorOS / Realme UI)
ColorOS restricts network and wake locks when the screen is turned off for more than 15 minutes.

1. **Auto-Launch & Background Activity**:
   - Open `Settings` -> `Apps` -> `App Management` -> `SM Driver Rewards`.
   - Tap **Battery Usage**.
   - Enable **Allow foreground activity**, **Allow background activity**, and **Allow auto-launch**.
2. **Power Saver Exemption**:
   - Open `Settings` -> `Battery` -> `More Settings` -> `App Battery Management`.
   - Select `SM Driver Rewards` -> Enable **Run in background**.
3. **Lock App**:
   - Open Recent Tasks -> Tap the three dots (or long press) -> Select **Lock**.

---

### 3. Samsung Galaxy (OneUI 5.x / 6.x / 7.x)
OneUI automatically puts unused apps into "Deep Sleep", disabling push notification receivers.

1. **Battery Unrestricted**:
   - Open `Settings` -> `Apps` -> `SM Driver Rewards`.
   - Tap **Battery** -> Change from *Optimized* to **Unrestricted**.
2. **Never Sleeping Apps**:
   - Open `Settings` -> `Battery` -> `Background usage limits`.
   - Ensure `Put unused apps to sleep` is NOT affecting the app.
   - Tap **Never sleeping apps** -> Tap `+` -> Add **SM Driver Rewards**.
3. **Background Data Allowance**:
   - In App Info, tap **Mobile Data**.
   - Enable **Allow background data usage** and **Allow data usage while Data saver is on**.

---

### 4. Vivo / iQOO (Funtouch OS / OriginOS)
Vivo applies strict process termination to apps using microphone in the background unless marked for high power consumption.

1. **High Background Power Consumption**:
   - Open `Settings` -> `Battery` -> `Background power consumption management`.
   - Find `SM Driver Rewards` and select **High background power consumption**.
2. **Autostart & Associated Startup**:
   - Open `iManager` app -> `App management` -> `Permission management` -> `Autostart`.
   - Enable `SM Driver Rewards`.
   - Also enable **Associated startup** if available.
3. **App Keep-Alive**:
   - Open App Info -> `Permissions` -> Enable **Display on Lock screen** and **Background pop-up**.

---

### 5. OnePlus (OxygenOS)
OxygenOS utilizes "Sleep Standby Optimization" which severs persistent network sockets during overnight hours.

1. **Battery Optimization**:
   - Open `Settings` -> `Battery` -> `Advanced Settings` -> `Optimize battery use`.
   - Find `SM Driver Rewards` -> Select **Don't optimize**.
2. **Disable Sleep Standby Optimization**:
   - In `Battery` -> `Advanced Settings`, toggle OFF **Sleep standby optimization** if device misses scheduled overnight tasks.
3. **Lock App in Task Switcher**:
   - Swipe up to Recents -> Tap three dots on upper right corner of app preview -> Select **Lock**.

---

## 3. Provisioning Verification Test

After completing the checklist, verify end-to-end functionality:
1. Open the **Owner Console** on your monitoring phone.
2. Select the newly paired device from the Fleet Radar.
3. Ensure status displays **ONLINE** with accurate battery percentage.
4. Tap **GET LOCATION** -> Confirm that a fresh coordinate fix arrives within 5 seconds.
5. Tap **START RECORD (5 m)** -> Wait 65 seconds -> Confirm that **Chunk #0 (60s)** appears in the recordings vault and plays with clear audio.
