### **Feature Specification: AI Personal Trainer**

**File Name:** `spec_ai_trainer_final.md`

### 1. Feature Name
AI Personal Trainer (Real-time Bidirectional Audio)

### 2. User Story
"As Dad, I want to talk to a friendly trainer to ask about my progress, get motivation, or ask for help, so that I feel supported and less isolated during my exercise. I want the conversation to feel natural, like talking to a person, where I can interrupt them if I have another thought. I'd also like to see what I said written on the screen to be sure it understood me correctly."

### 3. Acceptance Criteria
-   **Given** the app is idle, **when** I tap the "Talk to Trainer" button, **then** the app's state changes to `LISTENING`, and it immediately begins capturing and streaming microphone audio.
-   **Given** the app is `LISTENING`, **then** a live transcription of my speech appears on the screen in near real-time.
-   **Given** I stop speaking, **then** the AI's audio response is played back, a live transcription of its response appears on screen, and the app's state changes to `SPEAKING`.
-   **Given** the app is `SPEAKING`, **when** I interrupt by speaking, **then** the AI's audio playback stops immediately (as confirmed by the server's `interrupted` message).
-   **Given** I ask a question requiring data (e.g., "How was my last session?"), **then** the AI must successfully use the `lookup_session_history` tool, and its verbal response must include the correct data.
-   **Given** I give a command to save data (e.g., "Make a note..."), **then** the AI must use the `add_trainer_note` tool, and the corresponding data must be correctly saved in the local Room database.
-   **Given** I make a request requiring an external action (e.g., "Send a progress report"), **then** the AI must use the `send_progress_email` tool, which must successfully launch the Android default email client with pre-filled content.
-   **Given** an internet connection error occurs, **then** the app must not crash and must provide a graceful audio-only error message to the user.
-   **Given** an active conversation session reaches the 15-minute time limit, **when** the server closes the connection, **then** the app gracefully returns to the `IDLE` state.

### 4. UI/UX Requirements
-   The trainer interface is controlled by a single, large microphone button, which provides clear visual feedback on the system's state.
    -   **State 1: IDLE** (Standard microphone icon)
    -   **State 2: LISTENING** (Icon animates with a "pulsing" or "glowing" effect)
    -   **State 3: SPEAKING** (Icon changes to a "speaker" or different color)
    -   **State 4: ERROR** (Icon briefly flashes red before returning to IDLE)
-   A designated area on the screen will display the conversation transcript. It should clearly distinguish between "You:" (user input) and "Trainer:" (AI output). The text should update in real-time as `inputTranscription` and `outputTranscription` messages are received.

### 5. Functional Requirements
-   **State Management:** The `TrainerViewModel` will expose a `StateFlow<TrainerUiState>` to the UI. The `TrainerUiState` data class will contain fields like `isListening: Boolean`, `isSpeaking: Boolean`, `displayedTranscript: String`, `error: String?`.
-   **Audio Pipeline:**
    -   **Input:** The `TrainerRepository` will use Android's `AudioRecord` to capture audio. It must be configured for **16-bit PCM, 16kHz sample rate, mono channel**.
    -   **Output:** The `TrainerRepository` will use Android's `AudioTrack` to play back audio. It must be configured for **16-bit PCM, 24kHz sample rate, mono channel**.
-   **Tool (Function) Handling:** The `TrainerRepository` will parse `toolCall` messages and emit an event to the `ViewModel`. The `ViewModel` will execute the tool's logic and call a repository function to send the `ToolResponse` back to the API.
-   **Session Lifecycle:** The `TrainerRepository` must handle the `onClose` WebSocket callback. When triggered (e.g., by the 15-minute timeout), it must notify the `ViewModel` to reset the UI to the `IDLE` state.

### 6. API Implementation Details & Code Examples

This section details the direct interaction with the Gemini Live API, based on the official documentation.

#### 6.1. Establishing the Connection

The connection configuration must be precise and complete, defining the model, tools, personality, transcriptions, and voice.

```kotlin
// In TrainerRepository.kt

// Define the tools the AI can use.
val lookupHistoryTool = FunctionDeclaration(
    name = "lookup_session_history",
    description = "Gets the user's most recent cycling session stats."
)
val addNoteTool = FunctionDeclaration(
    name = "add_trainer_note",
    description = "Saves a textual note from the user about their session.",
    // Define parameters for structured data extraction
    parameters = mapOf("note_text" to mapOf("type" to "string", "description" to "The content of the note to be saved."))
)
val getNotesTool = FunctionDeclaration(
    name = "get_trainer_notes",
    description = "Retrieves all previously saved trainer notes."
)
val sendEmailTool = FunctionDeclaration(
    name = "send_progress_email",
    description = "Prepares an email with a summary of the latest session for the user to send."
)

// Configure the session
val sessionConfig = LiveSessionConfig(
    // 1. Model Selection: Half-cascade for robust tool support.
    model = "gemini-live-2.5-flash-preview",
    
    // 2. Response Modality: We only want audio back.
    responseModalities = listOf(Modality.AUDIO),

    // 3. Tool Declarations: Provide the list of available functions.
    tools = listOf(lookupHistoryTool, addNoteTool, getNotesTool, sendEmailTool),

    // 4. System Personality: Define the AI's character.
    systemInstruction = "You are a friendly, patient, and encouraging fitness coach for a stroke survivor. Keep your answers concise and positive.",
    
    // 5. Enable Transcriptions for UI Display
    inputAudioTranscription = true,
    outputAudioTranscription = true,

    // 6. Configure Voice
    speechConfig = SpeechConfig(
        voiceConfig = VoiceConfig(
            prebuiltVoiceConfig = PrebuiltVoiceConfig(voiceName = "Kore") // A clear, standard voice
        )
    )
)

// The connect function called from the ViewModel
fun startConversation() {
    geminiLiveApi.connect(
        config = sessionConfig,
        callbacks = object : LiveCallbacks {
            override fun onOpen() { /* Connection established, update UI state */ }
            override fun onMessage(message: LiveMessage) { handleIncomingMessage(message) }
            override fun onError(error: Throwable) { /* Handle connection error, update UI state */ }
            override fun onClose(reason: String) { /* Connection closed (e.g., timeout), update UI state to IDLE */ }
        }
    )
}
```

