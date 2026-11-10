package com.gilcap.app.domain.matching

import com.gilcap.app.core.model.TimeCondition
import com.gilcap.app.core.model.VehicleCondition
import com.gilcap.app.core.model.WiperState
import java.time.Instant
import java.time.ZoneId

/**
 * 스냅샷 값 → 조건 (F01 생성, F04 매칭에서 같은 기준으로 사용)
 *
 * @param zoneId 시간대 판정 기준 지역 (기본: 기기 설정)
 * @param timeOverride 시연용. 값을 주면 실제 시각 대신 이 시간대를 쓴다 (디버그 패널)
 */
class ConditionClassifier(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val timeOverride: () -> TimeCondition? = { null },
) {
    fun timeOf(timestamp: Long): TimeCondition {
        timeOverride()?.let { return it }
        val hour = Instant.ofEpochMilli(timestamp).atZone(zoneId).hour
        return when (hour) {
            in 6 until 19 -> TimeCondition.DAY      // 06:00 ~ 18:59
            in 4 until 6 -> TimeCondition.DAWN      // 04:00 ~ 05:59
            else -> TimeCondition.NIGHT             // 19:00 ~ 03:59
        }
    }

    fun vehicleOf(wiper: WiperState): VehicleCondition = when (wiper) {
        WiperState.OFF -> VehicleCondition.CLEAR
        WiperState.AUTO, WiperState.LOW -> VehicleCondition.RAIN
        WiperState.HIGH -> VehicleCondition.HEAVY_RAIN
    }
}