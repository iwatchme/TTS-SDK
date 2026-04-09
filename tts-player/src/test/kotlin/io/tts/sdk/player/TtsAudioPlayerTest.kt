package io.tts.sdk.player

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class TtsAudioPlayerTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val writtenData = mutableListOf<ByteArray>()

    private val fakeSink = object : AudioSink {
        var playing = false
        var released = false
        override fun write(data: ByteArray) { writtenData.add(data) }
        override fun play() { playing = true }
        override fun pause() { playing = false }
        override fun stop() { playing = false }
        override fun release() { released = true }
    }

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    private fun createPlayer(
        onPlayStart: () -> Unit = {},
        onPlayOver: () -> Unit = {},
    ) = TtsAudioPlayer(
        sampleRate = 24000,
        scope = scope,
        audioSinkFactory = { fakeSink },
        onPlayStart = onPlayStart,
        onPlayOver = onPlayOver,
    )

    @Test
    fun `initial state is Idle`() {
        val player = createPlayer()
        assertThat(player.state.value).isEqualTo(PlayerState.Idle)
    }

    @Test
    fun `play transitions to Playing and fires onPlayStart`() {
        var started = false
        val player = createPlayer(onPlayStart = { started = true })
        player.play()
        assertThat(player.state.value).isEqualTo(PlayerState.Playing)
        assertThat(started).isTrue()
        assertThat(player.isPlaying).isTrue()
    }

    @Test
    fun `pause transitions to Paused`() {
        val player = createPlayer()
        player.play()
        player.pause()
        assertThat(player.state.value).isEqualTo(PlayerState.Paused)
        assertThat(player.isPlaying).isFalse()
    }

    @Test
    fun `resume transitions back to Playing`() {
        val player = createPlayer()
        player.play()
        player.pause()
        player.resume()
        assertThat(player.state.value).isEqualTo(PlayerState.Playing)
    }

    @Test
    fun `stop transitions to Idle`() {
        val player = createPlayer()
        player.play()
        player.stop()
        assertThat(player.state.value).isEqualTo(PlayerState.Idle)
    }

    @Test
    fun `release transitions to Released`() {
        val player = createPlayer()
        player.play()
        player.release()
        assertThat(player.state.value).isEqualTo(PlayerState.Released)
        assertThat(fakeSink.released).isTrue()
    }

    @Test
    fun `markStreamEnd fires onPlayOver after drain`(): Unit = runBlocking {
        var playedOver = false
        val player = createPlayer(onPlayOver = { playedOver = true })

        player.play()
        delay(50)
        player.enqueue(byteArrayOf(1, 2, 3))
        delay(50)
        player.markStreamEnd()

        // Wait for the play job to finish draining
        delay(500)

        assertThat(playedOver).isTrue()
        assertThat(player.state.value).isEqualTo(PlayerState.Idle)
    }

    @Test
    fun `enqueue after release is ignored`() {
        val player = createPlayer()
        player.release()
        player.enqueue(byteArrayOf(1, 2, 3))
        // Should not crash
        assertThat(player.state.value).isEqualTo(PlayerState.Released)
    }

    @Test
    fun `pause has no effect when not playing`() {
        val player = createPlayer()
        player.pause() // should be no-op
        assertThat(player.state.value).isEqualTo(PlayerState.Idle)
    }

    @Test
    fun `resume has no effect when not paused`() {
        val player = createPlayer()
        player.resume() // should be no-op
        assertThat(player.state.value).isEqualTo(PlayerState.Idle)
    }
}
