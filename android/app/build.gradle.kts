plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kemzy.liveavatar"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kemzy.liveavatar"
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Configure locally with -PKEMZY_PRIVACY_PASSCODE=... or the
        // KEMZY_PRIVACY_PASSCODE environment variable. Never commit the secret.
        val configuredPasscode = providers.gradleProperty("KEMZY_PRIVACY_PASSCODE").orNull
            ?: System.getenv("KEMZY_PRIVACY_PASSCODE")
            ?: "CONFIGURE_PASSCODE"
        buildConfigField("String", "PRIVACY_PASSCODE", "\"$configuredPasscode\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures { buildConfig = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.2.10")
}
