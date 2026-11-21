package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ToastPlacement
import com.example.data.local.ToastVisualStyle
import com.example.ui.theme.LoheBackgroundDark
import com.example.ui.theme.LoheBorderDark
import com.example.ui.theme.LoheCyanAccent
import com.example.ui.theme.LoheElectricBlue
import com.example.ui.theme.LoheSurfaceCardDark

/**
 * Floating toast overlay rendered inside the main content area strictly above the docked
 * mini-player and bottom navigation bar so it never shifts layout or overlaps controls.
 */
@Composable
fun LoheToastOverlay(
    message: String?,
    style: ToastVisualStyle,
    placement: ToastPlacement,
    reducedMotion: Boolean = false,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alignment = when (placement) {
        ToastPlacement.TOP -> Alignment.TopCenter
        ToastPlacement.CENTER -> Alignment.Center
        ToastPlacement.BOTTOM -> Alignment.BottomCenter
    }

    val enterTransition = if (reducedMotion) {
        fadeIn()
    } else {
        when (placement) {
            ToastPlacement.TOP -> fadeIn() + slideInVertically { -it / 2 }
            ToastPlacement.CENTER -> fadeIn() + scaleIn(initialScale = 0.92f)
            ToastPlacement.BOTTOM -> fadeIn() + slideInVertically { it / 2 }
        }
    }

    val exitTransition = if (reducedMotion) {
        fadeOut()
    } else {
        when (placement) {
            ToastPlacement.TOP -> fadeOut() + slideOutVertically { -it / 2 }
            ToastPlacement.CENTER -> fadeOut() + scaleOut(targetScale = 0.92f)
            ToastPlacement.BOTTOM -> fadeOut() + slideOutVertically { it / 2 }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = alignment
    ) {
        AnimatedVisibility(
            visible = message != null,
            enter = enterTransition,
            exit = exitTransition
        ) {
            message?.let { text ->
                LoheToastVisual(
                    message = text,
                    style = style,
                    onDismiss = onDismiss,
                    modifier = Modifier.testTag("status_banner")
                )
            }
        }
    }
}

/**
 * Reusable visual renderer for Lohe toast notifications. Also used inside Settings
 * as a live preview card so the user sees each style before leaving the screen.
 */
@Composable
fun LoheToastVisual(
    message: String,
    style: ToastVisualStyle,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
    val clickModifier = if (onDismiss != null) Modifier.clickable(onClick = onDismiss) else Modifier

    when (style) {
        ToastVisualStyle.PILL -> {
            val bg = if (isDark) Color(0xF2151C28) else Color(0xF5FFFFFF)
            val border = if (isDark) LoheElectricBlue.copy(alpha = 0.55f) else LoheElectricBlue.copy(alpha = 0.45f)
            Surface(
                shape = RoundedCornerShape(50),
                color = bg,
                shadowElevation = 8.dp,
                modifier = modifier
                    .widthIn(max = 460.dp)
                    .border(1.dp, border, RoundedCornerShape(50))
                    .then(clickModifier)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(LoheElectricBlue)
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (onDismiss != null) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss notification",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        ToastVisualStyle.CARD -> {
            val bg = if (isDark) LoheSurfaceCardDark else MaterialTheme.colorScheme.surface
            val border = if (isDark) LoheBorderDark else MaterialTheme.colorScheme.outline
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = bg,
                shadowElevation = 10.dp,
                modifier = modifier
                    .fillMaxWidth()
                    .widthIn(max = 500.dp)
                    .border(1.dp, border, RoundedCornerShape(16.dp))
                    .then(clickModifier)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(LoheElectricBlue.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = null,
                            tint = LoheElectricBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (onDismiss != null) {
                        Text(
                            text = "Dismiss",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = LoheElectricBlue
                        )
                    }
                }
            }
        }

        ToastVisualStyle.MINIMAL -> {
            val bg = if (isDark) Color(0xFA101622) else Color(0xFA1D2430)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = bg,
                shadowElevation = 6.dp,
                modifier = modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .border(1.dp, LoheCyanAccent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .then(clickModifier)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(LoheElectricBlue)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 10.dp)
                    )
                    if (onDismiss != null) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss notification",
                            tint = Color(0xFFB5C0D0),
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .size(16.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                }
            }
        }

        ToastVisualStyle.ACCENT -> {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = LoheElectricBlue,
                shadowElevation = 10.dp,
                modifier = modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .then(clickModifier)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (onDismiss != null) {
                        Text(
                            text = "OK",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.92f)
                        )
                    }
                }
            }
        }
    }
}
