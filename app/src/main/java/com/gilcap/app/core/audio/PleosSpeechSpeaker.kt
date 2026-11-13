package com.gilcap.app.core.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pleos TTS의 초기화·재생 콜백을 suspend 함수로 연결한다.
 * 요청마다 SDK 연결을 분리해 이전 요청의 늦은 콜백이 다음 재생을 완료시키지 않도록 한다.
 */
class PleosSpeechSpeaker(context: Context) : SpeechSpeaker {
    private val applicationContext = context.applicationContext
    // 생성 안내와 발견 재생 등 여러 호출자의 발화가 겹치지 않도록 요청 단위로 잠근다.
    private val mutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    // 다른 스레드에서 stop()을 호출해도 현재 재생 요청을 확인할 수 있도록 한다.
    @Volatile private var active: Session? = null

    /** 정상 재생 완료까지 대기하며, 오류·중지·시간 초과는 호출자에게 예외로 전달한다. */
    override suspend fun speak(text: String) {
        require(text.isNotBlank())
        mutex.withLock {
            // SDK 호출과 리소스 해제를 메인 스레드에서 처리한다.
            withContext(Dispatchers.Main.immediate) {
                // 서버 인증 없이 차량 내부 음성 엔진을 사용하는 모드다.
                val session = Session(TextToSpeech(applicationContext, Mode.ON_DEVICE))
                active = session
                try {
                    // 초기화나 재생 완료 콜백이 오지 않아도 무한히 기다리지 않는다.
                    withTimeout(60_000) {
                        session.tts.initialize(object : OnInitializeListener {
                            override fun onSuccess() { session.initialized.complete(Unit) }
                            override fun onFailure(errorType: ErrorType) {
                                session.initialized.completeExceptionally(
                                    IllegalStateException("TTS initialization failed: $errorType"))
                            }
                        })
                        // 초기화가 성공한 뒤에만 리스너 등록과 음성 합성을 요청한다.
                        session.initialized.await()
                        session.tts.addEventListener(session.listener)
                        session.listening = true
                        session.tts.setLocale(Locale.KOREA)
                        session.tts.speak(text)
                        // speak() 호출 자체는 비동기이므로 onDone()을 받을 때까지 기다린다.
                        session.completed.await()
                    }
                } catch (timeout: TimeoutCancellationException) {
                    // 요청 시간 초과는 재시도 가능한 실패로 전달하고, 외부 코루틴 취소와 구분한다.
                    throw IllegalStateException("TTS request timed out", timeout)
                } finally {
                    active = null
                    // 정상 완료뿐 아니라 화면 종료·실패 시에도 해제하며, 일부 정리가 실패해도 release()는 호출한다.
                    try {
                        try { session.tts.stop() }
                        finally {
                            if (session.listening) session.tts.removeEventListener(session.listener)
                        }
                    } finally { session.tts.release() }
                }
            }
        }
    }

    /** 현재 요청만 중지한다. 대기 중인 다음 발화에는 중지 요청을 넘기지 않는다. */
    override fun stop() {
        val session = active ?: return
        mainHandler.post {
            // 메인 스레드에서 실행되기 전에 재생 요청이 바뀌었으면 이전 중지 요청을 무시한다.
            if (active === session) {
                val error = IllegalStateException("TTS playback stopped")
                // 초기화 또는 재생 중 어느 단계를 기다리고 있어도 대기를 끝낸다.
                session.initialized.completeExceptionally(error)
                session.completed.completeExceptionally(error)
                session.tts.stop()
            }
        }
    }

    /** 한 발화의 SDK 객체와 초기화·완료 신호를 묶는다. 중지·오류는 정상 완료로 처리하지 않는다. */
    private class Session(val tts: TextToSpeech) {
        val initialized = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()
        var listening = false
        val listener = object : EventListener {
            override fun onReady() = Unit
            override fun onStart() = Unit
            override fun onUpdatedRms(rms: Double) = Unit
            override fun onDone() { completed.complete(Unit) }
            override fun onStop() {
                completed.completeExceptionally(IllegalStateException("TTS playback stopped"))
            }
            override fun onError(errorType: ErrorType) {
                completed.completeExceptionally(IllegalStateException("TTS playback failed: $errorType"))
            }
        }
    }
}
