plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.capstone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.capstone"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    androidResources {
        noCompress += "task"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation("com.google.android.filament:filament-android:1.74.0")
    implementation("com.google.android.filament:gltfio-android:1.74.0")
    implementation("com.google.android.filament:filament-utils-android:1.74.0")
    // CameraX 1.5+ requires AGP 8.9.1 / compileSdk 36, which Android Studio Hedgehog cannot sync.
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    // 관절 인식: MediaPipe Pose Landmarker (모델은 assets/models/pose_landmarker_full.task)
    implementation("com.google.mediapipe:tasks-vision:0.10.21")
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}