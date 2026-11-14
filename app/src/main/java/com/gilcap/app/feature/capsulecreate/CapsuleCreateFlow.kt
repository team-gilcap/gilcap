package com.gilcap.app.feature.capsulecreate

import com.gilcap.app.core.audio.SpeechListener
import com.gilcap.app.core.audio.SpeechSpeaker
import com.gilcap.app.core.model.Capsule
import com.gilcap.app.core.model.VehicleSnapshot
import com.gilcap.app.core.vehicle.VehicleSnapshotProvider
import com.gilcap.app.domain.matching.ConditionClassifier
import com.gilcap.app.domain.repository.CapsuleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * F01 캡슐 생성 흐름
 *
 * ```
 * [캡슐 남기기] 버튼 → start()
 *   → 그 순간의 VehicleSnapshot 고정 (말하는 동안 이동해도 누른 지점이 기준)
 *   → "남길 말을 해주세요"                         Listening
 *   → 듣기 (최대 10초)
 *   → "'○○○' 이렇게 남길까요? 등록, 다시, 취소"    Confirming(text)
 *       ├ 등록 → 저장 → "비 오는 밤의 해안도로에 캡슐을 남겼어요"   Created(capsule)
 *       ├ 다시 → 듣기 단계로 (최대 2회)
 *       └ 취소 / 8초 무응답 → 저장 안 함                           Cancelled
 *   → 잠시 뒤 Idle
 * ```
 *
 * 화면(⑤)은 [state]만 보고 그리고, 버튼에서 [start]만 부르면 된다.
 * 주행 중 터치는 버튼 1번뿐이고 나머지는 전부 음성이다 (F07).
 *
 * @param currentUserId 캡슐 작성자 (DemoSettings.currentUserId)
 * @param placeNameOf 도감 이름용 지점 이름. MVP는 미리 정한 이름
 * @param onRunningChanged 생성 흐름이 시작(true)·끝(false)날 때 호출. 발견 흐름(③)을 잠시 멈추는 데 쓴다
 */
