// File: core_data/build.gradle.kts

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("org.jetbrains.kotlin.kapt")
    alias(libs.plugins.hilt.android)  // ADD THIS - Essential for Hilt modules
}

android {
    namespace = "com.di.fitric.core_data"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
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
    // Room Dependencies
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)  // ADD THIS - Need Room's annotation processor

    // Hilt for providing repositories
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)  // ADD THIS - Essential for processing Hilt annotations
}