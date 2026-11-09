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
// This file is the source of truth for the app's accent colours (the old
// brand/tokens.css is gone). The accents are read off the wordmark artwork
// rather than chosen by hand.
// The mark's letters are a slab of #008EFF that lifts into a #07C6FC
// highlight, so the ramp is anchored on the blue the eye actually reads as
// the brand's blue: #0052FF was more violet than anything in the artwork.
val ElectricBlue = Color(0xFF008EFF)    // --lohe-accent-700
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

    /** Mostly opaque satin-glass surface for the persistent player and nav. */
    val barGlassFill = Color(0xF21D2B43)
    val barGlassFillLight = Color(0xF2EDF3FB)
    val barGlassEdge = Color(0x383F78B8)
    val barGlassEdgeLight = Color(0x33245B91)

    /**
     * Light-mode glass.
     *
     * These must be an *ink* tint, not a white one. On paper white a 30% white
     * fill is indistinguishable from the page behind it, which is what made
     * every list row disappear in light mode.
     */
    val glassFillLight = Color(0x0F1C1C1E)     // ~6% charcoal — a surface on white
    val glassBorderLight = Color(0x1F121214)   // ~12% charcoal hairline

    val electricBlue = ElectricBlue
    val electricCyan = ElectricCyan
}

/*
 * Contrast (WCAG 2.x relative luminance, measured with a script over the
 * exact hex values below):
 *
 *   Dark scheme
 *     onPrimary (#121214) on primary (#008EFF)        5.63:1  text AA
 *     primary (#008EFF) on background (#121214)       5.63:1  text AA
 *     onSecondaryContainer (#E0ECFF) on #1E3B57       9.69:1  text AA
 *     onSurface (#F4F4F5) on surface (#1C1C1E)       15.48:1  text AA
 *     onSurfaceVariant (#A1A1A6) on surface           6.61:1  text AA
 *     onError (#121214) on error (#FF453A)            5.49:1  text AA
 *     FrostDisabled (#6E6E73) on Charcoal             3.36:1  non-text 3:1 only;
 *                                                             used solely as the
 *                                                             outline colour
 *   Light scheme
 *     onPrimary (#FFFFFF) on primary (#0077D6)        4.56:1  text AA
 *     primary (#0077D6) on background (#FFFFFF)       4.56:1  text AA
 *     onSecondary (#FFFFFF) on secondary (#007CBB)    4.56:1  text AA
 *     onTertiary (#FFFFFF) on tertiary (#007CBD)      4.55:1  text AA
 *     onSurface (#121214) on surface (#FFFFFF)       18.71:1  text AA
 *     onSurfaceVariant (#55555B) on surface           7.40:1  text AA
 *     onSecondaryContainer (#002436) on #D3E9F7      12.84:1  text AA
 *
 * ElectricBlue (#008EFF) itself is 3.33:1 on white and 5.63:1 on the dark
 * background. It is kept as the brand constant for artwork and dark-mode
 * accents; light-mode roles use the darker derivations above.
 */

// --- Material role sets ---------------------------------------------------

val LoheDarkColors = darkColorScheme(
    primary = ElectricBlue,
    // Near-black on the blue: white on #008EFF measured 3.33:1, this is 5.63:1.
    onPrimary = CharcoalDeep,
    // A deep navy rather than a saturated royal blue, which read as a
    // different colour from the accent it was supposed to belong to.
    primaryContainer = Color(0xFF16273D),
    onPrimaryContainer = Color(0xFFCFE0FF),
    secondary = ElectricCyan,
    onSecondary = CharcoalDeep,
    // The selected chip. A slate blue distinct from primary, so a selection
    // no longer shares the exact accent fill used for primary actions.
    secondaryContainer = Color(0xFF1E3B57),
    onSecondaryContainer = Color(0xFFE0ECFF),
    tertiary = ElectricBlueMid,
    onTertiary = CharcoalDeep,
    tertiaryContainer = Color(0xFF0F3A55),
    onTertiaryContainer = Color(0xFFC9EEFF),

    background = CharcoalDeep,
    onBackground = FrostWhite,
    surface = Charcoal,
    onSurface = FrostWhite,
    surfaceVariant = CharcoalRaised,
    onSurfaceVariant = FrostTertiary,
    surfaceDim = CharcoalDeep,
    surfaceBright = Color(0xFF3A3A41),
    surfaceContainerLowest = Color(0xFF0E0E10),
    surfaceContainerLow = Charcoal,
    surfaceContainer = CharcoalRaised,
    surfaceContainerHigh = CharcoalHairline,
    surfaceContainerHighest = Color(0xFF37373E),

    outline = FrostDisabled,
    outlineVariant = CharcoalHairline,
    scrim = Color.Black,

    inverseSurface = PaperSurface,
    inverseOnSurface = PaperInk,
    // A darker blue than ElectricBlue (5.06:1 on the light inverse surface).
    inversePrimary = Color(0xFF0068C8),

    error = Color(0xFFFF453A),
    // White on #FF453A measured 3.41:1; near-black is 5.49:1.
    onError = CharcoalDeep,
    errorContainer = Color(0xFF5C1A17),
    onErrorContainer = Color(0xFFFFDAD6),
)

val LoheLightColors = lightColorScheme(
    // Darkened from the brand accent so white text passes AA. ElectricBlue
    // itself stays the brand constant. See the contrast table above.
    primary = Color(0xFF0077D6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FF),
    onPrimaryContainer = Color(0xFF00194D),
    secondary = Color(0xFF007CBB),
    onSecondary = Color.White,
    // A pale sky tone, distinct from primary, so a selected chip is not the
    // same fill as a primary button.
    secondaryContainer = Color(0xFFD3E9F7),
    onSecondaryContainer = Color(0xFF002436),
    tertiary = Color(0xFF007CBD),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCBEBFF),
    onTertiaryContainer = Color(0xFF001E2E),

    background = PaperWhite,
    onBackground = PaperInk,
    surface = PaperWhite,
    onSurface = PaperInk,
    surfaceVariant = PaperSurface,
    onSurfaceVariant = PaperInkMuted,
    surfaceDim = Color(0xFFDDDDE3),
    surfaceBright = PaperWhite,
    surfaceContainerLowest = PaperWhite,
    surfaceContainerLow = Color(0xFFF9F9FB),
    surfaceContainer = PaperSurface,
    surfaceContainerHigh = PaperSurfaceRaised,
    surfaceContainerHighest = Color(0xFFE2E2E8),

    outline = PaperHairline,
    outlineVariant = PaperSurfaceRaised,
    scrim = Color.Black,

    inverseSurface = Charcoal,
    inverseOnSurface = FrostWhite,
    inversePrimary = ElectricBlue,

    error = Color(0xFFD70015),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)
