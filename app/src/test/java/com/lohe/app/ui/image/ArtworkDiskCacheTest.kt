package com.lohe.app.ui.image

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ArtworkDiskCacheTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun evictsLeastRecentlyUsedEntries() {
        val directory = temp.newFolder()
        val cache = ArtworkDiskCache(directory, 6)
        cache.write("a", byteArrayOf(1, 2, 3))
        cache.write("b", byteArrayOf(4, 5, 6))
        File(directory, "b").setLastModified(1L)
        cache.read("a")
        cache.write("c", byteArrayOf(7, 8, 9))
        assertNull(cache.read("b"))
        assertNotNull(cache.read("a"))
        assertNotNull(cache.read("c"))
        assertTrue(directory.listFiles()!!.sumOf { it.length() } <= 6)
    }

    @Test fun rejectsOversizedEntryAndCleansAbandonedWrites() {
        val directory = temp.newFolder()
        File(directory, "pending-old.tmp").writeText("partial")
        val cache = ArtworkDiskCache(directory, 3)
        cache.write("oversized", ByteArray(4))
        assertNull(cache.read("oversized"))
        assertFalse(File(directory, "pending-old.tmp").exists())
    }
}
