package com.lohe.app.dev

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Locale

internal class DevHttpException(val status: Int, message: String) : Exception(message)

internal data class DevHttpRequest(val method: String, val target: String, val headers: Map<String, String>, val body: String) {
    companion object {
        const val MAX_BODY_BYTES = 16 * 1024 * 1024

        /** Parse bytes, authenticate headers first, then read exactly Content-Length UTF-8 bytes. */
        fun read(input: InputStream, token: String): DevHttpRequest {
            val deadline = System.nanoTime() + 30_000_000_000L
            fun line(): String {
                val bytes = ByteArrayOutputStream()
                while (true) {
                    if (System.nanoTime() > deadline) throw DevHttpException(408, "Request timed out")
                    val ch = input.read()
                    if (ch == -1) throw DevHttpException(400, "Incomplete headers")
                    if (ch == 10) return bytes.toString(Charsets.US_ASCII.name()).removeSuffix("\r")
                    if (bytes.size() >= 8192) throw DevHttpException(431, "Header line too long")
                    bytes.write(ch)
                }
            }
            val first = line().split(' ')
            if (first.size != 3 || first[2] != "HTTP/1.1") throw DevHttpException(400, "Invalid request")
            val headers = mutableMapOf<String, String>()
            var total = 0
            while (true) {
                val next = line()
                if (next.isEmpty()) break
                total += next.length + 2
                if (total > 32 * 1024 || headers.size >= 64) throw DevHttpException(431, "Too many headers")
                val colon = next.indexOf(':')
                if (colon <= 0) throw DevHttpException(400, "Invalid header")
                val name = next.substring(0, colon).lowercase(Locale.ROOT)
                if (headers.put(name, next.substring(colon + 1).trim()) != null) throw DevHttpException(400, "Duplicate header")
            }
            if (!MessageDigest.isEqual(headers["authorization"].orEmpty().toByteArray(), "Bearer $token".toByteArray())) {
                throw DevHttpException(401, "Unauthorized")
            }
            if (headers.containsKey("transfer-encoding")) throw DevHttpException(400, "Transfer encoding unsupported")
            val length = headers["content-length"]?.let { it.toLongOrNull() ?: throw DevHttpException(400, "Invalid length") } ?: 0
            if (length < 0) throw DevHttpException(400, "Invalid length")
            if (length > MAX_BODY_BYTES) throw DevHttpException(413, "Request too large")
            val bytes = ByteArray(length.toInt())
            var offset = 0
            while (offset < bytes.size) {
                if (System.nanoTime() > deadline) throw DevHttpException(408, "Request timed out")
                val count = input.read(bytes, offset, minOf(8192, bytes.size - offset))
                if (count == -1) throw DevHttpException(400, "Incomplete body")
                offset += count
            }
            return DevHttpRequest(first[0], first[1], headers, bytes.toString(Charsets.UTF_8))
        }
    }
}
