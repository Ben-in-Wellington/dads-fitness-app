// FitnessApplication

package com.di.fitric

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * The Application class for the Fitness App.
 *
 * This class serves as the entry point for the entire application lifecycle.
 *
 * The @HiltAndroidApp annotation is the most critical part. It triggers Hilt's
 * code generation, which sets up a dependency injection container that is
 * attached to the application's lifecycle. This makes it possible for Hilt
 * to inject dependencies into your Activities, ViewModels, and other Android classes.
 */
@HiltAndroidApp
class FitnessApplication : Application() {
    // The body of this class is often empty.
    // Hilt's annotation processor handles the necessary setup in the background
    // based on the @HiltAndroidApp annotation. You would only add code here
    // for application-level initializations that need to run once when the
    // app starts.
}