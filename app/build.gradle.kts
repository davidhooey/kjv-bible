plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.squished18.kjvbible"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.squished18.kjvbible"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
        // Chapter recordings, one zip per voice and book (see tools/package_audio.py).
        buildConfigField("String", "AUDIO_BASE_URL", "\"https://github.com/squished18/kjv-bible/releases/download/audio-v1\"")
    }

    // Release signing comes from Gradle properties kept outside the repo, e.g. in
    // ~/.gradle/gradle.properties (see tools/create-release-key.sh):
    //   kjvbible.keystore=/path/to/kjvbible-release.p12
    //   kjvbible.keyAlias=kjvbible
    //   kjvbible.storePassword=...
    // Without them, assembleRelease produces an unsigned APK.
    val keystorePath = providers.gradleProperty("kjvbible.keystore").orNull
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storeType = "PKCS12"
                storePassword = providers.gradleProperty("kjvbible.storePassword").get()
                keyAlias = providers.gradleProperty("kjvbible.keyAlias").orElse("kjvbible").get()
                // PKCS12 keystores use the same password for the key.
                keyPassword = storePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            // Installs beside the release app, and fetches audio from a test server on the
            // computer: `adb reverse tcp:8765 tcp:8765` plus a web server serving the zips.
            applicationIdSuffix = ".debug"
            buildConfigField("String", "AUDIO_BASE_URL", "\"http://127.0.0.1:8765\"")
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
        buildConfig = true
    }
    androidResources {
        noCompress += "tsv"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")
}
