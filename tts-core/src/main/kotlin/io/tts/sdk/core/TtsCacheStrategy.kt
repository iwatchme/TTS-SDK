package io.tts.sdk.core

import java.io.File

fun interface TtsCacheStrategy {
    /**
     * Selects which files should be evicted from the cache.
     *
     * @param files current non-temp cache files
     * @param totalSize combined size of [files] in bytes
     * @param maxSizeBytes configured cache size limit
     * @return files to delete
     */
    fun selectFilesToEvict(
        files: List<File>,
        totalSize: Long,
        maxSizeBytes: Long,
    ): List<File>
}
