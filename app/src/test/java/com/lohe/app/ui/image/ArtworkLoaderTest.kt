package com.lohe.app.ui.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkLoaderTest {

    // --- Sampling ------------------------------------------------------------

    @Test
    fun `an image already within budget is not sampled`() {
        assertEquals(1, ArtworkLoader.sampleSizeFor(512, 512, 512))
        assertEquals(1, ArtworkLoader.sampleSizeFor(100, 100, 512))
    }

    @Test
    fun `sampling halves until the longest edge fits`() {
        assertEquals(2, ArtworkLoader.sampleSizeFor(1024, 1024, 512))
        assertEquals(4, ArtworkLoader.sampleSizeFor(2048, 2048, 512))
        assertEquals(8, ArtworkLoader.sampleSizeFor(4096, 2048, 512))
    }

    @Test
    fun `the longest edge governs, not the average`() {
        // A 200x2000 banner still needs downsampling; a wide image is not
        // exempt just because its short edge is small.
        assertEquals(2, ArtworkLoader.sampleSizeFor(200, 2_000, 512))
        assertEquals(1, ArtworkLoader.sampleSizeFor(200, 300, 512))
    }

    @Test
    fun `oversized artwork is sampled aggressively`() {
        assertEquals(1_024, ArtworkLoader.sampleSizeFor(1_000_000, 1_000_000, 512))
    }

    @Test
    fun `sampling stops before it would undershoot the budget`() {
        // Halving 1000 would give 500, which is under the 512 budget — so the
        // image is kept whole. Overshooting the budget slightly is preferable to
        // decoding something smaller than asked for.
        assertEquals(1, ArtworkLoader.sampleSizeFor(1_000, 1_000, 512))
        // One step further and halving no longer undershoots, so it happens.
        assertEquals(2, ArtworkLoader.sampleSizeFor(1_500, 1_500, 512))
    }

    @Test
    fun `unknown dimensions fall back to no sampling`() {
        // inJustDecodeBounds leaves these at -1 when the header could not be read.
        assertEquals(1, ArtworkLoader.sampleSizeFor(-1, -1, 512))
        assertEquals(1, ArtworkLoader.sampleSizeFor(0, 0, 512))
    }

    @Test
    fun `a non-positive budget is treated as no sampling`() {
        assertEquals(1, ArtworkLoader.sampleSizeFor(4_096, 4_096, 0))
        assertEquals(1, ArtworkLoader.sampleSizeFor(4_096, 4_096, -512))
    }

    @Test
    fun `sampling always leaves the image at or above the budget`() {
        val budget = 512
        listOf(600, 1_023, 1_024, 1_025, 2_000, 10_000).forEach { edge ->
            val sample = ArtworkLoader.sampleSizeFor(edge, edge, budget)
            val decoded = edge / sample
            assertTrue(
                "$edge -> $sample leaves $decoded px",
                decoded >= budget || sample == 1,
            )
        }
    }

    // --- Cache keys ----------------------------------------------------------

    @Test
    fun `cache keys are stable for the same url`() {
        val url = "https://example.com/art.jpg"
        assertEquals(ArtworkLoader.cacheKey(url), ArtworkLoader.cacheKey(url))
    }

    @Test
    fun `cache keys differ per url`() {
        assertNotEquals(
            ArtworkLoader.cacheKey("https://a.com/1.jpg"),
            ArtworkLoader.cacheKey("https://a.com/2.jpg"),
        )
    }

    @Test
    fun `cache keys are valid filenames`() {
        val key = ArtworkLoader.cacheKey("https://example.com/a b/c?d=e&f=/g.jpg")
        assertTrue(key.isNotEmpty())
        assertTrue("must be hex", key.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `cache keys differ only where the url differs`() {
        // A query string is part of the identity: the same path with different
        // params can serve different artwork.
        assertNotEquals(
            ArtworkLoader.cacheKey("https://a.com/i.jpg?size=100"),
            ArtworkLoader.cacheKey("https://a.com/i.jpg?size=600"),
        )
    }

    @Test
    fun `cache keys are short enough for any filesystem`() {
        val key = ArtworkLoader.cacheKey("https://example.com/${"x".repeat(2_000)}.jpg")
        assertEquals(32, key.length)
    }
}
