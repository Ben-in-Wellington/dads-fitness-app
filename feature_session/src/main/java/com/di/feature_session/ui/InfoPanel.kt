/*
 *  InfoPanel – left-hand column in landscape mode.
 *  Shows:
 *    • current rider  (tap to switch / add)
 *    • rotating last-achievement banner
 *    • today’s progress
 *    • Settings   • HELP
 */

@file:OptIn(ExperimentalAnimationApi::class)

package com.di.feature_session.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.di.core.data.UserManager
import com.di.core.data.database.TodayStats
import com.di.core.data.database.UserEntity
import com.di.feature_session.InfoPanelViewModel
import com.di.feature_session.OverallStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/* ─────────────────────────────────────────────────────────────── */
/*  ROOT COMPOSABLE                                                */
/* ─────────────────────────────────────────────────────────────── */
@Composable
fun InfoPanel(
    onNavigateToSettings: () -> Unit,
    onHelpClick: () -> Unit,
    viewModel: InfoPanelViewModel = hiltViewModel()
) {
    val userName      by viewModel.userName.collectAsState()
    val overallStats  by viewModel.overallStats.collectAsState()
    val achievements  by viewModel.unlockedAchievements.collectAsState()

    var showStatsDialog  by remember { mutableStateOf(false) }
    var showSwitchDialog by remember { mutableStateOf(false) }

    val todayStats    by viewModel.todayStats.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .30f))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        /*  current rider ----------------------------------------------------- */
        if (userName.isNotBlank()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { showSwitchDialog = true },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Person, null,
                    modifier = Modifier.size(18.dp),
                    tint     = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(
                    userName,
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color      = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        /*  rotating achievement banner --------------------------------------- */
        if (achievements.isNotEmpty()) {
            AchievementTicker(achievements, onClick = { showStatsDialog = true })
        } else {
            Text(
                "Start cycling to unlock achievements!",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f),
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(12.dp))

        /*  today’s progress card  – takes remaining space -------------------- */
        ProgressCard(
            stats = todayStats,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Spacer(Modifier.height(12.dp))

        /*  settings + help buttons ------------------------------------------ */
        SettingsAndHelpButtons(onNavigateToSettings, onHelpClick)
    }

    /* dialogs --------------------------------------------------------------- */
    if (showStatsDialog)
        StatsAndAchievementsDialog(overallStats, achievements) { showStatsDialog = false }

    /* ONLY open the switcher when requested */
    if (showSwitchDialog) {
        UserSwitcherDialog(
            onDismiss = { showSwitchDialog = false }   //  ←  use the right variable
        )
    }
}

/* ─────────────────────────────────────────────────────────────── */
/*  TODAY’S PROGRESS CARD                                          */
/* ─────────────────────────────────────────────────────────────── */
@Composable
fun ProgressCard(stats: TodayStats?, modifier: Modifier = Modifier) {
    Card(
        modifier
            .heightIn(min = 90.dp),          // guarantees minimum height
        colors = CardDefaults.cardColors(
            containerColor =
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .50f)
        )
    ) {
        /* slimmer padding than before */
        Column(
            Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment  = Alignment.CenterHorizontally
        ) {
            /*  NO icon – pure data  */
            if (stats == null || stats.sessions == 0) {
                Text("No rides today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            } else {
                /* use a slightly larger number style */
                Text(
                    "${stats.sessions} ride${if (stats.sessions == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "%.1f km".format(stats.distanceKm),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

/* ─────────────────────────────────────────────────────────────── */
/*  SETTINGS + HELP BUTTONS                                        */
/* ─────────────────────────────────────────────────────────────── */
@Composable
private fun SettingsAndHelpButtons(
    onNavigateToSettings: () -> Unit,
    onHelpClick: () -> Unit
) {
    Button(
        onClick = onNavigateToSettings,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape  = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Icon(Icons.Default.Settings, null)
        Spacer(Modifier.width(8.dp))
        Text("Settings", fontSize = 16.sp)
    }

    Spacer(Modifier.height(8.dp))

    OutlinedButton(
        onClick = onHelpClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp),
        shape  = RoundedCornerShape(8.dp)
    ) {
        Icon(Icons.Default.Emergency, null)
        Spacer(Modifier.width(8.dp))
        Text("HELP", fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/* ─────────────────────────────────────────────────────────────── */
/*  ACHIEVEMENT BANNER                                             */
/* ─────────────────────────────────────────────────────────────── */
data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val unlockedDate: String? = null
)

@Composable
private fun AchievementTicker(
    achievements: List<Achievement>,
    onClick: () -> Unit
) {
    /* ---------- keep at most three, newest first ---------- */
    val recent = remember(achievements) {
        achievements
            .sortedByDescending { it.unlockedDate }   // newest → oldest
            .take(3)
    }

    /* ---------- ticker index ---------- */
    var index by remember { mutableStateOf(0) }

    /*  Reset index whenever list size changes, and
        advance every 3.5 s if we have >1 badge          */
    LaunchedEffect(recent) {
        index = 0                                // safety reset
        while (recent.size > 1) {
            delay(3_500)                         // ms
            index = (index + 1) % recent.size
        }
    }

    /* ---------- UI ---------- */
    if (recent.isEmpty()) {
        // nothing unlocked yet ⇒ show a neutral card
        Card(
            Modifier
                .fillMaxWidth()
                .height(68.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f)
            )
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Unlock achievements by riding!",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return                                     // early-exit
    }

    Card(
        Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        AnimatedContent(
            targetState = recent[index],
            transitionSpec = { fadeIn() with fadeOut() },
            label = "achievement-ticker"
        ) { ach ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = ach.icon,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(4.dp))
                /*  Two-line text: name + short description  */
                Text(
                    text = ach.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1
                )
                Text(
                    text = ach.description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .8f),
                    maxLines = 1
                )
            }
        }
    }
}

/* ─────────────────────────────────────────────────────────────── */
/*  USER SWITCHER DIALOG                                           */
/* ─────────────────────────────────────────────────────────────── */

@Composable
fun UserSwitcherDialog(
    onDismiss: () -> Unit,
    vm: SwitcherVm = hiltViewModel()
) {
    val users      by vm.users.collectAsState()
    val activeUser by vm.activeUser.collectAsState()
    var newName    by remember { mutableStateOf("") }
    val scope      = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(20.dp)
        ) {
            /* one single lazy column: header, items, footer */
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                /* ----- header row with close icon --------------------------- */
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Choose rider",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                }

                /* ----- existing users -------------------------------------- */
                if (users.isEmpty()) {
                    item {
                        Text(
                            "No riders yet – add one below.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    items(users, key = { it.id }) { user ->
                        val isActive = user.id == activeUser?.id
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        vm.userManager.setActiveUser(user.id)
                                        onDismiss()
                                    }
                                },
                            border = BorderStroke(
                                2.dp,
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = .4f)
                            )
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Person, null)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    user.name,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isActive) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                /* ----- footer: add-new row ---------------------------------- */
                item {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(20) },
                        placeholder = { Text("New name") },
                        singleLine = true,
                        trailingIcon = {
                            Icon(Icons.Default.PersonAdd, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    /* use a smaller tonal button to save height */
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                vm.userManager.addNewUser(newName.trim())
                                newName = ""
                                onDismiss()
                            }
                        },
                        enabled = newName.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) { Text("Create & switch") }
                }
            }
        }
    }
}

/* ---------- simple VM wrapper ------------------------------------------- */
@HiltViewModel
class SwitcherVm @Inject constructor(
    val userManager: UserManager
) : androidx.lifecycle.ViewModel() {

    val users =
        userManager.allUsers.stateIn(viewModelScope,
            SharingStarted.Eagerly, emptyList())

    val activeUser =
        userManager.activeUser.stateIn(viewModelScope,
            SharingStarted.Eagerly, null)
}