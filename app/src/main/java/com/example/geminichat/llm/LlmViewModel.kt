package com.example.geminichat.llm

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.lang.StringBuilder

/**
 * Handles LLM state, model downloading, initialization, and inference.
 */
class LlmViewModel(
    private val appContext: Context,
    initialModelPath: String,
    private var hfToken: String
) : ViewModel() {

    // --- Public State for Composable Functions (used by MainActivity) ---
    var inProgress by mutableStateOf(false)
        private set
    var preparing by mutableStateOf(false) // Model is being loaded/initialized
        private set
    var isDownloading by mutableStateOf(false)
        private set
    var downloadProgress by mutableFloatStateOf(0f)
        private set
    var downloadComplete by mutableStateOf(false)
        private set
    var needsInitialization by mutableStateOf(false)
        private set

    var response by mutableStateOf<String?>(null) // Final LLM description
        private set
    var error by mutableStateOf<String?>(null) // Error message
        private set
    var currentModelPath by mutableStateOf(initialModelPath) // Path to the currently used model
    var isModelReady by mutableStateOf(false) // Whether the model is fully initialized and ready
        private set

    // --- Internal Properties ---
    private val httpClient = OkHttpClient()
    private var llmInstance: LlmModelInstance? = null // The actual LLM engine instance

    init {
        // Automatically start initialization if the model file is present
        if (File(currentModelPath).exists()) {
            initialize(currentModelPath)
        }
    }

    override fun onCleared() {
        LlmModelHelper.cleanUp(llmInstance)
        llmInstance = null
        super.onCleared()
    }

    /**
     * Initializes the LLM engine using the specified model path.
     */
    fun initialize(modelPath: String) {
        if (preparing) return
        preparing = true
        error = null
        currentModelPath = modelPath

        // Launch initialization on a background thread
        viewModelScope.launch(Dispatchers.Default) {
            val result = LlmModelHelper.initialize(
                context = appContext,
                modelPath = modelPath,
                enableVision = true,
            )
            result.onSuccess { inst ->
                llmInstance = inst
                withContext(Dispatchers.Main) {
                    error = null
                    isModelReady = true
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    error = e.message ?: "Model initialization failed."
                    isModelReady = false
                }
            }
            withContext(Dispatchers.Main) { preparing = false }
        }
    }

    /**
     * Initialize the model after download
     */
    fun initializeModel() {
        if (!needsInitialization) return

        preparing = true
        needsInitialization = false
        error = null

        Log.d("LlmViewModel", "Attempting to initialize model.")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = LlmModelHelper.initialize(
                    context = appContext,
                    modelPath = currentModelPath,
                    enableVision = true,
                )
                result.onSuccess { inst ->
                    llmInstance = inst
                    withContext(Dispatchers.Main) {
                        error = null
                        preparing = false
                        downloadComplete = true // Keep downloadComplete true to show delete button
                        isModelReady = true
                        Log.d("LlmViewModel", "Model initialized successfully.")
                    }
                }.onFailure { e ->
                    withContext(Dispatchers.Main) {
                        error = e.message ?: "Model initialization failed."
                        preparing = false
                        isModelReady = false
                        Log.e("LlmViewModel", "Model initialization failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = "Failed to initialize model: ${e.message}"
                    preparing = false
                    Log.e("LlmViewModel", "Failed to initialize model: ${e.message}")
                }
            }
        }
    }

    /**
     * Delete the current model file
     */
    fun deleteModel() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(currentModelPath)
                if (file.exists()) {
                    // Clean up the current instance first
                    LlmModelHelper.cleanUp(llmInstance)
                    llmInstance = null

                    // Then delete the file
                    val deleted = file.delete()
                    withContext(Dispatchers.Main) {
                        if (deleted) {
                            currentModelPath = ""
                            downloadComplete = false
                            needsInitialization = false
                            isModelReady = false
                        } else {
                            error = "Failed to delete model file"
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = "Error deleting model: ${e.message}"
                }
            }
        }
    }

    /**
     * Clears LLM response and error states.
     */
    fun clearState() {
        response = null
        error = null
    }

    /**
     * Attempts to stop the current LLM inference process immediately.
     * This is the core function for the 'Stop' button feature.
     */
    fun cancelInference() {
        // Closing and resetting the session is the correct way to cancel the MediaPipe async task.
        llmInstance?.let {
            // Resetting the session stops the active generation
            LlmModelHelper.resetSession(it, enableVision = true)
        }

        // Update UI state to reflect cancellation
        viewModelScope.launch(Dispatchers.Main) {
            // Set an error message that the ChatScreen will pick up and display in a bubble.
            error = "Response generation stopped by user."
            inProgress = false
        }
    }

    /**
     * Downloads the model file from the specified URL, using the Hugging Face token.
     */
    fun downloadModel(downloadUrl: String, fileName: String, onComplete: ((String) -> Unit)? = null) {
        if (isDownloading) return
        isDownloading = true
        downloadProgress = 0f
        downloadComplete = false
        needsInitialization = false
        error = null

        viewModelScope.launch(Dispatchers.IO) {
            val file = File(appContext.filesDir, "llm/$fileName")
            file.parentFile?.mkdirs()

            // Get token from SharedPreferences instead of BuildConfig
            val sharedPrefs = appContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val savedToken = sharedPrefs.getString("huggingface_token", "") ?: ""

            // Update the token with the one from SharedPreferences
            if (savedToken.isNotBlank()) {
                hfToken = savedToken
            }

            // The Authorization header is correctly added here
            val request = Request.Builder()
                .url(downloadUrl)
                .header("Authorization", "Bearer $hfToken")
                .build()

            try {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorDetail = if (response.code == 401) {
                            "Download failed: 401 Unauthorized. Check your Hugging Face Token."
                        } else {
                            "Download failed: HTTP ${response.code} - ${response.message}"
                        }
                        throw IOException(errorDetail)
                    }

                    // Stream the response body and track progress
                    response.body?.let { body ->
                        val totalBytes = body.contentLength()
                        var bytesRead: Long = 0
                        val inputStream: InputStream = body.byteStream()

                        file.outputStream().use { outputStream ->
                            val buffer = ByteArray(4096)
                            var read: Int
                            while (inputStream.read(buffer).also { read = it } != -1) {
                                outputStream.write(buffer, 0, read)
                                bytesRead += read
                                if (totalBytes > 0) {
                                    withContext(Dispatchers.Main) {
                                        downloadProgress = bytesRead.toFloat() / totalBytes.toFloat()
                                    }
                                }
                            }
                        }

                        // Set download complete flag
                        withContext(Dispatchers.Main) {
                            downloadComplete = true
                            needsInitialization = true
                            currentModelPath = file.absolutePath
                        }
                    } ?: throw IOException("Empty response body.")
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    error = e.message ?: "Network or file error during download."
                }
                file.delete()
            } finally {
                withContext(Dispatchers.Main) {
                    isDownloading = false
                    downloadProgress = 1f
                }
            }
        }
    }

    /**
     * Runs the cropped bitmap through the multimodal LLM to generate a description.
     */
    fun describeImage(bitmap: Bitmap, onResponseComplete: ((String) -> Unit)? = null) {
        val inst = llmInstance
        if (inst == null) {
            error = "AI Model is not ready. Please wait for initialization or download."
            Log.e("LlmViewModel", "describeImage: llmInstance is null.")
            return
        }

        // Standard prompt for Circle-to-Search style interaction
        val prompt = "Describe the circled region in the image."

        viewModelScope.launch(Dispatchers.Default) {
            withContext(Dispatchers.Main) {
                inProgress = true
                response = null
                error = null
                Log.d("LlmViewModel", "describeImage: Starting image description.")
            }
            try {
                LlmModelHelper.resetSession(inst, enableVision = true)
                val imgs = listOf(bitmap)

                // Run inference and stream the result back to the UI
                LlmModelHelper.runInference(
                    instance = inst,
                    input = prompt,
                    images = imgs,
                    resultListener = { partial, done ->
                        viewModelScope.launch(Dispatchers.Main) {
                            response = (response ?: "") + partial // Append the streamed response
                            if (done) {
                                inProgress = false
                                // Call the callback with the final response
                                response?.let { onResponseComplete?.invoke(it) }
                                Log.d("LlmViewModel", "describeImage: Image description completed. Response: ${response}")
                            }
                        }
                    },
                    cleanUpListener = {
                        // Cleanup logic is handled by the 'done' check in resultListener
                    }
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = e.message ?: "Inference error"
                    inProgress = false
                    Log.e("LlmViewModel", "describeImage: Inference error: ${e.message}")
                }
            }
        }
    }

    /**
     * Runs text inference for chat responses.
     */
    fun respondToText(input: String, conversationHistory: List<String> = emptyList(), onResponseComplete: ((String) -> Unit)? = null) {
        val inst = llmInstance
        if (inst == null) {
            error = "AI Model is not ready. Please wait for initialization or download."
            Log.e("LlmViewModel", "respondToText: llmInstance is null.")
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            withContext(Dispatchers.Main) {
                inProgress = true
                response = null
                error = null
                Log.d("LlmViewModel", "respondToText: Starting text response. Input: $input")
            }
            try {
                LlmModelHelper.resetSession(inst, enableVision = false)

                // Build context from conversation history
                val contextBuilder = StringBuilder()
                if (conversationHistory.isNotEmpty()) {
                    contextBuilder.append("Previous conversation:\n")
                    conversationHistory.forEach { message ->
                        contextBuilder.append("$message\n")
                    }
                    contextBuilder.append("\nNow respond to: ")
                }

                // Add current input
                val contextualInput = if (conversationHistory.isNotEmpty()) {
                    "${contextBuilder}$input"
                } else {
                    input
                }

                // Run inference and stream the result back to the UI
                LlmModelHelper.runInference(
                    instance = inst,
                    input = contextualInput,
                    images = emptyList(),
                    resultListener = { partial, done ->
                        viewModelScope.launch(Dispatchers.Main) {
                            response = (response ?: "") + partial // Append the streamed response
                            if (done) {
                                inProgress = false
                                // Call the callback with the final response
                                response?.let { onResponseComplete?.invoke(it) }
                                Log.d("LlmViewModel", "respondToText: Text response completed. Response: ${response}")
                            }
                        }
                    },
                    cleanUpListener = {
                        // Cleanup logic is handled by the 'done' check in resultListener
                    }
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = e.message ?: "Inference error"
                    inProgress = false
                    Log.e("LlmViewModel", "respondToText: Inference error: ${e.message}")
                }
            }
        }
    }

    /**
     * Resets the response/error states. Called when moving from Result back to Chat screen,
     * or after a message has been added to history (for cleanup).
     */
    fun clearResponse() {
        response = null
        // Note: We don't clear 'error' here because the ChatScreen needs it immediately
        // after cancellation to insert the "stopped by user" message into the chat history.
        inProgress = false
    }


    companion object {
        fun provideFactory(appContext: Context, modelPath: String, token: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    // Passed from MainActivity:
                    return LlmViewModel(appContext.applicationContext, modelPath, token) as T
                }
            }
    }
}