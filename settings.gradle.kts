// File: ./settings.gradle.kts (Project root)

// This block configures where Gradle should look for plugins (like the Android Gradle Plugin).
// Your configuration is standard and robust.
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// This block configures where Gradle should look for library dependencies.
// FAIL_ON_PROJECT_REPOS is a modern security best practice.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Sets the name for the entire project in Android Studio.
rootProject.name = "FitRic"

// These lines correctly declare every module that is part of your project.
// Gradle will now know that `:app` can depend on `:feature_session`, etc.
include(":app")
include(":core_data")
include(":feature_session")
include(":feature_audio")
include(":feature_trainer")
include(":core_bluetooth")
