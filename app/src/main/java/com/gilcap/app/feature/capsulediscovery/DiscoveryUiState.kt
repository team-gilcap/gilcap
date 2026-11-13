package com.gilcap.app.feature.capsulediscovery

import com.gilcap.app.core.model.Capsule

sealed interface DiscoveryUiState {
    data class Error(val message: String) : DiscoveryUiState             // 재생·수집 처리 실패 안내
    data object Idle : DiscoveryUiState                                 // 주행 중, 근처에 열 캡슐 없음
    data class Discovered(val capsule: Capsule) : DiscoveryUiState      // "누군가 남긴 캡슐이 있어요"
    data class Playing(val capsule: Capsule) : DiscoveryUiState         // TTS 재생 중 (자막)
    data class Collected(val capsule: Capsule) : DiscoveryUiState       // "도감에 추가됐어요"
}