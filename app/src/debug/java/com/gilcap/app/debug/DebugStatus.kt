package com.gilcap.app.debug

import com.gilcap.app.core.model.Capsule
import com.gilcap.app.core.model.TimeCondition
import com.gilcap.app.core.model.VehicleCondition
import com.gilcap.app.core.model.VehicleSnapshot
import com.gilcap.app.domain.matching.ConditionClassifier
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 디버그 패널에 표시할 현재 상태 한 장
 * - 화면(Compose)과 분리해 JVM 단위 테스트로 확인하기 위함
 * - 스냅샷이 없으면(위치 미수신) 차량, 조건 항목은 null
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
) {
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
        )
    }
}
