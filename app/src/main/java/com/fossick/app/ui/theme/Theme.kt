package com.fossick.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF83DDB2),
    onPrimary = Color.White,
    background = FossickDark,
    onBackground = FossickDarkText,
    surface = FossickDarkSurface,
    onSurface = FossickDarkText,
    surfaceVariant = Color(0xFF1C1D1B),
    onSurfaceVariant = FossickDarkMuted,
    outline = Color(0xFF454843),
    error = Color(0xFFFF8A80),
    tertiary = Color(0xFF83DDB2)
)

private val LightColorScheme = lightColorScheme(
    primary = FossickGreen,
    onPrimary = Color.White,
    background = FossickBone,
    onBackground = FossickInk,
    surface = Color.White,
    onSurface = FossickInk,
    surfaceVariant = Color(0xFFE9EBE6),
    onSurfaceVariant = FossickGraphite,
    outline = FossickHairline,
    error = FossickVermilion,
    tertiary = FossickGreen
)

@Composable
fun FossickTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FossickTypography,
        content = content
    )
}