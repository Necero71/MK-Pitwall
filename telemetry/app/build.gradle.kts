plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes -PversionCode=<n> -PversionName=<x.y.z> from telemetry-vX.Y.Z tags (see RELEASING.md).
val appVersionCode = (findProperty("versionCode") ?: "300").toString().toInt()
val appVersionName = (findProperty("versionName") ?: "0.3.0").toString()
val keystoreFile: String? = System.getenv("ANDROID_KEYSTORE_FILE")

android {
    namespace = "com.consoletelemetry"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mavrokinetic.telemetry"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        // Filled in by CI from the repo secrets shared with Pitwall. Without them the
        // release build stays unsigned, so nothing ships signed with the wrong key.
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreFile != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Newer releases of both are built with Kotlin 2.1+, which this Kotlin 2.0 project can't read.
    implementation("nl.dionsegijn:konfetti-compose:2.0.5")
    implementation("com.valentinilk.shimmer:compose-shimmer:1.3.1")

    testImplementation("junit:junit:4.13.2")
}
