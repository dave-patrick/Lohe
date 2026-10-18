package com.lohe.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Lohe appearance.
 *
 * Dark is the product's primary mode and the default — it does **not** follow
 * the system setting. Light is an explicit user choice from Settings.
 *
 * Rationale: the palette, the glass surfaces and the artwork treatment are all
 * designed dark-first. Treating light as a derived variant rather than a peer
 * keeps that honest.
 */
enum class Appearance { DARK, LIGHT }

@Composable
fun LoheTheme(
    appearance: Appearance = Appearance.DARK,
    content: @Composable () -> Unit,
) {
    val colors = when (appearance) {
        Appearance.DARK -> LoheDarkColors
        Appearance.LIGHT -> LoheLightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = appearance == Appearance.LIGHT
                isAppearanceLightNavigationBars = appearance == Appearance.LIGHT
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = LoheTypography,
        content = content,
    )
}