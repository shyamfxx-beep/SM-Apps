// Package: com.sm.ownerconsole.ui.devices
// Fleet dashboard listing active devices, battery metrics and pairing generator
package com.sm.ownerconsole.ui.devices

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sm.core.model.Device
import com.sm.core.model.DeviceStatus
import com.sm.ownerconsole.data.repository.OwnerRepository
import com.sm.ownerconsole.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    onSelectDevice: (String) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { OwnerRepository() }
    val coroutineScope = rememberCoroutineScope()

    var devices by remember { mutableStateOf<List<Device>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showPairDialog by remember { mutableStateOf(false) }
    var newDeviceName by remember { mutableStateOf("") }
    var generatedCode by remember { mutableStateOf<String?>(null) }

    fun refreshDevices() {
        isLoading = true
        coroutineScope.launch {
            val result = repository.fetchDevices()
            isLoading = false
            if (result.isSuccess) {
                devices = result.getOrNull().orEmpty()
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshDevices()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(CyanAccent, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "FLEET RADAR",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextWhite,
                            fontSize = 17.sp,
                            letterSpacing = 1.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { refreshDevices() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = SkyAccent)
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            repository.signOut()
                            onLogout()
                        }
                    }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = StatusRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    newDeviceName = ""
                    generatedCode = null
                    showPairDialog = true
                },
                containerColor = CyanAccent,
                contentColor = DarkBg,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = {
                    Text(
                        "PAIR NEW AGENT",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkBg)
        ) {
            if (isLoading && devices.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CyanAccent)
                }
            } else if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.SensorsOff,
                            contentDescription = null,
                            tint = DarkBorder,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "NO ACTIVE AGENTS FOUND",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Click 'PAIR NEW AGENT' to generate a pairing code for the disguised rewards app.",
                            color = TextMuted,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        // Summary bar
                        Surface(
                            color = DarkSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "MONITORED NODES: ${devices.size}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                                Text(
                                    text = "OPERATOR: ${repository.getCurrentUserEmail() ?: "Active"}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = CyanAccent
                                )
                            }
                        }
                    }

                    items(devices, key = { it.id }) { device ->
                        DeviceCard(
                            device = device,
                            onClick = { onSelectDevice(device.id) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            }
        }
    }

    // Pair Dialog
    if (showPairDialog) {
        AlertDialog(
            onDismissRequest = { showPairDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    "PAIR NEW AGENT DEVICE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (generatedCode == null) {
                        Text(
                            "Enter an optional alias for this vehicle or agent before generating a 6-digit fleet code:",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                        OutlinedTextField(
                            value = newDeviceName,
                            onValueChange = { newDeviceName = it },
                            label = { Text("Agent Alias") },
                            placeholder = { Text("e.g. Route Van 3") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanAccent,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "PAIRING CODE GENERATED",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = StatusGreen
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkBg, RoundedCornerShape(8.dp))
                                    .border(1.dp, CyanAccent, RoundedCornerShape(8.dp))
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = generatedCode ?: "",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CyanAccent,
                                    letterSpacing = 4.sp
                                )
                            }
                            Text(
                                "Enter this code inside the SM Driver Rewards activation screen on target phone.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (generatedCode == null) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val result = repository.createPairingSlot(newDeviceName)
                                if (result.isSuccess) {
                                    val dev = result.getOrNull()
                                    generatedCode = dev?.pairingCode
                                    refreshDevices()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                    ) {
                        Text("GENERATE CODE", color = DarkBg, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Pairing Code", generatedCode)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                            showPairDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                    ) {
                        Text("COPY & CLOSE", color = DarkBg, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showPairDialog = false }) {
                    Text("CANCEL", color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun DeviceCard(
    device: Device,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${device.model} • Android ${device.androidVersion}",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }

                StatusPill(status = device.status)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = DarkBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Battery & Charging
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (device.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                        contentDescription = null,
                        tint = if (device.batteryLevel > 20) StatusGreen else StatusRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${device.batteryLevel}%${if (device.isCharging) " (Charging)" else ""}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TextWhite
                    )
                }

                // Last Seen
                Text(
                    text = "LAST PING: ${formatLastSeen(device.lastSeenAt)}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
fun StatusPill(status: String) {
    val (bgColor, textColor, label) = when (status) {
        DeviceStatus.ONLINE -> Triple(StatusGreen.copy(alpha = 0.15f), StatusGreen, "ONLINE")
        DeviceStatus.BUSY -> Triple(StatusAmber.copy(alpha = 0.15f), StatusAmber, "BUSY")
        DeviceStatus.PENDING_PAIR -> Triple(CyanAccent.copy(alpha = 0.15f), CyanAccent, "WAITING PAIR")
        else -> Triple(Color(0xFF334155), TextMuted, "OFFLINE")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

fun formatLastSeen(timestamp: String?): String {
    if (timestamp.isNullOrBlank()) return "Never"
    return try {
        timestamp.replace("T", " ").take(16)
    } catch (_: Exception) {
        timestamp
    }
}
