package io.tts.sdk.core

import java.io.File

class TtsCache(
    private val cacheDir: File,
    private val maxSizeBytes: Long = 500L * 1024 * 1024,
    private val strategy: TtsCacheStrategy = LruCacheStrategy(),
) {
    fun get(key: TtsCacheKey): File? {
        val file = File(cacheDir, key.toFileName())
        return if (file.exists() && file.length() > 0) file else null
    }

    @Suppress("UNUSED_PARAMETER")
    fun createTemp(key: TtsCacheKey): File {
        cacheDir.mkdirs()
        return File.createTempFile("tts_", ".tmp", cacheDir)
    }

    fun commit(key: TtsCacheKey, tempFile: File): File {
        val target = File(cacheDir, key.toFileName())
        tempFile.renameTo(target)
        trimIfNeeded()
        return target
    }

    fun put(key: TtsCacheKey, sourceFile: File): File? {
        if (!sourceFile.exists() || sourceFile.length() == 0L) return null
        val temp = createTemp(key)
        sourceFile.copyTo(temp, overwrite = true)
        return commit(key, temp)
    }

    internal fun trimIfNeeded() {
        val files = cacheDir.listFiles()?.filter { !it.name.endsWith(".tmp") } ?: return
        val totalSize = files.sumOf { it.length() }
        val toEvict = strategy.selectFilesToEvict(files, totalSize, maxSizeBytes)
        toEvict.forEach { it.delete() }
    }

    fun delete(key: TtsCacheKey) {
        File(cacheDir, key.toFileName()).delete()
    }

    fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }
}
