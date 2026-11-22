package com.hcmcbustracker.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val DarkColorScheme = darkColorScheme(
    primary = GreenPrimaryLight,
    onPrimary = BackgroundDark,
    primaryContainer = GreenPrimaryDark,
    onPrimaryContainer = GreenPrimaryLight,
    secondary = BlueSecondaryLight,
    onSecondary = BackgroundDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
    error = RedApproaching,
    outline = DividerDark,
    outlineVariant = BorderSubtleDark
)

val LightColorScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = SurfaceLight,
    primaryContainer = GreenPrimaryLight.copy(alpha = 0.2f),
    onPrimaryContainer = GreenPrimaryDark,
    secondary = BlueSecondary,
    onSecondary = SurfaceLight,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    onSurfaceVariant = TextSecondaryLight,
    error = RedApproaching,
    outline = DividerLight,
    outlineVariant = BorderSubtleLight
)

/**
 * Accessor for subtle hairline borders adapted to current dark/light theme.
 */
val ColorScheme.borderSubtle: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) BorderSubtleDark else BorderSubtleLight

/**
 * Accessor for shimmer base color adapted to current dark/light theme.
 */
val ColorScheme.shimmerBase: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) ShimmerBaseDark else ShimmerBaseLight

/**
 * Accessor for shimmer highlight color adapted to current dark/light theme.
 */
val ColorScheme.shimmerHighlight: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) ShimmerHighlightDark else ShimmerHighlightLight

@Composable
fun HcmcBusTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Default false to enforce unified transit brand identity
    content: @Composable () -> Unit
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
        typography = HcmcTypography,
        shapes = HcmcShapes,
        content = content
    )
}
