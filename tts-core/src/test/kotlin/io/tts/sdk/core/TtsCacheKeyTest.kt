package io.tts.sdk.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TtsCacheKeyTest {

    @Test
    fun `same params produce identical filename`() {
        val params1 = TtsSdkParams(
            text = "hello", source = 1, voiceType = "abc",
            volume = "50", rate = 10, pitch = 5,
            effect = "reverb", effectValue = "0.5",
            cacheDir = java.io.File("/tmp"),
        )
        val params2 = params1.copy()
        assertThat(TtsCacheKey.from(params1).toFileName())
            .isEqualTo(TtsCacheKey.from(params2).toFileName())
    }

    @Test
    fun `different params produce different filename`() {
        val base = TtsSdkParams(
            text = "hello", source = 1, voiceType = "abc",
            volume = "50", rate = 10, pitch = 5,
            cacheDir = java.io.File("/tmp"),
        )
        val different = base.copy(text = "world")
        assertThat(TtsCacheKey.from(base).toFileName())
            .isNotEqualTo(TtsCacheKey.from(different).toFileName())
    }

    @Test
    fun `no separator collision between adjacent fields`() {
        // volume="1" voiceType="abc" vs volume="1abc" voiceType=""
        val params1 = TtsSdkParams(
            text = "t", source = 1, voiceType = "abc", volume = "1",
            cacheDir = java.io.File("/tmp"),
        )
        val params2 = TtsSdkParams(
            text = "t", source = 1, voiceType = "", volume = "1abc",
            cacheDir = java.io.File("/tmp"),
        )
        assertThat(TtsCacheKey.from(params1).toFileName())
            .isNotEqualTo(TtsCacheKey.from(params2).toFileName())
    }

    @Test
    fun `different source produces different filename`() {
        val base = TtsSdkParams(
            text = "hello", source = 1, voiceType = "abc",
            cacheDir = java.io.File("/tmp"),
        )
        val differentSource = base.copy(source = 2)
        assertThat(TtsCacheKey.from(base).toFileName())
            .isNotEqualTo(TtsCacheKey.from(differentSource).toFileName())
    }
}
