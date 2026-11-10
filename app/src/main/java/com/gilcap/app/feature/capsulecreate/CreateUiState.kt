package com.gilcap.app.feature.capsulecreate

import com.gilcap.app.core.model.Capsule

sealed interface CreateUiState {
    data object Idle : CreateUiState                                    // 대기: [캡슐 남기기] 버튼
    data object Listening : CreateUiState                               // "남길 말을 해주세요"
    data class Confirming(val text: String) : CreateUiState             // "'○○○' 등록, 다시, 취소"
    data class Created(val capsule: Capsule) : CreateUiState            // "캡슐을 남겼어요"
    data object Cancelled : CreateUiState                               // 취소 또는 무응답
}