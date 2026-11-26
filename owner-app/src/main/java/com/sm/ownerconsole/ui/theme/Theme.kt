package com.sm.ownerconsole.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = DarkBg,
    primaryContainer = DarkCard,
    secondary = SkyAccent,
    background = DarkBg,
    surface = DarkSurface,
    onSurface = TextWhite,
    outline = DarkBorder
)

@Composable
fun OwnerConsoleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
