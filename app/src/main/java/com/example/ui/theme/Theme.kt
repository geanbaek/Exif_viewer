package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkroomColorScheme = darkColorScheme(
    primary = AmberGold,
    onPrimary = OnAmberGold,
    primaryContainer = AmberGoldContainer,
    onPrimaryContainer = OnAmberGoldContainer,
    secondary = TelemetryCyan,
    onSecondary = Color(0xFF002430),
    secondaryContainer = TelemetryCyanContainer,
    onSecondaryContainer = OnTelemetryCyanContainer,
    tertiary = GpsEmerald,
    onTertiary = Color(0xFF002819),
    tertiaryContainer = GpsEmeraldContainer,
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = ObsidianBackground,
    onBackground = TitaniumText,
    surface = ObsidianSurface,
    onSurface = TitaniumText,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = SlateMutedText,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineSubtle,
    error = CoralError,
    onError = Color(0xFF330508),
    errorContainer = CoralErrorContainer,
    onErrorContainer = Color(0xFFFECACA)
)

private val PaperLightColorScheme = lightColorScheme(
    primary = DeepAmber,
    onPrimary = Color.White,
    primaryContainer = DeepAmberContainer,
    onPrimaryContainer = OnDeepAmberContainer,
    secondary = DeepTeal,
    onSecondary = Color.White,
    secondaryContainer = DeepTealContainer,
    onSecondaryContainer = OnDeepTealContainer,
    tertiary = Color(0xFF0D7A54),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF002819),
    background = PaperBackground,
    onBackground = InkText,
    surface = PaperSurface,
    onSurface = InkText,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = WarmGrayText,
    outline = PaperOutline,
    outlineVariant = Color(0xFFE5DFD3),
    error = LightError,
    onError = Color.White,
    errorContainer = LightErrorContainer,
    onErrorContainer = Color(0xFF410002)
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ExifLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkroomColorScheme else PaperLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
