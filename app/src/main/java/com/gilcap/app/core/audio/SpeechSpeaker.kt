package com.gilcap.app.core.audio

/** F01, F05 말하기 */
interface SpeechSpeaker {

    suspend fun speak(text: String)

    fun stop()
}