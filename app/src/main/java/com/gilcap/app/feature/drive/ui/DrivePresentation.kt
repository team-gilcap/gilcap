package com.gilcap.app.feature.drive.ui

import com.gilcap.app.feature.capsulecreate.CreateUiState
import com.gilcap.app.feature.capsulediscovery.DiscoveryUiState

/** 생성·발견 상태를 주행 화면에서 표시할 문구와 조작 가능 여부로 변환한다. */
data class DrivePresentation(
    val status: String,
    val subtitle: String,
    val isBusy: Boolean,
)

fun drivePresentation(create: CreateUiState, discovery: DiscoveryUiState): DrivePresentation {
    // 생성 중에는 음성 입력과 확인 안내를 우선 표시한다.
    return when (create) {
        CreateUiState.Listening -> DrivePresentation("듣고 있어요", "남길 말을 해주세요", true)
        is CreateUiState.Confirming -> DrivePresentation(
            "등록, 다시, 취소 중 말씀해주세요", create.text, true)
        is CreateUiState.Created -> DrivePresentation("캡슐을 남겼어요", create.capsule.dexName, true)
        CreateUiState.Cancelled -> DrivePresentation("캡슐 남기기를 취소했어요", "다음 길에서 다시 만나요", false)
        CreateUiState.Idle -> when (discovery) {
            DiscoveryUiState.Idle -> DrivePresentation("길 위의 캡슐을 찾고 있어요", "같은 길에서 만나는 누군가의 이야기", false)
            is DiscoveryUiState.Discovered -> DrivePresentation("누군가 남긴 캡슐이 있어요", "잠시 귀 기울여 보세요", true)
            is DiscoveryUiState.Playing -> DrivePresentation("캡슐을 듣고 있어요", discovery.capsule.messageText, true)
            is DiscoveryUiState.Collected -> DrivePresentation("도감에 추가됐어요", discovery.capsule.dexName, true)
            is DiscoveryUiState.Error -> DrivePresentation("캡슐을 열지 못했어요", discovery.message, false)
        }
    }
}
