import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    kotlin("android")
}
val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        load(FileInputStream(localFile))
    }
}

val huggingFaceToken: String = localProperties.getProperty("HUGGING_FACE_TOKEN") ?: ""

android {
    namespace = "com.example.geminichat"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.geminichat"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // 👇 Add your Hugging Face token to BuildConfig
        buildConfigField("String", "HUGGING_FACE_TOKEN", "\"$huggingFaceToken\"")
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.0")
    // Material 3 (M3)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")

    // Icons
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.code.gson:gson:2.9.0")
    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // LLM dependencies
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // MediaPipe Tasks GenAI + Framework (for BitmapImageBuilder/MPImage)
    implementation("com.google.mediapipe:tasks-genai:0.10.27")
    // Provides MPImage/BitmapImageBuilder used to wrap Bitmaps
    implementation("com.google.mediapipe:tasks-core:0.10.14")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    
    // Gson for JSON serialization/deserialization
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Foundation for verticalScroll
    implementation("androidx.compose.foundation:foundation")
}
