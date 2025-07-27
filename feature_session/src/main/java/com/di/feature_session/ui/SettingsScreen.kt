// SettingsScreen.kt

package com.di.feature_session.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.di.feature_session.SettingsViewModel

@Composable
fun SettingsScreen(
    onNavigateToCalibration: () -> Unit,
    onNavigateToPersonalInfo: () -> Unit,
    onNavigateToAISettings: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val wheelCircumference by viewModel.wheelCircumference.collectAsState()
    val lastConnectedDevice by viewModel.lastConnectedDevice.collectAsState()
    val autoReconnect by viewModel.autoReconnect.collectAsState()

    // --- NEW: State for showing the user switcher dialog and getting user list ---
    val allUsers by viewModel.allUsers.collectAsState()
    val activeUser by viewModel.activeUser.collectAsState()
    var showUserSwitcher by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(16.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Settings",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Settings Categories
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp) // Increased spacing
        ) {
            // --- NEW: User Management Section ---
            SettingsSection(title = "User Management") {
                SettingsItem(
                    icon = Icons.Default.SwitchAccount,
                    title = "Switch User",
                    subtitle = "Currently active: ${activeUser?.name ?: "No User"}",
                    onClick = { showUserSwitcher = true }
                )
            }

            // Sensor Settings Section
            SettingsSection(title = "Sensor & Calibration") {
                SettingsItem(
                    icon = Icons.Default.Speed,
                    title = "Wheel Calibration",
                    subtitle = "Current: ${wheelCircumference}m circumference",
                    onClick = onNavigateToCalibration
                )
                SettingsItem(
                    icon = Icons.Default.Bluetooth,
                    title = "Sensor Connection",
                    subtitle = lastConnectedDevice?.second ?: "No device connected",
                    trailing = {
                        Switch(
                            checked = autoReconnect,
                            onCheckedChange = { viewModel.setAutoReconnect(it) }
                        )
                    }
                )
            }

            // Personal Information Section
            SettingsSection(title = "Personal Information") {
                SettingsItem(
                    icon = Icons.Default.Person,
                    title = "Edit User Profile", // Clarified action
                    subtitle = "Name, age, and medical notes",
                    onClick = onNavigateToPersonalInfo
                )
            }

            // AI Trainer Section
            SettingsSection(title = "AI Personal Trainer") {
                SettingsItem(
                    icon = Icons.Default.Psychology,
                    title = "Trainer Settings",
                    subtitle = "Voice, personality, and preferences",
                    onClick = onNavigateToAISettings
                )
            }

            // App Settings Section
            SettingsSection(title = "Application") {
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = "Display",
                    subtitle = "Theme and contrast settings",
                    onClick = { /* TODO */ }
                )
                SettingsItem(
                    icon = Icons.Default.VolumeUp,
                    title = "Audio",
                    subtitle = "Radio station and volume",
                    onClick = { /* TODO */ }
                )
            }
        }
    }

    // --- NEW: Display the dialog when requested ---
    if (showUserSwitcher) {
        UserSwitcherDialog(
            onDismiss = { showUserSwitcher = false }
        )
    }
}


@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}