#### 6.2. The Bidirectional Data Loop

This logic handles all incoming message types: audio for playback, transcripts for display, and tool calls for execution.

```kotlin
// In TrainerRepository.kt

private val audioRecord: AudioRecord = ... // Initialized for 16kHz, 16-bit PCM, Mono
private val audioTrack: AudioTrack = ...   // Initialized for 24kHz, 16-bit PCM, Mono

// This is called when the user taps the mic button
fun startStreamingAudio() {
    // ... logic to start audioRecord.startRecording() and audioTrack.play() ...
    // Launch a coroutine to continuously read from AudioRecord and send to the API
    viewModelScope.launch(Dispatchers.IO) {
        val buffer = ByteArray(AUDIO_BUFFER_SIZE)
        while (isActive) {
            val readSize = audioRecord.read(buffer, 0, buffer.size)
            if (readSize > 0) {
                val base64Audio = Base64.encodeToString(buffer, Base64.NO_WRAP)
                geminiLiveApi.sendRealtimeInput(
                    RealtimeInput(audio = AudioInput(data = base64Audio, mimeType = "audio/pcm;rate=16000"))
                )
            }
        }
    }
}

// Handles all messages from the server on the WebSocket
private fun handleIncomingMessage(message: LiveMessage) {
    // A. Handle audio data for playback
    message.data?.let { audioDataString ->
        val audioData = Base64.decode(audioDataString, Base64.DEFAULT)
        audioTrack.write(audioData, 0, audioData.size)
    }

    // B. Handle tool call requests
    message.toolCall?.let { toolCall ->
        audioTrack.pause(); audioTrack.flush() // Stop speaking to handle the tool
        _eventFlow.tryEmit(LiveApiEvent.ToolCallReceived(toolCall))
    }
    
    // C. Handle server metadata, including transcripts and interruptions
    message.serverContent?.let { serverContent ->
        serverContent.inputTranscription?.let { _eventFlow.tryEmit(LiveApiEvent.UserTranscriptUpdated(it.text)) }
        serverContent.outputTranscription?.let { _eventFlow.tryEmit(LiveApiEvent.AiTranscriptUpdated(it.text)) }
        if (serverContent.interrupted == true) {
            audioTrack.pause(); audioTrack.flush()
        }
    }
}
```

#### 6.3. Executing a Tool Call

This logic resides in the `ViewModel` and demonstrates the full round-trip for a tool.

```kotlin
// In TrainerViewModel.kt

// The ViewModel collects events from the repository
private fun observeRepositoryEvents() {
    viewModelScope.launch {
        trainerRepository.events.collect { event ->
            when (event) {
                is LiveApiEvent.ToolCallReceived -> handleToolCall(event.toolCall)
                is LiveApiEvent.UserTranscriptUpdated -> _uiState.update { it.copy(displayedTranscript = "You: " + event.text) }
                is LiveApiEvent.AiTranscriptUpdated -> _uiState.update { it.copy(displayedTranscript = "Trainer: " + event.text) }
                // ... handle other events
            }
        }
    }
}

private suspend fun handleToolCall(toolCall: ToolCall) {
    val functionCall = toolCall.functionCalls.first() // Assuming one call per turn for simplicity
    
    val functionResponse = when (functionCall.name) {
        "lookup_session_history" -> {
            val lastSession = sessionRepository.getLatestSession()
            val result = lastSession?.let { "Your last session was ${it.durationMinutes} minutes." } ?: "I don't have any sessions logged yet."
            FunctionResponse(id = functionCall.id, name = functionCall.name, response = mapOf("result" to result))
        }
        "add_trainer_note" -> {
            val note = functionCall.args["note_text"] as? String ?: "No note content provided."
            trainerRepository.saveNote(note)
            FunctionResponse(id = functionCall.id, name = functionCall.name, response = mapOf("result" to "ok, I've made a note of that."))
        }
        "send_progress_email" -> {
            // This triggers a UI action via an event/shared flow that the Activity/Fragment observes.
            _uiAction.emit(UiAction.LaunchEmailIntent) 
            FunctionResponse(id = functionCall.id, name = functionCall.name, response = mapOf("result" to "ok, I've opened the email for you to send."))
        }
        else -> FunctionResponse(id = functionCall.id, name = functionCall.name, response = mapOf("error" to "Unknown function"))
    }
    
    // Send the result back to the API
    trainerRepository.sendToolResponse(ToolResponse(listOf(functionResponse)))
}