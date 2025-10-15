
Minimal Android project (Kotlin + Jetpack Compose) implementing a Gemini-like chat UI using Material3 design patterns.



**IMP** : Create a Local.properties file in Gradle scripts and place Hugging face token 
Features:
- Material3 color scheme with dynamic color fallback
- Rounded/asymmetrical chat bubbles
- Persistent input bar with send IconButton
- Bot persona with icon and labeled messages
- Simple echo bot behaviour for demo

Requirements:
- Java JDK 21 (you indicated you use this)
- Gradle 9.x (use your local Gradle or configure the wrapper)
- Android SDK with compileSdk 34

Build:
1. Open this folder in Android Studio (Arctic Fox or newer).
2. If using command-line Gradle, run: `./gradlew :app:installDebug` (or on Windows use `gradlew.bat`).
3. Alternatively import project into Android Studio and run.

Notes:
- AGP and Compose compiler versions may need to be adjusted to match your local Android Studio / AGP. The sample uses conservative values; bump versions if you face compatibility issues.
- This is a starting point — feel free to request additional features (message persistence, networking, icons, animations).
