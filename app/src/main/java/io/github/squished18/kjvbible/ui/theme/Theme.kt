package io.github.squished18.kjvbible.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import io.github.squished18.kjvbible.data.ThemeMode

private val Burgundy = Color(0xFF7A2E22)
private val Gold = Color(0xFFB8893A)

private val LightColors = lightColorScheme(
    primary = Burgundy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF5DDD5),
    onPrimaryContainer = Color(0xFF3B0D06),
    secondary = Gold,
    secondaryContainer = Color(0xFFF6E7C8),
    onSecondaryContainer = Color(0xFF3A2A06),
    background = Color(0xFFFFFBF7),
    surface = Color(0xFFFFFBF7),
    surfaceVariant = Color(0xFFF3EAE4),
)

private val SepiaColors = lightColorScheme(
    primary = Color(0xFF6B3A1F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9D3B4),
    onPrimaryContainer = Color(0xFF2E1605),
    secondary = Color(0xFF8C6A2F),
    secondaryContainer = Color(0xFFEBD9B2),
    onSecondaryContainer = Color(0xFF2E2205),
    background = Color(0xFFF4ECD8),
    onBackground = Color(0xFF3B2F22),
    surface = Color(0xFFF4ECD8),
    onSurface = Color(0xFF3B2F22),
    surfaceVariant = Color(0xFFE8DCC2),
    onSurfaceVariant = Color(0xFF5A4A36),
    surfaceContainer = Color(0xFFEDE2CA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE8B4A6),
    onPrimary = Color(0xFF4A160C),
    primaryContainer = Color(0xFF5E241A),
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = Color(0xFFE2C27E),
    secondaryContainer = Color(0xFF4A3A14),
    onSecondaryContainer = Color(0xFFF6E7C8),
    background = Color(0xFF16120F),
    onBackground = Color(0xFFE8E0D8),
    surface = Color(0xFF16120F),
    onSurface = Color(0xFFE8E0D8),
    surfaceVariant = Color(0xFF2A231E),
)

/** Serif face used for scripture text. */
val ScriptureFont = FontFamily.Serif

@Composable
fun KjvTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val colors: ColorScheme = when (mode) {
        ThemeMode.LIGHT -> LightColors
        ThemeMode.DARK -> DarkColors
        ThemeMode.SEPIA -> SepiaColors
        ThemeMode.SYSTEM -> if (isSystemInDarkTheme()) DarkColors else LightColors
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean =
    mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
