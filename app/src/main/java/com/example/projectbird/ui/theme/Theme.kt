package com.example.projectbird.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BirdGreen = Color(0xFF2F6B4F)
private val BirdGreenDark = Color(0xFF214B38)
private val MossTone = Color(0xFF6F8F7B)
private val SandTone = Color(0xFFF4F1EA)
private val StoneTone = Color(0xFFE7E2D8)
private val InkTone = Color(0xFF1D1F1E)
private val ThemeMist = Color(0xFFF9F8F4)
private val PineTone = Color(0xFFB8C8BC)
private val AmberTone = Color(0xFFB7853E)
private val SlateTone = Color(0xFF9AA19D)

private val LightColorScheme = lightColorScheme(
    primary = BirdGreen,
    onPrimary = Color.White,
    primaryContainer = PineTone,
    onPrimaryContainer = InkTone,
    secondary = MossTone,
    onSecondary = Color.White,
    secondaryContainer = StoneTone,
    onSecondaryContainer = InkTone,
    tertiary = AmberTone,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF2DFC2),
    onTertiaryContainer = InkTone,
    background = ThemeMist,
    onBackground = InkTone,
    surface = ThemeMist,
    onSurface = InkTone,
    surfaceVariant = SandTone,
    onSurfaceVariant = Color(0xFF4A4F4C),
    outline = SlateTone,
    outlineVariant = Color(0xFFD7D4CC),
    scrim = Color(0x66000000),
    inverseSurface = Color(0xFF2A2D2B),
    inverseOnSurface = Color(0xFFF3F2EE),
    inversePrimary = Color(0xFFA9D2B8)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA9D2B8),
    onPrimary = Color(0xFF0E2419),
    primaryContainer = BirdGreenDark,
    onPrimaryContainer = Color(0xFFD5EBDC),
    secondary = Color(0xFFB7CCC0),
    onSecondary = Color(0xFF22322A),
    secondaryContainer = Color(0xFF33463C),
    onSecondaryContainer = Color(0xFFDCE7E0),
    tertiary = Color(0xFFE4C48E),
    onTertiary = Color(0xFF3C2A09),
    tertiaryContainer = Color(0xFF5A4115),
    onTertiaryContainer = Color(0xFFF7E3BC),
    background = Color(0xFF111413),
    onBackground = Color(0xFFE7EAE7),
    surface = Color(0xFF111413),
    onSurface = Color(0xFFE7EAE7),
    surfaceVariant = Color(0xFF2A312D),
    onSurfaceVariant = Color(0xFFC2C9C3),
    outline = Color(0xFF8C938E),
    outlineVariant = Color(0xFF434A46),
    scrim = Color(0x99000000),
    inverseSurface = Color(0xFFE7EAE7),
    inverseOnSurface = Color(0xFF1A1D1C),
    inversePrimary = BirdGreen
)

@Composable
fun ProjectBirdTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
