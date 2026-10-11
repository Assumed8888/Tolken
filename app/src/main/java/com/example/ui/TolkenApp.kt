package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.core.engine.ModelDownloadState
import com.example.ui.components.ClauseHistoryList
import com.example.ui.components.HelpManualDialog
import com.example.ui.components.LatencyAuditDialog
import com.example.ui.components.LiveSpeechCard
import com.example.ui.components.SettingsSheet
import com.example.ui.components.TelemetryBar
import com.example.ui.components.TestModeDialog
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.TealAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TolkenApp(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val isRunning by viewModel.isRunning.collectAsState()
    val liveDanishPartial by viewModel.liveDanishPartial.collectAsState()
    val liveRussianLatest by viewModel.liveRussianLatest.collectAsState()
    val micRmsDb by viewModel.micRmsDb.collectAsState()
    val clauses by viewModel.clauses.collectAsState()
    val metrics by viewModel.metrics.collectAsState()
    val latencyRecords by viewModel.latencyRecords.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val modelState by viewModel.modelDownloadState.collectAsState()
    val audioRoute by viewModel.audioRouteStatus.collectAsState()
    val testPhrases by viewModel.testPhrases.collectAsState()
    val isTestRunning by viewModel.isTestRunning.collectAsState()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showLatencyDialog by remember { mutableStateOf(false) }
    var showTestModeDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    val speakerSource by viewModel.currentSpeaker.collectAsState()

    // Runtime Permission Launcher
    val requiredPermissions = mutableListOf(
        Manifest.permission.RECORD_AUDIO
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val recordAudioGranted = permissionsMap[Manifest.permission.RECORD_AUDIO] ?: false
        if (recordAudioGranted) {
            viewModel.toggleListening(context)
        }
    }

    fun handleStartStopClick() {
        if (isRunning) {
            viewModel.toggleListening(context)
        } else {
            val allGranted = requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            if (allGranted) {
                viewModel.toggleListening(context)
            } else {
                permissionLauncher.launch(requiredPermissions)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Tolken",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(TealAccent.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "da \u2192 ru",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TealAccent
                                )
                            }
                        }
                        Text(
                            text = "Синхронный перевод в наушники",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Test Mode action
                    IconButton(
                        onClick = { showTestModeDialog = true },
                        modifier = Modifier.testTag("test_mode_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = "Тестовый режим",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Latency / Audit action
                    IconButton(
                        onClick = { showLatencyDialog = true },
                        modifier = Modifier.testTag("latency_audit_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "Анализ задержки",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Help / Instructions action
                    IconButton(
                        onClick = { showHelpDialog = true },
                        modifier = Modifier.testTag("help_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Инструкция",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Settings action
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier.testTag("settings_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Настройки",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // Audio Routing Status Bar
            AudioRouteBadge(audioRouteStatus = audioRoute)

            Spacer(modifier = Modifier.height(10.dp))

            // Model Download Banner (if needed)
            when (val state = modelState) {
                is ModelDownloadState.Downloading -> {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = state.message, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                is ModelDownloadState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Ошибка загрузки языковых пакетов: ${state.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            Button(onClick = { viewModel.downloadModelNow() }) {
                                Text("Повторить")
                            }
                        }
                    }
                }
                else -> {}
            }

            // Big Start / Stop Button with Animated Ring
            BigStartStopButton(
                isRunning = isRunning,
                onClick = { handleStartStopClick() }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Telemetry Bar (Rate, Queue, Latency)
            TelemetryBar(metrics = metrics)

            Spacer(modifier = Modifier.height(12.dp))

            // Live Danish / Russian Speech Stream Card
            LiveSpeechCard(
                isListening = isRunning,
                liveDanishPartial = liveDanishPartial,
                liveRussianLatest = liveRussianLatest,
                micRmsDb = micRmsDb,
                speakerSource = speakerSource,
                ownVoiceThresholdDb = settings.ownVoiceRmsThresholdDb
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Clause History List
            ClauseHistoryList(
                clauses = clauses,
                modifier = Modifier.weight(1f)
            )
        }
    }

    // Bottom Sheets & Dialogs
    if (showHelpDialog) {
        HelpManualDialog(
            onDismiss = { showHelpDialog = false }
        )
    }

    if (showSettingsSheet) {
        SettingsSheet(
            settings = settings,
            onSaveSettings = { viewModel.updateSettings(it) },
            onDismiss = { showSettingsSheet = false }
        )
    }

    if (showLatencyDialog) {
        LatencyAuditDialog(
            metrics = metrics,
            records = latencyRecords,
            onExportCsv = { viewModel.shareCsvAudit(context) },
            onClearHistory = { viewModel.clearHistory() },
            onDismiss = { showLatencyDialog = false }
        )
    }

    if (showTestModeDialog) {
        TestModeDialog(
            testPhrases = testPhrases,
            isTestRunning = isTestRunning,
            onRunAllTests = { viewModel.runAllTestPhrases() },
            onRunSingleTest = { viewModel.runSingleTestPhrase(it) },
            onDismiss = { showTestModeDialog = false }
        )
    }
}

@Composable
fun AudioRouteBadge(
    audioRouteStatus: com.example.core.engine.AudioRouteStatus
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (audioRouteStatus.isBluetoothConnected)
                EmeraldSuccess.copy(alpha = 0.12f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (audioRouteStatus.isBluetoothConnected) Icons.Default.Headset else Icons.Default.Speaker,
                    contentDescription = "Audio output",
                    tint = if (audioRouteStatus.isBluetoothConnected) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Вывод звука: ${audioRouteStatus.activeDeviceName}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (audioRouteStatus.isLowLatencyActive) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(EmeraldSuccess)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "НИЗКАЯ ЗАДЕРЖКА",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                Text(
                    text = audioRouteStatus.deviceType,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun BigStartStopButton(
    isRunning: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isRunning) {
            // Pulsing background circle
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(CrimsonError.copy(alpha = 0.25f))
            )
        }

        Button(
            onClick = onClick,
            modifier = Modifier
                .height(56.dp)
                .fillMaxWidth(0.85f)
                .testTag("start_stop_button"),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) CrimsonError else TealAccent
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRunning) "Остановить" else "Начать",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isRunning) "ОСТАНОВИТЬ ПЕРЕВОД" else "НАЧАТЬ ПЕРЕВОД",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
