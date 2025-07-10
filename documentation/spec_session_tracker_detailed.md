### Feature Specification: Session Tracking & Display

**File Name:** `spec_session_tracker_detailed.md`

### 1. Feature Name
Session Tracking & Display

### 2. User Story
"As Dad, I want to easily start and stop a cycling session and see my progress in real-time, so that I can focus on my exercise without getting frustrated by the technology."

### 3. Acceptance Criteria
-   **Given** the app is on the main screen in its idle state, **when** I tap the "START" button, **then** a new session is created in the database with a status of `ACTIVE`, and the session timer begins counting up from 00:00.
-   **Given** a session is active, **then** the main screen must display the following constantly updating stats:
    -   A session timer in `MM:SS` format.
    -   An estimated current speed (e.g., in km/h or mph).
    -   An estimated total distance cycled for the current session.
-   **Given** a session is active, **when** I tap the "STOP" button, **then** the timer stops, the session's status in the database is updated to `COMPLETED`, and all final data (duration, distance) is saved.
-   **Given** a session has been successfully stopped and saved, **then** the application must immediately navigate to the Post-Session Survey screen.
-   **Given** the app is closed or crashes during an `ACTIVE` session, **when** the app is reopened, **then** the active session must be found, and the timer and stats must resume from their last saved state.

### 4. UI/UX Requirements
-   **Button Placement & Design:**
    -   The "START" button must be located in the center-right of the screen. It must be large, with a high-contrast label (e.g., "START") and a distinct color (e.g., green).
    -   During an active session, a "STOP" button must replace the "START" button in the **exact same position**. It must have the same large size but a different, cautionary color (e.g., red) and a clear "STOP" label.
-   **Stat Display:** All real-time stats (Time, Speed, Distance) must be displayed in the right-hand panel, above or around the main control button, using a very large, bold, and easily legible font.
-   **Orientation:** The entire feature must be presented in a **fixed landscape mode**.
-   **Interaction:** All functionality must be controllable via single taps. No complex gestures, swiping, or fine motor skills are required.

### 5. Functional Requirements & Code Examples
The `SessionViewModel` is the core component for this feature. It manages the session's state, handles the timer logic, and communicates with the `SessionRepository` to persist data.

#### 5.1. State Management
The UI will be driven by a `StateFlow` from the ViewModel. This ensures the UI is a simple reflection of the current state.

```kotlin
// In a shared model file or within SessionViewModel.kt

// This data class represents everything the UI needs to draw itself.
data class SessionUiState(
    val isActive: Boolean = false,
    val sessionId: Long? = null,
    val elapsedTimeSeconds: Long = 0L,
    val estimatedDistance: Double = 0.0,
    val estimatedSpeed: Double = 0.0,
    val displayTime: String = "00:00" // Formatted time for direct display
)
```

#### 5.2. ViewModel Logic
The `SessionViewModel` contains the business logic for starting, stopping, and managing the session timer.

```kotlin
// In SessionViewModel.kt

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    
    // Check for an active session when the ViewModel is created.
    init {
        viewModelScope.launch {
            val activeSession = sessionRepository.findActiveSession()
            activeSession?.let { resumeSession(it) }
        }
    }

    fun startSession() {
        viewModelScope.launch {
            val newSessionId = sessionRepository.createNewSession()
            _uiState.update { it.copy(isActive = true, sessionId = newSessionId, elapsedTimeSeconds = 0L) }
            startTimer()
        }
    }

    fun stopSession() {
        timerJob?.cancel()
        viewModelScope.launch {
            val finalState = _uiState.value
            sessionRepository.completeSession(
                sessionId = finalState.sessionId!!,
                duration = finalState.elapsedTimeSeconds,
                distance = finalState.estimatedDistance
            )
            _uiState.value = SessionUiState() // Reset to initial state
            // TODO: Add navigation logic to survey screen.
        }
    }

    private fun startTimer() {
        timerJob?.cancel() // Ensure no multiple timers are running
        timerJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis() - (_uiState.value.elapsedTimeSeconds * 1000)
            
            while (isActive) {
                delay(1000L) // Tick every second
                val newElapsedTime = (System.currentTimeMillis() - startTime) / 1000
                
                // TODO: Replace with actual calculation logic
                val newSpeed = calculateSpeed(newElapsedTime) 
                val newDistance = calculateDistance(newElapsedTime)

                _uiState.update {
                    it.copy(
                        elapsedTimeSeconds = newElapsedTime,
                        displayTime = formatTime(newElapsedTime),
                        estimatedSpeed = newSpeed,
                        estimatedDistance = newDistance
                    )
                }

                // For robustness: periodically update the DB
                if (newElapsedTime % 30 == 0L) { // Every 30 seconds
                    sessionRepository.updateActiveSessionProgress(
                        sessionId = _uiState.value.sessionId!!,
                        currentDuration = newElapsedTime
                    )
                }
            }
        }
    }
    
    private fun resumeSession(session: SessionEntity) {
        _uiState.update {
            it.copy(isActive = true, sessionId = session.id, elapsedTimeSeconds = session.durationSeconds)
        }
        startTimer()
    }
    
    private fun formatTime(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return "%02d:%02d".format(minutes, remainingSeconds)
    }
}
```

#### 5.3. UI (Jetpack Compose)
The Composable function will be simple. It observes the `uiState` and draws the correct UI based on its properties.

```kotlin
// In SessionScreen.kt

@Composable
fun SessionScreen(viewModel: SessionViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Stats Display
        Text(text = uiState.displayTime, fontSize = 96.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Distance: %.2f km".format(uiState.estimatedDistance), fontSize = 32.sp)
        Spacer(modifier = Modifier.height(32.dp))

        // Control Button
        if (!uiState.isActive) {
            Button(
                onClick = { viewModel.startSession() },
                modifier = Modifier.size(width = 250.dp, height = 100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
            ) {
                Text("START", fontSize = 32.sp)
            }
        } else {
            Button(
                onClick = { viewModel.stopSession() },
                modifier = Modifier.size(width = 250.dp, height = 100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("STOP", fontSize = 32.sp)
            }
        }
    }
}
```

### 6. Data Models Involved
-   **Create/Update/Read:** `SessionEntity(id, startTime, endTime, durationSeconds, estimatedDistance, status: String)`
    -   The `status` field is critical for robustness, and will be an enum or string (`ACTIVE`, `COMPLETED`, `CANCELED`).

### 7. Error States & Edge Cases
-   **App Crash / Device Reboot (Robustness):** This is handled by the `init` block in the `SessionViewModel`. On startup, it queries the `SessionRepository` for any session with an `ACTIVE` status. If found, the `resumeSession` function is called to restore the UI and timer to their last saved state. The periodic database updates ensure minimal data loss.
-   **Storage Full:** If the `SessionRepository` throws an exception when trying to write to the Room database, the ViewModel should catch it. The app must not crash. The session will continue in-memory, but a log should be written so the caregiver can diagnose the issue. The app can attempt to save again when "STOP" is pressed.