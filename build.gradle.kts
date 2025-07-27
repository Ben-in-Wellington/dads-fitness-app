// File: ./build.gradle.kts (Project root)

plugins {
    // Defines the Android Application plugin. 'apply false' means it's available
    // for sub-modules to use, but not applied to the root project itself.
    alias(libs.plugins.android.application) apply false

    // Defines the Android Library plugin for your feature and data modules.
    alias(libs.plugins.android.library) apply false

    // Defines the Kotlin plugin for Android.
    alias(libs.plugins.jetbrains.kotlin.android) apply false

    // Defines the Hilt plugin for dependency injection.
    alias(libs.plugins.hilt.android) apply false
}