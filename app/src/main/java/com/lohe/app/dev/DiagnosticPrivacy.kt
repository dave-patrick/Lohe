package com.lohe.app.dev

/** URLs can contain private feed credentials in any component, including paths. */
internal fun redactDiagnosticText(text: String): String = text
    .replace(Regex("(?i)https?://[^\\s<>\"']+"), "[URL redacted]")
    .replace(Regex("(?i)content://[^\\s<>\"']+"), "[local URI redacted]")

