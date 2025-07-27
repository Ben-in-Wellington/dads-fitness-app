import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)

    // ESSENTIAL: These plugins enable Hilt and serialization for this module
    id("kotlin-kapt")
    alias(libs.plugins.hilt.android)
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23"
}

android {
    namespace = "com.di.feature_trainer"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }

        // Add the API key as a BuildConfig field
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${localProperties.getProperty("gemini.api.key", "")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true // Enable BuildConfig for API key access
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11" // Fixed: Use version directly
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Project dependencies
    implementation(project(":core_data"))
    // Note: We might need core:bluetooth in the future for real-time integration
    // implementation(project(":core:bluetooth"))

    // Standard AndroidX & Compose dependencies
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Material icons for the UI
    implementation(libs.androidx.material.icons.extended)

    // ESSENTIAL: Hilt dependencies for this module
    implementation(libs.hilt.android)

    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // --- NEW: AI Trainer specific dependencies ---

    // WebSocket client for Gemini Live API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0") // For debugging

    // JSON serialization for API communication
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // --- Room database support (if we need additional queries) ---
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)

    // --- Navigation support ---
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // --- Date/time formatting ---
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

    // Testing dependencies
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0") // For WebSocket testing

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.debug.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// KAPT configuration for better build performance
kapt {
    correctErrorTypes = true
    useBuildCache = true
}