package io.tts.sdk

import io.tts.sdk.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.Closeable
import java.io.File

class TtsEngine private constructor(
    private val core: TtsEngineCore,
    private val cache: TtsCache,
    private val cacheDir: File,
    private val scope: CoroutineScope,
) : Closeable {

    fun preview(text: String, source: Int, voice: TtsVoiceParams): Flow<TtsEvent> = flow {
        val params = TtsSdkParams(
            text = text,
            source = source,
            voiceType = voice.voiceType,
            engineName = voice.engineName,
            volume = voice.volume,
            rate = voice.rate,
            pitch = voice.pitch,
            effect = voice.effect,
            effectValue = voice.effectValue,
            sampleRate = voice.sampleRate,
            encodeType = voice.encodeType,
            cacheDir = cacheDir,
        )

        try {
            core.streamOne(params).collect { pcm ->
                emit(TtsEvent.AudioChunk(pcm))
            }
            val path = core.synthesizeOne(params)
            emit(TtsEvent.Done(path))
        } catch (e: CancellationException) {
            emit(TtsEvent.Cancelled)
            throw e
        } catch (e: io.tts.sdk.internal.TtsSynthesisException) {
            emit(TtsEvent.Error(e.retCode, e.message))
        } catch (e: Exception) {
            emit(TtsEvent.Error(-1, e.message))
        }
    }

    suspend fun generate(
        items: List<TtsItem>,
        voice: TtsVoiceParams,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null,
    ): List<TtsItem> {
        if (items.isEmpty()) return items

        // Deduplicate by (text + source)
        val uniqueItems = items.distinctBy { it.text to it.source }
        val paramsList = uniqueItems.map { item ->
            TtsSdkParams.from(item, voice, item.source, cacheDir)
        }

        val results = core.generateBatch(paramsList, onProgress)

        // Assign filePaths back to ALL original items (including duplicates)
        uniqueItems.forEachIndexed { index, uniqueItem ->
            val path = results[index].getOrNull()
            items.filter { it.text == uniqueItem.text && it.source == uniqueItem.source }
                .forEach { it.filePath = path }
        }

        return items
    }

    fun cancel() {
        core.cancelAll()
    }

    override fun close() {
        runBlocking {
            core.release()
        }
        scope.cancel()
    }

    class Builder {
        private val sdkRegistrations = mutableMapOf<Int, SdkRegistration>()
        private var cacheDir: File? = null
        private var coroutineScope: CoroutineScope? = null
        private var cacheStrategy: TtsCacheStrategy? = null

        fun sdk(source: Int, factory: suspend () -> ITtsSdk, poolSize: Int = 1): Builder {
            sdkRegistrations[source] = SdkRegistration(factory, poolSize)
            return this
        }

        fun sdk(source: Int, sdk: ITtsSdk): Builder = sdk(source, { sdk }, poolSize = 1)

        fun cacheDir(dir: File): Builder {
            cacheDir = dir
            return this
        }

        fun cacheStrategy(strategy: TtsCacheStrategy): Builder {
            cacheStrategy = strategy
            return this
        }

        fun coroutineScope(scope: CoroutineScope): Builder {
            coroutineScope = scope
            return this
        }

        fun build(): TtsEngine {
            val dir = requireNotNull(cacheDir) { "cacheDir must be set" }
            val scope = coroutineScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val pools = sdkRegistrations.map { (source, reg) ->
                source to TtsSdkPool(factory = reg.factory, maxSize = reg.poolSize)
            }.toMap()
            val strategy = cacheStrategy ?: LruCacheStrategy()
            val cache = TtsCache(dir, strategy = strategy)
            val core = TtsEngineCore(pools, scope, cache)
            return TtsEngine(core, cache, dir, scope)
        }

        private data class SdkRegistration(
            val factory: suspend () -> ITtsSdk,
            val poolSize: Int,
        )
    }
}
