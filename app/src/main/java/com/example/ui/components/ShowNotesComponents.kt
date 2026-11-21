package com.example.ui.components

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.Html
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.URLSpan
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.example.ui.theme.LoheElectricBlue
import com.example.ui.theme.LoheTextPrimaryDark
import java.net.URI
import java.util.Locale

private val BareUrlRegex = Regex(
    """(?i)\b((?:https?://|www\.)[^\s<>()"'\]]+|mailto:[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,})"""
)

/**
 * Validates and normalizes an external URL extracted from untrusted RSS show notes or chapters.
 * Only allows `https`, `http`, and `mailto` schemes; rejects `javascript:`, `data:`, `file:`,
 * `content:`, `intent:`, or malformed hosts.
 */
fun sanitizeExternalNoteUrl(rawUrl: String?): String? {
    if (rawUrl.isNullOrBlank()) return null
    val trimmed = rawUrl
        .trim()
        .trimEnd('.', ',', ';', ')', ']', '!', '?')
    if (trimmed.isBlank()) return null

    // Reject control characters or embedded whitespace
    if (trimmed.any { it.isISOControl() || it.isWhitespace() }) return null

    val candidate = if (trimmed.startsWith("www.", ignoreCase = true)) {
        "https://$trimmed"
    } else {
        trimmed
    }

    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
    return when (scheme) {
        "https", "http" -> {
            val host = uri.host?.trim().orEmpty()
            if (host.isBlank() || !host.contains('.')) null else candidate
        }
        "mailto" -> {
            val target = candidate.substringAfter(":", "").trim()
            if (target.isBlank() || !target.contains('@')) null else candidate
        }
        else -> null
    }
}

/**
 * Parses RSS HTML show notes into a Jetpack Compose [AnnotatedString] with clickable links
 * (both `<a href>` anchor tags and plain-text URLs) after strict URL scheme validation.
 */
fun buildClickableShowNotesAnnotatedString(
    rawHtml: String,
    linkColor: Color = LoheElectricBlue,
    onOpenUrl: (String) -> Unit = {}
): AnnotatedString {
    val source = rawHtml.trim()
    if (source.isBlank()) {
        return AnnotatedString("No show notes provided for this episode.")
    }

    val spanned: Spanned = Html.fromHtml(source, Html.FROM_HTML_MODE_COMPACT)
    val rawText = spanned.toString()
    val trimStart = rawText.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
    val trimEnd = (rawText.indexOfLast { !it.isWhitespace() } + 1).coerceAtLeast(trimStart)
    val cleanText = rawText.substring(trimStart, trimEnd)
    if (cleanText.isBlank()) {
        return AnnotatedString("No show notes provided for this episode.")
    }

    val linkStyle = TextLinkStyles(
        style = SpanStyle(
            color = linkColor,
            textDecoration = TextDecoration.Underline,
            fontWeight = FontWeight.Medium
        )
    )

    val linkedRanges = mutableListOf<IntRange>()

    return buildAnnotatedString {
        append(cleanText)

        // Preserve basic bold/italic emphasis from HTML spans
        spanned.getSpans(0, spanned.length, StyleSpan::class.java).forEach { span ->
            val start = (spanned.getSpanStart(span) - trimStart).coerceIn(0, cleanText.length)
            val end = (spanned.getSpanEnd(span) - trimStart).coerceIn(start, cleanText.length)
            if (end > start) {
                when (span.style) {
                    Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                    Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                    Typeface.BOLD_ITALIC -> addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                        start,
                        end
                    )
                }
            }
        }

        // Convert HTML <a href="..."> spans into validated clickable Compose links
        spanned.getSpans(0, spanned.length, URLSpan::class.java).forEach { urlSpan ->
            val safeUrl = sanitizeExternalNoteUrl(urlSpan.url) ?: return@forEach
            val start = (spanned.getSpanStart(urlSpan) - trimStart).coerceIn(0, cleanText.length)
            val end = (spanned.getSpanEnd(urlSpan) - trimStart).coerceIn(start, cleanText.length)
            if (end > start) {
                linkedRanges.add(start until end)
                addLink(
                    clickable = LinkAnnotation.Clickable(
                        tag = "note_link",
                        styles = linkStyle,
                        linkInteractionListener = { onOpenUrl(safeUrl) }
                    ),
                    start = start,
                    end = end
                )
            }
        }

        // Auto-link bare http/https/www/mailto URLs that were not already wrapped in <a> tags
        BareUrlRegex.findAll(cleanText).forEach { match ->
            val safeUrl = sanitizeExternalNoteUrl(match.value) ?: return@forEach
            val start = match.range.first
            val trimmedValue = match.value.trimEnd('.', ',', ';', ')', ']', '!', '?')
            val end = (start + trimmedValue.length).coerceAtMost(cleanText.length)
            if (end <= start) return@forEach
            val overlapsExisting = linkedRanges.any { existing ->
                start <= existing.last && (end - 1) >= existing.first
            }
            if (!overlapsExisting) {
                linkedRanges.add(start until end)
                addLink(
                    clickable = LinkAnnotation.Clickable(
                        tag = "note_link",
                        styles = linkStyle,
                        linkInteractionListener = { onOpenUrl(safeUrl) }
                    ),
                    start = start,
                    end = end
                )
            }
        }
    }
}

@Composable
fun ClickableShowNotesText(
    rawHtml: String,
    modifier: Modifier = Modifier,
    textColor: Color = LoheTextPrimaryDark,
    linkColor: Color = LoheElectricBlue,
    style: TextStyle = MaterialTheme.typography.bodyLarge
) {
    val context = LocalContext.current
    val annotated = remember(rawHtml, linkColor) {
        buildClickableShowNotesAnnotatedString(
            rawHtml = rawHtml,
            linkColor = linkColor,
            onOpenUrl = { safeUrl ->
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
        )
    }

    Text(
        text = annotated,
        style = style,
        color = textColor,
        modifier = modifier
    )
}
