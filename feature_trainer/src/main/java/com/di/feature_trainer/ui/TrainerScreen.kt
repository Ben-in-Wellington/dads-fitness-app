// TrainerScreen.kt - REFACTORED FOR LANDSCAPE
package com.di.feature_trainer.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.di.feature_trainer.ConversationState
import com.di.feature_trainer.TrainerUiState
import com.di.feature_trainer.TrainerViewModel
import com.di.feature_trainer.TranscriptEntry
import kotlinx.coroutines.launch

/* ───────────────────────── Overlay Entry Point ────────────────────────── */

@Composable
fun TrainerOverlay(
    workoutSessionId: Long? = null,
    isPostWorkout: Boolean = false,
    onDismiss: () -> Unit,
    onStartSession: () -> Unit = {},
    viewModel: TrainerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    DisposableEffect(workoutSessionId, isPostWorkout) {
        // Start fresh conversation when overlay appears
        if (isPostWorkout) {
            viewModel.startPostWorkoutDebrief(workoutSessionId)
        } else {
            viewModel.startPreWorkoutChat(workoutSessionId)
        }

        onDispose {
            // Clean up when overlay closes
            viewModel.stopConversation()
        }
    }

    // Handle navigation events
    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                is TrainerViewModel.TrainerNavigationEvent.StartWorkoutSession -> {
                    viewModel.stopConversation()
                    onStartSession()
                    onDismiss()
                }
            }
        }
    }

    // Add error handling with retry
    if (uiState.conversationState == ConversationState.ERROR) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Connection Error") },
            text = { Text(uiState.error ?: "Failed to connect to AI service") },
            confirmButton = {
                Button(onClick = { viewModel.restartConversation() }) {
                    Text("Retry")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    } else {
        AudioPermissionHandler(
            onPermissionGranted = {
                // Start conversation once permission is granted
                LaunchedEffect(workoutSessionId, isPostWorkout) {
                    if (uiState.conversationState == ConversationState.IDLE) {
                        if (isPostWorkout)
                            viewModel.startPostWorkoutDebrief(workoutSessionId)
                        else
                            viewModel.startPreWorkoutChat(workoutSessionId)
                    }
                }

                TrainerDialog(
                    uiState = uiState,
                    isPostWorkout = isPostWorkout,
                    onDismiss = {
                        viewModel.stopConversation()
                        onDismiss()
                    },
                    onConclude = {
                        viewModel.concludeDebriefAndSaveNote()
                        onDismiss()
                    },
                    onReconnect = { viewModel.restartConversation() },
                    onMuteToggle = { viewModel.toggleMute() }
                )
            },
            onDismissRequest = onDismiss
        )
    }
}

/* ───────────────────────── Main Dialog Layout ─────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrainerDialog(
    uiState: TrainerUiState,
    isPostWorkout: Boolean,
    onDismiss: () -> Unit,
    onConclude: () -> Unit,
    onReconnect: () -> Unit,
    onMuteToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                // Top Bar with title and controls
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "AI Trainer",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    navigationIcon = {
                        // Exit button - always visible and prominent
                        FilledTonalIconButton(
                            onClick = onDismiss,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back to Dashboard")
                        }
                    },
                    actions = {
                        // Connection status badge
                        ConnectionStatusBadge(
                            state = uiState.conversationState,
                            onReconnect = onReconnect
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                HorizontalDivider()

                // Main content area
                Row(
                    Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    // Left Panel: Transcript
                    TranscriptPanel(
                        modifier = Modifier
                            .weight(0.65f)
                            .fillMaxHeight(),
                        uiState = uiState
                    )

                    // Separator
                    VerticalDivider(
                        Modifier
                            .fillMaxHeight()
                            .padding(vertical = 16.dp)
                    )

                    // Right Panel: Controls
                    ControlsPanel(
                        modifier = Modifier
                            .weight(0.35f)
                            .fillMaxHeight(),
                        uiState = uiState,
                        isPostWorkout = isPostWorkout,
                        onConclude = onConclude,
                        onMuteToggle = onMuteToggle
                    )
                }
            }
        }
    }
}

/* ─────────────────── Connection Status Badge ───────────────────────── */

@Composable
private fun ConnectionStatusBadge(
    state: ConversationState,
    onReconnect: () -> Unit
) {
    val (text, color, showReconnect) = when (state) {
        ConversationState.IDLE -> Triple("Idle", MaterialTheme.colorScheme.surfaceVariant, false)
        ConversationState.CONNECTING -> Triple("Connecting...", MaterialTheme.colorScheme.secondary, false)
        ConversationState.ACTIVE -> Triple("Connected", MaterialTheme.colorScheme.primary, false)
        ConversationState.ERROR -> Triple("Disconnected", MaterialTheme.colorScheme.error, true)
    }

    Row(
        modifier = Modifier.padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Status indicator dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )

        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Reconnect button when disconnected
        AnimatedVisibility(visible = showReconnect) {
            TextButton(
                onClick = onReconnect,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Reconnect",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Reconnect", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/* ─────────────────────── Left Panel: Transcript ───────────────────────── */

@Composable
private fun TranscriptPanel(
    modifier: Modifier = Modifier,
    uiState: TrainerUiState
) {
    Column(modifier.padding(24.dp)) {
        // Transcript Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.conversationState == ConversationState.CONNECTING -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Connecting to AI trainer...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                uiState.transcript.isEmpty() -> {
                    Text(
                        "Your AI trainer will greet you shortly…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    TranscriptList(
                        modifier = Modifier.fillMaxSize(),
                        transcript = uiState.transcript
                    )
                }
            }
        }
    }
}

