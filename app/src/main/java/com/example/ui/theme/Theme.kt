package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val NovaDarkColorScheme = darkColorScheme(
    primary = ColorPurple, onPrimary = ColorWhite, primaryContainer = ColorPurpleDeep,
    onPrimaryContainer = ColorWhite, secondary = ColorBlue, onSecondary = ColorWhite,
    secondaryContainer = ColorDarkSurface, onSecondaryContainer = ColorLavender,
    tertiary = ColorMint, onTertiary = ColorInk, background = ColorDarkBackground,
    onBackground = ColorWhite, surface = ColorDarkSurface, onSurface = ColorWhite,
    surfaceVariant = ColorDarkCard, onSurfaceVariant = ColorDarkMuted,
    outline = ColorDarkOutline, error = StatusError, onError = ColorWhite
)

private val NovaLightColorScheme = lightColorScheme(
    primary = ColorPurple, onPrimary = ColorWhite, primaryContainer = ColorLavender,
    onPrimaryContainer = ColorInk, secondary = ColorBlue, onSecondary = ColorWhite,
    secondaryContainer = ColorPaleBlue, onSecondaryContainer = ColorInk,
    tertiary = ColorMint, onTertiary = ColorInk, background = ColorLightBackground,
    onBackground = ColorInk, surface = ColorWhite, onSurface = ColorInk,
    surfaceVariant = ColorLightCard, onSurfaceVariant = ColorLightMuted,
    outline = ColorLightOutline, error = StatusError, onError = ColorWhite
)

val NovaShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun NovaNotesTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) NovaDarkColorScheme else NovaLightColorScheme,
        typography = Typography,
        shapes = NovaShapes,
        content = content
    )
}
