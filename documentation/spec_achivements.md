#### Feature Spec Outline: Motivational Achievements

**File Name:** `spec_achievements.md`

**1. Feature Name:**
Motivational Achievements & Progress Visualization

**2. User Story:**
"As Dad, I want to see my progress over time and get positive feedback for hitting milestones, so that I stay motivated to continue exercising regularly."

**3. Acceptance Criteria:**
-   **Given** I complete a session, **then** the system must check if any achievement criteria have been met.
-   **Given** a new achievement is unlocked, **then** a clear visual notification must be displayed.
-   **Given** I have unlocked achievements, **then** they must be permanently saved and displayed in a list on the left-hand panel of the main screen.

**4. UI/UX Requirements:**
-   **Unlock Notification:** A non-intrusive but clear notification should appear after a session is saved (e.g., a "toast" message with an icon, or a small pop-up).
-   **Display List:** A simple, scrollable list on the left panel will show icons and names for all unlocked achievements.
-   **Visuals:** Each achievement should have a unique, simple, high-contrast icon.

**5. Functional Requirements (The "Rules Engine"):**
This is the core of the spec. It would define the specific achievements to be implemented in Phase 1.

| Achievement Name         | Triggering Rule                                                         | Data Required from DB             |
| ------------------------ | ----------------------------------------------------------------------- | --------------------------------- |
| **First Ride**           | `count(sessions)` == 1                                                  | Session count                     |
| **Consistent Cyclist**   | `count(sessions where date is in last 7 days)` >= 3                     | Session history (timestamps)      |
| **15-Minute Milestone**  | `session.duration` >= 900 seconds (for the first time)                  | Session history (durations)       |
| **30-Minute Milestone**  | `session.duration` >= 1800 seconds (for the first time)                 | Session history (durations)       |
| **5km Total**            | `sum(sessions.distance)` >= 5 km                                        | Session history (distances)       |
| **Personal Best**        | `session.duration` > `max(previous_sessions.duration)`                  | Session history (durations)       |
