package com.lohe.app.dev

import android.app.Activity
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Wraps the entire app in the local-dev build with a draggable floating "BUG"
 * button that captures a live screenshot + full diagnostic logs for review.
 */
@Composable
fun BugReportOverlay(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var capturing by remember { mutableStateOf(false) }
    var dialogOpen by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var note by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        content()

        if (!capturing && !dialogOpen) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 38.dp)
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    }
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFD9381E).copy(alpha = 0.90f))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .clickable {
                        DevDiagnostics.recordAction("Tapped BUG report button on route=${DevDiagnostics.currentRoute}")
                        if (activity == null) {
                            capturedBitmap = null
                            dialogOpen = true
                        } else {
                            capturing = true
                            scope.launch {
                                // Let one frame render without the floating pill so the screenshot is clean.
                                delay(60)
                                DevDiagnostics.captureScreenshot(activity) { bmp ->
                                    capturedBitmap = bmp
                                    capturing = false
                                    dialogOpen = true
                                }
                            }
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.BugReport,
                    contentDescription = "Submit Bug Report",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "BUG",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (dialogOpen) {
            AlertDialog(
                onDismissRequest = {
                    if (!submitting) {
                        dialogOpen = false
                        note = ""
                    }
                },
                title = {
                    Text("Submit Bug Report (Dev)")
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Route: ${DevDiagnostics.currentRoute}\nCaptures screenshot, app event log, DB summary, and logcat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        capturedBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Captured screenshot preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(8.dp),
                                    ),
                            )
                        }

                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("What happened? (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !submitting,
                        onClick = {
                            submitting = true
                            scope.launch {
                                val reportId = DevDiagnostics.submitBugReport(
                                    context = context,
                                    userNote = note.trim(),
                                    screenshot = capturedBitmap,
                                )
                                submitting = false
                                dialogOpen = false
                                note = ""
                                Toast.makeText(
                                    context,
                                    "Bug report submitted: $reportId",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                    ) {
                        Text(if (submitting) "Saving…" else "Submit Report")
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !submitting,
                        onClick = {
                            dialogOpen = false
                            note = ""
                        },
                    ) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}
