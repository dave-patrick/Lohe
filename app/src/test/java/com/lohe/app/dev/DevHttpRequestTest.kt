package com.lohe.app.dev

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class DevHttpRequestTest {
    private fun read(headers: String, body: String = "") = DevHttpRequest.read(
        ByteArrayInputStream(("POST /mcp HTTP/1.1\r\n$headers\r\n\r\n$body").toByteArray()), "test-token")

    @Test fun unauthorizedLargeBodyIsRejectedBeforeAllocationOrReading() {
        val failure = assertThrows(DevHttpException::class.java) { read("Content-Length: 2147483647") }
        assertEquals(401, failure.status)
    }

    @Test fun rejectsOversizedAuthenticatedBody() {
        val failure = assertThrows(DevHttpException::class.java) {
            read("Authorization: Bearer test-token\r\nContent-Length: 2147483647")
        }
        assertEquals(413, failure.status)
    }

    @Test fun lengthCountsUtf8BytesAndNotCharacters() {
        val body = "{\"title\":\"Hawaiʻi\"}"
        assertEquals(body, read("Authorization: Bearer test-token\r\nContent-Length: ${body.toByteArray().size}", body).body)
    }

    @Test fun rejectsAmbiguousFramingAndTruncation() {
        for (headers in listOf("Content-Length: 1\r\nContent-Length: 2", "Content-Length: -1", "Transfer-Encoding: chunked", "Content-Length: 3")) {
            assertEquals(400, assertThrows(DevHttpException::class.java) {
                read("Authorization: Bearer test-token\r\n$headers")
            }.status)
        }
    }

    @Test fun limitsHeaderLines() {
        assertEquals(431, assertThrows(DevHttpException::class.java) { read("X-Large: ${"x".repeat(8192)}") }.status)
    }
}
