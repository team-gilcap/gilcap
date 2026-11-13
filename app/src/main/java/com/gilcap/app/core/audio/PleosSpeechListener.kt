package com.gilcap.app.core.audio

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.OnServerConnectionListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * F01 듣기: Pleos SpeechToText SDK (v3.3.1)
 *
 * [listen] 한 번 = 인식 세션 한 번.
 * - 말이 끝나서(EPD) 최종 결과가 오면 그 문장을 돌려준다.
 * - [timeoutMillis]가 지나면 인식을 멈추고, 그때까지 들린 말이 있으면 그 말을, 없으면 null.
 * - 오류(권한, 마이크, 네트워크 등)는 예외 대신 null (= 무응답)로 돌려준다.
 *
 * 모드
 * - clientId / clientSecret 이 없으면 ON_DEVICE (앱 등록 불필요).
 * - 둘 다 있으면 HYBRID + registerApp (서버 우선, 네트워크 실패 시 On-device).
 *
 * 필요 권한: `pleos.car.permission.STT_SERVICE`
 * 에뮬레이터: Extended Controls → Microphone 에서 호스트 마이크 입력 허용 필요.
 */
class PleosSpeechListener(
    context: Context,
    private val clientId: String? = null,
    private val clientSecret: String? = null,
    private val locale: Locale = Locale.KOREA,
) : SpeechListener {

    private val appContext = context.applicationContext
    private val hasCredentials = !clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()
    private val mode = if (hasCredentials) Mode.HYBRID else Mode.ON_DEVICE

    private val initMutex = Mutex()
    private val listenMutex = Mutex()

    @Volatile private var stt: SpeechToText? = null
    @Volatile private var session: Session? = null

    /** listen() 한 번 동안의 상태 */
    private class Session {
        @Volatile var active = false          // onReady / onStarted / onUpdated 중 하나라도 받았는지
        @Volatile var latestText = ""         // 중간 결과 포함 가장 최근 텍스트
        val result = CompletableDeferred<String?>()   // 최종 문장 (null = 못 들음)
        val ended = CompletableDeferred<Unit>()       // onEndedRecognition
    }

    private val resultListener = object : ResultListener {
        override fun onReady() { session?.active = true }
        override fun onStartedRecognition() { session?.active = true }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit

        override fun onUpdated(stt: String, completed: Boolean) {
            val s = session ?: return
            s.active = true
            if (stt.isNotBlank()) s.latestText = stt
            if (completed) s.result.complete(s.latestText.clean())
        }

        override fun onEndedRecognition() {
            val s = session ?: return
            // 이전 세션의 종료 이벤트가 늦게 도착한 경우는 무시
            if (!s.active) return
            s.ended.complete(Unit)
            s.result.complete(s.latestText.clean())
        }

        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "STT 오류: ${errorType.name}")
            val s = session ?: return
            s.result.complete(s.latestText.clean())
            s.ended.complete(Unit)
        }
    }

    override suspend fun listen(timeoutMillis: Long): String? = listenMutex.withLock {
        val engine = ensureReady() ?: run {
            Log.w(TAG, "STT를 쓸 수 없음 → 무응답 처리")
            return@withLock null
        }
        val current = Session()
        session = current
        try {
            withContext(Dispatchers.Main) { engine.request() }

            var text = withTimeoutOrNull(timeoutMillis) { current.result.await() }
            if (!current.result.isCompleted) {
                // 제한 시간 초과: 인식을 멈추고, 멈추면서 나오는 최종 결과를 잠깐 기다린다
                stopEngine(engine)
                text = withTimeoutOrNull(FINALIZE_GRACE_MILLIS) { current.result.await() }
                    ?: current.latestText.clean()
            }
            // 세션이 깔끔하게 닫힌 뒤 돌아가야 다음 listen()이 이전 이벤트를 받지 않는다
            if (withTimeoutOrNull(END_GRACE_MILLIS) { current.ended.await() } == null) {
                stopEngine(engine)
            }
            Log.d(TAG, "STT 결과: $text")
            text
        } catch (e: CancellationException) {
            stopEngine(engine)
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "STT listen 실패", e)
            stopEngine(engine)
            null
        } finally {
            session = null
        }
    }

    /** 앱 종료 시 호출 (선택). 이후 다시 listen하면 새로 초기화한다 */
    fun release() {
        val engine = stt ?: return
        stt = null
        runCatching {
            engine.removeListener(resultListener)
            engine.release()
        }
    }

    private fun stopEngine(engine: SpeechToText) {
        runCatching { engine.stop() }.onFailure { Log.w(TAG, "STT stop 실패", it) }
    }

    /** 처음 들을 때 한 번만 초기화. 실패하면 null (다음 listen 때 다시 시도) */
    private suspend fun ensureReady(): SpeechToText? {
        stt?.let { return it }
        return initMutex.withLock {
            stt?.let { return@withLock it }
            try {
                val engine = withContext(Dispatchers.Main) { SpeechToText(appContext, mode) }

                val initialized = CompletableDeferred<ErrorType?>()
                withContext(Dispatchers.Main) {
                    engine.initialize(object : OnInitializeListener {
                        override fun onSuccess() { initialized.complete(null) }
                        override fun onFailure(errorType: ErrorType) { initialized.complete(errorType) }
                    })
                }
                val initError = withTimeoutOrNull(INIT_TIMEOUT_MILLIS) { initialized.await() }
                if (!initialized.isCompleted || initError != null) {
                    Log.e(TAG, "STT 초기화 실패: ${initError?.name ?: "응답 없음"} (STT_SERVICE 권한, 에뮬레이터 확인)")
                    runCatching { engine.release() }
                    return@withLock null
                }

                withContext(Dispatchers.Main) {
                    engine.addListener(resultListener)   // request() 전에 등록
                    engine.setLocale(locale)
                }

                if (hasCredentials) {
                    val connected = CompletableDeferred<ErrorType?>()
                    withContext(Dispatchers.Main) {
                        engine.registerApp(clientId!!, clientSecret!!, object : OnServerConnectionListener {
                            override fun onConnected() { connected.complete(null) }
                            override fun onFailed(errorType: ErrorType) { connected.complete(errorType) }
                        })
                    }
                    val connectError = withTimeoutOrNull(INIT_TIMEOUT_MILLIS) { connected.await() }
                    if (!connected.isCompleted || connectError != null) {
                        // HYBRID라 서버 연결이 안 돼도 On-device로 계속 시도한다
                        Log.w(TAG, "STT 앱 등록 실패: ${connectError?.name ?: "응답 없음"}")
                    }
                }

                stt = engine
                engine
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "STT 초기화 중 예외", e)
                null
            }
        }
    }

    private companion object {
        const val TAG = "GILCAP-STT"
        const val INIT_TIMEOUT_MILLIS = 5_000L
        const val FINALIZE_GRACE_MILLIS = 1_500L
        const val END_GRACE_MILLIS = 700L

        fun String.clean(): String? = trim().takeIf { it.isNotEmpty() }
    }
}
