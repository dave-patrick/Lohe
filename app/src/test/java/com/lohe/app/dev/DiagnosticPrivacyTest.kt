package com.lohe.app.dev

import org.junit.Assert.*
import org.junit.Test

class DiagnosticPrivacyTest {
    @Test fun removesCredentialsQueriesAndPrivateFeedPaths() {
        val output = redactDiagnosticText("Failed https://user:password@feed.example/private-secret/feed?token=secret and content://private/audio")
        assertFalse(output.contains("password"))
        assertFalse(output.contains("private-secret"))
        assertFalse(output.contains("token=secret"))
        assertFalse(output.contains("content://"))
        assertTrue(output.startsWith("Failed"))
    }
}
