package io.tts.sdk.testing

import io.tts.sdk.core.TtsCacheStrategy
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class FakeCacheStrategy(
    private val filesToEvict: (List<File>) -> List<File> = { emptyList() },
) : TtsCacheStrategy {

    data class Invocation(val files: List<File>, val totalSize: Long, val maxSizeBytes: Long)

    val invocations = CopyOnWriteArrayList<Invocation>()

    override fun selectFilesToEvict(
        files: List<File>,
        totalSize: Long,
        maxSizeBytes: Long,
    ): List<File> {
        invocations.add(Invocation(files, totalSize, maxSizeBytes))
        return filesToEvict(files)
    }
}
