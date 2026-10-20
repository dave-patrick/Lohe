package com.lohe.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The app's popup style, in one place.
 *
 * Material3's stock [androidx.compose.material3.AlertDialog] and
 * [ModalBottomSheet] land on the screen as flat slabs of `surfaceContainer`:
 * a different shape language, a different button treatment and a different
 * type scale from the glass rows the rest of the app is built from. Every
 * popup used to be one of those, so the app had two visual languages.
 *
 * These two composables are the single answer to "what does a popup look
 * like in Lohe", and every dialog and sheet in the app goes through them.
 */

/**
 * A dialog in the house style.
 *
 * The surface is the same translucent, hairline-edged panel the list rows
 * use, with the accent bleeding in from the top. The title carries an icon
 * tile — the same tinted tile the action sheet uses — so a dialog and a
 * sheet read as the same family.
 *
 * @param icon leading icon in a tinted tile; defaults to a warning glyph for
 *   destructive dialogs so the tile is never empty.
 * @param confirmLabel omit for a dialog that only dismisses.
 * @param onDismissAction run when the dismiss button is pressed, for a
 *   dismiss that does something rather than simply closing.
 * @param destructive paints the accent and the confirm button in the error
 *   colour — used for "remove", "delete" and "clear" decisions.
 */
@Composable
fun LoheDialog(
    onDismiss: () -> Unit,
    title: String,
    body: @Composable ColumnScope.() -> Unit,
    icon: ImageVector? = null,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    dismissLabel: String? = "Cancel",
    onDismissAction: (() -> Unit)? = null,
    destructive: Boolean = false,
) {
    val isLight = backgroundIsLight()
    val accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(28.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .padding(horizontal = 24.dp)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ),
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            if (isLight) LoheAccent.glassBorderLight else LoheAccent.glassBorder,
                            Color.Transparent,
                        ),
                    ),
                    shape = shape,
                ),
        ) {
            // The accent bleeds in from the top, the way light catches the
            // top edge of a glass panel. Kept light: it is a glow behind the
            // title, not a wash over the content.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.13f), Color.Transparent),
                            radius = 400f,
                        ),
                    ),
            )

            Column(modifier = Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon ?: Icons.Filled.Warning,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Spacer(Modifier.width(13.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Spacer(Modifier.height(14.dp))

                body()

                if (confirmLabel != null || dismissLabel != null) {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (dismissLabel != null) {
                            TextButton(
                                onClick = {
                                    onDismissAction?.invoke()
                                    onDismiss()
                                },
                            ) {
                                Text(dismissLabel)
                            }
                        }
                        if (confirmLabel != null && onConfirm != null) {
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = onConfirm,
                                enabled = confirmEnabled,
                                colors = if (destructive) {
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    )
                                } else {
                                    ButtonDefaults.buttonColors()
                                },
                            ) {
                                Text(confirmLabel)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A bottom sheet in the house style.
 *
 * Same panel, same hairline, same type scale as [LoheDialog] — and the same
 * top corner radius, so a sheet and a dialog look like the same object
 * opened two different ways.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoheSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { LoheDragHandle() },
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .align(Alignment.CenterHorizontally)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            content = content,
        )
    }
}

/** The slim accent-tinted grab handle that sits at the top of every sheet. */
@Composable
fun LoheDragHandle() {
    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    ),
                ),
            ),
    )
}