class CapsuleCreateFlow(
    private val snapshotProvider: VehicleSnapshotProvider,
    private val speaker: SpeechSpeaker,
    private val listener: SpeechListener,
    private val classifier: ConditionClassifier,
    private val capsuleRepository: CapsuleRepository,
    private val currentUserId: () -> String,
    private val scope: CoroutineScope,
    private val placeNameOf: (VehicleSnapshot) -> String = { DEFAULT_PLACE_NAME },
    private val onRunningChanged: (Boolean) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val resultHoldMillis: Long = RESULT_HOLD_MILLIS,
) {
    private val _state = MutableStateFlow<CreateUiState>(CreateUiState.Idle)
    val state: StateFlow<CreateUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** 생성 흐름이 진행 중인지 (발견 흐름과 겹치지 않게 할 때 사용) */
    val isRunning: Boolean
        get() = job?.isActive == true

    /** [캡슐 남기기] 버튼. 이미 진행 중이면 무시한다 */
    @Synchronized
    fun start() {
        if (isRunning) return
        job = scope.launch {
            onRunningChanged(true)
            try {
                runOnce()
                delay(resultHoldMillis)   // 결과 화면(Created / Cancelled)을 잠깐 보여준다
            } finally {
                _state.value = CreateUiState.Idle
                onRunningChanged(false)
            }
        }
    }

    /** 화면에서 강제로 끝낼 때 (예: 앱이 뒤로 감). 음성 "취소"와 달리 안내 없이 바로 멈춘다 */
    @Synchronized
    fun cancel() {
        if (!isRunning) return
        job?.cancel()
        speaker.stop()
    }

    /**
     * 생성 흐름을 한 번 끝까지 돌리고 마지막 상태([CreateUiState.Created] 또는
     * [CreateUiState.Cancelled])를 돌려준다. 보통은 [start]를 쓰고, 이건 테스트용.
     */
    suspend fun runOnce(): CreateUiState {
        // 1. 버튼을 누른 순간의 스냅샷 고정
        val snapshot = snapshotProvider.snapshot.value
            ?: return finishCancelled(MSG_NO_LOCATION)

        var retriesLeft = MAX_RETRIES
        var prompt = MSG_ASK_MESSAGE

        while (true) {
            // 2. 남길 말 듣기
            _state.value = CreateUiState.Listening
            say(prompt)
            val message = listener.listen(MESSAGE_TIMEOUT_MILLIS)?.trim().orEmpty()

            if (message.isEmpty()) {
                if (retriesLeft == 0) return finishCancelled(MSG_NOT_HEARD_CANCEL)
                retriesLeft--
                prompt = MSG_NOT_HEARD_RETRY
                continue
            }

            // 3. 확인: 등록 / 다시 / 취소
            _state.value = CreateUiState.Confirming(message)
            say("'$message' $MSG_CONFIRM")

            when (askCommand()) {
                VoiceCommand.REGISTER -> return register(message, snapshot)

                VoiceCommand.RETRY -> {
                    if (retriesLeft == 0) return finishCancelled(MSG_RETRY_LIMIT)
                    retriesLeft--
                    prompt = MSG_ASK_AGAIN
                }

                VoiceCommand.CANCEL -> return finishCancelled(MSG_CANCELLED)
            }
        }
    }

    /**
     * 확인 대답 듣기. 8초 무응답은 취소.
     * 엉뚱한 말이 들리면 한 번만 다시 묻고, 그래도 못 알아들으면 취소한다 (잘못 저장되는 것보다 안전).
     */
    private suspend fun askCommand(): VoiceCommand {
        repeat(2) { attempt ->
            val answer = listener.listen(CONFIRM_TIMEOUT_MILLIS) ?: return VoiceCommand.CANCEL
            VoiceCommand.parse(answer)?.let { return it }
            if (attempt == 0) say(MSG_ASK_COMMAND_AGAIN)
        }
        return VoiceCommand.CANCEL
    }

    /** 4. 저장. 조건은 버튼을 누른 순간의 시간대·와이퍼 상태가 그대로 된다 */
    private suspend fun register(message: String, snapshot: VehicleSnapshot): CreateUiState {
        val capsule = Capsule(
            id = newId(),
            authorId = currentUserId(),
            messageText = message,
            latitude = snapshot.latitude,
            longitude = snapshot.longitude,
            headingDegrees = snapshot.headingDegrees,
            placeName = placeNameOf(snapshot),
            timeCondition = classifier.timeOf(snapshot.timestamp),
            vehicleCondition = classifier.vehicleOf(snapshot.wiper),
            createdAt = now(),
        )
        capsuleRepository.save(capsule)

        val created = CreateUiState.Created(capsule)
        _state.value = created
        say("${capsule.dexName}에 캡슐을 남겼어요")
        return created
    }

    private suspend fun finishCancelled(message: String): CreateUiState {
        _state.value = CreateUiState.Cancelled
        say(message)
        return CreateUiState.Cancelled
    }

    /**
     * 안내 말하기. PleosSpeechSpeaker는 TTS 오류·중지·시간 초과를 예외로 알리는데,
     * 소리가 안 났다고 생성 흐름까지 죽으면 안 되므로 로그만 남기고 계속 간다.
     */
    private suspend fun say(text: String) {
        try {
            speaker.speak(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("GILCAP [Create] TTS 실패: ${e.message}")
            currentCoroutineContext().ensureActive()   // cancel()로 끊긴 경우는 여기서 멈춘다
        }
    }

    companion object {
        /** 남길 말 듣기 제한 */
        const val MESSAGE_TIMEOUT_MILLIS = 10_000L

        /** 확인 대답 제한. 넘으면 취소 */
        const val CONFIRM_TIMEOUT_MILLIS = 8_000L

        /** "다시"로 돌아갈 수 있는 횟수 (못 들은 경우 포함) */
        const val MAX_RETRIES = 2

        /** Created / Cancelled 를 보여준 뒤 Idle로 돌아가기까지 */
        const val RESULT_HOLD_MILLIS = 3_000L

        const val DEFAULT_PLACE_NAME = "해안도로"

        // 시연 대사
        const val MSG_ASK_MESSAGE = "남길 말을 해주세요"
        const val MSG_ASK_AGAIN = "다시 말씀해 주세요"
        const val MSG_NOT_HEARD_RETRY = "잘 듣지 못했어요. 다시 말씀해 주세요"
        const val MSG_CONFIRM = "이렇게 남길까요? 등록, 다시, 취소 중에 말씀해 주세요"
        const val MSG_ASK_COMMAND_AGAIN = "등록, 다시, 취소 중에 말씀해 주세요"
        const val MSG_CANCELLED = "캡슐을 남기지 않았어요"
        const val MSG_NOT_HEARD_CANCEL = "잘 듣지 못해서 캡슐을 남기지 않았어요"
        const val MSG_RETRY_LIMIT = "다시 말하기는 두 번까지예요. 캡슐을 남기지 않았어요"
        const val MSG_NO_LOCATION = "아직 위치를 찾지 못했어요. 잠시 후 다시 시도해 주세요"
    }
}
