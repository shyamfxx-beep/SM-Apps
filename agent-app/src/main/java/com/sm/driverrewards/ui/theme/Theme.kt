package com.sm.driverrewards.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = CardWhite,
    primaryContainer = BlueLight,
    secondary = GoldAccent,
    background = BackgroundLight,
    surface = CardWhite,
    onSurface = TextDark
)

@Composable
fun DriverRewardsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
