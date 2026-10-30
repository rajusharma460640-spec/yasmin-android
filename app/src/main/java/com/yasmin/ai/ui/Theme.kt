package com.yasmin.ai.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val YasminDark = darkColorScheme(
    primary = Color(0xFFC77DFF),
    onPrimary = Color(0xFF1A0B2E),
    secondary = Color(0xFF5EEAD4),
    background = Color(0xFF0E0F14),
    onBackground = Color(0xFFECE9F5),
    surface = Color(0xFF171923),
    onSurface = Color(0xFFECE9F5),
    surfaceVariant = Color(0xFF232636),
    onSurfaceVariant = Color(0xFFE3E0EE),
    error = Color(0xFFFF8A8A)
)

@Composable
fun YasminTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = YasminDark, content = content)
}
