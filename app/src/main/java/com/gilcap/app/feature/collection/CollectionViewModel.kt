package com.gilcap.app.feature.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gilcap.app.domain.repository.CapsuleRepository
import com.gilcap.app.domain.repository.DiscoveryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionViewModel(
    currentUserId: StateFlow<String>,
    capsules: CapsuleRepository,
    discoveries: DiscoveryRepository,
) : ViewModel() {
    // 사용자 전환 시 이전 사용자의 구독을 취소하고 새 도감 목록을 구독한다.
    val state: StateFlow<CollectionUiState> = currentUserId.flatMapLatest { user ->
        combine(discoveries.observeByUser(user), capsules.observeAll()) { records, allCapsules ->
            val byId = allCapsules.associateBy { it.id }
            val items = records.sortedByDescending { it.discoveredAt }
                .distinctBy { it.capsuleId }
                .map { CollectionItem(it, byId[it.capsuleId]) }
            CollectionUiState.Content(user, items) as CollectionUiState
        }.onStart { emit(CollectionUiState.Loading(user)) }
            .catch { emit(CollectionUiState.Error(user)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
        CollectionUiState.Loading(currentUserId.value))
}
