# Dad's Fitness Assistant

This is an Android application designed to serve as a fitness assistant and companion for a user who has had a stroke. It is intended to run on a dedicated Android phone mounted on an exercycle.

The core design philosophy prioritizes extreme simplicity, accessibility for users with physical and visual impairments (specifically left-side neglect), and motivation.

See [ARCHITECTURE.md](ARCHITECTURE.md) for a detailed breakdown of the application's architecture and [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) for a concise project overview.

## Features

-   **Simple Session Tracking:** One-tap start/stop for cycling sessions with large, clear real-time stats.
-   **AI Personal Trainer:** A real-time, voice-driven conversational assistant powered by the Gemini Live API. The trainer can discuss progress, provide motivation, and take notes.
-   **Streaming Radio:** A simple player for a pre-set internet radio station.
-   **Post-Session Feedback:** Asks the user simple questions after a workout to track qualitative progress.
-   **Safety & Motivation:** Includes an emergency "Help" button and a system of unlockable achievements.

## Tech Stack & Architecture

-   **Language:** 100% [Kotlin](https://kotlinlang.org/)
-   **UI:** [Jetpack Compose](https://developer.android.com/jetpack/compose)
-   **Architecture:** Model-View-ViewModel (MVVM)
-   **Asynchronous:** [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
-   **Local Storage:** [Room](https://developer.android.com/training/data-storage/room)
-   **AI:** [Gemini Live API](https://ai.google.dev/docs/live_api) via WebSocket
-   **Audio:** [Jetpack Media3 (ExoPlayer)](https://developer.android.com/jetpack/media3) for radio, `AudioRecord`/`AudioTrack` for AI chat.
-   **Dependency Injection:** Hilt

## Project Setup and Build Instructions

Follow these steps to get the project running on your local machine.

### Prerequisites

-   Android Studio (latest stable version recommended)
-   Java Development Kit (JDK) 17 or higher
-   An Android device or emulator running API level 26+

### 1. Clone the Repository

\`\`\`bash
git clone <your-repository-url>
cd <your-repository-name>
\`\`\`

### 2. Set Up API Key

This project requires a Gemini API key to function. To keep it secure, the key is not checked into version control. You must add it locally.

1.  Navigate to the root directory of the project.
2.  Create a file named `local.properties`.
3.  Add your API key to this file in the following format:

    \`\`\`properties
    GEMINI_API_KEY="YOUR_API_KEY_HERE"
    \`\`\`

The app's `build.gradle.kts` file is configured to read this key and make it available to the application at build time.

### 3. Build and Run

1.  Open the project in Android Studio.
2.  Android Studio will automatically sync the Gradle project. This may take a few moments.
3.  Select a run configuration (either an emulator or a connected physical device).
4.  Click the "Run" button (▶️) to build and install the application on your selected device.