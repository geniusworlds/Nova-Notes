package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val NovaDarkColorScheme = darkColorScheme(
    primary = NovaCyan,
    onPrimary = ObsidianBlack,
    primaryContainer = NovaDeepTeal,
    onPrimaryContainer = TextWhite,
    secondary = NovaTeal,
    onSecondary = ObsidianBlack,
    secondaryContainer = CardElevatedGray,
    onSecondaryContainer = NovaCyan,
    tertiary = NovaAccentGlow,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = TextWhite,
    surface = DeepCharcoal,
    onSurface = TextWhite,
    surfaceVariant = CardDarkGray,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceBorder,
    error = StatusError,
    onError = ObsidianBlack
)

val NovaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NovaNotesTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NovaDarkColorScheme,
        typography = Typography,
        shapes = NovaShapes,
        content = content
    )
}
