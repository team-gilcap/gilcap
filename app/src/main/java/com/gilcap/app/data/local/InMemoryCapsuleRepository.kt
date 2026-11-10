package com.gilcap.app.data.local

import com.gilcap.app.core.model.Capsule
import com.gilcap.app.domain.matching.GeoMath
import com.gilcap.app.domain.repository.CapsuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** MVP 저장소: 메모리에만 저장 (앱을 끄면 사라짐, P1에서 Room으로 교체) */
class InMemoryCapsuleRepository : CapsuleRepository {
    private val capsules = MutableStateFlow<List<Capsule>>(emptyList())

    override suspend fun save(capsule: Capsule) {
        capsules.update { it + capsule }
    }

    override suspend fun findNearby(latitude: Double, longitude: Double): List<Capsule> =
        capsules.value.filter {
            GeoMath.distanceMeters(it.latitude, it.longitude, latitude, longitude) <= it.radiusMeters
        }

    override fun observeAll(): Flow<List<Capsule>> = capsules.asStateFlow()

    override suspend fun clear() {
        capsules.value = emptyList()
    }
}