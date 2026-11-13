package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = MintPulse,
    onPrimary = Color(0xFF032318),
    primaryContainer = MintDeep,
    onPrimaryContainer = MintOnDeep,
    secondary = IndigoSignal,
    onSecondary = Color(0xFF141133),
    secondaryContainer = IndigoDeep,
    onSecondaryContainer = IndigoOnDeep,
    tertiary = AmberSpark,
    onTertiary = Color(0xFF281700),
    tertiaryContainer = AmberDeep,
    onTertiaryContainer = Color(0xFFFDE68A),
    background = VoidObsidian,
    onBackground = TextPrimaryDark,
    surface = SlateSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = SlateCard,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = VoidObsidian,
    surfaceContainerLow = SlateSurface,
    surfaceContainer = SlateCard,
    surfaceContainerHigh = SlateElevated,
    surfaceContainerHighest = Color(0xFF273549),
    outline = SlateBorder,
    outlineVariant = Color(0xFF1E293B),
    error = CoralAlert,
    onError = Color(0xFF3B0808),
    errorContainer = CoralDeep,
    onErrorContainer = Color(0xFFFECACA),
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldOnContainer,
    secondary = IndigoPrimaryLight,
    onSecondary = Color.White,
    secondaryContainer = IndigoContainerLight,
    onSecondaryContainer = IndigoOnContainerLight,
    tertiary = AmberLight,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = Color(0xFF451A03),
    background = MistCanvas,
    onBackground = TextPrimaryLight,
    surface = FrostSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = FrostCard,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = MistCanvas,
    surfaceContainer = FrostCard,
    surfaceContainerHigh = FrostElevated,
    surfaceContainerHighest = Color(0xFFD0DEE8),
    outline = FrostBorder,
    outlineVariant = Color(0xFFE2ECF2),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
)

val PocketMindShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PocketMindTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PocketMindTypography,
        shapes = PocketMindShapes,
        content = content,
    )
}
