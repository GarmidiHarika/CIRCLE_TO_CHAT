# CircletoSearch App

A minimal Android project (Kotlin + Jetpack Compose) implementing a Gemini-like chat interface, featuring multimodal (vision) support using MediaPipe's On-Device Large Language Model (LLM) inference. The application uses modern Material 3 design patterns.

## Features

This application showcases several key features, including:

* **LLM-Powered Chat:** Integrates a local, on-device LLM (specifically the `gemma-3n-E2B-it-int4.task` model) for text and multimodal responses.
* **Multimodal Input (Circle-to-Chat):** Allows users to capture an image or select one from the gallery and then use a **circular selection tool** to crop a specific region. This cropped image is passed to the LLM with the prompt "Describe the circled region in the image". The app also includes a "Use Full Image" option to skip cropping.
* **Persistent Conversation History:** Chat messages are saved and loaded using Android's `SharedPreferences` to maintain context across sessions.
* **Settings and Model Management:**
    * Toggle for Dark Theme/Light Theme.
    * Field to input and save the **Hugging Face Token** for model download authorization.
    * Functionality to **download the 3.2 GB LLM file** from the specified Hugging Face URL to the app's internal storage.
    * Real-time download progress indication (percentage and `LinearProgressIndicator`).
    * Display of model status (Ready, Initializing, Downloading, Not Found).
* **Robust Chat UI:** Implements rounded, asymmetrical chat bubbles for user and bot messages, a dedicated **Stop Generation** button that cancels ongoing inference, and a **Clear Chat History** button.

## Requirements

* **Java Development Kit (JDK):** Version 17 or higher (the Gradle build script specifies target compatibility 17).
* **Android SDK:** `compileSdk 34`.
* **Gradle:** Version 9.0-milestone-1 (as specified in `gradle/wrapper/gradle-wrapper.properties`).
* **Hugging Face Token:** A personal access token is required to download the LLM model file.

## Setup & Configuration

### 1. Configure Hugging Face Token

To enable model download, you must set up your Hugging Face token:

1.  Create a file named `local.properties` in the root directory of the project (at the same level as `build.gradle.kts`).
2.  Add your Hugging Face token to this file:
    ```properties
    HUGGING_FACE_TOKEN=hf_YOUR_TOKEN_HERE
    ```
    The build system reads this token and embeds it into `BuildConfig.HUGGING_FACE_TOKEN`. The application also prompts the user to input and save the token in the Settings screen, which is used for the actual download.

### 2. Permissions

The application requires the following permissions, which are declared in `app/src/main/AndroidManifest.xml`:

* `android.permission.CAMERA` (for capturing images)
* `android.permission.READ_EXTERNAL_STORAGE` (for reading images from gallery)
* `android.permission.WRITE_EXTERNAL_STORAGE` (maxSdkVersion 28)
* `android.permission.INTERNET` (for model download)
* `android.permission.ACCESS_NETWORK_STATE`

## Core Dependencies

The core LLM and UI logic rely on the following key dependencies:

| Library | Version | Purpose |
| :--- | :--- | :--- |
| `com.google.mediapipe:tasks-genai` | `0.10.27` | Provides the LLM Inference engine. |
| `com.google.mediapipe:tasks-core` | `0.10.14` | Provides core utilities like `BitmapImageBuilder` for multimodal input. |
| `com.squareup.okhttp3:okhttp` | `4.12.0` | Used for the HTTP client required for model downloading. |
| `androidx.compose:compose-bom` | `2024.06.00` | Jetpack Compose Bill of Materials for UI development. |
| `com.google.code.gson:gson` | `2.10.1` | Used for saving and loading chat history to/from JSON format. |

## Build and Run

1.  Open the project folder in **Android Studio**.
2.  Ensure you have completed the [Setup & Configuration](#1-configure-hugging-face-token) steps.
3.  Synchronize the Gradle project.
4.  Run the application on a device or emulator (API 24+).
    * **Note:** Due to the large size of the LLM model (~3.2 GB), it is highly recommended to use a physical device or an emulator with adequate storage and memory for a smooth experience.

Alternatively, using the command line:

```bash
# On macOS/Linux
./gradlew :app:installDebug

# On Windows
gradlew.bat :app:installDebug
