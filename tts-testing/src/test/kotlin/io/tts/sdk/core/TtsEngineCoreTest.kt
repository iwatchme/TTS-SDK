package io.tts.sdk.core

import io.tts.sdk.internal.TtsSynthesisException
import io.tts.sdk.testing.FakeTtsSdk
import io.tts.sdk.testing.TtsSdkScenario
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class TtsEngineCoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @TempDir
    lateinit var tempDir: File

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    private fun makeParams(text: String = "hello", source: Int = 1) = TtsSdkParams(
        text = text, source = source, voiceType = "default",
        cacheDir = File(System.getProperty("java.io.tmpdir")),
    )

    @Test
    fun `synthesizeOne returns path on success`(): Unit = runBlocking {
        val sdk = FakeTtsSdk(fakeFilePath = "/output/test.mp3")
        val pool = TtsSdkPool(factory = { sdk }, maxSize = 1)
        val core = TtsEngineCore(mapOf(1 to pool), scope)

        val result = core.synthesizeOne(makeParams())
        assertThat(result).isEqualTo("/output/test.mp3")
    }

    @Test
    fun `synthesizeOne throws on error`(): Unit = runBlocking {
        val sdk = FakeTtsSdk(behavior = TtsSdkScenario.Error)
        val pool = TtsSdkPool(factory = { sdk }, maxSize = 1)
        val core = TtsEngineCore(mapOf(1 to pool), scope)

        val result = runCatching { core.synthesizeOne(makeParams()) }
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(TtsSynthesisException::class.java)
    }

    @Test
    fun `concurrency is bounded by pool maxSize`(): Unit = runBlocking {
        val concurrent = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)

        val sdk = FakeTtsSdk(delay = 100.milliseconds, fakeFilePath = "/out.mp3")
        val pool = TtsSdkPool(
            factory = {
                object : ITtsSdk by sdk {
                    override suspend fun synthesize(params: TtsSdkParams): String {
                        val c = concurrent.incrementAndGet()
                        maxConcurrent.updateAndGet { max -> maxOf(max, c) }
                        try {
                            return sdk.synthesize(params)
                        } finally {
                            concurrent.decrementAndGet()
                        }
                    }
                }
            },
            maxSize = 2,
        )
        val core = TtsEngineCore(mapOf(1 to pool), scope)

        val params = (1..5).map { makeParams("text$it") }
        core.generateBatch(params, null)

        assertThat(maxConcurrent.get()).isLessThanOrEqualTo(12)
    }

    @Test
    fun `generateBatch preserves order`(): Unit = runBlocking {
        val sdk = FakeTtsSdk(delay = 10.milliseconds)
        val pool = TtsSdkPool(factory = { sdk }, maxSize = 3)
        val core = TtsEngineCore(mapOf(1 to pool), scope)

        val params = (1..5).map { makeParams("text$it") }
        val results = core.generateBatch(params, null)

        assertThat(results).hasSize(5)
        results.forEach { assertThat(it.isSuccess).isTrue() }
    }

    @Test
    fun `generateBatch reports progress`(): Unit = runBlocking {
        val sdk = FakeTtsSdk()
        val pool = TtsSdkPool(factory = { sdk }, maxSize = 2)
        val core = TtsEngineCore(mapOf(1 to pool), scope)

        val progressUpdates = CopyOnWriteArrayList<Pair<Int, Int>>()
        val params = (1..3).map { makeParams("text$it") }
        core.generateBatch(params) { completed, total ->
            progressUpdates.add(completed to total)
        }

        assertThat(progressUpdates).hasSize(3)
        assertThat(progressUpdates.map { it.second }).containsOnly(3)
        assertThat(progressUpdates.map { it.first }).containsExactlyInAnyOrder(1, 2, 3)
    }

    @Test
    fun `error in one item does not cancel others`(): Unit = runBlocking {
        val pool = TtsSdkPool(
            factory = {
                object : ITtsSdk {
                    override fun handles(source: Int) = true
                    override suspend fun initialize() {}
                    override fun release() {}
                    override suspend fun synthesize(params: TtsSdkParams): String {
                        if (params.text == "fail") throw TtsSynthesisException(-1)
                        return "/ok.mp3"
                    }
                    override fun synthesizeStreaming(params: TtsSdkParams) = emptyFlow<ByteArray>()
                }
            },
            maxSize = 3,
        )
        val core = TtsEngineCore(mapOf(1 to pool), scope)

        val params = listOf(makeParams("ok1"), makeParams("fail"), makeParams("ok2"))
        val results = core.generateBatch(params, null)

        assertThat(results[0].isSuccess).isTrue()
        assertThat(results[1].isFailure).isTrue()
        assertThat(results[2].isSuccess).isTrue()
    }

    @Test
    fun `cancelAll cancels children but scope remains reusable`(): Unit = runBlocking {
        val sdk = FakeTtsSdk(behavior = TtsSdkScenario.HangForever)
        val pool = TtsSdkPool(factory = { sdk }, maxSize = 2)
        val core = TtsEngineCore(mapOf(1 to pool), scope)

        val job = scope.launch {
            core.generateBatch(listOf(makeParams()), null)
        }
        delay(100)
        core.cancelAll()
        job.join()

        assertThat(scope.isActive).isTrue()

        val sdk2 = FakeTtsSdk(fakeFilePath = "/reuse.mp3")
        val pool2 = TtsSdkPool(factory = { sdk2 }, maxSize = 1)
        val core2 = TtsEngineCore(mapOf(1 to pool2), scope)
        val result = core2.synthesizeOne(makeParams())
        assertThat(result).isEqualTo("/reuse.mp3")
    }

    @Test
    fun `no SDK for source throws`(): Unit = runBlocking {
        val core = TtsEngineCore(emptyMap(), scope)
        val result = runCatching { core.synthesizeOne(makeParams(source = 99)) }
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(TtsSynthesisException::class.java)
    }

    @Test
    fun `cache hit skips SDK call`(): Unit = runBlocking {
        val cache = TtsCache(tempDir)
        val params = makeParams("cached-text")
        val key = TtsCacheKey.from(params)

        // Pre-populate cache
        val temp = cache.createTemp(key)
        temp.writeText("cached audio data")
        cache.commit(key, temp)

        val sdk = FakeTtsSdk(fakeFilePath = "/should-not-be-called.mp3")
        val pool = TtsSdkPool(factory = { sdk }, maxSize = 1)
        val core = TtsEngineCore(mapOf(1 to pool), scope, cache)

        val result = core.synthesizeOne(params)

        // Should return cached path, not SDK path
        assertThat(result).endsWith(key.toFileName())
        assertThat(sdk.synthesizeCalls).isEmpty()
    }

    @Test
    fun `cache miss calls SDK then caches result`(): Unit = runBlocking {
        val cache = TtsCache(tempDir)

        // Create a real file that the SDK "produces"
        val outputFile = File(tempDir, "sdk_output.mp3")
        outputFile.writeText("synthesized audio")

        val sdk = FakeTtsSdk(fakeFilePath = outputFile.absolutePath)
        val pool = TtsSdkPool(factory = { sdk }, maxSize = 1)
        val core = TtsEngineCore(mapOf(1 to pool), scope, cache)

        val params = makeParams("new-text")
        val key = TtsCacheKey.from(params)

        // First call — cache miss, should call SDK
        val result = core.synthesizeOne(params)
        assertThat(sdk.synthesizeCalls).hasSize(1)
        assertThat(result).isEqualTo(outputFile.absolutePath)

        // Cache should now have the file
        assertThat(cache.get(key)).isNotNull()
    }

    @Test
    fun `second call with same params hits cache`(): Unit = runBlocking {
        val cache = TtsCache(tempDir)

        val outputFile = File(tempDir, "sdk_output.mp3")
        outputFile.writeText("synthesized audio")

        val sdk = FakeTtsSdk(fakeFilePath = outputFile.absolutePath)
        val pool = TtsSdkPool(factory = { sdk }, maxSize = 1)
        val core = TtsEngineCore(mapOf(1 to pool), scope, cache)

        val params = makeParams("repeat-text")

        // First call — SDK
        core.synthesizeOne(params)
        assertThat(sdk.synthesizeCalls).hasSize(1)

        // Second call — should hit cache, SDK not called again
        val result2 = core.synthesizeOne(params)
        assertThat(sdk.synthesizeCalls).hasSize(1) // still 1, not 2
        assertThat(File(result2).readText()).isEqualTo("synthesized audio")
    }
}
