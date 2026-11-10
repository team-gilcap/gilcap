package com.gilcap.app.core.model

/** 사용자가 연 캡슐 기록 = (F06) */
data class CapsuleDiscovery(
    val capsuleId: String,
    val userId: String,                          // 연 사용자 (A / B 도감 구분)
    val discoveredAt: Long,
    val matchedConditions: Set<MatchedCondition>,
)