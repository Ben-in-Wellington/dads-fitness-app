// file: feature_session/src/main/java/com/di/feature_session/SurveyViewModel.kt

package com.di.feature_session

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.MoodBad
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.di.core.data.SessionRepository
import com.di.core.data.UserManager // Import UserManager
import com.di.feature_audio.RadioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first // For .first()
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SurveyQuestion(
    val id: String,
    val text: String,
    val options: List<SurveyOption>
)

data class SurveyOption(
    val text: String,
    val value: String,
    val icon: ImageVector
)

@HiltViewModel
class SurveyViewModel @Inject constructor(
    private val repository: SessionRepository,
    private val userManager: UserManager,
    private val radioRepository: RadioRepository,   // NEW
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedStateHandle["sessionId"])

    val totalQuestions = 3

    private val questions = listOf(
        SurveyQuestion(
            id = "difficulty",
            text = "How did that session feel?",
            options = listOf(
                SurveyOption("Easy", "easy", Icons.Default.SentimentSatisfied),
                SurveyOption("Good", "good", Icons.Default.ThumbUp),
                SurveyOption("Tough", "tough", Icons.Default.LocalFireDepartment)
            )
        ),
        SurveyQuestion(
            id = "pain",
            text = "Any discomfort today?",
            options = listOf(
                SurveyOption("None", "none", Icons.Default.Mood),
                SurveyOption("A little", "mild", Icons.Default.MoodBad),
                SurveyOption("Yes", "significant", Icons.Default.Warning)
            )
        ),
        SurveyQuestion(
            id = "motivation",
            text = "Ready for the next ride?",
            options = listOf(
                SurveyOption("Can't wait!", "high", Icons.Default.Rocket),
                SurveyOption("Sure", "medium", Icons.Default.DirectionsBike),
                SurveyOption("Need rest", "low", Icons.Default.Hotel)
            )
        )
    )

    private val _currentQuestionIndex = MutableStateFlow(0)
    val currentQuestionIndex: StateFlow<Int> = _currentQuestionIndex.asStateFlow()

    private val _currentQuestion = MutableStateFlow(questions[0])
    val currentQuestion: StateFlow<SurveyQuestion> = _currentQuestion.asStateFlow()

    private val _isComplete = MutableStateFlow(false)
    val isComplete: StateFlow<Boolean> = _isComplete.asStateFlow()

    fun answerQuestion(answer: String) {
        viewModelScope.launch {
            val userId = userManager.activeUser.first()?.id ?: run {
                // Log error or handle case where no active user is found (should not happen if UserManager works correctly)
                return@launch
            }
            // Save the response
            repository.saveSurveyResponse(
                userId = userId, // Pass userId
                sessionId = sessionId,
                question = _currentQuestion.value.id,
                response = answer
            )

            // Move to next question or complete
            val nextIndex = _currentQuestionIndex.value + 1
            if (nextIndex < questions.size) {
                _currentQuestionIndex.value = nextIndex
                _currentQuestion.value = questions[nextIndex]
            } else {
                radioRepository.stop()
                _isComplete.value = true
            }
        }
    }
}