package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class TelemetrySemanticColors(
    val good: Color,
    val goodContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val wifiAccent: Color,
    val cellularAccent: Color,
    val cardBorder: Color,
    val deepBackdrop: Color
)

val LocalTelemetryColors = staticCompositionLocalOf {
    TelemetrySemanticColors(
        good = SignalEmerald,
        goodContainer = SignalEmeraldContainer,
        warning = SignalAmber,
        warningContainer = SignalAmberContainer,
        danger = SignalCrimson,
        dangerContainer = SignalCrimsonContainer,
        wifiAccent = ElectricCyan,
        cellularAccent = CellularViolet,
        cardBorder = NavyOutline,
        deepBackdrop = NavyVoid
    )
}

private val NetSwitchDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = NavyVoid,
    primaryContainer = ElectricBlueSoft,
    onPrimaryContainer = TextPrimaryDark,
    secondary = ElectricBlue,
    onSecondary = Color.White,
    secondaryContainer = NavyElevated,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = SignalEmerald,
    onTertiary = NavyVoid,
    tertiaryContainer = SignalEmeraldContainer,
    onTertiaryContainer = SignalEmerald,
    background = NavyVoid,
    onBackground = TextPrimaryDark,
    surface = NavyDeep,
    onSurface = TextPrimaryDark,
    surfaceVariant = NavyCard,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = NavyVoid,
    surfaceContainerLow = NavyDeep,
    surfaceContainer = NavySurface,
    surfaceContainerHigh = NavyCard,
    surfaceContainerHighest = NavyElevated,
    outline = NavyOutline,
    outlineVariant = Color(0xFF1D3259),
    error = SignalCrimson,
    onError = Color.White,
    errorContainer = SignalCrimsonContainer,
    onErrorContainer = SignalCrimson
)

private val NetSwitchLightColorScheme = lightColorScheme(
    primary = ElectricBlueLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E7FF),
    onPrimaryContainer = Color(0xFF001C4D),
    secondary = ElectricCyanLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEEF8),
    onSecondaryContainer = Color(0xFF002D3D),
    tertiary = SignalEmeraldLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCFF7DF),
    onTertiaryContainer = Color(0xFF00381A),
    background = LightCanvas,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7FAFF),
    surfaceContainer = LightSurface,
    surfaceContainerHigh = Color(0xFFECF3FC),
    surfaceContainerHighest = Color(0xFFE0EBFA),
    outline = LightOutline,
    outlineVariant = Color(0xFFD4E1F2),
    error = SignalCrimsonLight,
    onError = Color.White,
    errorContainer = Color(0xFFFFD9DF),
    onErrorContainer = Color(0xFF6B0018)
)

@Composable
fun NetSwitchTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) NetSwitchDarkColorScheme else NetSwitchLightColorScheme
    val semanticColors = if (darkTheme) {
        TelemetrySemanticColors(
            good = SignalEmerald,
            goodContainer = SignalEmeraldContainer,
            warning = SignalAmber,
            warningContainer = SignalAmberContainer,
            danger = SignalCrimson,
            dangerContainer = SignalCrimsonContainer,
            wifiAccent = ElectricCyan,
            cellularAccent = CellularViolet,
            cardBorder = NavyOutline,
            deepBackdrop = NavyVoid
        )
    } else {
        TelemetrySemanticColors(
            good = SignalEmeraldLight,
            goodContainer = Color(0xFFD7F7E5),
            warning = SignalAmberLight,
            warningContainer = Color(0xFFFFF0CF),
            danger = SignalCrimsonLight,
            dangerContainer = Color(0xFFFFDCE2),
            wifiAccent = ElectricBlueLight,
            cellularAccent = Color(0xFF5B45D6),
            cardBorder = LightOutline,
            deepBackdrop = LightCanvas
        )
    }

    CompositionLocalProvider(LocalTelemetryColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
