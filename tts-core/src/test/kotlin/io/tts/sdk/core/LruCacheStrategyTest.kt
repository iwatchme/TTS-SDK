package io.tts.sdk.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LruCacheStrategyTest {

    @TempDir
    lateinit var tempDir: File

    private val strategy = LruCacheStrategy()

    private fun createFile(name: String, size: Int, lastModified: Long): File {
        val file = File(tempDir, name)
        file.writeBytes(ByteArray(size))
        file.setLastModified(lastModified)
        return file
    }

    @Test
    fun `returns empty when under limit`() {
        val files = listOf(
            createFile("a.mp3", 100, 1000),
            createFile("b.mp3", 100, 2000),
        )
        val result = strategy.selectFilesToEvict(files, 200, 500)
        assertThat(result).isEmpty()
    }

    @Test
    fun `returns empty when exactly at limit`() {
        val files = listOf(createFile("a.mp3", 500, 1000))
        val result = strategy.selectFilesToEvict(files, 500, 500)
        assertThat(result).isEmpty()
    }

    @Test
    fun `evicts oldest files first`() {
        val oldest = createFile("old.mp3", 100, 1000)
        val middle = createFile("mid.mp3", 100, 2000)
        val newest = createFile("new.mp3", 100, 3000)
        val files = listOf(oldest, middle, newest)

        // total=300, limit=250 → need to free 50, oldest (100) is enough
        val result = strategy.selectFilesToEvict(files, 300, 250)
        assertThat(result).containsExactly(oldest)
    }

    @Test
    fun `evicts multiple files until under limit`() {
        val f1 = createFile("1.mp3", 100, 1000)
        val f2 = createFile("2.mp3", 100, 2000)
        val f3 = createFile("3.mp3", 100, 3000)
        val files = listOf(f1, f2, f3)

        // total=300, limit=100 → need to free 200, must evict f1+f2
        val result = strategy.selectFilesToEvict(files, 300, 100)
        assertThat(result).containsExactly(f1, f2)
    }

    @Test
    fun `evicts all files if all needed`() {
        val f1 = createFile("1.mp3", 100, 1000)
        val f2 = createFile("2.mp3", 100, 2000)
        val files = listOf(f1, f2)

        // total=200, limit=0 → evict everything
        val result = strategy.selectFilesToEvict(files, 200, 0)
        assertThat(result).containsExactly(f1, f2)
    }

    @Test
    fun `handles empty file list`() {
        val result = strategy.selectFilesToEvict(emptyList(), 0, 100)
        assertThat(result).isEmpty()
    }
}
