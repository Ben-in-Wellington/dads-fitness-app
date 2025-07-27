plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)

    // ESSENTIAL: These two plugins enable Hilt for this module.
    id("kotlin-kapt")
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.di.fitric.feature_session"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.kotlinCompilerExtension.get()
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
    // Project dependency on the data layer
    implementation(project(":core_data"))
    implementation(project(":core_bluetooth"))

    // Standard AndroidX & Compose dependencies
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.material.icons.extended)

    // ESSENTIAL: Hilt dependencies for this module
    implementation(libs.hilt.android)
    implementation(project(":feature_audio")) // Lets you use Hilt annotations
    kapt(libs.hilt.compiler)          // Tells kapt to run the Hilt compiler
    implementation(libs.hilt.navigation.compose) // Provides hiltViewModel()
}