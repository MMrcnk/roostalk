plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val ksFile = System.getenv("KEYSTORE_FILE")

android {
    namespace = "pl.blitz.callwebhook"
    compileSdk = 34

    defaultConfig {
        applicationId = "pl.blitz.callwebhook"
        minSdk = 22          // Android 5.1+ (od tej wersji Android obsługuje dual SIM)
        targetSdk = 34
        versionCode = runNumber
        versionName = "1.$runNumber"
        buildConfigField("String", "GH_REPO", "\"${System.getenv("GITHUB_REPOSITORY") ?: ""}\"")
    }

    buildFeatures { buildConfig = true }

    signingConfigs {
        create("release") {
            if (ksFile != null) {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = "callwebhook"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = if (ksFile != null) signingConfigs.getByName("release")
                            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.core:core-ktx:1.13.1")
}
