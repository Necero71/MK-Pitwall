plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.flowtv.launcher"
    compileSdk = 35

    defaultConfig {
        // Mavro Kinetic lineup ID. The code keeps its app.flowtv.launcher namespace.
        applicationId = "com.mavrokinetic.flow"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1-mk"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Signed with the debug key so the APK can be sideloaded straight away.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
