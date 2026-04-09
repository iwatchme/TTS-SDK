package io.tts.sdk

sealed class TtsEvent {
    class AudioChunk(val pcm: ByteArray) : TtsEvent() {
        override fun equals(other: Any?) =
            other is AudioChunk && pcm.contentEquals(other.pcm)
        override fun hashCode() = pcm.contentHashCode()
    }

    data class Done(val filePath: String) : TtsEvent()
    data class Progress(val completed: Int, val total: Int) : TtsEvent()
    data class Error(val retCode: Int, val message: String?) : TtsEvent()
    object Cancelled : TtsEvent()
}
