package com.example.geminichat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme as Material3Theme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun GeminiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = if (darkTheme) {
        // Try to use dynamic color on supported devices, otherwise fallback to DarkColors
        try {
            dynamicDarkColorScheme(context)
        } catch (e: Exception) {
            DarkColors
        }
    } else {
        try {
            dynamicLightColorScheme(context)
        } catch (e: Exception) {
            LightColors
        }
    }

    Material3Theme(
        colorScheme = colorScheme,
        typography = GeminiTypography,
        content = content
    )
}
