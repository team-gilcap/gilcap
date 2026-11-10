package com.gilcap.app.domain.repository

import com.gilcap.app.core.model.Capsule
import kotlinx.coroutines.flow.Flow

/** 캡슐 저장소 (F01 저장, F03 근처 캡슐 찾기) */
interface CapsuleRepository {
    suspend fun save(capsule: Capsule)

    /** 이 지점에서 radiusMeters 안에 들어오는 캡슐들 */
    suspend fun findNearby(latitude: Double, longitude: Double): List<Capsule>

    fun observeAll(): Flow<List<Capsule>>

    suspend fun clear()   // 시연 초기화
}