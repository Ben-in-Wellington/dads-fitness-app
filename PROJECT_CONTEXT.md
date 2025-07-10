# PROJECT_CONTEXT.md

## 1. Core Mission
To create a simple, accessible, and motivating Android fitness assistant for a stroke survivor with partial left-side paralysis and left-side visual neglect. The app will be a fixed panel on an exercycle.

## 2. Primary User & Accessibility Mandates
- **User:** Stroke survivor.
- **Physical:** Difficulty with left-hand dexterity.
- **Visual:** Left-side neglect (may not notice things on the left unless prompted).
- **Non-Negotiable Design Principles:**
    - The app is **LOCKED to Landscape Mode**.
    - **Right-Biased Interaction:** All primary interactive controls (Start/Stop, AI, Radio) MUST be on the center/right of the screen.
    - **Left-Side for Information:** The left side is for secondary, non-interactive information (e.g., achievements, stats display).
    - **Extreme Simplicity:** Large touch targets, high-contrast colors, minimal text, no complex menus.

## 3. Core Features Scope
1.  **Session Tracker:** Simple Start/Stop for cycling sessions with real-time stats (Time, Speed).
2.  **AI Personal Trainer:** A bidirectional, real-time **audio-only** conversation using the **Gemini Live API**.
3.  **AI Tools (Function Calling):** The AI must have access to tools to:
    - `lookup_session_history`
    - `add_trainer_note`
    - `get_trainer_notes`
    - `send_progress_email`
4.  **Audio Player:** Simple one-button playback for a pre-configured streaming radio station.
5.  **Safety & Motivation:**
    - A prominent "Help" button to send an SMS.
    - A system for simple, visual achievements.

## 4. Core Technology Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Architecture:** MVVM
- **Database:** Room
- **Async:** Kotlin Coroutines
- **AI Backend:** Gemini Live API (`gemini-live-2.5-flash-preview` model for tool support)