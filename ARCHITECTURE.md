# ARCHITECTURE.md

## 1. Overview

This document outlines the architecture for "Dad's Fitness Assistant," an Android application designed to aid a stroke survivor during exercycle sessions. The user has partial left-side paralysis and left-side visual neglect.

The application's primary goals are:
*   Provide an extremely simple interface for starting, stopping, and tracking cycling sessions.
*   Offer simple entertainment through a built-in streaming radio player.
*   Incorporate a voice-driven AI Personal Trainer (using Gemini Live API) for a real-time, bidirectional audio conversation.
*   Provide the AI Trainer with tools to access session history, take notes, and help the user send progress reports.
*   Capture subjective post-session feedback to track qualitative progress.
*   Prioritize accessibility, safety, and motivation in every design decision.

## 2. Guiding Principles

*   **Accessibility First:** The UI/UX must cater to physical and visual impairments.
*   **Simplicity:** Core tasks must be achievable in one or two taps. The interface will be decluttered and intuitive.
*   **Fixed Landscape Orientation:** The app will be locked to landscape mode to best utilize the screen real estate on a fixed panel.
*   **Right-Biased Interaction:** Critical interactive elements will be placed in the center and right regions of the screen. The left region will be used for secondary, non-interactive information.
*   **Offline-First:** Core session tracking and data storage must function without an internet connection.
*   **Modularity:** The application will be divided into distinct, loosely-coupled feature modules.

## 3. UI/UX and Accessibility Details

*   **Layout:** A two-panel landscape design.
    *   **Right/Center Panel (approx. 70% of screen):** Main interaction zone. Contains large buttons for Start/Stop, Radio Play/Pause, and "Talk to Trainer". Displays large-font real-time stats (Time, Speed, etc.).
    *   **Left Panel (approx. 30% of screen):** Secondary information zone. Displays non-interactive content like achievements, simple progress visuals, and the Help button.
*   **Visual Cues:** Subtle animations (e.g., a soft glow) will originate from the right panel and point left to draw attention to new information appearing on the left panel.
*   **High Contrast & Large Fonts:** A simple, high-contrast color theme (with a clear dark mode) will be used. All text will be large and easily legible. All touch targets will be significantly larger than standard Android guidelines.
*   **Sleep Mode:** After 5 minutes of inactivity, the app will enter a "sleep" state, dimming the screen and showing a large digital clock to save power and reduce distraction. A single tap anywhere will wake it up.

## 4. Technology Stack

| Component                | Technology/Library                                   | Rationale                                                                                                       |
| ------------------------ | ---------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| **Language**             | Kotlin                                               | Official, modern, and safe language for Android.                                                                |
| **UI Toolkit**           | Jetpack Compose                                      | Modern declarative UI for building responsive, state-driven, accessible UIs in a landscape-first design.        |
| **Architecture**         | MVVM (Model-View-ViewModel)                          | Clear separation of concerns, testability, and lifecycle-awareness.                                             |
| **Asynchronous**         | Kotlin Coroutines                                    | For lightweight concurrency for background tasks (DB, timers, audio I/O).                                       |
| **Local Database**       | Room                                                 | Robust, boilerplate-free persistence for session data, notes, and surveys.                                      |
| **AI Integration**       | **Gemini Live API (WebSocket)**                      | For real-time, low-latency, bidirectional audio conversation. Manages STT and TTS server-side.                  |
| **AI Model**             | **`gemini-live-2.5-flash-preview`**                  | The "half-cascade" model is chosen for its superior support for Function Calling (Tool Use).                    |
| **Audio Input**          | Android `AudioRecord`                                | To capture raw audio from the microphone in the required format (16-bit PCM, 16kHz, mono).                      |
| **Audio Output**         | Android `AudioTrack`                                 | To play the raw audio stream received from the Gemini Live API (16-bit PCM, 24kHz, mono).                         |
| **Audio Streaming**      | Jetpack Media3 (ExoPlayer)                           | Powerful and robust library for streaming the internet radio station.                                           |
| **Dependency Injection** | Hilt                                                 | Manages dependencies, decouples components, and simplifies testing.                                             |

## 5. Application Architecture (MVVM)

The app follows a standard MVVM pattern. The AI integration is handled via a dedicated repository managing a WebSocket connection.

