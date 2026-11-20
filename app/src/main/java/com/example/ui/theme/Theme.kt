package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LoheDarkColorScheme = darkColorScheme(
    primary = LoheElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3B57),
    onPrimaryContainer = LoheCyanAccent,
    secondary = LoheCyanAccent,
    onSecondary = LoheBackgroundDark,
    secondaryContainer = LoheSurfaceElevatedDark,
    onSecondaryContainer = LoheTextPrimaryDark,
    tertiary = LoheElectricBlue,
    background = LoheBackgroundDark,
    onBackground = LoheTextPrimaryDark,
    surface = LoheSurfaceDark,
    onSurface = LoheTextPrimaryDark,
    surfaceVariant = LoheSurfaceElevatedDark,
    onSurfaceVariant = LoheTextMutedDark,
    outline = LoheBorderDark,
    outlineVariant = LoheBorderDark
)

private val LoheLightColorScheme = lightColorScheme(
    primary = LoheElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EEFF),
    onPrimaryContainer = Color(0xFF002B52),
    secondary = Color(0xFF0077CC),
    onSecondary = Color.White,
    secondaryContainer = LoheSurfaceElevatedLight,
    onSecondaryContainer = LoheTextPrimaryLight,
    tertiary = LoheElectricBlue,
    background = LoheBackgroundLight,
    onBackground = LoheTextPrimaryLight,
    surface = LoheSurfaceLight,
    onSurface = LoheTextPrimaryLight,
    surfaceVariant = LoheSurfaceElevatedLight,
    onSurfaceVariant = LoheTextMutedLight,
    outline = LoheBorderLight,
    outlineVariant = LoheBorderLight
)

enum class ThemePreference {
    DARK, LIGHT
}

@Composable
fun LoheTheme(
    themePreference: ThemePreference = ThemePreference.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themePreference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
    }
    val colorScheme = if (useDark) LoheDarkColorScheme else LoheLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    LoheTheme(
        themePreference = if (darkTheme) ThemePreference.DARK else ThemePreference.LIGHT,
        content = content
    )
}
