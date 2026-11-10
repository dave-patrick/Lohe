package com.lohe.app.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lohe.app.ui.theme.LoheDialog

/** Bundled text stays readable even when a website or network is unavailable. */
@Composable
internal fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val paragraphs = remember {
        context.assets.open("privacy-policy.txt").bufferedReader().use { it.readText() }.trim().split(Regex("""\n\s*\n"""))
    }
    LoheDialog(
        onDismiss = onDismiss,
        title = "Privacy policy",
        icon = Icons.Filled.Policy,
        confirmLabel = null,
        dismissLabel = "Close",
        body = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                paragraphs.forEach { paragraph ->
                    Text(
                        text = paragraph.removePrefix("## ").removePrefix("# "),
                        style = if (paragraph.startsWith("#")) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                        color = if (paragraph.startsWith("#")) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }
        },
    )
}
