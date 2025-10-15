package com.example.geminichat.llm

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions

/**
 * Data class to hold the MediaPipe LLM engine and its active session.
 */
data class LlmModelInstance(val engine: LlmInference, var session: LlmInferenceSession)

// Type aliases for cleaner function signatures
typealias ResultListener = (partialResult: String, done: Boolean) -> Unit
typealias CleanUpListener = () -> Unit

/**
 * A static helper object to manage the lifecycle and inference execution of the MediaPipe LLM.
 * This abstracts the direct MediaPipe SDK calls from the ViewModel.
 */
object LlmModelHelper {
    private const val TAG = "LlmModelHelper"

    /**
     * Initializes the LLM engine and creates an initial session.
     * @return a Result containing the LlmModelInstance on success, or an Exception on failure.
     */
    fun initialize(
        context: Context,
        modelPath: String,
        maxTokens: Int = 512,
        enableVision: Boolean = true,
    ): Result<LlmModelInstance> = try {
        // 1. Configure the LLM Engine
        val engineOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(maxTokens)
            // Configure image handling capability based on the vision flag
            .setMaxNumImages(if (enableVision) 1 else 0)
            .build()

        // Create the engine (heavy operation, runs on the thread it's called on)
        val engine = LlmInference.createFromOptions(context, engineOptions)

        // 2. Create the Session
        val sessionOptions = LlmInferenceSessionOptions.builder()
            .setGraphOptions(
                GraphOptions.builder()
                    .setEnableVisionModality(enableVision)
                    .build()
            )
            .build()

        val session = LlmInferenceSession.createFromOptions(engine, sessionOptions)

        Result.success(LlmModelInstance(engine, session))
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize LLM", e)
        Result.failure(e)
    }

    /**
     * Resets the current inference session. This is used to clear conversation history
     * and, importantly, to **cancel any ongoing async inference** (Stop button feature).
     */
    fun resetSession(instance: LlmModelInstance, enableVision: Boolean = true) {
        try {
            // Close the old session to stop any running async task
            instance.session.close()
        } catch (_: Exception) {
            // Ignore if session is already closed or closing
        }

        // Create a new session with the required vision setting
        val sessionOptions = LlmInferenceSessionOptions.builder()
            .setGraphOptions(
                GraphOptions.builder()
                    .setEnableVisionModality(enableVision)
                    .build()
            )
            .build()

        instance.session = LlmInferenceSession.createFromOptions(instance.engine, sessionOptions)
    }

    /**
     * Runs the asynchronous inference process, streams partial results, and handles completion.
     * Note: This function is asynchronous and returns immediately.
     */
    fun runInference(
        instance: LlmModelInstance,
        input: String,
        images: List<Bitmap> = emptyList(),
        resultListener: ResultListener,
        cleanUpListener: CleanUpListener,
    ) {
        val session = instance.session

        // 1. Add text query chunk
        if (input.isNotBlank()) session.addQueryChunk(input)

        // 2. Add image data (for multimodal tasks)
        for (bmp in images) {
            // Convert Android Bitmap to MediaPipe Image
            session.addImage(BitmapImageBuilder(bmp).build())
        }

        // 3. Start asynchronous generation
        session.generateResponseAsync { partial, done ->
            try {
                Log.d(TAG, "Inference callback: partial='$partial', done=$done")
                resultListener(partial, done)
            } finally {
                if (done) cleanUpListener()
            }
        }
    }

    /**
     * Releases all LLM resources (session and engine).
     */
    fun cleanUp(instance: LlmModelInstance?) {
        if (instance == null) return
        try { instance.session.close() } catch (_: Exception) {}
        try { instance.engine.close() } catch (_: Exception) {}
    }
}