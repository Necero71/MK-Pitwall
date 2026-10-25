package com.consoletelemetry.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun ConsoleTelemetryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF4FC3F7),
            background = Color(0xFF0E1116),
            surface = Color(0xFF171B22),
            surfaceVariant = Color(0xFF222833),
        ),
        content = content,
    )
}
