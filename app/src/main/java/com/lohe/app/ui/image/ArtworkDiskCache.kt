package com.lohe.app.ui.image

import java.io.File

/** Accessed only on Dispatchers.IO. Atomic writes and bounded LRU storage. */
internal class ArtworkDiskCache(private val directory: File, private val maxBytes: Long = 64L * 1024 * 1024) {
    private var initialized = false

    @Synchronized fun read(key: String): ByteArray? = runCatching {
        initialize()
        val file = File(directory, key)
        if (!file.exists()) return null
        if (file.length() !in 1..MAX_ENTRY_BYTES.toLong()) {
            file.delete()
            return null
        }
        file.readBytes().also { file.setLastModified(System.currentTimeMillis()) }
    }.getOrNull()

    @Synchronized fun write(key: String, bytes: ByteArray) {
        if (bytes.size > MAX_ENTRY_BYTES || bytes.size > maxBytes) return
        runCatching {
            initialize()
            val temp = File.createTempFile("pending-", ".tmp", directory)
            try {
                temp.writeBytes(bytes)
                val target = File(directory, key)
                if (target.exists()) target.delete()
                check(temp.renameTo(target))
                prune()
            } finally { temp.delete() }
        }
    }

    @Synchronized fun remove(key: String) { File(directory, key).delete() }

    private fun initialize() {
        if (initialized) return
        directory.mkdirs()
        directory.listFiles()?.filter { it.name.endsWith(".tmp") || it.length() > MAX_ENTRY_BYTES }?.forEach { it.delete() }
        prune()
        initialized = true
    }

    private fun prune() {
        val files = directory.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() }.orEmpty()
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= maxBytes) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    companion object { const val MAX_ENTRY_BYTES = 8 * 1024 * 1024 }
}
