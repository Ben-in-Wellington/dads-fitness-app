package com.di.feature_trainer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.di.feature_trainer.AITrainerSettingsViewModel

/**
 * Composable screen for configuring AI Trainer settings.
 * Allows users to choose personality, motivation level, voice speed, and behavior toggles.
 *
 * @param onBack Callback to navigate back to the previous screen.
 * @param viewModel The Hilt-injected ViewModel for AI Trainer settings.
 */
@Composable
fun AITrainerSettingsScreen(
    onBack: () -> Unit,
    viewModel: AITrainerSettingsViewModel = hiltViewModel()
) {
    // Collect state from the ViewModel for reactive UI updates
    val trainerPersonality by viewModel.trainerPersonality.collectAsState()
    val motivationLevel by viewModel.motivationLevel.collectAsState()
    val voiceSpeed by viewModel.voiceSpeed.collectAsState()
    val autoGreetings by viewModel.autoGreetings.collectAsState()
    val progressReminders by viewModel.progressReminders.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Header Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Screen title
            Text(
                text = "AI Trainer Settings",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Scrollable content area for settings cards
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()), // Enables scrolling for long content
            verticalArrangement = Arrangement.spacedBy(20.dp) // Spacing between cards
        ) {
            // Trainer Personality Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Card header row with icon and title
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Psychology, // Icon representing personality/mind
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Trainer Personality",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Card description
                    Text(
                        text = "Choose how your AI trainer interacts with you",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    // Personality Options - Updated to new personas
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PersonalityOption(
                            title = "Manic Motivator",
                            description = "Energetic, wildly unpredictable, and deeply empathetic (Robin Williams inspired)",
                            isSelected = trainerPersonality == "manic_motivator",
                            onClick = { viewModel.setTrainerPersonality("manic_motivator") }
                        )

                        PersonalityOption(
                            title = "Zen Coach",
                            description = "Calm, mindful, and focused on inner peace through movement",
                            isSelected = trainerPersonality == "zen_coach",
                            onClick = { viewModel.setTrainerPersonality("zen_coach") }
                        )

                        PersonalityOption(
                            title = "Data-Driven Friend",
                            description = "Knowledgeable, friendly, and motivated by your progress stats",
                            isSelected = trainerPersonality == "data_driven_friend",
                            onClick = { viewModel.setTrainerPersonality("data_driven_friend") }
                        )
                    }
                }
            }

            // Motivation Level Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Motivation Level",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "How much encouragement would you like?",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    // Slider labels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Low", fontSize = 14.sp)
                        Text("High", fontSize = 14.sp)
                    }

                    // Motivation Level Slider
                    Slider(
                        value = motivationLevel,
                        onValueChange = { viewModel.setMotivationLevel(it) },
                        valueRange = 0f..1f,
                        steps = 2, // Three distinct levels: Low, Medium, High
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Voice Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Card header row with icon and title
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.RecordVoiceOver, // Icon for voice settings
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Voice Settings",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Voice Speed Slider
                    Column {
                        Text(
                            text = "Speaking Speed",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )

                        // Slider labels
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Slow", fontSize = 14.sp)
                            Text("Fast", fontSize = 14.sp)
                        }

                        Slider(
                            value = voiceSpeed,
                            onValueChange = { viewModel.setVoiceSpeed(it) },
                            valueRange = 0.5f..1.5f, // Range from half speed to 1.5x speed
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Trainer Behavior Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Trainer Behavior",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Auto Greetings Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto Greetings",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Trainer greets you when starting a session",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = autoGreetings,
                            onCheckedChange = { viewModel.setAutoGreetings(it) }
                        )
                    }

                    Divider() // Separator

                    // Progress Reminders Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Progress Reminders",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Periodic updates during your workout",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = progressReminders,
                            onCheckedChange = { viewModel.setProgressReminders(it) }
                        )
                    }
                }
            }

            // Privacy Note Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info, // Information icon
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "The AI trainer uses your profile information to provide personalized encouragement and appropriate exercise guidance.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Reusable Composable for displaying a single personality option.
 *
 * @param title The main title of the personality option.
 * @param description A brief description of the personality.
 * @param isSelected Boolean indicating if this option is currently selected.
 * @param onClick Lambda to be invoked when the option is clicked.
 */
@Composable
private fun PersonalityOption(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer // Highlight selected card
            } else {
                MaterialTheme.colorScheme.surface // Default background for unselected
            }
        ),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder() // Add a border for selected card
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text content for title and description
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            // Checkmark icon if selected
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}