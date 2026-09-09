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

        // The PIN itself is never committed; this is its SHA-256 digest.
        // A build may override it with -P KEMZY_PRIVACY_PIN_SHA256 or the
        // KEMZY_PRIVACY_PIN_SHA256 environment variable.
        val configuredPinHash = providers.gradleProperty("KEMZY_PRIVACY_PIN_SHA256").orNull
            ?: System.getenv("KEMZY_PRIVACY_PIN_SHA256")
            ?: "facfb3c210d09f7443c677eda3a5a2dad35c422f8c3430c0c4cf7f2bca9df931"
        buildConfigField("String", "PRIVACY_PIN_SHA256", "\"$configuredPinHash\"")
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
    implementation("androidx.lifecycle:lifecycle-service:2.9.2")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.2.10")
}
