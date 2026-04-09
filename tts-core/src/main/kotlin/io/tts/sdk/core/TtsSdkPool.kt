package io.tts.sdk.core

import io.tts.sdk.internal.TtsSynthesisException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class TtsSdkPool(
    private val factory: suspend () -> ITtsSdk,
    val maxSize: Int,
) {
    private val available = Channel<ITtsSdk>(Channel.UNLIMITED)
    private val createdCount = AtomicInteger(0)
    private val checkedOutCount = AtomicInteger(0)
    private val all = CopyOnWriteArrayList<ITtsSdk>()
    @Volatile
    private var closing = false

    suspend fun <T> withSdk(block: suspend (ITtsSdk) -> T): T {
        check(!closing) { "Pool is shutting down, no new checkouts allowed" }
        val sdk = acquire()
        checkedOutCount.incrementAndGet()
        var discarded = false
        return try {
            block(sdk)
        } catch (e: TtsSynthesisException) {
            // SDK may be corrupted — discard this instance
            all.remove(sdk)
            createdCount.decrementAndGet()
            discarded = true
            runCatching { sdk.release() }
            throw e
        } finally {
            if (!discarded && !closing) {
                available.send(sdk)
            }
            checkedOutCount.decrementAndGet()
        }
    }

    private suspend fun acquire(): ITtsSdk {
        available.tryReceive().getOrNull()?.let { return it }

        val prev = createdCount.getAndUpdate { current ->
            if (current < maxSize) current + 1 else current
        }

        if (prev < maxSize) {
            return try {
                val sdk = factory()
                sdk.initialize()
                all.add(sdk)
                sdk
            } catch (e: Exception) {
                createdCount.decrementAndGet()
                throw e
            }
        }

        return available.receive()
    }

    suspend fun release() {
        closing = true
        available.close()
        while (checkedOutCount.get() > 0) {
            delay(10)
        }
        all.forEach { runCatching { it.release() } }
        all.clear()
    }

    internal fun createdCount(): Int = createdCount.get()
}
