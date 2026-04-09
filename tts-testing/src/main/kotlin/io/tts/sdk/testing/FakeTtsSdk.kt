package io.tts.sdk.testing

import io.tts.sdk.core.ITtsSdk
import io.tts.sdk.core.TtsSdkParams
import io.tts.sdk.internal.TtsSynthesisException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class FakeTtsSdk(
    private val behavior: TtsSdkScenario = TtsSdkScenario.Success,
    private val handledSources: Set<Int> = setOf(1, 2, 3),
    private val delay: Duration = Duration.ZERO,
    private val fakeFilePath: String = "/fake/tts/output.mp3",
    private val streamChunks: List<ByteArray> = emptyList(),
) : ITtsSdk {

    val initializeCallCount = AtomicInteger(0)
    val releaseCallCount = AtomicInteger(0)
    val synthesizeCalls = CopyOnWriteArrayList<TtsSdkParams>()

    override fun handles(source: Int) = source in handledSources

    override suspend fun initialize() {
        initializeCallCount.incrementAndGet()
    }

    override fun release() {
        releaseCallCount.incrementAndGet()
    }

    override suspend fun synthesize(params: TtsSdkParams): String {
        synthesizeCalls.add(params)
        if (delay > Duration.ZERO) kotlinx.coroutines.delay(delay.inWholeMilliseconds)
        return when (behavior) {
            TtsSdkScenario.Success -> fakeFilePath
            TtsSdkScenario.Error -> throw TtsSynthesisException(retCode = -1)
            TtsSdkScenario.NetworkError -> throw TtsSynthesisException(retCode = -2, "Network error")
            TtsSdkScenario.HangForever -> suspendCancellableCoroutine { /* never resumes */ }
        }
    }

    override fun synthesizeStreaming(params: TtsSdkParams): Flow<ByteArray> = flow {
        if (delay > Duration.ZERO) kotlinx.coroutines.delay(delay.inWholeMilliseconds)
        streamChunks.forEach { emit(it) }
    }
}
