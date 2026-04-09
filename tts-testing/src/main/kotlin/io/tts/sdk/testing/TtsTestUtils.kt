package io.tts.sdk.testing

import io.tts.sdk.TtsItem
import io.tts.sdk.TtsVoiceParams
import io.tts.sdk.core.TtsSdkParams
import java.io.File

object TtsTestUtils {
    fun makeParams(
        text: String = "hello",
        source: Int = 1,
        voiceType: String = "default",
        cacheDir: File = File(System.getProperty("java.io.tmpdir"), "tts-test-cache"),
    ) = TtsSdkParams(
        text = text,
        source = source,
        voiceType = voiceType,
        cacheDir = cacheDir,
    )

    fun makeItem(text: String = "hello", source: Int = 1) = TtsItem(text = text, source = source)

    fun makeVoice(voiceType: String = "default") = TtsVoiceParams(voiceType = voiceType)
}
