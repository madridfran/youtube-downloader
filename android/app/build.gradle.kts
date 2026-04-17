plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tradervolume.ytdl"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tradervolume.ytdl"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}

dependencies {
    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")

    // Extracción YouTube — NewPipeExtractor vía JitPack
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.24.2")

    // HTTP (para NewPipe downloader interface)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Cookies cifradas
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // FFmpeg (audio → mp3). ~20 MB añadidos al APK.
    implementation("com.arthenica:ffmpeg-kit-audio:6.0-2")

    // Corrutinas
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
