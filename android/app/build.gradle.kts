import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Firma estable: si existe keystore.properties (no versionado) se usa esa clave,
// así las actualizaciones se instalan encima sin desinstalar. Si no, debug.keystore.
val ksProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.tradervolume.ytdl"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tradervolume.ytdl"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "1.5.1"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("ydownRelease") {
            val path = ksProps.getProperty("storeFile")
            if (path != null) {
                storeFile = rootProject.file(path)
                storePassword = ksProps.getProperty("storePassword")
                keyAlias = ksProps.getProperty("keyAlias")
                keyPassword = ksProps.getProperty("keyPassword")
            }
        }
        create("releaseDebugSigned") {
            val ks = file(System.getProperty("user.home") + "/.android/debug.keystore")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (ksProps.getProperty("storeFile") != null)
                signingConfigs.getByName("ydownRelease")
            else
                signingConfigs.getByName("releaseDebugSigned")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }
}

configurations.all {
    // No cachear artefactos SNAPSHOT: siempre coger la versión más reciente de JitPack.
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    resolutionStrategy.cacheDynamicVersionsFor(0, "seconds")
}

dependencies {
    // AndroidX core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Coil (miniaturas de YouTube vía URL)
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // NewPipeExtractor (via JitPack), fijado a un commit de la rama dev para que las
    // compilaciones sean reproducibles (antes dev-SNAPSHOT: cada build podía traer otro código).
    // 8443c91bf4 = cabeza de dev el 2026-10-13, la misma que resolvió la compilación de la 1.3.0.
    // Si YouTube rompe el extractor: poner aquí el hash corto del último commit de
    // https://github.com/TeamNewPipe/NewPipeExtractor/commits/dev y recompilar.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:8443c91bf4")
    // Rhino motor JS que NewPipe usa para desofuscar el signatureCipher.
    // Lo declaramos explícito por si JitPack no pasa la dep transitiva.
    implementation("org.mozilla:rhino:1.7.14")

    // FFmpegKit: el artefacto com.arthenica:ffmpeg-kit-audio fue retirado
    // de Maven Central en 2025. En v1 se descarga el audio nativo (m4a) sin
    // reconvertir a MP3. La conversión real llega en v1.1 con un fork mantenido.

    // EncryptedSharedPreferences for cookie storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // WebView helpers
    implementation("androidx.webkit:webkit:1.10.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    testImplementation("junit:junit:4.13.2")
}
