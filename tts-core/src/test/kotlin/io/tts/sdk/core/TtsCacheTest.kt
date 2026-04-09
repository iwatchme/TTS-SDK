package io.tts.sdk.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class TtsCacheTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var cache: TtsCache

    private val testParams = TtsSdkParams(
        text = "hello", source = 1, voiceType = "abc",
        volume = "50", rate = 10, pitch = 5,
        cacheDir = File("/tmp"),
    )
    private val testKey = TtsCacheKey.from(testParams)

    @BeforeEach
    fun setup() {
        cache = TtsCache(tempDir)
    }

    @Test
    fun `get returns null for missing file`() {
        assertThat(cache.get(testKey)).isNull()
    }

    @Test
    fun `get returns null for zero-length file`() {
        File(tempDir, testKey.toFileName()).createNewFile()
        assertThat(cache.get(testKey)).isNull()
    }

    @Test
    fun `get returns valid file after commit`() {
        val temp = cache.createTemp(testKey)
        temp.writeText("audio data")
        val committed = cache.commit(testKey, temp)
        assertThat(cache.get(testKey)).isNotNull
        assertThat(cache.get(testKey)!!.readText()).isEqualTo("audio data")
        assertThat(committed.name).isEqualTo(testKey.toFileName())
    }

    @Test
    fun `createTemp and commit is atomic - temp file renamed`() {
        val temp = cache.createTemp(testKey)
        temp.writeText("data")
        val tempPath = temp.absolutePath
        cache.commit(testKey, temp)
        // temp file should no longer exist at original path (renamed)
        assertThat(File(tempPath).exists()).isFalse()
    }

    @Test
    fun `delete removes cached file`() {
        val temp = cache.createTemp(testKey)
        temp.writeText("data")
        cache.commit(testKey, temp)
        assertThat(cache.get(testKey)).isNotNull
        cache.delete(testKey)
        assertThat(cache.get(testKey)).isNull()
    }

    @Test
    fun `clear removes all files`() {
        val key1 = testKey
        val key2 = TtsCacheKey.from(testParams.copy(text = "world"))

        val t1 = cache.createTemp(key1); t1.writeText("a"); cache.commit(key1, t1)
        val t2 = cache.createTemp(key2); t2.writeText("b"); cache.commit(key2, t2)

        assertThat(tempDir.listFiles()!!.size).isEqualTo(2)
        cache.clear()
        assertThat(tempDir.listFiles()!!.size).isEqualTo(0)
    }

    @Test
    fun `trimIfNeeded evicts oldest files when over limit`() {
        val smallCache = TtsCache(tempDir, maxSizeBytes = 10)

        // Write 3 files, each 5 bytes
        val keys = (1..3).map { i ->
            val params = testParams.copy(text = "text$i")
            val key = TtsCacheKey.from(params)
            val temp = smallCache.createTemp(key)
            temp.writeBytes(ByteArray(5) { it.toByte() })
            // Set different last-modified times so eviction order is deterministic
            smallCache.commit(key, temp).also {
                it.setLastModified(System.currentTimeMillis() - (3 - i) * 1000L)
            }
            key
        }

        // Total = 15 bytes, limit = 10 → should evict oldest until under 10
        smallCache.trimIfNeeded()

        // The oldest file(s) should be evicted
        val remaining = tempDir.listFiles()!!.filter { !it.name.endsWith(".tmp") }
        val totalSize = remaining.sumOf { it.length() }
        assertThat(totalSize).isLessThanOrEqualTo(10)
    }

    @Test
    fun `custom strategy is called on commit`() {
        val evicted = mutableListOf<File>()
        val customStrategy = TtsCacheStrategy { files, _, _ ->
            // Evict all files as a test
            files.also { evicted.addAll(it) }
        }
        val customCache = TtsCache(tempDir, maxSizeBytes = 10, strategy = customStrategy)

        val temp = customCache.createTemp(testKey)
        temp.writeBytes(ByteArray(5))
        customCache.commit(testKey, temp)

        // Strategy should have been invoked
        assertThat(evicted).isNotEmpty()
    }

    @Test
    fun `custom strategy controls which files are evicted`() {
        // Strategy that never evicts anything
        val noEvictCache = TtsCache(tempDir, maxSizeBytes = 1, strategy = { _, _, _ -> emptyList() })

        val keys = (1..3).map { i ->
            val params = testParams.copy(text = "text$i")
            val key = TtsCacheKey.from(params)
            val temp = noEvictCache.createTemp(key)
            temp.writeBytes(ByteArray(10))
            noEvictCache.commit(key, temp)
            key
        }

        // Even though total (30) > limit (1), no files should be evicted
        val remaining = tempDir.listFiles()!!.filter { !it.name.endsWith(".tmp") }
        assertThat(remaining).hasSize(3)
    }
}
