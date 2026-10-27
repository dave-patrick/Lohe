package com.lohe.app.network

import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class BoundedBodyTest {
    @Test fun acceptsExactlyTheLimit() {
        assertArrayEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3).toResponseBody().readBoundedBytes(3))
    }

    @Test fun rejectsOversizedDeclaredBody() {
        assertThrows(IOException::class.java) { ByteArray(4).toResponseBody().readBoundedBytes(3) }
    }

    @Test fun rejectsUnknownLengthOrLyingBodiesByActualBytes() {
        for (length in listOf(-1L, 1L)) {
            val body = object : ResponseBody() {
                override fun contentType() = null
                override fun contentLength() = length
                override fun source() = Buffer().write(ByteArray(4))
            }
            assertThrows(IOException::class.java) { body.readBoundedBytes(3) }
        }
    }
}
