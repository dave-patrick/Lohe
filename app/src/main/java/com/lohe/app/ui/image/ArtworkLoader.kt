package com.lohe.app.ui.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * Small artwork loader: OkHttp fetch, memory cache, disk cache.
 *
 * Written rather than pulled in because no image library is available offline,
 * and the app only needs one thing — show a remote square image at a known
 * size. Everything here is deliberately modest: no transformations beyond a
 * downscale, no request prioritisation, no lifecycle awareness beyond
 * cancellation by the Compose caller.
 *
 * Not a general-purpose replacement for Coil; it is enough for podcast artwork
 * and small enough to audit.
 */
class ArtworkLoader(
    private val context: Context,
    private val client: okhttp3.OkHttpClient,
    private val maxEdgePx: Int = DEFAULT_MAX_EDGE_PX,
    memoryCacheBytes: Int = DEFAULT_MEMORY_CACHE_BYTES,
) {

    private val memory = object : LruCache<String, Bitmap>(memoryCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val diskDir: File by lazy {
        File(context.cacheDir, "artwork").apply { mkdirs() }
    }

    fun cached(url: String): Bitmap? = memory.get(cacheKey(url))

    /**
     * Loads [url], returning null on any failure.
     *
     * Never throws: a missing piece of artwork is a cosmetic problem and must
     * not take down a list row.
     */
    suspend fun load(url: String): Bitmap? {
        if (url.isBlank()) return null

        cached(url)?.let { return it }
        diskFile(url).takeIf { it.exists() && it.length() > 0L }?.let { file ->
            val decoded = withContext(Dispatchers.IO) { decodeFile(file) }
            if (decoded != null) {
                memory.put(cacheKey(url), decoded)
                return decoded
            }
            // A corrupt cache entry would otherwise be served forever.
            withContext(Dispatchers.IO) { runCatching { file.delete() } }
        }

        val bytes = withContext(Dispatchers.IO) { download(url) } ?: return null
        val bitmap = withContext(Dispatchers.IO) { decode(bytes) } ?: return null

        memory.put(cacheKey(url), bitmap)
        withContext(Dispatchers.IO) {
            runCatching { diskFile(url).writeBytes(bytes) }
        }
        return bitmap
    }

    private fun download(url: String): ByteArray? = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.bytes()
        }
    }.getOrNull()

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdgePx)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun decodeFile(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdgePx)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun diskFile(url: String) = File(diskDir, cacheKey(url))

    companion object {
        const val DEFAULT_MAX_EDGE_PX = 512
        const val DEFAULT_MEMORY_CACHE_BYTES = 8 * 1024 * 1024

        /**
         * Largest power-of-two sample that still covers [maxEdgePx].
         *
         * Sampling in powers of two is what BitmapFactory can actually apply;
         * rounding to an exact target would decode far more pixels than needed.
         */
        internal fun sampleSizeFor(width: Int, height: Int, maxEdgePx: Int): Int {
            if (width <= 0 || height <= 0 || maxEdgePx <= 0) return 1
            var sample = 1
            var longest = maxOf(width, height)
            while (longest / 2 >= maxEdgePx) {
                longest /= 2
                sample *= 2
            }
            return sample
        }

        /**
         * Disk-cache filename for [url].
         *
         * Hashed because feed URLs contain slashes and query strings that are
         * not valid in a filename; the digest keeps the mapping stable so a
         * cached file is reused across launches.
         */
        internal fun cacheKey(url: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            return digest.take(16).joinToString("") { "%02x".format(it) }
        }
    }
}
