import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val recommendBaseUrl: String =
    localProperties.getProperty("recommend.baseUrl", "http://10.0.2.2:5000").trim()

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

        buildConfigField("String", "RECOMMEND_BASE_URL", "\"$recommendBaseUrl\"")
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

    // 단위 테스트(PC에서 도는 JVM 테스트)에는 안드로이드의 Log가 없어서, 호출해도 오류 없이 넘어가게 한다
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    // 관절 인식: MediaPipe Pose Landmarker (모델은 assets/models/pose_landmarker_full.task)
    implementation("com.google.mediapipe:tasks-vision:0.10.33")
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")
    // 사진 모델(ONNX, assets/models/body_photo.onnx) 실행. 16KB 페이지 기기를 위해 1.22 이상을 쓴다
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
    testImplementation(libs.junit)
    // 단위 테스트에는 안드로이드의 org.json이 비어 있어서 실제 구현을 따로 넣는다
    testImplementation("org.json:json:20231013")
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}