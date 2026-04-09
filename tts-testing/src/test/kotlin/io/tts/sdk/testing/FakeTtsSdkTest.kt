package io.tts.sdk.testing

import io.tts.sdk.core.TtsSdkParams
import io.tts.sdk.internal.TtsSynthesisException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class FakeTtsSdkTest {

    private fun makeParams(text: String = "hello") = TtsSdkParams(
        text = text, source = 1, voiceType = "default",
        cacheDir = File(System.getProperty("java.io.tmpdir")),
    )

    @Test
    fun `records all synthesize calls`() = runTest {
        val sdk = FakeTtsSdk()
        sdk.initialize()
        sdk.synthesize(makeParams("a"))
        sdk.synthesize(makeParams("b"))

        assertThat(sdk.synthesizeCalls).hasSize(2)
        assertThat(sdk.synthesizeCalls[0].text).isEqualTo("a")
        assertThat(sdk.synthesizeCalls[1].text).isEqualTo("b")
    }

    @Test
    fun `returns fakeFilePath on Success`() = runTest {
        val sdk = FakeTtsSdk(fakeFilePath = "/my/path.mp3")
        sdk.initialize()
        val result = sdk.synthesize(makeParams())
        assertThat(result).isEqualTo("/my/path.mp3")
    }

    @Test
    fun `throws on Error`() = runTest {
        val sdk = FakeTtsSdk(behavior = TtsSdkScenario.Error)
        sdk.initialize()
        val result = runCatching { sdk.synthesize(makeParams()) }
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(TtsSynthesisException::class.java)
    }

    @Test
    fun `HangForever cancels cleanly`() = runTest {
        val sdk = FakeTtsSdk(behavior = TtsSdkScenario.HangForever)
        sdk.initialize()

        val job = launch { sdk.synthesize(makeParams()) }
        delay(50)
        job.cancelAndJoin()
        // Should complete without hanging
    }

    @Test
    fun `streaming emits all configured chunks`() = runTest {
        val chunks = listOf(byteArrayOf(1, 2), byteArrayOf(3, 4), byteArrayOf(5))
        val sdk = FakeTtsSdk(streamChunks = chunks)
        sdk.initialize()

        val emitted = sdk.synthesizeStreaming(makeParams()).toList()
        assertThat(emitted).hasSize(3)
        assertThat(emitted[0]).isEqualTo(byteArrayOf(1, 2))
        assertThat(emitted[1]).isEqualTo(byteArrayOf(3, 4))
        assertThat(emitted[2]).isEqualTo(byteArrayOf(5))
    }

    @Test
    fun `initialize and release counts are tracked`() = runTest {
        val sdk = FakeTtsSdk()
        assertThat(sdk.initializeCallCount.get()).isEqualTo(0)
        sdk.initialize()
        assertThat(sdk.initializeCallCount.get()).isEqualTo(1)
        sdk.release()
        assertThat(sdk.releaseCallCount.get()).isEqualTo(1)
    }

    @Test
    fun `handles returns true for configured sources`() {
        val sdk = FakeTtsSdk(handledSources = setOf(1, 3))
        assertThat(sdk.handles(1)).isTrue()
        assertThat(sdk.handles(2)).isFalse()
        assertThat(sdk.handles(3)).isTrue()
    }
}
