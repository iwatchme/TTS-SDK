package io.tts.sdk.player

sealed class PlayerState {
    object Idle : PlayerState()
    object Playing : PlayerState()
    object Paused : PlayerState()
    object Released : PlayerState()
}
