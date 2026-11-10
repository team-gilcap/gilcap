package com.gilcap.app.core.audio

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 가짜 TTS. 소리 대신 로그를 찍고, 말하는 시간만큼 기다린다. 마지막 문장은 자막용으로 보관 */
class FakeSpeechSpeaker(private val millisPerChar: Long = 40) : SpeechSpeaker {
    private val _lastSpoken = MutableStateFlow<String?>(null)
    val lastSpoken: StateFlow<String?> = _lastSpoken.asStateFlow()

    override suspend fun speak(text: String) {
        println("GILCAP [TTS] $text")
        _lastSpoken.value = text
        delay((text.length * millisPerChar).coerceAtMost(3_000))
    }

    override fun stop() {}
}