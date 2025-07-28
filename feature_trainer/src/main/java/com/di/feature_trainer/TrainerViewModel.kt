// TrainerViewModel.kt
//
// • Handles new LiveApiEvent.GenerationComplete & GoAway
// • Uses the updated AudioManager.stopPlayback(flush = …)
// • Sends audioStreamEnd before releasing mic
// • No more duplicate / truncated assistant messages
//
package com.di.feature_trainer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.di.core.data.SessionRepository
import com.di.core.data.UserManager
import com.di.core.data.database.TrainerSessionDao
import com.di.core.data.database.TrainerSessionEntity
import com.di.feature_trainer.audio.AudioManager
import com.di.feature_trainer.data.TrainerRepository
import com.di.feature_trainer.data.models.LiveApiEvent
import com.di.feature_trainer.data.models.ToolCall
import com.di.feature_trainer.tools.TrainerTools
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TrainerViewModel @Inject constructor(
    private val trainerRepository: TrainerRepository,
    private val audioManager: AudioManager,
    private val sessionRepository: SessionRepository,
    private val userManager: UserManager,
    private val trainerTools: TrainerTools,
    private val trainerSessionDao: TrainerSessionDao
) : ViewModel() {

    /* ─────────────────── UI state ─────────────────── */
    private val _ui = MutableStateFlow(TrainerUiState())
    val uiState: StateFlow<TrainerUiState> = _ui.asStateFlow()

    private var currentWorkoutSessionId: Long? = null
    private var isPostWorkout: Boolean = false

    /* ─────────────────── init subscriptions ─────────────────── */
    init {
        viewModelScope.launch { trainerRepository.events.collect(::handleApiEvent) }
        viewModelScope.launch {
            audioManager.audioInputStream.collect { trainerRepository.sendAudioData(it) }
        }
        viewModelScope.launch {
            audioManager.audioError.collect { e ->
                _ui.update { it.copy(error = e, conversationState = ConversationState.ERROR) }
            }
        }

        // 1. ADD this new collector to sync isSpeaking state
        viewModelScope.launch {
            audioManager.isSpeaking.collect { speaking ->
                _ui.update { it.copy(isSpeaking = speaking) }
                if (speaking) {
                    audioManager.muteMic()
                    _ui.update { it.copy(isListening = false) }
                } else {
                    // Small delay to avoid instantly re-activating mic
                    delay(200)
                    audioManager.unMuteMic()
                    // Only set listening to true if conversation is active
                    if (_ui.value.conversationState == ConversationState.ACTIVE) {
                        _ui.update { it.copy(isListening = true) }
                    }
                }
            }
        }
    }

    /* ─────────────────── lifecycle helpers ─────────────────── */

    fun startPreWorkoutChat(workoutSessionId: Long?) {
        currentWorkoutSessionId = workoutSessionId
        isPostWorkout = false
        startFreshConversation()
    }

    fun startPostWorkoutDebrief(workoutSessionId: Long?) {
        currentWorkoutSessionId = workoutSessionId
        isPostWorkout = true
        startFreshConversation()
    }

    private fun startFreshConversation() {
        viewModelScope.launch {
            if (_ui.value.conversationState == ConversationState.CONNECTING) {
                Log.d(TAG, "Already connecting, ignoring duplicate request")
                return@launch
            }

            // Reset state completely
            _ui.value = TrainerUiState(conversationState = ConversationState.CONNECTING)

            val userId = userManager.activeUser.first()?.id
            if (userId == null) {
                _ui.update { it.copy(error = "No active user", conversationState = ConversationState.ERROR) }
                return@launch
            }

            try {
                Log.d(TAG, "Starting fresh ${if (isPostWorkout) "post-workout" else "pre-workout"} session")

                // Always pass null for session handle - start fresh
                trainerRepository.startSession(
                    userId = userId,
                    workoutSessionId = currentWorkoutSessionId,
                    sessionHandle = null,  // Always fresh
                    isPostWorkoutDebrief = isPostWorkout
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start conversation", e)
                _ui.update {
                    it.copy(
                        error = "Failed to start: ${e.message}",
                        conversationState = ConversationState.ERROR
                    )
                }
            }
        }
    }

    fun restartConversation() {
        viewModelScope.launch {
            stopConversation()
            delay(500)
            startFreshConversation()
        }
    }

    fun stopConversation() {
        viewModelScope.launch {
            runCatching { trainerRepository.sendAudioStreamEnd() }
        }
        audioManager.release()
        trainerRepository.disconnect()

        // Clear everything
        currentWorkoutSessionId = null
        isPostWorkout = false
        _ui.value = TrainerUiState()
    }


    fun concludeDebriefAndSaveNote() {
        viewModelScope.launch {
            updateAssistantTranscript("Saving summary…", isFinal = true)
            trainerRepository.sendTextMessage(
                "Please summarise our debrief and save it as a trainer note.",
                turnComplete = true
            )
            delay(2_000)
            stopConversation()
        }
    }

    fun toggleMute() {
        val currentMuted = _ui.value.isMuted
        if (currentMuted) {
            audioManager.unMuteMic()
        } else {
            audioManager.muteMic()
        }
        _ui.update { it.copy(isMuted = !currentMuted) }
    }

    override fun onCleared() {
        super.onCleared()
        stopConversation()
    }

    /* ─────────────────── Live-API event handler ─────────────────── */
    private suspend fun handleApiEvent(ev: LiveApiEvent) {
        when (ev) {
            LiveApiEvent.ConnectionOpened -> {
                Log.d(TAG, "Connection opened - initializing audio")
                _ui.update { it.copy(conversationState = ConversationState.ACTIVE) }

                // Always initialize audio playback, even for resumed sessions
                audioManager.initializePlayback()
                audioManager.startRecording()
                _ui.update { it.copy(isListening = true) }

                trainerRepository.sendTextMessage("", turnComplete = true)
            }

            LiveApiEvent.ConnectionClosed -> stopConversation()

            /* audio */
            is LiveApiEvent.AudioReceived -> {
                audioManager.playAudio(ev.base64Data)
            }

            // 3. SIMPLIFY Interrupted handler
            is LiveApiEvent.Interrupted -> {
                audioManager.stopPlaybackAndClear()
            }

            // 4. MODIFY GenerationComplete handler to use the new signal
            is LiveApiEvent.GenerationComplete -> {
                Log.d(TAG, "Generation complete received, signaling end of turn.")
                audioManager.signalEndOfTurn()
            }

            is LiveApiEvent.UserTranscriptUpdated -> {
                // Simply ignore user transcripts or just update listening state
                if (ev.isFinal) {
                    _ui.update { it.copy(isListening = false) }
                }
            }

            is LiveApiEvent.AiTranscriptUpdated -> {
                updateAssistantTranscript(ev.text, ev.isFinal)
            }

            /* tools */
            is LiveApiEvent.ToolCallReceived -> executeTool(ev.toolCall)

            /* session handle */
            is LiveApiEvent.SessionHandleUpdated -> {
                // Ignore - we're not resuming sessions anymore
                Log.d(TAG, "Received session handle but ignoring (using fresh sessions)")
            }

            /* GoAway → auto-reconnect so Dad never notices 10-min resets */
            is LiveApiEvent.GoAway -> {
                Log.d(TAG, "Server requesting disconnect in ${ev.timeLeft}")
                // Don't auto-reconnect - let the user finish their workout
            }

            /* error */
            is LiveApiEvent.Error -> _ui.update {
                it.copy(
                    error = ev.message,
                    conversationState = ConversationState.ERROR,
                    isListening = false,
                    isSpeaking = false,
                    isExecutingTool = false
                )
            }
        }
    }

    /* ─────────────────── tool execution ─────────────────── */

    private val _navigationEvents = Channel<TrainerNavigationEvent>()
    val navigationEvents = _navigationEvents.receiveAsFlow()

    sealed class TrainerNavigationEvent {
        object StartWorkoutSession : TrainerNavigationEvent()
    }

    private fun executeTool(toolCall: ToolCall) {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(isExecutingTool = true) }
            try {
                // Check if this is the start workout tool
                val hasStartWorkout = toolCall.functionCalls.any { it.name == "start_workout_session" }

                val resp = trainerTools.executeTool(toolCall)
                trainerRepository.sendToolResponse(resp)

                // If it was the start workout tool, trigger navigation after a brief delay
                if (hasStartWorkout) {
                    delay(1500) // Give time for the AI to say goodbye
                    _navigationEvents.send(TrainerNavigationEvent.StartWorkoutSession)
                }
            } catch (e: Exception) {
                _ui.update { it.copy(error = "Tool failed: ${e.message}") }
            } finally {
                _ui.update { it.copy(isExecutingTool = false) }
            }
        }
    }

    /* ─────────────────── transcript helpers ─────────────────── */

    private fun updateUserTranscript(text: String, isFinal: Boolean) {
        if (text.isBlank()) return

        _ui.update { currentState ->
            val transcript = currentState.transcript.toMutableList()

            // Find the last USER message that is not yet final
            val lastUserEntryIndex = transcript.findLastIndex { it.isUser && !it.isFinal }

            if (lastUserEntryIndex != -1) {
                // If we found an in-progress user message, update its text
                transcript[lastUserEntryIndex] = transcript[lastUserEntryIndex].copy(
                    text = text,
                    isFinal = isFinal
                )
            } else {
                // Otherwise, this is a new message from the user
                transcript.add(
                    TranscriptEntry(text = text, isUser = true, isFinal = isFinal)
                )
            }
            currentState.copy(transcript = transcript)
        }
    }

    private fun updateAssistantTranscript(text: String, isFinal: Boolean) {
        if (text.isBlank()) return

        _ui.update { currentState ->
            val transcript = currentState.transcript.toMutableList()

            // Find the last AI message that is not yet final
            val lastAiEntryIndex = transcript.findLastIndex { !it.isUser && !it.isFinal }

            if (lastAiEntryIndex != -1) {
                val existingEntry = transcript[lastAiEntryIndex]
                // IMPORTANT: For incremental updates, we need to check if this is new content
                // The API might send the same content multiple times
                val updatedText = if (text.startsWith(existingEntry.text)) {
                    // This is a continuation - the API sent the full text including what we already have
                    text
                } else {
                    // This is additional content - append it
                    existingEntry.text + text
                }

                transcript[lastAiEntryIndex] = existingEntry.copy(
                    text = updatedText,
                    isFinal = isFinal
                )
            } else {
                // This is a new message from the AI
                transcript.add(
                    TranscriptEntry(text = text, isUser = false, isFinal = isFinal)
                )
            }
            currentState.copy(transcript = transcript)
        }
    }

    // Helper extension function to add to the file or a utils file
    private fun <T> List<T>.findLastIndex(predicate: (T) -> Boolean): Int {
        for (i in lastIndex downTo 0) {
            if (predicate(this[i])) {
                return i
            }
        }
        return -1
    }

    companion object { private const val TAG = "TrainerVM" }
}

/* ───────────────────── data classes ───────────────────── */

data class TrainerUiState(
    val conversationState: ConversationState = ConversationState.IDLE,
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val isExecutingTool: Boolean = false,
    val transcript: List<TranscriptEntry> = emptyList(),
    val error: String? = null,
    val isMuted: Boolean = false
)

enum class ConversationState { IDLE, CONNECTING, ACTIVE, ERROR }

data class TranscriptEntry(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val isFinal: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)