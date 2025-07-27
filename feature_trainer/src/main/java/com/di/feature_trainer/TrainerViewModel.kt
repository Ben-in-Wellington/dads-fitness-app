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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    private var currentTrainerSession: TrainerSessionEntity? = null
    private var currentWorkoutSessionId: Long? = null

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

    fun startPreWorkoutChat(workoutSessionId: Long?) =
        startConversation(workoutSessionId, isPost = false)

    fun startPostWorkoutDebrief(workoutSessionId: Long?) =
        startConversation(workoutSessionId, isPost = true)

    private fun startConversation(workoutSessionId: Long?, isPost: Boolean) {
        viewModelScope.launch {
            if (_ui.value.conversationState == ConversationState.CONNECTING) return@launch

            _ui.value = TrainerUiState(conversationState = ConversationState.CONNECTING)
            currentWorkoutSessionId = workoutSessionId

            val userId = userManager.activeUser.first()?.id
            if (userId == null) {
                _ui.update { it.copy(error = "No active user", conversationState = ConversationState.ERROR) }
                return@launch
            }

            try {
                val handle = workoutSessionId?.let {
                    trainerSessionDao.getActiveTrainerSession(it)?.geminiSessionHandle
                }

                currentTrainerSession = trainerRepository.startSession(
                    userId, workoutSessionId, handle, isPost
                )
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        error = "Failed to start conversation: ${e.message}",
                        conversationState = ConversationState.ERROR
                    )
                }
            }
        }
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

    fun stopConversation() {
        viewModelScope.launch {
            runCatching { trainerRepository.sendAudioStreamEnd() }
        }
        audioManager.release() // This will now properly clean everything up
        trainerRepository.disconnect()
        _ui.value = TrainerUiState()
    }

    override fun onCleared() {
        super.onCleared()
        stopConversation()
    }

    /* ─────────────────── Live-API event handler ─────────────────── */
    private fun handleApiEvent(ev: LiveApiEvent) {
        when (ev) {
            LiveApiEvent.ConnectionOpened -> {
                Log.d(TAG, "Connection opened")
                _ui.update { it.copy(conversationState = ConversationState.ACTIVE) }
                audioManager.initializePlayback()
                audioManager.startRecording()
                _ui.update { it.copy(isListening = true) }
                viewModelScope.launch {
                    trainerRepository.sendTextMessage(
                        "<SYSTEM_TRIGGER>Please begin with a greeting.</SYSTEM_TRIGGER>", true
                    )
                }
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
                updateUserTranscript(ev.text, ev.isFinal) // Add user's speech to the transcript
                if (ev.isFinal) {
                    _ui.update { it.copy(isListening = false) }
                }
            }

            is LiveApiEvent.AiTranscriptUpdated -> {
                updateAssistantTranscript(ev.text, ev.isFinal)
                // Don't manage speaking/listening state here anymore
            }

            /* tools */
            is LiveApiEvent.ToolCallReceived -> executeTool(ev.toolCall)

            /* session handle */
            is LiveApiEvent.SessionHandleUpdated -> {
                currentTrainerSession?.let { s ->
                    viewModelScope.launch {
                        trainerRepository.updateSessionHandle(s.id, ev.handle)
                        currentTrainerSession = s.copy(geminiSessionHandle = ev.handle)
                    }
                }
            }

            /* GoAway → auto-reconnect so Dad never notices 10-min resets */
            is LiveApiEvent.GoAway -> {
                Log.d(TAG, "Server reset in ${ev.timeLeft} → reconnecting")
                trainerRepository.disconnect()
                startConversation(currentWorkoutSessionId, isPost = false)
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
    private fun executeTool(toolCall: ToolCall) {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(isExecutingTool = true) }
            try {
                val resp = trainerTools.executeTool(toolCall)
                trainerRepository.sendToolResponse(resp)
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

            // Find the last message from the AI that is not yet final
            val lastAiEntryIndex = transcript.findLastIndex { !it.isUser && !it.isFinal }

            if (lastAiEntryIndex != -1) {
                // If we found an in-progress AI message, update it
                transcript[lastAiEntryIndex] = transcript[lastAiEntryIndex].copy(
                    text = text, // The API sends the full updated text, so we just replace it
                    isFinal = isFinal
                )
            } else {
                // Otherwise, this is a new message from the AI
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
    val error: String? = null
)

enum class ConversationState { IDLE, CONNECTING, ACTIVE, ERROR }

data class TranscriptEntry(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val isFinal: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)