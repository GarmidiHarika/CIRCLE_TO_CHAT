package com.example.geminichat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect as AndroidRect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.graphics.ImageDecoder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.consumeAllChanges

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color as ComposeColor
import com.example.geminichat.ui.theme.GeminiTheme
import com.example.geminichat.llm.LlmViewModel

import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.min


data class Message(
    val text: String,
    val isBot: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val imageUri: String? = null,
    val usedForContext: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // get an Android Context for file lookups inside composable
            val appContext = LocalContext.current.applicationContext

            // App-level states remembered inside composition (correct usage)
            var isDarkTheme by remember { mutableStateOf(false) }
            var currentScreen by remember { mutableStateOf("chat") }
            var attachedImage by remember { mutableStateOf<Bitmap?>(null) }
            var imageToCrop by remember { mutableStateOf<Bitmap?>(null) }


            // Determine the initial model path. Check for downloaded file first, then fallback to legacy ADB path.
            val initialAppFilePath = remember {
                File(appContext.filesDir, "llm/gemma-3n-E2B-it-int4.task").absolutePath
            }
            val legacyModelPath = "/data/local/tmp/llm/gemma-3n-E2B-it-int4.task"
            val initialModelPath = remember {
                if (File(initialAppFilePath).exists()) initialAppFilePath else legacyModelPath
            }

            // LLM ViewModel
            val llmVm: LlmViewModel = viewModel(
                factory = LlmViewModel.provideFactory(
                    appContext = appContext,
                    modelPath = initialModelPath,
                    token = BuildConfig.HUGGING_FACE_TOKEN
                )
            )

            GeminiTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (currentScreen) {
                        "chat" -> ChatScreen(
                            onSettingsClick = { currentScreen = "settings" },
                            onImageSelected = { bitmap ->
                                imageToCrop = bitmap
                                currentScreen = "crop"
                            },
                            attachedImage = attachedImage,
                            onRemoveImage = { attachedImage = null },
                            llmVm = llmVm
                        )
                        "settings" -> SettingsScreen(
                            isDarkTheme = isDarkTheme,
                            onThemeToggle = { isDarkTheme = it },
                            onBackClick = { currentScreen = "chat" },
                            llmVm = llmVm // Pass llmVm here
                        )
                        "crop" -> imageToCrop?.let { bitmap ->
                            ImageCropScreen(
                                imageBitmap = bitmap,
                                onCropComplete = { croppedBitmap ->
                                    currentScreen = "chat"
                                    imageToCrop = null
                                    attachedImage = croppedBitmap
                                },
                                onCancel = {
                                    currentScreen = "chat"
                                    imageToCrop = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onSettingsClick: () -> Unit,
    onImageSelected: (Bitmap) -> Unit,
    attachedImage: Bitmap?,
    onRemoveImage: () -> Unit,
    llmVm: LlmViewModel
) {
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    // Load saved messages from SharedPreferences
    val messages = remember {
        val savedMessages = sharedPrefs.getString("chat_messages", null)
        if (savedMessages != null) {
            try {
                val type = object : TypeToken<List<Message>>() {}.type
                val messageList: List<Message> = Gson().fromJson(savedMessages, type)
                mutableStateListOf(*messageList.toTypedArray())
            } catch (e: Exception) {
                mutableStateListOf(
                    Message("Hello! I'm Gemini-like assistant — how can I help?", true)
                )
            }
        } else {
            mutableStateListOf(
                Message("Hello! I'm Gemini-like assistant — how can I help?", true)
            )
        }
    }

    // Define the local clear function
    val clearChatHistory: () -> Unit = {
        // 1. Clear the mutable list
        messages.clear()
        // 2. Add the initial welcome message
        messages.add(Message("Hello! I'm Gemini-like assistant — how can I help?", true))
        // 3. Clear SharedPreferences
        sharedPrefs.edit().remove("chat_messages").apply()
        // 4. Clear attached image and input text
        onRemoveImage()
        text = ""
        // 5. Cancel any ongoing LLM inference and clear error state
        llmVm.cancelInference()
        llmVm.clearState()
    }


    // Save messages when they change
    LaunchedEffect(messages.size) {
        val messagesJson = Gson().toJson(messages.toList())
        sharedPrefs.edit().putString("chat_messages", messagesJson).apply()
    }

    var showImagePreview by remember { mutableStateOf(false) }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            onImageSelected(bitmap)
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                // Use ContentResolver to get Bitmap
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }

                if (bitmap != null) onImageSelected(bitmap)
            } catch (e: Exception) {
                // Log or handle error as needed
            }
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(12.dp)) {
        // Top bar with settings, CLEAR BUTTON, and model status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Model status (Left side)
            val colorScheme = MaterialTheme.colorScheme
            val modelFileExists = remember(llmVm.currentModelPath) {
                // Check against the expected app path for the downloaded model
                File(context.filesDir, "llm/gemma-3n-E2B-it-int4.task").exists() || File(llmVm.currentModelPath).exists()
            }
            val modelStatus = remember(llmVm.isModelReady, modelFileExists, llmVm.preparing, llmVm.isDownloading) {
                when {
                    llmVm.isDownloading -> "Downloading Model..."
                    llmVm.preparing -> "Initializing Model..."
                    llmVm.isModelReady -> "Model Ready"
                    modelFileExists -> "Model Downloaded, Initializing..."
                    else -> "Model Not Found"
                }
            }
            val statusColor = remember(llmVm.isModelReady, modelFileExists) {
                when {
                    llmVm.isModelReady -> colorScheme.primary
                    modelFileExists -> colorScheme.secondary
                    else -> colorScheme.error
                }
            }
            Text(
                text = modelStatus,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor
            )

            // Right side: Group Clear and Settings buttons
            Row(verticalAlignment = Alignment.CenterVertically) {

                // Clear Chat Button
                IconButton(
                    onClick = clearChatHistory,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Clear Chat History",
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                // Existing: Settings Button
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Chat area with auto-scrolling
        val listState = rememberLazyListState()

        // Create a combined list of messages for the LazyColumn
        val allChatItems = remember(messages.size, llmVm.inProgress) {
            val list = messages.toMutableList<Any>()
            // Only show loading indicator, not temporary responses
            if (llmVm.inProgress) list.add("loading_indicator") // Use a unique string/object for the loading indicator
            list
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            items(allChatItems) { item ->
                when (item) {
                    is Message -> {
                        ChatBubble(message = item, context = context)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    is String -> if (item == "loading_indicator") {
                        // Show loading indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Android,
                                contentDescription = "Bot",
                                modifier = Modifier
                                    .size(36.dp)
                                    .align(Alignment.CenterVertically)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(
                                    topEnd = 18.dp,
                                    topStart = 6.dp,
                                    bottomEnd = 18.dp,
                                    bottomStart = 18.dp
                                ),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 2.dp,
                                modifier = Modifier.widthIn(max = 260.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Assistant",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "Thinking", style = MaterialTheme.typography.bodyLarge)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // Auto-scroll to bottom when new items are added
        LaunchedEffect(allChatItems.size) {
            if (allChatItems.isNotEmpty()) {
                listState.animateScrollToItem(index = allChatItems.size - 1)
            }
        }

        // Input bar
        Surface(
            tonalElevation = 6.dp,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(4.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Attached image preview
                attachedImage?.let { bitmap ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Attached image",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showImagePreview = true },
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Image attached",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = onRemoveImage,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove image",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = TextStyle(fontSize = 16.sp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) { innerTextField ->
                        if (text.isEmpty()) {
                            Text(
                                "Type a message",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        innerTextField()
                    }

                    // --- IMAGE AND CAMERA BUTTONS ---
                    // These buttons should be disabled when inference is in progress.
                    IconButton(
                        onClick = {
                            cameraLauncher.launch(null)
                        },
                        enabled = !llmVm.inProgress,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Icon(imageVector = Icons.Filled.PhotoCamera, contentDescription = "Camera")
                    }

                    IconButton(
                        onClick = {
                            galleryLauncher.launch("image/*")
                        },
                        enabled = !llmVm.inProgress,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Icon(imageVector = Icons.Filled.Image, contentDescription = "Gallery")
                    }

                    // --- SEND / STOP BUTTON ---
                    IconButton(
                        onClick = {
                            val isSending = llmVm.inProgress

                            if (isSending) {
                                // ACTION: Stop the current process
                                llmVm.cancelInference()
                            } else {
                                // ACTION: Send a new message
                                if (text.isNotBlank() || attachedImage != null) {
                                    // Save image to internal storage if attached
                                    var imageUri: String? = null
                                    if (attachedImage != null) {
                                        val fileName = "img_${System.currentTimeMillis()}.png"
                                        val file = File(context.filesDir, fileName)
                                        try {
                                            val outputStream = FileOutputStream(file)
                                            attachedImage.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                                            outputStream.flush()
                                            outputStream.close()
                                            imageUri = Uri.fromFile(file).toString()
                                        } catch (e: Exception) {
                                            // Handle error
                                        }
                                    }

                                    // Clear previous response/error state before starting new operation
                                    llmVm.clearState()

                                    // Add user message with image URI if available
                                    messages.add(Message(text.trim(), false, imageUri = imageUri))

                                    // Build conversation history from previous messages
                                    val conversationHistory = messages
                                        .filter { !it.usedForContext }
                                        .take(6) // Limit context to last 6 messages
                                        .map { if (it.isBot) "Assistant: ${it.text}" else "User: ${it.text}" }

                                    // Mark messages as used for context
                                    messages.forEach { message ->
                                        if (!message.usedForContext) {
                                            val index = messages.indexOf(message)
                                            if (index >= 0) {
                                                messages[index] = message.copy(usedForContext = true)
                                            }
                                        }
                                    }

                                    // Define the response handler (used for both text and image)
                                    val handleResponse: (String) -> Unit = { response ->
                                        // Use the LLM's error state if the response is empty (e.g., due to cancellation)
                                        val finalResponse = if (response.isNotEmpty()) {
                                            response
                                        } else {
                                            llmVm.error ?: "Sorry, I couldn't generate a response."
                                        }

                                        messages.add(Message(finalResponse, true))

                                        // *IMPORTANT: Clear the error state *after adding it to the message list
                                        llmVm.clearState()
                                    }

                                    // Use LLM for response with conversation history
                                    if (attachedImage != null) {
                                        llmVm.describeImage(attachedImage, handleResponse)
                                    } else {
                                        llmVm.respondToText(text.trim(), conversationHistory, handleResponse)
                                    }

                                    text = ""
                                    onRemoveImage()
                                }
                            }
                        },
                        // Button is always enabled to allow the Stop action
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        // SWITCH ICON: Show Stop if in progress, otherwise show Send
                        if (llmVm.inProgress) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Stop Generation",
                                tint = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Message"
                            )
                        }
                    }
                }
            }
        }
    }

    // Image preview dialog
    if (showImagePreview && attachedImage != null) {
        AlertDialog(
            onDismissRequest = { showImagePreview = false },
            title = { Text("Image Preview") },
            text = {
                Image(
                    bitmap = attachedImage.asImageBitmap(),
                    contentDescription = "Attached image preview",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            },
            confirmButton = {
                Button(onClick = { showImagePreview = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    llmVm: LlmViewModel
) {
    val context = LocalContext.current
    var huggingFaceToken by remember { mutableStateOf("") }
    var showTokenSavedMessage by remember { mutableStateOf(false) }

    // Load saved token if exists
    val sharedPrefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    val savedToken = remember { sharedPrefs.getString("huggingface_token", "") ?: "" }

    // Use the actual path where the model is expected to be downloaded
    val modelFilePath = remember { File(context.filesDir, "llm/gemma-3n-E2B-it-int4.task").absolutePath }

    LaunchedEffect(Unit) {
        huggingFaceToken = savedToken
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar with back button
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        )

        // Settings content
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Theme toggle section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Dark Theme",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = onThemeToggle
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hugging Face Token Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Hugging Face Token",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Enter your Hugging Face token to download models",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = huggingFaceToken,
                        onValueChange = { huggingFaceToken = it },
                        label = { Text("Hugging Face Token") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            // Save token to SharedPreferences
                            sharedPrefs.edit().putString("huggingface_token", huggingFaceToken.trim()).apply()
                            showTokenSavedMessage = true
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save Token")
                    }

                    if (showTokenSavedMessage) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Token saved successfully!",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        // Hide the message after 3 seconds
                        LaunchedEffect(showTokenSavedMessage) {
                            delay(3000)
                            showTokenSavedMessage = false
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Model download section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Model Management",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val modelFile = remember(modelFilePath) { File(modelFilePath) }
                    val modelFileExists by remember { mutableStateOf(modelFile.exists()) }
                    // Re-calculate based on state
                    val modelIsReady by remember(llmVm.preparing, llmVm.isDownloading, modelFileExists) {
                        derivedStateOf { modelFile.exists() && !llmVm.preparing && !llmVm.isDownloading }
                    }

                    val isDownloading = llmVm.isDownloading
                    val downloadProgress = llmVm.downloadProgress

                    if (!modelFileExists) {
                        Text(
                            "Model not found. Download the required model (~3.2 GB) to enable LLM responses.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val downloadUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-preview/resolve/main/gemma-3n-E2B-it-int4.task"
                                val fileName = "gemma-3n-E2B-it-int4.task"
                                // Pass the current token from the input field
                                llmVm.downloadModel(downloadUrl, fileName)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = huggingFaceToken.isNotBlank() && !isDownloading
                        ) {
                            Text("Download Model (~3.2 GB)")
                        }
                        if (huggingFaceToken.isBlank()) {
                            Text(
                                "Please enter a Hugging Face token to download the model",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (isDownloading) {
                            LinearProgressIndicator(progress = downloadProgress, modifier = Modifier.fillMaxWidth())
                            Text("Downloading: ${(downloadProgress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        }

                        // Show initialization animation when download is complete but model needs initialization
                        if (llmVm.downloadComplete && llmVm.needsInitialization && !llmVm.preparing) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Initializing model...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            // Auto-initialize the model
                            LaunchedEffect(llmVm.downloadComplete, llmVm.needsInitialization) {
                                if (llmVm.downloadComplete && llmVm.needsInitialization) {

                                    llmVm.initializeModel()
                                }
                            }
                        }

                        // Show preparing state when model is being initialized
                        if (llmVm.preparing) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Initializing model...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        // Model exists, show delete option
                        Text(
                            "Model is downloaded and ready.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                llmVm.deleteModel()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text("Delete Model")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CircleOverlay(
    boxSize: IntSize,
    imageBounds: Rect,
    center: Offset,
    radius: Float,
    onChange: (Offset, Float) -> Unit
) {
    val density = LocalDensity.current
    val strokePx = 3f
    val handleRadiusPx = with(density) { 20f }
    val handleGrabRadiusPx = with(density) { 36f }
    val minRadiusPx = with(density) { 24f }

    var currentCenter by remember { mutableStateOf(center) }
    var currentRadius by remember { mutableStateOf(radius) }

    val latestCenter by rememberUpdatedState(center)
    val latestRadius by rememberUpdatedState(radius)
    LaunchedEffect(latestCenter) { currentCenter = latestCenter }
    LaunchedEffect(latestRadius) { currentRadius = latestRadius }

    var isResizing by remember { mutableStateOf(false) }
    var isMoving by remember { mutableStateOf(false) }

    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .pointerInput(boxSize, imageBounds) {
                detectDragGestures(
                    onDragStart = { down ->
                        val handleCenter = Offset(currentCenter.x + currentRadius, currentCenter.y)
                        val distToHandle = distance(down, handleCenter)

                        if (distToHandle <= handleGrabRadiusPx) {
                            isResizing = true
                            isMoving = false
                            return@detectDragGestures
                        }

                        if (distance(down, currentCenter) <= currentRadius) {
                            isResizing = false
                            isMoving = true
                        } else {
                            isResizing = false
                            isMoving = false
                        }
                    },
                    onDrag = { change, drag ->
                        change.consumeAllChanges()
                        if (boxSize == IntSize.Zero) return@detectDragGestures

                        if (isResizing) {
                            val newR = distance(currentCenter, change.position)
                            val clampedR = clampRadiusToRect(newR, currentCenter, imageBounds, minRadiusPx)
                            currentRadius = clampedR
                            onChange(currentCenter, currentRadius)
                        } else if (isMoving) {
                            val newCenter = Offset(currentCenter.x + drag.x, currentCenter.y + drag.y)
                            val clampedC = clampCenterToRect(newCenter, currentRadius, imageBounds)
                            currentCenter = clampedC
                            onChange(currentCenter, currentRadius)
                        }
                    },
                    onDragEnd = {
                        isResizing = false
                        isMoving = false
                    },
                    onDragCancel = {
                        isResizing = false
                        isMoving = false
                    }
                )
            }
            .fillMaxSize()
    ) {
        drawRect(color = ComposeColor.Black.copy(alpha = 0.10f))
        drawRect(color = ComposeColor.Black.copy(alpha = 0.25f), topLeft = imageBounds.topLeft, size = imageBounds.size)

        drawCircle(
            color = ComposeColor.Cyan.copy(alpha = 0.15f),
            radius = currentRadius,
            center = currentCenter
        )
        drawCircle(
            color = ComposeColor.Cyan,
            radius = currentRadius,
            center = currentCenter,
            style = Stroke(width = strokePx, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)))
        )
        drawCircle(
            color = ComposeColor.Cyan,
            radius = handleRadiusPx,
            center = Offset(currentCenter.x + currentRadius, currentCenter.y)
        )
    }
}

private fun distance(a: Offset, b: Offset): Float = hypot(a.x - b.x, a.y - b.y)

private fun clampCenterToRect(center: Offset, r: Float, rect: Rect): Offset {
    val minX = rect.left + r
    val maxX = rect.right - r
    val minY = rect.top + r
    val maxY = rect.bottom - r
    val cx = center.x.coerceIn(minX, maxX)
    val cy = center.y.coerceIn(minY, maxY)
    return Offset(cx, cy)
}

private fun clampRadiusToRect(r: Float, center: Offset, rect: Rect, minR: Float): Float {
    val maxR = min(
        min(center.x - rect.left, rect.right - center.x),
        min(center.y - rect.top, rect.bottom - center.y)
    )
    return r.coerceIn(minR, maxR)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCropScreen(
    imageBitmap: Bitmap,
    onCropComplete: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var imageBounds by remember { mutableStateOf(Rect.Zero) }
    var center by remember { mutableStateOf(Offset.Zero) }
    var radius by remember { mutableStateOf(120f) }

    val canCrop = imageBounds.width > 0f &&
            imageBounds.height > 0f &&
            radius >= 8f &&
            center.x - radius >= imageBounds.left &&
            center.x + radius <= imageBounds.right &&
            center.y - radius >= imageBounds.top &&
            center.y + radius <= imageBounds.bottom

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Circle selector") }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { sz ->
                    boxSize = sz
                    imageBounds = computeFitBounds(sz, imageBitmap.width, imageBitmap.height)
                    if (center == Offset.Zero) {
                        center = imageBounds.center
                        radius = min(imageBounds.width, imageBounds.height) * 0.25f
                    }
                }
        ) {
            Image(
                bitmap = imageBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            CircleOverlay(
                boxSize = boxSize,
                imageBounds = imageBounds,
                center = center,
                radius = radius,
                onChange = { c, r -> center = c; radius = r }
            )
        }

        // MODIFIED: Button row now includes "Use Full Image" option
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Back") }

            // 3. Existing Crop Button
            Button(
                enabled = canCrop,
                onClick = {
                    try {
                        val out = cropCircleFromBitmap(
                            source = imageBitmap,
                            imageBoundsInView = imageBounds,
                            circleCenterInView = center,
                            circleRadiusInView = radius
                        )
                        onCropComplete(out)
                    } catch (e: Exception) {
                        // Handle cropping error, e.g., out of memory
                        android.widget.Toast.makeText(
                            context,
                            "Failed to crop image: ${e.localizedMessage ?: "Unknown error"}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        onCancel()
                    }
                },
                modifier = Modifier.weight(2f)
            ) { Text(if (canCrop) "Crop selected area" else "Adjust circle") }
        }
    }
}
fun computeFitBounds(viewSize: IntSize, imgW: Int, imgH: Int): Rect {
    val viewW = viewSize.width.toFloat()
    val viewH = viewSize.height.toFloat()
    val imgAspect = imgW.toFloat() / imgH.toFloat()
    val viewAspect = viewW / viewH

    val fittedW: Float
    val fittedH: Float
    if (imgAspect > viewAspect) {
        fittedW = viewW
        fittedH = viewW / imgAspect
    } else {
        fittedH = viewH
        fittedW = viewH * imgAspect
    }

    val left = (viewW - fittedW) / 2f
    val top = (viewH - fittedH) / 2f
    return Rect(left, top, left + fittedW, top + fittedH)
}

/**
 * Create a circular crop of the source bitmap given a circle specified in view coordinates.
 *
 * - imageBoundsInView: rectangle where the image is drawn in the view (same coordinate space as circleCenterInView)
 * - circleCenterInView / circleRadiusInView: circle in view coordinates
 *
 * Returns a square ARGB_8888 bitmap of size (radius*2).
 */
fun cropCircleFromBitmap(
    source: Bitmap,
    imageBoundsInView: Rect,
    circleCenterInView: Offset,
    circleRadiusInView: Float
): Bitmap {
    val softwareBitmap = source.copy(Bitmap.Config.ARGB_8888, true)
    // Output size in pixels (based on view radius scaled to source using same scale used below)
    val scaleX = softwareBitmap.width.toFloat() / imageBoundsInView.width
    val scaleY = softwareBitmap.height.toFloat() / imageBoundsInView.height
    val scale = min(scaleX, scaleY)

    val bitmapRadius = circleRadiusInView * scale
    val outputSize = (bitmapRadius * 2f).toInt().coerceAtLeast(1)

    // Circle center in bitmap coordinates
    val bitmapCenterX = (circleCenterInView.x - imageBoundsInView.left) * scale
    val bitmapCenterY = (circleCenterInView.y - imageBoundsInView.top) * scale

    // Source rect area to sample (rounded and clamped)
    val left = (bitmapCenterX - bitmapRadius).toInt().coerceAtLeast(0)
    val top = (bitmapCenterY - bitmapRadius).toInt().coerceAtLeast(0)
    val right = (bitmapCenterX + bitmapRadius).toInt().coerceAtMost(softwareBitmap.width)
    val bottom = (bitmapCenterY + bitmapRadius).toInt().coerceAtMost(softwareBitmap.height)

    val srcRect = AndroidRect(left, top, right, bottom)
    val destRect = AndroidRect(0, 0, outputSize, outputSize)

    val output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawARGB(0, 0, 0, 0)

    // Paint for drawing opaque circle mask
    val maskPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
    }
    // Draw circle (dest) as mask (opaque)
    canvas.drawCircle(outputSize / 2f, outputSize / 2f, outputSize / 2f, maskPaint)

    // Now draw bitmap with SRC_IN so only the circle area remains
    val paint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }
    canvas.drawBitmap(softwareBitmap, srcRect, destRect, paint)

    // Clear xfermode (best practice)
    paint.xfermode = null

    return output
}

@Composable
fun ChatBubble(message: Message, context: Context) {
    val bubbleColor = if (message.isBot) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer
    val textColor = if (message.isBot) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer
    val shape = if (message.isBot)
        RoundedCornerShape(topEnd = 18.dp, topStart = 6.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
    else
        RoundedCornerShape(topEnd = 6.dp, topStart = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (message.isBot) Arrangement.Start else Arrangement.End) {
        if (message.isBot) {
            // Bot icon
            Icon(imageVector = Icons.Outlined.Android, contentDescription = "Bot", modifier = Modifier
                .size(36.dp)
                .align(Alignment.CenterVertically))
            Spacer(modifier = Modifier.width(6.dp))
        }

        Surface(
            shape = shape,
            color = bubbleColor,
            tonalElevation = if (message.isBot) 2.dp else 4.dp,
            modifier = Modifier
                .widthIn(max = 260.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (message.isBot) {
                    Text(
                        text = "Assistant",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Display image if available
                message.imageUri?.let { uriString ->
                    val bitmap = remember(uriString) {
                        try {
                            val uri = Uri.parse(uriString)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                val source = ImageDecoder.createSource(context.contentResolver, uri)
                                ImageDecoder.decodeBitmap(source)
                            } else {
                                @Suppress("DEPRECATION")
                                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }

                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Attached image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        Text(
                            text = "[Image could not be loaded]",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // Display message text
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                }
            }
        }
    }
}