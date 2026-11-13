package com.gilcap.app.feature.collection

import com.gilcap.app.core.model.Capsule
import com.gilcap.app.core.model.CapsuleDiscovery

sealed interface CollectionUiState {
    val userId: String
    data class Loading(override val userId: String) : CollectionUiState
    data class Content(override val userId: String, val items: List<CollectionItem>) : CollectionUiState
    data class Error(override val userId: String) : CollectionUiState
}

data class CollectionItem(val discovery: CapsuleDiscovery, val capsule: Capsule?)
