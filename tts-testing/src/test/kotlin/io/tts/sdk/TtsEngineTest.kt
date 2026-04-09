package io.tts.sdk

import io.tts.sdk.core.TtsCacheStrategy
import io.tts.sdk.testing.FakeCacheStrategy
import io.tts.sdk.testing.FakeTtsSdk
import io.tts.sdk.testing.TtsSdkScenario
import kotlinx.coroutines.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class TtsEngineTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var engine: TtsEngine

    @AfterEach
    fun tearDown() {
        if (::engine.isInitialized) engine.close()
    }

    private fun buildEngine(sdk: FakeTtsSdk, source: Int = 1): TtsEngine {
        engine = TtsEngine.Builder()
            .sdk(source, sdk)
            .cacheDir(tempDir)
            .build()
        return engine
    }

    @Test
    fun `generate returns items with filePaths filled`(): Unit = runBlocking {
        val sdk = FakeTtsSdk(fakeFilePath = "/out/test.mp3")
        val engine = buildEngine(sdk)
        val voice = TtsVoiceParams(voiceType = "default")
        val items = listOf(TtsItem("hello", 1), TtsItem("world", 1))

        val result = engine.generate(items, voice)

        assertThat(result).hasSize(2)
        result.forEach { assertThat(it.filePath).isEqualTo("/out/test.mp3") }
    }

    @Test
    fun `generate reports progress`(): Unit = runBlocking {
        val sdk = FakeTtsSdk()
        val engine = buildEngine(sdk)
        val voice = TtsVoiceParams(voiceType = "default")
        val items = (1..3).map { TtsItem("text$it", 1) }

        val progressUpdates = CopyOnWriteArrayList<Pair<Int, Int>>()
        engine.generate(items, voice) { completed, total ->
            progressUpdates.add(completed to total)
        }

        assertThat(progressUpdates).hasSize(3)
        assertThat(progressUpdates.map { it.second }).containsOnly(3)
    }

    @Test
    fun `generate deduplicates by text and source`(): Unit = runBlocking {
        val sdk = FakeTtsSdk(fakeFilePath = "/out.mp3")
        val engine = buildEngine(sdk)
        val voice = TtsVoiceParams(voiceType = "default")
        val items = listOf(TtsItem("same", 1), TtsItem("same", 1), TtsItem("different", 1))

        engine.generate(items, voice)

        assertThat(sdk.synthesizeCalls).hasSize(2)
        assertThat(items[0].filePath).isEqualTo("/out.mp3")
        assertThat(items[1].filePath).isEqualTo("/out.mp3")
        assertThat(items[2].filePath).isEqualTo("/out.mp3")
    }

    @Test
    fun `generate with empty list returns immediately`(): Unit = runBlocking {
        val sdk = FakeTtsSdk()
        val engine = buildEngine(sdk)
        val result = engine.generate(emptyList(), TtsVoiceParams(voiceType = "default"))
        assertThat(result).isEmpty()
    }

    @Test
    fun `close releases SDK`(): Unit = runBlocking {
        val sdk = FakeTtsSdk()
        val engine = buildEngine(sdk)
        engine.generate(listOf(TtsItem("hello", 1)), TtsVoiceParams(voiceType = "default"))
        engine.close()
        assertThat(sdk.releaseCallCount.get()).isEqualTo(1)
    }

    @Test
    fun `preview emits AudioChunk then Done`(): Unit = runBlocking {
        val chunks = listOf(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6))
        val sdk = FakeTtsSdk(streamChunks = chunks, fakeFilePath = "/preview.mp3")
        val engine = buildEngine(sdk)
        val voice = TtsVoiceParams(voiceType = "default")

        val events = mutableListOf<TtsEvent>()
        engine.preview("hello", 1, voice).collect { events.add(it) }

        val audioChunks = events.filterIsInstance<TtsEvent.AudioChunk>()
        assertThat(audioChunks).hasSize(2)
        assertThat(audioChunks[0]).isEqualTo(TtsEvent.AudioChunk(byteArrayOf(1, 2, 3)))
        assertThat(events.last()).isInstanceOf(TtsEvent.Done::class.java)
    }

    @Test
    fun `preview emits Error on failure`(): Unit = runBlocking {
        val sdk = FakeTtsSdk(behavior = TtsSdkScenario.Error)
        val engine = buildEngine(sdk)
        val voice = TtsVoiceParams(voiceType = "default")

        val events = mutableListOf<TtsEvent>()
        engine.preview("hello", 1, voice).collect { events.add(it) }

        assertThat(events.last()).isInstanceOf(TtsEvent.Error::class.java)
    }

    @Test
    fun `builder accepts custom cache strategy`() {
        val fakeStrategy = FakeCacheStrategy()
        val sdk = FakeTtsSdk()
        engine = TtsEngine.Builder()
            .sdk(1, sdk)
            .cacheDir(tempDir)
            .cacheStrategy(fakeStrategy)
            .build()
        // Engine should build successfully with custom strategy
        assertThat(engine).isNotNull
    }
}
