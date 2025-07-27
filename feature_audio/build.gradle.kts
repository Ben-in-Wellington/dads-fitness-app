plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    // add for Hilt in feature module if not present:
    id("org.jetbrains.kotlin.kapt")
    // add for Hilt in features if using ViewModel injection:
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.di.feature_audio"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // Media3 Player for radio streaming
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    // implementation("androidx.media3:media3-ui:1.3.1") // only if you want widget UI

    // Hilt for DI/viewmodel
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Compose UI (usually some are transitive, but safe to include)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)

    // You likely do not need these here, unless composing UI directly in this module:
    // implementation(libs.androidx.ui.graphics)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}