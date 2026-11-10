package com.gilcap.app.domain.matching

import com.gilcap.app.core.model.Capsule
import com.gilcap.app.core.model.MatchedCondition
import com.gilcap.app.core.model.VehicleSnapshot

/** 매칭 결과. 맞은 조건 목록을 같이 돌려준다 (도감 기록, P1 흔적 발견에 사용) */
data class MatchResult(val matchedConditions: Set<MatchedCondition>) {
    /** 4개 조건이 모두 맞아야 개봉 */
    val isMatched: Boolean
        get() = matchedConditions.size == MatchedCondition.entries.size
}

/**
 * F03, F04: 캡슐과 지금 차 상태를 비교한다.
 * 작성자 본인 여부, 이미 연 캡슐 여부는 여기서 보지 않는다 (발견 흐름 F07에서 처리).
 */
class CapsuleMatcher(private val classifier: ConditionClassifier) {

    fun match(capsule: Capsule, snapshot: VehicleSnapshot): MatchResult {
        val matched = buildSet {
            // ① 위치: 반경 안
            val distance = GeoMath.distanceMeters(
                capsule.latitude, capsule.longitude,
                snapshot.latitude, snapshot.longitude,
            )
            if (distance <= capsule.radiusMeters) add(MatchedCondition.LOCATION)

            // ② 방향: ±45°
            val headingDiff = GeoMath.headingDifference(capsule.headingDegrees, snapshot.headingDegrees)
            if (headingDiff <= Capsule.HEADING_TOLERANCE_DEGREES) add(MatchedCondition.HEADING)

            // ③ 시간대
            if (classifier.timeOf(snapshot.timestamp) == capsule.timeCondition) add(MatchedCondition.TIME)

            // ④ 차량 상태 (와이퍼)
            if (classifier.vehicleOf(snapshot.wiper) == capsule.vehicleCondition) add(MatchedCondition.VEHICLE)
        }
        return MatchResult(matched)
    }
}