// DashboardScreen.kt

package com.di.fitric.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.di.core.bluetooth.model.BleConnectionState
import com.di.feature_session.BluetoothViewModel
import com.di.feature_session.NavigationEvent
import com.di.feature_session.SessionUiState   // ← correct public model
import com.di.feature_session.SessionViewModel
import com.di.feature_session.ui.BluetoothDeviceDialog
import com.di.feature_session.ui.BluetoothPermissionHandler
import com.di.feature_trainer.ui.TrainerOverlay

/**
 * Permission-gated entry point – once BLE permission is granted,
 * [DashboardContent] is shown.
 */
@Composable
fun DashboardScreen(
    onNavigateToSurvey: (Long) -> Unit,
    onNavigateToRadio:  () -> Unit,
    sessionViewModel:   SessionViewModel   = hiltViewModel(),
    bluetoothViewModel: BluetoothViewModel = hiltViewModel()
) {
    var permissionsGranted by remember { mutableStateOf(false) }

    BluetoothPermissionHandler(
        onPermissionsGranted = {
            permissionsGranted = true
            bluetoothViewModel.startScanning()
        }
    ) {
        DashboardContent(
            onNavigateToSurvey = onNavigateToSurvey,
            onNavigateToRadio  = onNavigateToRadio,
            sessionViewModel   = sessionViewModel,
            bluetoothViewModel = bluetoothViewModel,
            permissionsGranted = permissionsGranted
        )
    }
}

/* ----------------------------------------------------------------------------
   DASHBOARD  (stats + controls)  – owns all overlays / dialogs
---------------------------------------------------------------------------- */
@Composable
private fun DashboardContent(
    onNavigateToSurvey: (Long) -> Unit,
    onNavigateToRadio:  () -> Unit,
    sessionViewModel:   SessionViewModel,
    bluetoothViewModel: BluetoothViewModel,
    permissionsGranted: Boolean
) {
    /* -------- VM state -------- */
    val uiState      by sessionViewModel.uiState.collectAsState()
    val devices      by bluetoothViewModel.availableDevices.collectAsState()
    val isScanning   by bluetoothViewModel.isScanning.collectAsState()

    /* -------- local UI state -------- */
    var showDeviceDialog   by remember { mutableStateOf(false) }
    var trainerOverlayInfo by remember { mutableStateOf<Pair<Long?, Boolean>?>(null) }
    var debriefCandidateId by remember { mutableStateOf<Long?>(null) }
    var showDebriefDialog  by remember { mutableStateOf(false) }

    /* ── 1. navigation event -> survey ───────────────────────────── */
    LaunchedEffect(Unit) {
        sessionViewModel.navigationEvent.collect { event ->
            if (event is NavigationEvent.NavigateToSurvey) {
                onNavigateToSurvey(event.sessionId)
            }
        }
    }

    /* ── 2. get “survey finished” result from NavHost ─────────────── */
    val navController = LocalNavigation.current
    LaunchedEffect(Unit) {
        navController?.currentBackStackEntry
            ?.savedStateHandle
            ?.getStateFlow<Long?>("completedSessionId", null)
            ?.collect { sid ->
                sid ?: return@collect
                debriefCandidateId = sid
                showDebriefDialog  = true
                navController.currentBackStackEntry
                    ?.savedStateHandle?.set("completedSessionId", null)
            }
    }

    /* ── 3. MAIN CONTENT ─────────────────────────────────────────── */
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            StatsColumn(uiState)
            ControlColumn(
                uiState            = uiState,
                onStart            = sessionViewModel::startSession,
                onStop             = sessionViewModel::stopSession,
                onRadio            = onNavigateToRadio,
                onTrainer          = { trainerOverlayInfo = uiState.sessionId to false }
            )
        }

        ConnectionStatusBadge(
            modifier           = Modifier.align(Alignment.TopEnd),
            connectionState    = uiState.bleConnectionState,
            permissionsGranted = permissionsGranted,
            onConnectClick     = {
                if (uiState.bleConnectionState !is BleConnectionState.Connected) {
                    showDeviceDialog = true
                    bluetoothViewModel.startScanning()
                }
            }
        )
    }

    /* ── 4. Dialogs / Overlays ───────────────────────────────────── */
    if (showDeviceDialog && permissionsGranted &&
        uiState.bleConnectionState !is BleConnectionState.Connected
    ) {
        BluetoothDeviceDialog(
            devices         = devices,
            isScanning      = isScanning,
            onDeviceSelected= {
                bluetoothViewModel.connectToDevice(it.address)
                showDeviceDialog = false
            },
            onDismiss       = { showDeviceDialog = false },
            onScanClick     = bluetoothViewModel::startScanning
        )
    }

    if (showDebriefDialog) {
        DebriefDialog(
            onConfirm = {
                trainerOverlayInfo = debriefCandidateId to true
                debriefCandidateId = null
                showDebriefDialog  = false
            },
            onDismiss = {
                debriefCandidateId = null
                showDebriefDialog  = false
            }
        )
    }

    trainerOverlayInfo?.let { (sid, post) ->
        TrainerOverlay(
            workoutSessionId = sid,
            isPostWorkout    = post,
            onDismiss        = { trainerOverlayInfo = null }
        )
    }
}

