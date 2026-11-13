package com.gilcap.app.feature.capsulediscovery

import com.gilcap.app.core.audio.SpeechSpeaker
import com.gilcap.app.core.demo.DemoSettings
import com.gilcap.app.core.model.CapsuleDiscovery
import com.gilcap.app.core.vehicle.VehicleSnapshotProvider
import com.gilcap.app.domain.matching.CapsuleMatcher
import com.gilcap.app.domain.repository.CapsuleRepository
import com.gilcap.app.domain.repository.DiscoveryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 현재 차량 조건에 맞는 캡슐 하나를 골라 안내 → 본문 재생 → 도감 저장 순서로 처리한다.
 * 화면 생명주기에 묶인 코루틴에서 run()을 호출하며, 재생 도중 취소되면 수집하지 않는다.
 */
class CapsuleDiscoveryFlow(
    private val snapshotProvider: VehicleSnapshotProvider,
    private val capsules: CapsuleRepository,
    private val discoveries: DiscoveryRepository,
    private val matcher: CapsuleMatcher,
    private val speaker: SpeechSpeaker,
    private val settings: DemoSettings,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow<DiscoveryUiState>(DiscoveryUiState.Idle)
    // 화면에서는 상태를 관찰만 하고, 상태 전환은 이 흐름에서 담당한다.
    val state = _state.asStateFlow()
    // run()이 여러 곳에서 호출되어도 발견 흐름은 한 번에 하나만 실행한다.
    private val runMutex = Mutex()
    // (사용자 ID, 캡슐 ID)별로 재시도가 가능한 시각을 보관한다.
    private val retryAfter = mutableMapOf<Pair<String, String>, Long>()

    /** 위치·사용자·시간대·캡슐 목록 변경과 1초 주기에 반응해 후보를 다시 찾는다. */
    suspend fun run() = runMutex.withLock {
        // 차량 값이 그대로여도 시간대 경계와 재시도 대기시간을 다시 판정한다.
        val ticks = flow {
            while (currentCoroutineContext().isActive) {
                emit(now())
                delay(1_000)
            }
        }
        try {
            combine(snapshotProvider.snapshot, settings.currentUserId,
                settings.timeOverride, capsules.observeAll(), ticks
            ) { _, _, _, _, _ -> Unit
            // collectLatest를 사용하면 위치 갱신마다 진행 중인 TTS가 취소되므로 순차 처리한다.
            }.collect {
                // 이전 재생이 끝난 뒤 대기 중이던 알림을 처리할 때도 최신 사용자와 위치를 읽는다.
                val user = settings.currentUserId.value
                // 좌표·차량 값은 유지하고 시간 조건은 현재 시각으로 판정한다. 위치 최신성 검사는 별도다.
                val snapshot = snapshotProvider.snapshot.value?.copy(timestamp = now())
                if (snapshot == null) {
                    _state.value = DiscoveryUiState.Idle
                    return@collect
                }
                try {
                    // 반경 안에서 본인 작성·재시도 대기·수집 완료 캡슐을 제외하고 네 조건을 확인한다.
                    val capsule = capsules.findNearby(snapshot.latitude, snapshot.longitude)
                        .firstOrNull {
                            it.authorId != user &&
                                (retryAfter[user to it.id] ?: 0L) <= now() &&
                                !discoveries.isDiscovered(it.id, user) &&
                                matcher.match(it, snapshot).isMatched
                        }
                    if (capsule == null) {
                        _state.value = DiscoveryUiState.Idle
                        return@collect
                    }
                    // 재생 중 시연 설정이 바뀌어도 발견 순간의 조건을 도감 기록에 남긴다.
                    val matchedConditions = matcher.match(capsule, snapshot).matchedConditions
                    val key = user to capsule.id
                    try {
                        // 각 speak()가 완료될 때까지 기다려 안내와 본문이 겹치지 않게 한다.
                        _state.value = DiscoveryUiState.Discovered(capsule)
                        speaker.speak("누군가 남긴 캡슐이 있어요")
                        // 화면은 Playing 상태의 messageText를 본문 자막으로 표시한다.
                        _state.value = DiscoveryUiState.Playing(capsule)
                        speaker.speak(capsule.messageText)
                        // 본문이 정상 완료된 경우에만, 재생을 시작한 사용자의 도감에 저장한다.
                        discoveries.save(CapsuleDiscovery(
                            capsuleId = capsule.id, userId = user, discoveredAt = now(),
                            matchedConditions = matchedConditions,
                        ))
                        retryAfter.remove(key)
                        _state.value = DiscoveryUiState.Collected(capsule)
                        // 완료 문구를 잠시 보여준 뒤 다음 후보를 찾는다.
                        delay(1_500)
                    } catch (cancelled: CancellationException) {
                        // 화면 종료에 의한 취소를 재생 오류로 바꾸지 않고 상위 코루틴에 전달한다.
                        throw cancelled
                    } catch (error: Exception) {
                        // 실패한 캡슐을 매초 반복 안내하지 않도록 30초 동안 재시도를 막는다.
                        retryAfter[key] = now() + 30_000
                        throw error
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    _state.value = DiscoveryUiState.Error("캡슐을 재생하지 못했어요. 잠시 후 다시 시도해요.")
                    delay(2_000)
                }
            }
        } finally {
            // 화면 재진입 시 이전 재생 상태나 자막이 남지 않도록 초기화한다.
            _state.value = DiscoveryUiState.Idle
        }
    }
}
