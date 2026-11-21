package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Lohe Dark-First Theme Palette (Exact Prototype & Screenshot Match)
val LoheBackgroundDark = Color(0xFF121214)
val LoheSurfaceDark = Color(0xFF1C1C1E)
val LoheSurfaceCardDark = Color(0xFF191D24)
val LoheSurfaceElevatedDark = Color(0xFF24262C)
val LoheBorderDark = Color(0xFF2C2C30)
val LoheGlassNavDark = Color(0xFF161B24)
val LoheGlassNavBorderDark = Color(0xFF252D3A)
val LoheMiniPlayerStartDark = Color(0xFF141D2B)
val LoheMiniPlayerEndDark = Color(0xFF1C2533)
val LoheMiniPlayerBorderDark = Color(0xFF2A3E5C)

val LoheElectricBlue = Color(0xFF008EFF)
val LoheCyanAccent = Color(0xFF9AA3B2)
val LoheBrightBlueText = Color(0xFF2997FF)
val LoheTextPrimaryDark = Color(0xFFF4F4F5)
val LoheTextMutedDark = Color(0xFF8E8E93)

// Optional Light Theme Palette (Appearance Settings)
val LoheBackgroundLight = Color(0xFFF6F7F9)
val LoheSurfaceLight = Color(0xFFFFFFFF)
val LoheSurfaceElevatedLight = Color(0xFFEEF1F6)
val LoheBorderLight = Color(0xFFDCDFE6)
val LoheTextPrimaryLight = Color(0xFF121214)
val LoheTextMutedLight = Color(0xFF636369)

// Free Preset Up Next Queue Indicator Colors (FOSS Parity + Appearance Presets)
enum class QueuePresetColor(val label: String, val hex: String, val color: Color) {
    BLUE("Blue", "#008EFF", Color(0xFF008EFF)),
    TEAL("Teal", "#14B8A6", Color(0xFF14B8A6)),
    AMBER("Amber", "#FF9F0A", Color(0xFFFF9F0A)),
    GREEN("Green", "#22C55E", Color(0xFF22C55E)),
    RED("Red", "#EF4444", Color(0xFFEF4444)),
    ORANGE("Orange", "#F97316", Color(0xFFF97316)),
    YELLOW("Yellow", "#EAB308", Color(0xFFEAB308)),
    BROWN("Brown", "#A16207", Color(0xFFA16207)),
    PURPLE("Purple", "#A855F7", Color(0xFFA855F7)),
    GRAY("Gray", "#8E8E93", Color(0xFF8E8E93));

    companion object {
        fun fromName(name: String): QueuePresetColor =
            entries.find { it.name.equals(name, ignoreCase = true) || it.label.equals(name, ignoreCase = true) }
                ?: BLUE
    }
}

fun parseHexColorOrDefault(hex: String, defaultColor: Color = LoheElectricBlue): Color {
    val cleaned = hex.trim().removePrefix("#")
    return try {
        when (cleaned.length) {
            6 -> Color(("FF$cleaned").toLong(16))
            8 -> Color(cleaned.toLong(16))
            else -> defaultColor
        }
    } catch (_: Exception) {
        defaultColor
    }
}
