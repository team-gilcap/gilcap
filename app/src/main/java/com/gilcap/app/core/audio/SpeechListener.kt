package com.gilcap.app.core.audio

/** F01 듣기 */
interface SpeechListener {

    suspend fun listen(timeoutMillis: Long = 10_000): String?
}