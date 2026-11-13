package com.gilcap.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GilcapColors = darkColorScheme(
    primary = CapsuleMint,
    onPrimary = NightRoad,
    background = NightRoad,
    onBackground = RoadText,
    surface = NightRoad,
    onSurface = RoadText,
    surfaceVariant = RoadSurface,
    onSurfaceVariant = RoadMuted,
    outline = Color(0xFF566D77),
)

@Composable
fun GilcapTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GilcapColors, typography = GilcapTypography, content = content)
}
