// SessionScreen.kt

package com.di.feature_session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.di.feature_session.NavigationEvent
import com.di.feature_session.SessionViewModel

@Composable
fun SessionScreen(
    onNavigateToSurvey: (Long) -> Unit,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Listen for navigation events
    LaunchedEffect(key1 = Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is NavigationEvent.NavigateToSurvey -> {
                    onNavigateToSurvey(event.sessionId)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Large text for stats display
        Text(
            text = uiState.displayTime,
            fontSize = 96.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Stats display
        Text(
            text = "Distance: %.2f km".format(uiState.estimatedDistance),
            fontSize = 32.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Speed: %.1f km/h".format(uiState.currentSpeed),
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )
        Text(
            text = "Cadence: ${uiState.currentCadence} rpm",
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Session control button
        if (!uiState.isActive) {
            Button(
                onClick = { viewModel.startSession() },
                modifier = Modifier.size(width = 300.dp, height = 120.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("START", fontSize = 40.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = { viewModel.stopSession() },
                modifier = Modifier.size(width = 300.dp, height = 120.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("STOP", fontSize = 40.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}