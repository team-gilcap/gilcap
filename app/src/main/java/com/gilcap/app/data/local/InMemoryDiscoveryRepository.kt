package com.gilcap.app.data.local

import com.gilcap.app.core.model.CapsuleDiscovery
import com.gilcap.app.domain.repository.DiscoveryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class InMemoryDiscoveryRepository : DiscoveryRepository {
    private val discoveries = MutableStateFlow<List<CapsuleDiscovery>>(emptyList())

    override suspend fun save(discovery: CapsuleDiscovery) {
        discoveries.update { it + discovery }
    }

    override suspend fun isDiscovered(capsuleId: String, userId: String): Boolean =
        discoveries.value.any { it.capsuleId == capsuleId && it.userId == userId }

    override fun observeByUser(userId: String): Flow<List<CapsuleDiscovery>> =
        discoveries.map { list -> list.filter { it.userId == userId } }

    override suspend fun clear() {
        discoveries.value = emptyList()
    }
}