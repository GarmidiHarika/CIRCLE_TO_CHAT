package com.example.geminichat.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val md_theme_light_primary = Color(0xFF006CFF)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFD7E3FF)
val md_theme_light_onPrimaryContainer = Color(0xFF001945)

val LightColors = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    surface = Color(0xFFF7F8FA),
    onSurface = Color(0xFF111827),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111827)
)

val md_theme_dark_primary = Color(0xFF9CC4FF)
val md_theme_dark_onPrimary = Color(0xFF00305A)

val DarkColors = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    surface = Color(0xFF0F1720),
    onSurface = Color(0xFFE6EEF8),
    background = Color(0xFF071028),
    onBackground = Color(0xFFE6EEF8)
)
