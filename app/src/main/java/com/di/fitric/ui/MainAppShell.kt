/*
 *  MainAppShell – houses the persistent InfoPanel on the left
 *  and a NavHost (right 70 %) that swaps the main feature screens.
 */

package com.di.fitric.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.di.feature_session.ui.*
import com.di.feature_trainer.ui.AITrainerSettingsScreen
import com.di.fitric.ui.navigation.Routes
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.compositionLocalOf

val LocalNavigation = compositionLocalOf<NavHostController?> { null }

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MainAppShell() {
    val navController = rememberNavController()
    var showHelpDialog by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalNavigation provides navController) {

        Scaffold { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                /* ------------ left info panel ------------ */
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.30f)
                        .padding(16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    InfoPanel(
                        onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                        onHelpClick = { showHelpDialog = true }
                    )
                }

                /* ------------ main content --------------- */
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.70f)
                        .padding(vertical = 16.dp, horizontal = 24.dp)
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.DASHBOARD
                    ) {
                        /* dashboard */
                        composable(Routes.DASHBOARD) {
                            DashboardScreen(
                                onNavigateToSurvey = { sid ->
                                    navController.navigate(Routes.surveyScreen(sid))
                                },
                                onNavigateToRadio = { navController.navigate(Routes.RADIO) }
                            )
                        }

                        /* survey – returns result via SavedStateHandle */
                        composable(
                            route = Routes.SURVEY,
                            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
                        ) { bse ->
                            val sid = bse.arguments?.getLong("sessionId")!!
                            SurveyScreen(
                                onSurveyComplete = {
                                    navController.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set("completedSessionId", sid)
                                    navController.popBackStack()
                                }
                            )
                        }

                        /* radio */
                        composable(Routes.RADIO) {
                            RadioScreen(onBack = { navController.popBackStack() })
                        }

                        /* settings + sub-screens */
                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                onNavigateToCalibration = { navController.navigate(Routes.CALIBRATION) },
                                onNavigateToPersonalInfo = { navController.navigate(Routes.PERSONAL_INFO) },
                                onNavigateToAISettings = { navController.navigate(Routes.AI_TRAINER_SETTINGS) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.CALIBRATION) { CalibrationScreen(onBack = { navController.popBackStack() }) }
                        composable(Routes.PERSONAL_INFO) { PersonalInfoScreen(onBack = { navController.popBackStack() }) }
                        composable(Routes.AI_TRAINER_SETTINGS) { AITrainerSettingsScreen(onBack = { navController.popBackStack() }) }
                    }
                }
            }

            /* floating overlay for new achievements */
            AchievementOverlay()
        }

        /* ----------------- Help dialog ----------------- */
        if (showHelpDialog) {
            HelpDialog(
                onDismiss = { showHelpDialog = false },
                onConfirmHelp = { showHelpDialog = false /* TODO e-mail / SMS */ }
            )
        }
    }
}

/* ----------------------------------------------------------------
   Confirmation dialog for the HELP button.
------------------------------------------------------------------*/
@Composable
fun HelpDialog(
    onDismiss: () -> Unit,
    onConfirmHelp: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Do you need help?") },
        text  = { Text("This will alert your emergency contact.") },
        confirmButton = {
            Button(
                onClick = onConfirmHelp,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("YES, GET HELP", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}