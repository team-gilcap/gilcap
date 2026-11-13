package com.gilcap.app.debug

import com.gilcap.app.core.location.PleosLocationSource
import com.gilcap.app.core.model.Capsule
import com.gilcap.app.core.model.TimeCondition
import com.gilcap.app.core.model.VehicleCondition
import com.gilcap.app.core.model.VehicleSnapshot
import com.gilcap.app.core.vehicle.PleosVehicleStateSource
import com.gilcap.app.domain.matching.ConditionClassifier
import com.gilcap.app.feature.capsulediscovery.DiscoveryUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 디버그 패널에 표시할 현재 상태 한 장
 * - 화면(Compose)과 분리해 JVM 단위 테스트로 확인하기 위함
 * - 스냅샷이 없으면(위치 미수신) 차량, 조건 항목은 null
 * - SDK 연결 상태가 null이면 Fake 구현 사용 중
 */
data class DebugStatus(
    val userId: String,
    val timeOverride: TimeCondition?,
    val snapshotTime: String?,
    val location: String?,
    val heading: String?,
    val speed: String?,
    val gear: String?,
    val wiper: String?,
    val time: TimeCondition?,
    val vehicle: VehicleCondition?,
    val nearby: List<String>,
    val totalCapsules: Int,
    val vehicleSource: String = FAKE,
    val locationSource: String = FAKE,
    val discovery: String = DISCOVERY_IDLE,
) {
    val vehicleSourceOk: Boolean get() = vehicleSource == RECEIVING || vehicleSource == FAKE
    val locationSourceOk: Boolean get() = locationSource == RECEIVING || locationSource == FAKE

    /** 맨 위 상태 줄: 위치가 안 들어올 때 원인(권한 없음 등)까지 바로 보이게 하기 위함 */
    val headerText: String
        get() = if (snapshotTime != null) "차량 상태 수신 중  $snapshotTime" else "위치 미수신  ($locationSource)"

    /** 차량, 위치 모두 정상 수신 중일 때만 정상 표시 */
    val allOk: Boolean get() = snapshotTime != null && vehicleSourceOk && locationSourceOk

    /**
     * 날씨 판정과 근거: "비  (와이퍼 LOW)"
     * - 날씨는 와이퍼 상태로 판단하므로 근거를 함께 표시해 바로 이해되게 하기 위함
     */
    val weatherText: String?
        get() = vehicle?.let { "${weatherName(it)}  (와이퍼 $wiper)" }

    /** 시간대 판정과 근거: "밤  (덮어쓰기)" 또는 "낮  (실제 시각)" */
    val timeText: String?
        get() = time?.let { "${timeName(it)}  (${if (timeOverride != null) "덮어쓰기" else "실제 시각"})" }

    companion object {
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
        const val FAKE = "Fake"
        const val RECEIVING = "수신 중"
        const val DISCOVERY_IDLE = "대기 중"

        fun vehicleSourceName(status: PleosVehicleStateSource.Status?) = when (status) {
            null -> FAKE
            PleosVehicleStateSource.Status.RUNNING -> RECEIVING
            PleosVehicleStateSource.Status.NO_PERMISSION -> "권한 없음"
            PleosVehicleStateSource.Status.ERROR -> "오류"
            PleosVehicleStateSource.Status.STOPPED -> "중지"
        }

        fun locationSourceName(status: PleosLocationSource.Status?) = when (status) {
            null -> FAKE
            PleosLocationSource.Status.RUNNING -> RECEIVING
            PleosLocationSource.Status.NO_PERMISSION -> "권한 없음"
            PleosLocationSource.Status.UNSUPPORTED -> "미지원 기기"
            PleosLocationSource.Status.ERROR -> "오류"
            PleosLocationSource.Status.STOPPED -> "중지"
        }

        /** 발견 흐름 단계와 캡슐 이름: "재생 중  (비 오는 밤의 해안도로)" */
        fun discoveryName(state: DiscoveryUiState) = when (state) {
            DiscoveryUiState.Idle -> DISCOVERY_IDLE
            is DiscoveryUiState.Discovered -> "발견  (${state.capsule.dexName})"
            is DiscoveryUiState.Playing -> "재생 중  (${state.capsule.dexName})"
            is DiscoveryUiState.Collected -> "도감 저장  (${state.capsule.dexName})"
        }

        fun weatherName(condition: VehicleCondition) = when (condition) {
            VehicleCondition.CLEAR -> "맑음"
            VehicleCondition.RAIN -> "비"
            VehicleCondition.HEAVY_RAIN -> "폭우"
        }

        fun timeName(condition: TimeCondition) = when (condition) {
            TimeCondition.DAY -> "낮"
            TimeCondition.NIGHT -> "밤"
            TimeCondition.DAWN -> "새벽"
        }

        fun of(
            userId: String,
            timeOverride: TimeCondition?,
            snapshot: VehicleSnapshot?,
            classifier: ConditionClassifier,
            nearby: List<Capsule>,
            totalCapsules: Int,
            vehicleSourceStatus: PleosVehicleStateSource.Status? = null,
            locationSourceStatus: PleosLocationSource.Status? = null,
            discovery: DiscoveryUiState = DiscoveryUiState.Idle,
            zoneId: ZoneId = ZoneId.systemDefault(),
        ) = DebugStatus(
            userId = userId,
            timeOverride = timeOverride,
            snapshotTime = snapshot?.let {
                Instant.ofEpochMilli(it.timestamp).atZone(zoneId).format(TIME_FORMAT)
            },
            location = snapshot?.let { "%.5f, %.5f".format(it.latitude, it.longitude) },
            heading = snapshot?.let { "%.0f°".format(it.headingDegrees) },
            speed = snapshot?.let { "%.0f km/h".format(it.speed) },
            gear = snapshot?.gear?.name,
            wiper = snapshot?.wiper?.name,
            time = snapshot?.let { classifier.timeOf(it.timestamp) },
            vehicle = snapshot?.let { classifier.vehicleOf(it.wiper) },
            nearby = nearby.map { "${it.dexName} (${it.authorId})" },
            totalCapsules = totalCapsules,
            vehicleSource = vehicleSourceName(vehicleSourceStatus),
            locationSource = locationSourceName(locationSourceStatus),
            discovery = discoveryName(discovery),
        )
    }
}
