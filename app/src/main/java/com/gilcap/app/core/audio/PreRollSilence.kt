package com.gilcap.app.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 음성 안내 앞에 넣는 선행 묵음 (Pleos "TTS / 음성 안내 선행 묵음 적용 가이드").
 *
 * 묵음 없이 바로 말하면 출력 경로가 깨어나는 동안 첫 음절이 잘릴 수 있다.
 * 가이드 권장값은 150~200ms 이고, 발화를 새로 시작할 때마다 적용한다.
 */
fun interface PreRollSilence {
    /** 묵음을 끝까지 내보낸 뒤 돌아온다. 실패해도 예외를 던지지 않는다 (안내 음성이 막히면 안 된다) */
    suspend fun play()

    companion object {
        /** 가이드 기본 권장 구간(150~200ms)의 가운데 값 */
        const val DEFAULT_MILLIS = 180

        const val SAMPLE_RATE = 24_000
        private const val BYTES_PER_FRAME = 2   // 16bit mono

        /** [millis] 길이의 16bit mono PCM 묵음 (전부 0) */
        fun silencePcm(millis: Int, sampleRate: Int = SAMPLE_RATE): ByteArray =
            ByteArray(sampleRate * millis / 1000 * BYTES_PER_FRAME)

        /** 묵음을 넣지 않는다 (테스트용) */
        val None = PreRollSilence { }
    }
}

/**
 * 묵음 PCM을 TTS와 같은 출력 경로로 실제 재생한다.
 *
 * Pleos TextToSpeech SDK는 합성과 재생을 SDK 안에서 한꺼번에 처리하고 PCM을 앱에 주지 않아서,
 * 가이드 1·2순위(TTS 오디오 데이터 앞에 직접 패딩)는 쓸 수 없다. 대신 같은 AudioAttributes로
 * 묵음 데이터를 먼저 흘려 출력 경로를 깨운 뒤 곧바로 speak() 한다.
 * 재생 시작만 늦추는 방식(3순위, 비권장)과 달리 출력 장치에는 실제 묵음 구간이 나간다.
 */
class AudioTrackPreRollSilence(
    private val millis: Int = PreRollSilence.DEFAULT_MILLIS,
) : PreRollSilence {

    override suspend fun play() {
        try {
            withContext(Dispatchers.IO) {
                val pcm = PreRollSilence.silencePcm(millis)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(TTS_ATTRIBUTES)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(PreRollSilence.SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size)
                    .build()
                try {
                    track.write(pcm, 0, pcm.size)
                    track.play()
                    delay(millis.toLong())
                } finally {
                    runCatching { track.stop() }
                    track.release()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "선행 묵음 재생 실패 (안내 음성은 그대로 진행)", e)
        }
    }

    private companion object {
        const val TAG = "GILCAP-TTS"

        /**
         * TextToSpeech SDK 3.3.1 의 기본 출력 속성과 같게 맞춘다 (SPEECH + USAGE_MEDIA).
         * 차량은 usage 별로 출력 경로가 달라서, 다르면 엉뚱한 경로만 깨운다.
         * PleosSpeechSpeaker 에서 setAudioAttributes 를 쓰게 되면 여기도 같이 바꿀 것.
         */
        val TTS_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
    }
}
