// TrainerScreen.kt - REFACTORED FOR LANDSCAPE
package com.di.feature_trainer.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
    viewModel: TrainerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

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
                }
            )
        },
        onDismissRequest = onDismiss
    )
}

/* ───────────────────────── Main Dialog Layout ─────────────────────────── */

@Composable
private fun TrainerDialog(
    uiState: TrainerUiState,
    isPostWorkout: Boolean,
    onDismiss: () -> Unit,
    onConclude: () -> Unit,
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
            Row(Modifier.fillMaxSize()) {
                // Left Panel: Transcript
                TranscriptPanel(
                    modifier = Modifier
                        .weight(0.65f)
                        .fillMaxHeight(),
                    uiState = uiState,
                    onDismiss = onDismiss
                )

                // Separator
                VerticalDivider(
                    Modifier
                        .fillMaxHeight()
                        .padding(vertical = 24.dp)
                )

                // Right Panel: Controls
                ControlsPanel(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight(),
                    uiState = uiState,
                    isPostWorkout = isPostWorkout,
                    onConclude = onConclude
                )
            }
        }
    }
}


/* ─────────────────────── Left Panel: Transcript ───────────────────────── */

@Composable
private fun TranscriptPanel(
    modifier: Modifier = Modifier,
    uiState: TrainerUiState,
    onDismiss: () -> Unit
) {
    Column(modifier.padding(24.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AI Trainer", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
        Spacer(Modifier.height(16.dp))

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
            if (uiState.transcript.isEmpty() && uiState.conversationState != ConversationState.CONNECTING) {
                Text(
                    "Say hello to start the conversation…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            } else {
                TranscriptList(
                    modifier = Modifier.fillMaxSize(),
                    transcript = uiState.transcript
                )
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
    onConclude: () -> Unit
) {
    Column(
        modifier = modifier.padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Status Text
        StatusIndicator(uiState)
        Spacer(Modifier.height(24.dp))

        // Microphone Button
        MicrophoneButton(uiState.isListening, uiState.isSpeaking)

        // Post-Workout Action Button
        if (isPostWorkout) {
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onConclude,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("Save & Finish", style = MaterialTheme.typography.bodyLarge)
            }
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
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = entry.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun ColumnScope.StatusIndicator(uiState: TrainerUiState) {
    val statusText = when {
        uiState.error != null -> "Error: ${uiState.error}"
        uiState.conversationState == ConversationState.CONNECTING -> "Connecting…"
        uiState.isExecutingTool -> "Thinking…"
        uiState.isSpeaking -> "Speaking…"
        uiState.isListening -> "Listening…"
        uiState.conversationState == ConversationState.ACTIVE -> "Ready"
        else -> "Session ended"
    }
    val statusColor = if (uiState.error != null) MaterialTheme.colorScheme.error else Color.Unspecified

    // Thinking indicator
    AnimatedVisibility(
        visible = uiState.isExecutingTool,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
    }
    Spacer(Modifier.height(12.dp))

    // Status text
    Text(
        text = statusText,
        style = MaterialTheme.typography.titleMedium,
        color = statusColor,
        textAlign = TextAlign.Center,
        modifier = Modifier.height(40.dp) // Reserve space to prevent layout shifts
    )
}


@Composable
private fun MicrophoneButton(isListening: Boolean, isSpeaking: Boolean) {
    val (icon, desc) = when {
        isSpeaking -> Icons.Default.VolumeUp to "AI speaking"
        isListening -> Icons.Default.Mic to "Listening"
        else -> Icons.Default.MicOff to "Mic off"
    }
    val (bgColor, iconColor) = when {
        isSpeaking -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
        isListening -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    FloatingActionButton(
        onClick = {}, // Button is for display only
        modifier = Modifier.size(80.dp),
        containerColor = bgColor,
        contentColor = iconColor,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp, pressedElevation = 12.dp)
    ) {
        Icon(icon, desc, Modifier.size(40.dp))
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