/* ───────────────────── Right Panel: Controls & Status ─────────────────── */

@Composable
private fun ControlsPanel(
    modifier: Modifier = Modifier,
    uiState: TrainerUiState,
    isPostWorkout: Boolean,
    onConclude: () -> Unit,
    onMuteToggle: () -> Unit
) {
    Column(
        modifier = modifier.padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Enhanced Status Indicator
        StatusCard(uiState)

        Spacer(Modifier.height(32.dp))

        // Microphone Button
        MicrophoneButton(
            isListening = uiState.isListening,
            isSpeaking = uiState.isSpeaking,
            onMuteToggle = onMuteToggle,
            isMuted = uiState.isMuted
        )

        // Post-Workout Action Button
        if (isPostWorkout) {
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onConclude,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.secondary),
                enabled = uiState.conversationState == ConversationState.ACTIVE
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("Save & Finish", style = MaterialTheme.typography.bodyLarge)
            }
        }

        // Helper text
        Spacer(Modifier.height(16.dp))
        AnimatedVisibility(
            visible = uiState.conversationState == ConversationState.ACTIVE && !uiState.isSpeaking,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = if (uiState.isListening) "Listening..." else "AI is thinking...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/* ───────────────────── Enhanced Status Card ──────────────────────────── */

@Composable
private fun StatusCard(uiState: TrainerUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Activity indicator
            AnimatedVisibility(
                visible = uiState.isExecutingTool || uiState.conversationState == ConversationState.CONNECTING,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp
                )
            }

            if (uiState.isExecutingTool || uiState.conversationState == ConversationState.CONNECTING) {
                Spacer(Modifier.height(8.dp))
            }

            // Status text
            val statusText = when {
                uiState.error != null -> "Error: ${uiState.error}"
                uiState.conversationState == ConversationState.CONNECTING -> "Connecting…"
                uiState.isExecutingTool -> "Looking up information…"
                uiState.isSpeaking -> "Speaking…"
                uiState.isListening -> "Listening…"
                uiState.conversationState == ConversationState.ACTIVE -> "Ready"
                else -> "Session ended"
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = if (uiState.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/* ───────────────────────── UI Components ──────────────────────────────── */

@Composable
private fun TranscriptList(modifier: Modifier = Modifier, transcript: List<TranscriptEntry>) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Scroll to the bottom when a new message is added
    LaunchedEffect(transcript.size) {
        if (transcript.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(transcript.lastIndex) }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
    ) {
        items(transcript, key = { it.id }) { entry ->
            TranscriptBubble(entry)
        }
    }
}

@Composable
private fun TranscriptBubble(entry: TranscriptEntry) {
    val alignment = if (entry.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val colors = if (entry.isUser) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            colors = colors,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (entry.isUser) 16.dp else 4.dp,
                bottomEnd = if (entry.isUser) 4.dp else 16.dp
            )
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.bodyLarge
                )
                // Show "..." indicator for non-final messages
                AnimatedVisibility(visible = !entry.isFinal) {
                    Text(
                        text = "...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MicrophoneButton(
    isListening: Boolean,
    isSpeaking: Boolean,
    onMuteToggle: () -> Unit,
    isMuted: Boolean
) {
    val (icon, desc) = when {
        isSpeaking -> Icons.Default.VolumeUp to "AI speaking"
        isMuted -> Icons.Default.MicOff to "Mic muted"
        isListening -> Icons.Default.Mic to "Listening"
        else -> Icons.Default.MicOff to "Mic off"
    }
    val (bgColor, iconColor) = when {
        isSpeaking -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
        isMuted -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        isListening -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(contentAlignment = Alignment.Center) {
        // Pulsing animation for active states
        AnimatedVisibility(
            visible = (isListening || isSpeaking) && !isMuted,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(bgColor.copy(alpha = 0.2f))
            )
        }

        FloatingActionButton(
            onClick = onMuteToggle,
            modifier = Modifier.size(80.dp),
            containerColor = bgColor,
            contentColor = iconColor,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 12.dp
            )
        ) {
            Icon(icon, desc, Modifier.size(40.dp))
        }
    }
}

/* ───────────────────────── Permission Handling ────────────────────────── */

@Composable
private fun AudioPermissionHandler(
    onPermissionGranted: @Composable () -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(PermissionStatus.UNCHECKED) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED
    }

    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    when (status) {
        PermissionStatus.GRANTED -> onPermissionGranted()
        PermissionStatus.DENIED -> PermissionDeniedDialog(
            onConfirm = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                onDismissRequest()
            },
            onDismiss = onDismissRequest
        )
        PermissionStatus.UNCHECKED -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
                Text("Requesting microphone permission...", modifier = Modifier.padding(top = 60.dp))
            }
        }
    }
}

private enum class PermissionStatus { UNCHECKED, GRANTED, DENIED }

@Composable
private fun PermissionDeniedDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permission Required") },
        text = { Text("The AI Trainer requires microphone access to function. Please grant the permission in your device settings.") },
        confirmButton = { Button(onClick = onConfirm) { Text("Open Settings") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
