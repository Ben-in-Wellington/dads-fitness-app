package com.di.fitric.ui.navigation

/**
 * A singleton object that defines all the unique navigation routes as string constants.
 * Using a centralized object like this helps prevent typos and makes it easy to manage
 * all navigation paths in one place.
 */
object Routes {
    /** The main screen displaying workout stats and controls. */
    const val DASHBOARD = "dashboard"

    /**
     * The route pattern for the post-session survey screen.
     * It includes a mandatory 'sessionId' argument.
     */
    const val SURVEY = "survey/{sessionId}"

    /** The screen for the streaming radio player. */
    const val RADIO = "radio"

    /** The main settings screen, which links to other sub-settings screens. */
    const val SETTINGS = "settings"

    /** A sub-screen within settings for sensor calibration. */
    const val CALIBRATION = "settings/calibration"

    /** A sub-screen within settings for entering personal information. */
    const val PERSONAL_INFO = "settings/personal_info"

    /** A sub-screen within settings for configuring the AI Trainer's behavior. */
    const val AI_TRAINER_SETTINGS = "settings/ai_trainer"

    /**
     * A helper function to build the complete, type-safe route for the survey screen.
     * This avoids manual string concatenation in the app code.
     *
     * @param sessionId The ID of the completed workout session.
     * @return The full navigation route string, e.g., "survey/123".
     */
    fun surveyScreen(sessionId: Long) = "survey/$sessionId"
}