\`\`\`
+------------------------------------------------------+
|                      View (UI)                       |
|           (Jetpack Compose Screens)                  |
|   (Landscape Layout, Observes ViewModel State)       |
+--------------------------^---------------------------+
                           |
+--------------------------v---------------------------+
|                    ViewModel                         |
|   (e.g., SessionViewModel, TrainerViewModel)         |
|   (Orchestrates Audio I/O, WebSocket, DB ops)        |
+--------------------------^---------------------------+
                           |
+--------------------------v---------------------------+
|                     Repositories                     |
| (SessionRepository, TrainerRepository, RadioService) |
+--------------------------^---------------------------+
                           |
+-----------------+--------------------------+---------+
|   Data Source   |      Data Source         | Data Source |
|   (Room DB)     | (Gemini Live WebSocket)  | (ExoPlayer) |
+-----------------+--------------------------+-----------+
\`\`\`

## 6. Core Modules & Features

### `:app`
Integrates all feature modules and handles top-level navigation and state.

### `:core:data`
Contains the data layer (database definitions, repositories).
*   **Room Database:** `AppDatabase`
*   **Entities:** `SessionEntity`, `SurveyResponseEntity`, `TrainerNoteEntity`, `AchievementEntity`.
*   **DAOs:** `SessionDao`, `TrainerNoteDao`, `AchievementDao`.
*   **Repositories:**
    *   `SessionRepository`: Manages session and achievement data.
    *   `TrainerRepository`: **Crucially, this repository will now manage the Gemini Live API WebSocket connection.** It will handle connecting, sending/receiving audio streams, parsing incoming messages for `toolCall` events, and sending `toolResponse` messages.

### `:feature:session`
Handles the core cycling experience.
*   **UI (Compose):**
    *   **Main Screen:** Displays the right/center and left panels.
    *   **Active Session UI:** Large stats, large "STOP" button.
    *   **Post-Session Survey Screen:** Simple, large-button questions.
*   **ViewModel (`SessionViewModel`):** Manages session state (timer, stats), saves session data via `SessionRepository`, and checks for/unlocks achievements.

### `:feature:audio`
Handles the radio player.
*   **UI (Compose):** A simple component on the right panel with a large Play/Pause button.
*   **Service (`RadioService`):** A foreground service managing the `ExoPlayer` instance to allow background playback.
*   **ViewModel (`AudioViewModel`):** Communicates with the `RadioService` to control playback.

### `:feature:trainer`
The AI personal trainer.
*   **UI (Compose):**
    *   A large "Talk to Trainer" microphone button on the right panel.
    *   A visual indicator for listening/speaking states.
    *   An optional, non-interactive transcript view can be displayed to show the recognized text (`input_audio_transcription`).
*   **ViewModel (`TrainerViewModel`):**
    *   Orchestrates the entire conversation. On button press, it will:
        1.  Tell `TrainerRepository` to open the WebSocket session.
        2.  Start `AudioRecord` and stream audio data to the repository.
        3.  Receive audio data and play it via `AudioTrack`.
        4.  Listen for `toolCall` messages from the repository. When one is received, it will call the appropriate local function (e.g., from `SessionRepository`) and send the result back.
*   **Gemini Live API Tool Definitions:** The following functions will be declared in the WebSocket connection config:
    *   `lookup_session_history(limit: Int)`: Fetches recent sessions from `SessionRepository`.
    *   `add_trainer_note(note: String)`: Saves a string to `TrainerNoteEntity`.
    *   `get_trainer_notes()`: Retrieves all previous trainer notes.
    *   `send_progress_email()`: Creates an `Intent` to open an email client with a pre-filled summary of the latest session.

## 7. Additional Features

*   **Help Button:** A large, clearly marked button on the left panel. When pressed and held for 3 seconds, it sends a pre-configured SMS message to a designated contact.
*   **Motivational Achievements:** The system will award simple badges for milestones (e.g., "First 30-minute session!", "5-day streak!"). These appear on the left panel.
*   **Personalization:** A hidden settings screen (e.g., accessed by a 5-tap gesture on a corner) will allow a caregiver to configure the radio station URL, the "Help" contact number, and other basic settings.

## 8. Development Plan & Phased Rollout

### Phase 1: Core App & Session Tracking (MVP)
1.  **Project Setup:** Set up modules, Hilt, Room, and landscape-locked Compose theme.
2.  **Session Feature:** Implement the session start/stop screen, real-time stat display, and local data storage.
3.  **Radio Feature:** Implement the radio player with `ExoPlayer` and a foreground service.
4.  **Initial UI/UX:** Build the core two-panel landscape layout with high-contrast, large elements.

### Phase 2: AI Trainer Integration (Gemini Live)
1.  **Audio I/O:** Implement the `AudioRecord` -> `AudioTrack` pipeline for capturing and playing raw audio.
2.  **WebSocket Repository:** Build the `TrainerRepository` to manage the Gemini Live WebSocket connection, authentication (using the API key directly for this personal project), and message handling.
3.  **Basic Conversation:** Achieve a full audio-in, audio-out conversation loop.
4.  **Tool Implementation:** Implement the full function calling flow: define tools, receive `toolCall` messages, execute local functions, and send back `toolResponse` messages.

### Phase 3: Polish and Helper Features
1.  **Post-Session Survey:** Implement the survey flow and data storage.
2.  **Achievements & Help Button:** Build the logic for achievements and the SMS-based help button.
3.  **Personalization Screen:** Create the hidden settings screen.
4.  **Continuous User Testing:** Test with Dad at the end of each phase to gather feedback and make iterative improvements.