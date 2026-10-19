package com.lohe.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Lohe type scale.
 *
 * Uses the platform default font family so the app has zero font dependencies.
 * Tight tracking on the display sizes gives the "elite developer" read.
 */
val LoheFontFamily = FontFamily.Default

private val tight = TextStyle(
    fontFamily = LoheFontFamily,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = (-0.5).sp,
)

val LoheTypography = Typography(
    displaySmall = tight.copy(fontSize = 34.sp, lineHeight = 40.sp),
    headlineLarge = tight.copy(fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = tight.copy(fontSize = 23.sp, lineHeight = 29.sp),
    headlineSmall = tight.copy(fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = tight.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(
        fontFamily = LoheFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = LoheFontFamily,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = LoheFontFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = LoheFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = LoheFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.5.sp,
    ),
)
