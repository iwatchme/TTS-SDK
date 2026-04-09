package io.tts.sdk

data class TtsItem(
    val text: String,
    val source: Int,
    var filePath: String? = null,
)
