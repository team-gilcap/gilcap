package com.gilcap.app.core.audio

import kotlinx.coroutines.delay

/** 가짜 STT. enqueue로 넣어둔 문장을 순서대로 돌려준다. 비어 있으면 무응답(null) */
class FakeSpeechListener(private val listenDelayMillis: Long = 500) : SpeechListener {
    private val answers = ArrayDeque<String?>()

    @Synchronized
    fun enqueue(vararg texts: String?) { answers.addAll(texts) }

    override suspend fun listen(timeoutMillis: Long): String? {
        delay(listenDelayMillis)
        return synchronized(this) { answers.removeFirstOrNull() }
    }
}