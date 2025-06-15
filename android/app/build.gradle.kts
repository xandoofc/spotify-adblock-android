plugins {
    id("com.android.application")
    id("kotlin-android")
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.xanspot.net"
    compileSdk = 35 // Android 14
    ndkVersion = "27.0.12077973"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }

    defaultConfig {
        applicationId = "com.xanspot.net"
        minSdk = 21 // Supports Android 10 and above
        targetSdk = 34 // Android 14
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug") // TODO: Replace with your release signing config
            isMinifyEnabled = true // Enable ProGuard/R8
            isShrinkResources = true // Remove unused resources
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

flutter {
    source = "../.."
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.20")
}