package com.gilcap.app.core.model

/** 시간대 조건. 캡슐을 만든 순간의 시각 */
enum class TimeCondition(val label: String) {
    DAY("낮의"),      // 06~19시
    NIGHT("밤의"),    // 19~04시
    DAWN("새벽의"),   // 04~06시
}

/** 차량 상태 조건. 와이퍼 상태 */
enum class VehicleCondition(val label: String) {
    CLEAR("맑은"),          // 와이퍼 OFF
    RAIN("비 오는"),        // 와이퍼 AUTO, LOW
    HEAVY_RAIN("폭우 속"),  // 와이퍼 HIGH
}

enum class MatchedCondition { LOCATION, HEADING, TIME, VEHICLE }

enum class Gear { P, R, N, D, UNKNOWN }

enum class WiperState { OFF, AUTO, LOW, HIGH }

