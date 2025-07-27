import java.util.Properties

// File: app/build.gradle.kts

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)

    // Apply the Kotlin Annotation Processing Tool (kapt) for Hilt
    id("org.jetbrains.kotlin.kapt")

    // Apply the Hilt plugin to this module
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.di.fitric" // Your package name
    compileSdk = 34

    defaultConfig {
        applicationId = "com.di.fitric"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Load properties from local.properties
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

    compileOptions {
        // Updated to Java 17, which is the modern standard for Android development.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        // Aligned with the Java 17 compile options.
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        // Reference the compiler version from the libs.versions.toml file
        kotlinCompilerExtensionVersion = libs.versions.kotlinCompilerExtension.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core & UI Dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Hilt for Dependency Injection
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)

    // Project Module Dependencies
    // This is where you connect the app module to your features
    implementation(project(":feature_session"))
    implementation(project(":feature_audio"))
    implementation(project(":feature_trainer"))
    implementation(project(":core_data"))
    implementation(project(":core_bluetooth"))

    // Note: You don't need to depend on :core:data here directly,
    // as the feature modules will provide it transitively.

    // Testing Dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.debug.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.androidx.material.icons.extended)

    kapt(libs.hilt.compiler)
}