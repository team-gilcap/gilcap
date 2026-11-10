package com.gilcap.app.domain.repository

import com.gilcap.app.core.model.CapsuleDiscovery
import kotlinx.coroutines.flow.Flow

/** 도감 저장소 (F06 수집, F07 중복 방지) */
interface DiscoveryRepository {
    suspend fun save(discovery: CapsuleDiscovery)

    /** 이 사용자가 이미 연 캡슐인지 (다시 안 열리게) */
    suspend fun isDiscovered(capsuleId: String, userId: String): Boolean

    /** 사용자별 도감 목록 */
    fun observeByUser(userId: String): Flow<List<CapsuleDiscovery>>

    suspend fun clear()
}