package com.lohe.app.playback

internal fun floatingPlayerPosition(fraction: Float, available: Int): Int =
    ((if (fraction.isFinite()) fraction else 0.5f).coerceIn(0f, 1f) * available.coerceAtLeast(0)).toInt()

internal fun floatingPlayerFraction(position: Int, available: Int): Float =
    if (available <= 0) 0f else (position.toFloat() / available).coerceIn(0f, 1f)

internal fun safeFloatingPlayerOpacity(value: Float): Float =
    if (value.isFinite()) value.coerceIn(0.25f, 1f) else 1f

internal fun floatingPlayerButtonSize(diameter: Int): Int = (diameter - 8).coerceAtLeast(48)
