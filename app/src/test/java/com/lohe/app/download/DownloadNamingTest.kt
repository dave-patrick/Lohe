package com.lohe.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Episode titles come from remote feeds and are attacker-influenced, and these
 * strings become real filenames. The contract under test: the result stays
 * inside the episodes directory and remains usable on every filesystem.
 */
class DownloadNamingTest {

    @Test
    fun `a plain title passes through`() {
        assertEquals(
            "Small_Things-a1b2c3d4.mp3",
            DownloadNaming.fileName("Small Things", "a1b2c3d4e5", "audio/mpeg"),
        )
    }

    @Test
    fun `common mime types map to the right extension`() {
        // audio/mpeg is the single most common enclosure type, and its subtype is
        // emphatically not the extension.
        assertEquals("mp3", DownloadNaming.extensionFor("audio/mpeg"))
        assertEquals("m4a", DownloadNaming.extensionFor("audio/mp4"))
        assertEquals("m4a", DownloadNaming.extensionFor("audio/x-m4a"))
        assertEquals("aac", DownloadNaming.extensionFor("audio/aac"))
        assertEquals("ogg", DownloadNaming.extensionFor("audio/ogg"))
        assertEquals("flac", DownloadNaming.extensionFor("audio/flac"))
        assertEquals("wav", DownloadNaming.extensionFor("audio/wav"))
    }

    @Test
    fun `mime type matching is case and whitespace insensitive`() {
        assertEquals("mp3", DownloadNaming.extensionFor("  AUDIO/MPEG "))
    }

    @Test
    fun `path separators cannot escape the episodes directory`() {
        val name = DownloadNaming.fileName("../../etc/passwd", "a1b2c3d4", "audio/mpeg")
        assertFalse("still contains a separator", name.contains('/'))
        assertFalse("still contains a separator", name.contains('\\'))
        assertFalse("hidden file", name.startsWith("."))
    }

    @Test
    fun `windows-reserved characters are stripped`() {
        val name = DownloadNaming.fileName("Episode: \"Best\" <Ever>", "a1b2c3d4", "audio/mpeg")
        assertFalse(name.contains(':'))
        assertFalse(name.contains('"'))
        assertFalse(name.contains('<'))
        assertFalse(name.contains('>'))
    }

    @Test
    fun `an empty or all-punctuation title still yields a usable name`() {
        assertTrue(
            DownloadNaming.fileName("", "a1b2c3d4", "audio/mpeg").startsWith("episode-"),
        )
        assertTrue(
            DownloadNaming.fileName("///", "a1b2c3d4", "audio/mpeg").startsWith("episode-"),
        )
    }

    @Test
    fun `a very long title is truncated`() {
        val name = DownloadNaming.fileName("x".repeat(500), "a1b2c3d4", "audio/mpeg")
        assertTrue("name was ${name.length} chars", name.length <= 60 + 1 + 8 + 1 + 3)
    }

    @Test
    fun `different episodes get different filenames`() {
        assertTrue(
            DownloadNaming.fileName("Same Title", "aaaaaaaa1111", "audio/mpeg") !=
                DownloadNaming.fileName("Same Title", "bbbbbbbb2222", "audio/mpeg"),
        )
    }

    @Test
    fun `a real mime type keeps a usable extension`() {
        assertTrue(DownloadNaming.fileName("E", "a1b2c3d4", "audio/mp4").endsWith(".m4a"))
        assertTrue(DownloadNaming.fileName("E", "a1b2c3d4", "audio/flac").endsWith(".flac"))
    }

    @Test
    fun `an mp4 container is named m4a not mp4`() {
        // Podcast enclosures declared audio/mp4 are MPEG-4 audio; naming them
        // .mp4 would suggest video to players and file managers.
        assertEquals("m4a", DownloadNaming.extensionFor("audio/mp4"))
        assertTrue(DownloadNaming.fileName("E", "a1b2c3d4", "audio/mp4").endsWith(".m4a"))
    }

    @Test
    fun `a hostile mime type cannot inject a weird extension`() {
        listOf(
            "audio/../../etc",
            "audio/abcdefghij",
            "audio/x;rm -rf",
            "not a mime type",
            "",
        ).forEach { mime ->
            assertTrue(
                "mime '$mime' produced an unexpected name",
                DownloadNaming.fileName("E", "a1b2c3d4", mime).endsWith(".mp3"),
            )
        }
    }

    @Test
    fun `a null mime type falls back to mp3`() {
        assertTrue(DownloadNaming.fileName("E", "a1b2c3d4", null).endsWith(".mp3"))
    }

    @Test
    fun `non-latin titles degrade rather than failing`() {
        val name = DownloadNaming.fileName("日本語", "a1b2c3d4", "audio/mpeg")
        assertTrue(name.endsWith("-a1b2c3d4.mp3"))
    }

    @Test
    fun `sanitize collapses disallowed runs`() {
        assertEquals("a_b", DownloadNaming.sanitize("a /// b"))
        assertEquals("a_b", DownloadNaming.sanitize("a      b"))
    }

    @Test
    fun `sanitize keeps dashes but trims underscores at the edges`() {
        // Dashes are legal in filenames, so they survive; underscores are only
        // produced by substitution, so leading/trailing ones are trimmed.
        assertEquals("---a_b---", DownloadNaming.sanitize("---a /// b---"))
        assertEquals("a_b", DownloadNaming.sanitize("___a /// b___"))
    }

    @Test
    fun `sanitize leaves a safe title untouched`() {
        assertEquals("Already_Safe-123", DownloadNaming.sanitize("Already_Safe-123"))
    }

    @Test
    fun `sanitize output is always a single path segment`() {
        val hostile = listOf(
            "..", "../..", "a/b/c", "a\\b\\c", "....//....//",
        )
        hostile.forEach { input ->
            val out = DownloadNaming.sanitize(input)
            assertFalse("'$input' -> '$out'", out.contains('/') || out.contains('\\'))
            assertFalse("'$input' -> '$out'", out == ".." || out == ".")
        }
    }
}