/* ==========  LEFT column: stats =================================================== */
@Composable
private fun RowScope.StatsColumn(state: SessionUiState) {   // RowScope needed for .weight
    Column(
        modifier = Modifier
            .weight(1.5f)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        /* TIME */
        Text(
            state.displayTime,
            fontSize   = 80.sp,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(24.dp))

        /* DISTANCE / CADENCE / SPEED */
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LargeStatDisplay("Distance", "%.2f".format(state.estimatedDistance), "km", fontSize = 36.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                LargeStatDisplay(
                    "Cadence",
                    state.currentCadence.toString(),
                    "rpm",
                    isHighlighted = state.currentCadence > 0,
                    fontSize = 32.sp
                )
                LargeStatDisplay("Speed", "%.1f".format(state.currentSpeed), "km/h", fontSize = 32.sp)
            }
        }

        if (state.isActive && state.averageCadence > 0) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Average: %.1f rpm".format(state.averageCadence),
                fontSize = 18.sp,
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

/* ==========  RIGHT column: controls ============================================== */
@Composable
private fun RowScope.ControlColumn(
    uiState: SessionUiState,
    onStart: () -> Unit,
    onStop:  () -> Unit,
    onRadio: () -> Unit,
    onTrainer: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        /* START / STOP -------------------------------------------------- */
        val (label, colour, click) = if (!uiState.isActive) {
            Triple("START", MaterialTheme.colorScheme.primary, onStart)
        } else {
            Triple("STOP",  MaterialTheme.colorScheme.error,   onStop)
        }

        Button(
            onClick  = click,
            modifier = Modifier.size(width = 240.dp, height = 100.dp),
            colors   = ButtonDefaults.buttonColors(colour),
            shape    = RoundedCornerShape(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                if (!uiState.isActive && uiState.bleConnectionState !is BleConnectionState.Connected) {
                    Text("Manual mode", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f))
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        /* AI trainer + RADIO ------------------------------------------- */
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick  = onTrainer,
                modifier = Modifier
                    .width(220.dp)
                    .height(60.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Icon(Icons.Default.RecordVoiceOver, null, Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("AI Trainer", fontSize = 18.sp)
            }

            Button(
                onClick  = onRadio,
                modifier = Modifier
                    .width(180.dp)
                    .height(50.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Icon(Icons.Default.Radio, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Radio", fontSize = 16.sp)
            }
        }
    }
}

/* ==========  Debrief dialog ======================================================= */
@Composable
private fun DebriefDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title           = { Text("Workout complete!", style = MaterialTheme.typography.headlineSmall) },
        text            = { Text("Great job!  Would you like to discuss your session with the AI trainer?") },
        confirmButton   = { Button(onClick = onConfirm) { Text("Yes, let's talk") } },
        dismissButton   = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}

/* ==========  Pretty stat  ========================================================= */
@Composable
private fun LargeStatDisplay(
    label: String,
    value: String,
    unit:  String,
    isHighlighted: Boolean = false,
    fontSize: androidx.compose.ui.unit.TextUnit = 28.sp
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .6f))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                fontSize   = fontSize,
                fontWeight = FontWeight.Bold,
                color      = if (isHighlighted)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.width(4.dp))
            Text(
                unit,
                fontSize = fontSize * .6f,
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = .7f)
            )
        }
    }
}

/* ==========  BLE badge  =========================================================== */
@Composable
private fun ConnectionStatusBadge(
    modifier: Modifier = Modifier,
    connectionState: BleConnectionState,
    permissionsGranted: Boolean,
    onConnectClick: () -> Unit
) {
    val (bg, icon, text) = when {
        !permissionsGranted                     -> Triple(MaterialTheme.colorScheme.errorContainer, Icons.Default.Error,               "Permission")
        connectionState is BleConnectionState.Connected  -> Triple(MaterialTheme.colorScheme.primaryContainer,   Icons.Default.Bluetooth,         "Connected")
        connectionState is BleConnectionState.Connecting -> Triple(MaterialTheme.colorScheme.secondaryContainer, Icons.Default.BluetoothSearching,"Connecting")
        connectionState is BleConnectionState.Error      -> Triple(MaterialTheme.colorScheme.errorContainer,     Icons.Default.BluetoothDisabled, "Error")
        else                                             -> Triple(MaterialTheme.colorScheme.surfaceVariant,     Icons.Default.BluetoothDisabled, "No sensor")
    }

    Card(
        modifier = modifier.clickable(
            enabled = connectionState !is BleConnectionState.Connected && permissionsGranted,
            onClick = onConnectClick
        ),
        colors   = CardDefaults.cardColors(bg),
        elevation= CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, Modifier.size(16.dp))
            Text(text, fontSize = 12.sp)
        }
    }
}