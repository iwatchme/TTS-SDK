package io.tts.sdk.player

import io.tts.sdk.core.ioDispatcher
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface AudioSink {
    fun write(data: ByteArray)
    fun play()
    fun pause()
    fun stop()
    fun release()
}

class TtsAudioPlayer(
    private val sampleRate: Int = 24000,
    private val scope: CoroutineScope,
    private val audioSinkFactory: () -> AudioSink,
    private val callbackDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onPlayStart: () -> Unit = {},
    private val onPlayOver: () -> Unit = {},
) {
    private val _stopRequested = atomic(false)
    private var queue = Channel<ByteArray>(capacity = 16)
    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val state: StateFlow<PlayerState> = _state

    private var audioSink: AudioSink? = null
    private var playJob: Job? = null

    val isPlaying: Boolean get() = _state.value is PlayerState.Playing

    fun enqueue(pcmData: ByteArray) {
        if (_state.value is PlayerState.Released) return
        val q = queue
        scope.launch { q.send(pcmData) }
    }

    fun markStreamEnd() {
        queue.close()
    }

    fun play() {
        if (_state.value is PlayerState.Released) return
        _stopRequested.value = false
        queue = Channel(capacity = 16)

        val sink = audioSinkFactory()
        audioSink = sink
        sink.play()
        _state.value = PlayerState.Playing
        onPlayStart()

        val currentQueue = queue
        playJob = scope.launch(ioDispatcher) {
            try {
                for (chunk in currentQueue) {
                    if (_stopRequested.value) break
                    sink.write(chunk)
                }
                if (!_stopRequested.value) {
                    withContext(callbackDispatcher) {
                        _state.value = PlayerState.Idle
                        onPlayOver()
                    }
                }
            } catch (_: Exception) {
                // Channel closed or cancelled
            }
        }
    }

    fun pause() {
        if (_state.value != PlayerState.Playing) return
        audioSink?.pause()
        _state.value = PlayerState.Paused
    }

    fun resume() {
        if (_state.value != PlayerState.Paused) return
        audioSink?.play()
        _state.value = PlayerState.Playing
    }

    fun stop() {
        _stopRequested.value = true
        playJob?.cancel()
        playJob = null
        audioSink?.stop()
        _state.value = PlayerState.Idle
        while (queue.tryReceive().isSuccess) { /* discard */ }
    }

    fun release() {
        stop()
        audioSink?.release()
        audioSink = null
        _state.value = PlayerState.Released
    }
}
