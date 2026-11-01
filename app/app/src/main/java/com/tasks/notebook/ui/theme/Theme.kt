package com.tasks.notebook.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun TasksTheme(palette: TasksPalette, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        background = palette.background,
        surface = palette.cloud,
        onBackground = TasksColors.text,
        onSurface = TasksColors.text,
    )
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
    }
}
