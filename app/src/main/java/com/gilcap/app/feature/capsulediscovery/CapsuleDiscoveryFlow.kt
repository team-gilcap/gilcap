package com.gilcap.app.feature.capsulediscovery

import com.gilcap.app.core.audio.SpeechSpeaker
import com.gilcap.app.core.model.Capsule
import com.gilcap.app.core.model.CapsuleDiscovery
import com.gilcap.app.core.model.MatchedCondition
import com.gilcap.app.core.model.VehicleSnapshot
import com.gilcap.app.core.vehicle.VehicleSnapshotProvider
import com.gilcap.app.domain.matching.CapsuleMatcher
import com.gilcap.app.domain.matching.GeoMath
import com.gilcap.app.domain.repository.CapsuleRepository
import com.gilcap.app.domain.repository.DiscoveryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * F03~F06 발견 → 재생 → 수집 (③)
 *
 * 1초마다 지금 스냅샷을 보고
 *   근처 캡슐 찾기(F03) → 내 캡슐·이미 연 캡슐 제외(F07) → 4조건 매칭(F04)
 *   → "누군가 남긴 캡슐이 있어요" → 메시지 TTS + 자막(F05) → 도감 저장(F06) → 대기
 *
 * 안전 규칙(F07)
 * - 한 번에 하나만. 재생 흐름이 끝나기 전에는 새 캡슐을 찾지 않는다
 * - 이미 연 캡슐은 다시 안 열린다 (DiscoveryRepository)
 * - [pause] 동안은 찾지 않는다 (예: ④ 캡슐 생성 중)
 */
class CapsuleDiscoveryFlow(
    private val snapshotProvider: VehicleSnapshotProvider,
    private val capsuleRepository: CapsuleRepository,
    private val discoveryRepository: DiscoveryRepository,
    private val matcher: CapsuleMatcher,
    private val speaker: SpeechSpeaker,
    private val currentUserId: () -> String,
    private val scope: CoroutineScope,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val tickMillis: Long = TICK_MILLIS,
    private val collectedHoldMillis: Long = COLLECTED_HOLD_MILLIS,
) {
    private val _state = MutableStateFlow<DiscoveryUiState>(DiscoveryUiState.Idle)
    val state: StateFlow<DiscoveryUiState> = _state.asStateFlow()

    private val paused = MutableStateFlow(false)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                if (!paused.value) {
                    try {
                        checkOnce()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        println("GILCAP [Discovery] 확인 실패: ${e.message}")
                        _state.value = DiscoveryUiState.Idle
                    }
                }
                delay(tickMillis)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        speaker.stop()
        _state.value = DiscoveryUiState.Idle
    }

    /** 다른 음성 흐름(캡슐 생성 등)이 도는 동안 발견을 멈춘다. 이미 재생 중인 캡슐은 끝까지 간다 */
    fun pause() { paused.value = true }
    fun resume() { paused.value = false }

    /** 한 번 확인. 열 캡슐이 있으면 재생·수집까지 끝내고 돌아온다 */
    internal suspend fun checkOnce() {
        if (_state.value != DiscoveryUiState.Idle) return
        val snapshot = snapshotProvider.snapshot.value?.copy(timestamp = now()) ?: return
        val userId = currentUserId()
        val (capsule, matched) = findOpenable(snapshot, userId) ?: return
        open(capsule, matched, userId)
    }

    private suspend fun findOpenable(snapshot: VehicleSnapshot, userId: String): Pair<Capsule, Set<MatchedCondition>>? =
        capsuleRepository.findNearby(snapshot.latitude, snapshot.longitude)
            .asSequence()
            .filter { it.authorId != userId }
            .sortedBy { GeoMath.distanceMeters(it.latitude, it.longitude, snapshot.latitude, snapshot.longitude) }
            .map { it to matcher.match(it, snapshot) }
            .filter { (_, result) -> result.isMatched }
            .firstOrNull { (capsule, _) -> !discoveryRepository.isDiscovered(capsule.id, userId) }
            ?.let { (capsule, result) -> capsule to result.matchedConditions }

    private suspend fun open(capsule: Capsule, matched: Set<MatchedCondition>, userId: String) {
        try {
            _state.value = DiscoveryUiState.Discovered(capsule)
            speakSafely(ANNOUNCE_TEXT)

            _state.value = DiscoveryUiState.Playing(capsule)
            speakSafely(capsule.messageText)
        } finally {
            // 재생이 중간에 끊겨도(TTS 오류, 정지) 같은 캡슐이 계속 다시 열리지 않도록 수집은 남긴다
            withContext(NonCancellable) {
                discoveryRepository.save(
                    CapsuleDiscovery(
                        capsuleId = capsule.id,
                        userId = userId,
                        discoveredAt = now(),
                        matchedConditions = matched,
                    )
                )
            }
        }
        _state.value = DiscoveryUiState.Collected(capsule)
        speakSafely(collectedText(capsule))
        delay(collectedHoldMillis)
        _state.value = DiscoveryUiState.Idle
    }

    private suspend fun speakSafely(text: String) {
        try {
            speaker.speak(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("GILCAP [Discovery] TTS 실패: ${e.message}")
        }
    }

    companion object {
        const val TICK_MILLIS = 1_000L
        const val COLLECTED_HOLD_MILLIS = 3_000L
        const val ANNOUNCE_TEXT = "누군가 남긴 캡슐이 있어요"

        fun collectedText(capsule: Capsule) = "${capsule.dexName} 캡슐을 도감에 담았어요"
    }
}
