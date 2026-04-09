package io.tts.sdk.core

import java.io.File

class LargestFirstCacheStrategy : TtsCacheStrategy {
    override fun selectFilesToEvict(
        files: List<File>,
        totalSize: Long,
        maxSizeBytes: Long,
    ): List<File> {
        if (totalSize <= maxSizeBytes) return emptyList()
        val toFree = totalSize - maxSizeBytes
        var freed = 0L
        return files.sortedByDescending { it.length() }.takeWhile { file ->
            if (freed >= toFree) return@takeWhile false
            freed += file.length()
            true
        }
    }
}
