package com.lohe.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Lohe palette. Mirrors brand/tokens.css — keep the two in sync.
 *
 * Accent is rationed: it marks things that are tappable or currently active,
 * and nothing else.
 */

// --- Surface: dark charcoal ------------------------------------------------
val CharcoalDeep = Color(0xFF121214)
val Charcoal = Color(0xFF1C1C1E)
val CharcoalRaised = Color(0xFF26262A)
val CharcoalHairline = Color(0xFF323238)

// --- Accent: electric blue -------------------------------------------------
// Values come from brand/tokens.css, which self-declares as the source of
// truth and is corroborated by BRAND.md §2 ("#0052FF -> #00D2FF").
//
// An earlier pass took #0080FF / #0052CC from the brand identity artwork
// instead. That was a misread of a colour circle in a rendered JPEG, and it
// desynced this file from tokens.css — see docs/TODO.md item 2.
val ElectricBlue = Color(0xFF0052FF)     // --lohe-accent-700
val ElectricBlueMid = Color(0xFF00A8FF)  // --lohe-accent-500
val ElectricCyan = Color(0xFF00D2FF)     // --lohe-accent-300

// --- Text: frosted white ---------------------------------------------------
val FrostWhite = Color(0xFFF4F4F5)
val FrostDim = Color(0xFFE4E4E7)
val FrostTertiary = Color(0xFFA1A1A6)
val FrostDisabled = Color(0xFF6E6E73)

// --- Light-mode neutrals ---------------------------------------------------
val PaperWhite = Color(0xFFFFFFFF)
val PaperSurface = Color(0xFFF5F5F7)
val PaperSurfaceRaised = Color(0xFFEBEBF0)
val PaperInk = Color(0xFF121214)
val PaperInkMuted = Color(0xFF55555B)
val PaperHairline = Color(0xFFD9D9E0)

/** Brand values that sit outside the Material role set. */
object LoheAccent {
    /** Frosted-glass panel fill over episode artwork. */
    val glassFill = Color(0x14E4E4E7)          // ~8% frosted white
    val glassFillStrong = Color(0x24E4E4E7)    // ~14%
    val glassBorder = Color(0x29E4E4E7)        // ~16% hairline light catch

    /** Light-mode glass: more subtle frosted fill for refined look. */
    val glassFillLight = Color(0x4FFFFFFF)     // ~30% white (reduced from 40%)
    val glassBorderLight = Color(0x15121214)   // ~8% dark (reduced from 10%)

    val electricBlue = ElectricBlue
    val electricCyan = ElectricCyan
}

val LoheDarkColors = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF002AA8),
    onPrimaryContainer = Color(0xFFD6E2FF),
    secondary = ElectricCyan,
    onSecondary = CharcoalDeep,
    secondaryContainer = Color(0xFF00363F),
    onSecondaryContainer = Color(0xFFB8F1FF),
    tertiary = ElectricBlueMid,
    onTertiary = CharcoalDeep,

    background = CharcoalDeep,
    onBackground = FrostWhite,
    surface = Charcoal,
    onSurface = FrostWhite,
    surfaceVariant = CharcoalRaised,
    onSurfaceVariant = FrostTertiary,
    surfaceContainer = CharcoalRaised,
    surfaceContainerHigh = CharcoalHairline,

    outline = FrostDisabled,
    outlineVariant = CharcoalHairline,
    error = Color(0xFFFF453A),
    onError = Color.White,
)

val LoheLightColors = lightColorScheme(
    // #0052FF on white is ~5.8:1 — passes AA without darkening the brand hue.
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FF),
    onPrimaryContainer = Color(0xFF00194D),
    secondary = Color(0xFF0084C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEBFF),
    onSecondaryContainer = Color(0xFF00243A),
    tertiary = ElectricBlueMid,
    onTertiary = Color.White,

    background = PaperWhite,
    onBackground = PaperInk,
    surface = PaperWhite,
    onSurface = PaperInk,
    surfaceVariant = PaperSurface,
    onSurfaceVariant = PaperInkMuted,
    surfaceContainer = PaperSurface,
    surfaceContainerHigh = PaperSurfaceRaised,

    outline = PaperHairline,
    outlineVariant = PaperSurfaceRaised,
    error = Color(0xFFD70015),
    onError = Color.White,
)