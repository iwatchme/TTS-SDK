package io.tts.sdk.player

import kotlinx.coroutines.*
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @AfterTest
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
    fun initialStateIsIdle() {
        val player = createPlayer()
        assertEquals(PlayerState.Idle, player.state.value)
    }

    @Test
    fun playTransitionsToPlayingAndFiresOnPlayStart() {
        var started = false
        val player = createPlayer(onPlayStart = { started = true })
        player.play()
        assertEquals(PlayerState.Playing, player.state.value)
        assertTrue(started)
        assertTrue(player.isPlaying)
    }

    @Test
    fun pauseTransitionsToPaused() {
        val player = createPlayer()
        player.play()
        player.pause()
        assertEquals(PlayerState.Paused, player.state.value)
        assertFalse(player.isPlaying)
    }

    @Test
    fun resumeTransitionsBackToPlaying() {
        val player = createPlayer()
        player.play()
        player.pause()
        player.resume()
        assertEquals(PlayerState.Playing, player.state.value)
    }

    @Test
    fun stopTransitionsToIdle() {
        val player = createPlayer()
        player.play()
        player.stop()
        assertEquals(PlayerState.Idle, player.state.value)
    }

    @Test
    fun releaseTransitionsToReleased() {
        val player = createPlayer()
        player.play()
        player.release()
        assertEquals(PlayerState.Released, player.state.value)
        assertTrue(fakeSink.released)
    }

    @Test
    fun markStreamEndFiresOnPlayOverAfterDrain(): Unit = runBlocking {
        var playedOver = false
        val player = createPlayer(onPlayOver = { playedOver = true })

        player.play()
        delay(50)
        player.enqueue(byteArrayOf(1, 2, 3))
        delay(50)
        player.markStreamEnd()

        delay(500)

        assertTrue(playedOver)
        assertEquals(PlayerState.Idle, player.state.value)
    }

    @Test
    fun enqueueAfterReleaseIsIgnored() {
        val player = createPlayer()
        player.release()
        player.enqueue(byteArrayOf(1, 2, 3))
        assertEquals(PlayerState.Released, player.state.value)
    }

    @Test
    fun pauseHasNoEffectWhenNotPlaying() {
        val player = createPlayer()
        player.pause()
        assertEquals(PlayerState.Idle, player.state.value)
    }

    @Test
    fun resumeHasNoEffectWhenNotPaused() {
        val player = createPlayer()
        player.resume()
        assertEquals(PlayerState.Idle, player.state.value)
    }
}
