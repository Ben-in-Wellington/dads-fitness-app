// SurveyScreen.kt

package com.di.feature_session.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.di.feature_session.SurveyViewModel
import kotlinx.coroutines.delay

/**
 * Three-question post-session survey – large buttons, scrollable column
 * so nothing gets clipped on small screens.
 */
@Composable
fun SurveyScreen(
    onSurveyComplete: () -> Unit,
    viewModel: SurveyViewModel = hiltViewModel()
) {
    val questionIdx  by viewModel.currentQuestionIndex.collectAsState()
    val question     by viewModel.currentQuestion.collectAsState()
    val isComplete   by viewModel.isComplete.collectAsState()

    /* ---------------  FINISHED ---------------- */
    if (isComplete) {
        LaunchedEffect(Unit) {
            delay(1500)
            onSurveyComplete()
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Great job!  See you next time!",
                    fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
        return
    }

    /* ---------------  QUESTIONS --------------- */
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        /* progress bar */
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(viewModel.totalQuestions) { idx ->
                Box(
                    Modifier
                        .height(4.dp)
                        .weight(1f)
                        .background(
                            if (idx <= questionIdx)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        )
                )
            }
        }

        /* question text */
        Text(
            question.text,
            fontSize   = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center,
            lineHeight = 38.sp
        )

        Spacer(Modifier.height(32.dp))

        /* answer buttons */
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            question.options.forEach { option ->
                SurveyButton(option.text, option.icon) {
                    viewModel.answerQuestion(option.value)
                }
            }
        }
    }
}

/* one big answer button */
@Composable
private fun SurveyButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Button(
        onClick  = onClick,
        modifier = Modifier
            .fillMaxWidth(0.8f)   // 80 % of column width
            .height(60.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor   = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(icon, null, Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}