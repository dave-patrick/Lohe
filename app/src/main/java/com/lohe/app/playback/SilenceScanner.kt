package com.lohe.app.playback

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Finds where an episode actually starts talking.
 *
 * Pocket Casts-style trimming means cutting the dead air at the top of an
 * episode, not chopping a fixed number of seconds off it. There is no metadata
 * that says where the talking begins, so the only honest answer comes from
 * decoding the opening and measuring it.
 *
 * The scan decodes the first [SCAN_LIMIT_MS] of the file and walks it in
 * [WINDOW_MS] windows, looking for the first window that carries speech-level
 * energy. A quarter-second is left before that point so the first syllable is
 * not clipped. Anything that fails to decode is reported as "no trim" rather
 * than as a guess.
 */
object SilenceScanner {

    /** Resolution of the energy measurement. Short enough to catch a word. */
    private const val WINDOW_MS = 100L

    /**
     * Windows quieter than this count as silence. Speech sits around -20 dBFS;
     * room tone and a faded-in music bed sit well below -45, so the line is
     * drawn between the two rather than at digital zero.
     */
    private const val SILENCE_DBFS = -45.0

    /** How far into an episode the scan will look before giving up. */
    private const val SCAN_LIMIT_MS = 45_000L

    /** Kept in front of the detected speech so the first word is intact. */
    private const val LEAD_IN_KEEP_MS = 250L

    /** A long music sting is not silence; never skip more than this. */
    private const val MAX_SKIP_MS = 20_000L

    /** One entry per episode, so replaying does not decode the file again. */
    private val cache = LinkedHashMap<String, Long>()

    /**
     * Milliseconds of leading silence in [uri], or null when it cannot be told.
     *
     * A result of 0 means "starts straight away", which is a real answer and is
     * cached. Null means the file could not be decoded at all.
     */
    suspend fun leadingSilenceMs(
        context: Context,
        episodeUuid: String,
        uri: String,
    ): Long? = withContext(Dispatchers.IO) {
        synchronized(cache) { cache[episodeUuid] }?.let { return@withContext it }

        val found = runCatching { scan(context.applicationContext, uri) }.getOrNull()
            ?: return@withContext null

        android.util.Log.d("SilenceScanner", "${episodeUuid.take(8)} leading silence ${found}ms")

        synchronized(cache) {
            if (cache.size >= 64) cache.remove(cache.keys.first())
            cache[episodeUuid] = found
        }
        found
    }

    private fun scan(context: Context, uri: String): Long {
        val extractor = MediaExtractor()
        try {
            when {
                uri.startsWith("http://") || uri.startsWith("https://") ->
                    extractor.setDataSource(uri, emptyMap())

                uri.startsWith("content://") ->
                    extractor.setDataSource(context, Uri.parse(uri), null)

                else -> extractor.setDataSource(uri.removePrefix("file://"))
            }

            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return 0L

            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return 0L
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)

            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                return decodeUntilSpeech(codec, extractor, sampleRate, channels)
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    /**
     * Decodes sample by sample and returns the first moment that is loud
     * enough to be speech, or 0 when the scan limit passes without any.
     */
    private fun decodeUntilSpeech(
        codec: MediaCodec,
        extractor: MediaExtractor,
        sampleRate: Int,
        channels: Int,
    ): Long {
        val info = MediaCodec.BufferInfo()
        val windowFrames = (sampleRate.toLong() * WINDOW_MS / 1_000L).toInt().coerceAtLeast(1)
        val scanLimitFrames = sampleRate.toLong() * SCAN_LIMIT_MS / 1_000L

        var totalFrames = 0L
        var windowFramesSeen = 0
        var windowEnergy = 0.0
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val buffer = codec.getInputBuffer(inputIndex) ?: continue
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(
                            inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
            if (outputIndex < 0) continue

            val output = codec.getOutputBuffer(outputIndex)
            if (output != null && info.size > 0) {
                output.position(info.offset)
                output.limit(info.offset + info.size)
                val samples = output.order(ByteOrder.nativeOrder()).asShortBuffer()

                while (samples.hasRemaining()) {
                    val sample = samples.get().toDouble()
                    windowEnergy += sample * sample
                    windowFramesSeen++
                    if (windowFramesSeen < channels) continue

                    // A frame is one sample on every channel.
                    windowFramesSeen = 0
                    totalFrames++

                    if (totalFrames % windowFrames == 0L) {
                        val framesInWindow = windowFrames.toDouble()
                        val mean = windowEnergy / (framesInWindow * channels)
                        windowEnergy = 0.0

                        val dbfs = if (mean <= 0.0) {
                            -120.0
                        } else {
                            20.0 * log10(sqrt(mean) / 32_768.0)
                        }

                        if (dbfs > SILENCE_DBFS) {
                            val speechAtMs = totalFrames * 1_000L / sampleRate
                            val startMs = (speechAtMs - WINDOW_MS - LEAD_IN_KEEP_MS)
                                .coerceAtLeast(0L)
                            return startMs.coerceAtMost(MAX_SKIP_MS)
                        }
                        if (totalFrames > scanLimitFrames) return 0L
                    }
                }
            }

            codec.releaseOutputBuffer(outputIndex, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
        }

        return 0L
    }
}
