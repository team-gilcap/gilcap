package com.gilcap.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreRollSilenceTest {

    @Test fun `기본 길이는 가이드 권장 구간 150~200ms 안이다`() {
        assertTrue(PreRollSilence.DEFAULT_MILLIS in 150..200)
    }

    @Test fun `묵음 PCM은 길이만큼의 0으로 채워진다`() {
        val pcm = PreRollSilence.silencePcm(millis = 180, sampleRate = 24_000)

        assertEquals(24_000 * 180 / 1000 * 2, pcm.size)   // 16bit mono
        assertTrue(pcm.all { it == 0.toByte() })
    }
}
