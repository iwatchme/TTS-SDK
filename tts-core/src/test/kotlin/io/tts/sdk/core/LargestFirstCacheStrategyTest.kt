package io.tts.sdk.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LargestFirstCacheStrategyTest {

    @TempDir
    lateinit var tempDir: File

    private val strategy = LargestFirstCacheStrategy()

    private fun createFile(name: String, size: Int): File {
        val file = File(tempDir, name)
        file.writeBytes(ByteArray(size))
        return file
    }

    @Test
    fun `returns empty when under limit`() {
        val files = listOf(createFile("a.mp3", 50), createFile("b.mp3", 50))
        val result = strategy.selectFilesToEvict(files, 100, 200)
        assertThat(result).isEmpty()
    }

    @Test
    fun `returns empty when exactly at limit`() {
        val files = listOf(createFile("a.mp3", 100))
        val result = strategy.selectFilesToEvict(files, 100, 100)
        assertThat(result).isEmpty()
    }

    @Test
    fun `evicts largest file first`() {
        val small = createFile("small.mp3", 10)
        val large = createFile("large.mp3", 100)
        val medium = createFile("medium.mp3", 50)
        val files = listOf(small, large, medium)

        // total=160, limit=100 → need to free 60, largest (100) is enough
        val result = strategy.selectFilesToEvict(files, 160, 100)
        assertThat(result).containsExactly(large)
    }

    @Test
    fun `evicts multiple files largest first until under limit`() {
        val f1 = createFile("1.mp3", 10)
        val f2 = createFile("2.mp3", 80)
        val f3 = createFile("3.mp3", 50)
        val f4 = createFile("4.mp3", 30)
        val files = listOf(f1, f2, f3, f4)

        // total=170, limit=50 → need to free 120, evict f2(80)+f3(50)
        val result = strategy.selectFilesToEvict(files, 170, 50)
        assertThat(result).containsExactly(f2, f3)
    }

    @Test
    fun `evicts all files if all needed`() {
        val f1 = createFile("1.mp3", 100)
        val f2 = createFile("2.mp3", 50)
        val files = listOf(f1, f2)

        val result = strategy.selectFilesToEvict(files, 150, 0)
        assertThat(result).containsExactly(f1, f2)
    }

    @Test
    fun `handles empty file list`() {
        val result = strategy.selectFilesToEvict(emptyList(), 0, 100)
        assertThat(result).isEmpty()
    }
}
