package com.lohe.app.network

import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Bounds the actual decoded stream, including gzip and unknown-length responses. */
fun ResponseBody.readBoundedBytes(maxBytes: Int): ByteArray {
    require(maxBytes > 0)
    if (contentLength() > maxBytes) throw IOException("Response exceeds $maxBytes bytes")
    byteStream().use { input ->
        val out = ByteArrayOutputStream(minOf(maxBytes, 8192))
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, maxBytes - out.size() + 1))
            if (count == -1) return out.toByteArray()
            if (out.size().toLong() + count > maxBytes) throw IOException("Response exceeds $maxBytes bytes")
            out.write(buffer, 0, count)
        }
    }
}
