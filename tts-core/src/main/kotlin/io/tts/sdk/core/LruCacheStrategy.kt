package io.tts.sdk.core

import java.io.File

class LruCacheStrategy : TtsCacheStrategy {
    override fun selectFilesToEvict(
        files: List<File>,
        totalSize: Long,
        maxSizeBytes: Long,
    ): List<File> {
        if (totalSize <= maxSizeBytes) return emptyList()
        val toFree = totalSize - maxSizeBytes
        var freed = 0L
        return files.sortedBy { it.lastModified() }.takeWhile { file ->
            if (freed >= toFree) return@takeWhile false
            freed += file.length()
            true
        }
    }
}
