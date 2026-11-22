plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val appVersion = "0.1.0"
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "dev.wizpor"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.wizpor"
        minSdk = 29
        targetSdk = 35
        versionCode = buildNumber
        versionName = "$appVersion ($buildNumber)"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        // Personal sideloaded app: a fixed committed key so every build installs as an update.
        create("release") {
            storeFile = file("wizpor-release.jks")
            storePassword = "wizpor-personal"
            keyAlias = "wizpor"
            keyPassword = "wizpor-personal"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    implementation("org.apache.commons:commons-compress:1.27.1")
    testImplementation("junit:junit:4.13.2")
}
