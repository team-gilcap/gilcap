package com.gilcap.app.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.gilcap.app.core.model.*
import com.gilcap.app.feature.capsulecreate.CreateUiState
import com.gilcap.app.feature.capsulediscovery.DiscoveryUiState
import com.gilcap.app.feature.collection.CollectionItem
import com.gilcap.app.feature.collection.CollectionUiState
import com.gilcap.app.feature.collection.ui.CollectionScreen
import com.gilcap.app.feature.drive.ui.DriveScreen
import com.gilcap.app.ui.theme.GilcapTheme

// 미리보기 전용 데이터이며 실제 저장소에는 넣지 않는다.
private val previewCapsule = Capsule(
    id = "preview", authorId = "user-a", messageText = "비 오는 날 이 길을 지나면, 잠시 창밖을 바라봐요. 좋은 하루 보내세요.",
    latitude = 37.77, longitude = 128.95, headingDegrees = 90f, placeName = "해안도로",
    timeCondition = TimeCondition.NIGHT, vehicleCondition = VehicleCondition.RAIN, createdAt = 0,
)

@Composable
private fun PreviewDrive(create: CreateUiState = CreateUiState.Idle,
                         discovery: DiscoveryUiState = DiscoveryUiState.Idle) {
    GilcapTheme {
        Surface {
            DriveScreen(create, discovery, Gear.D, canCreate = true,
                onCreateCapsule = {}, onOpenCollection = {})
        }
    }
}

@Preview(name = "주행 대기", widthDp = 1024, heightDp = 600)
@Composable private fun IdlePreview() = PreviewDrive()

@Preview(name = "음성 입력", widthDp = 1024, heightDp = 600)
@Composable private fun ListeningPreview() = PreviewDrive(CreateUiState.Listening)

@Preview(name = "등록 확인", widthDp = 1024, heightDp = 600)
@Composable private fun ConfirmingPreview() = PreviewDrive(CreateUiState.Confirming(previewCapsule.messageText))

@Preview(name = "생성 완료", widthDp = 1024, heightDp = 600)
@Composable private fun CreatedPreview() = PreviewDrive(CreateUiState.Created(previewCapsule))

@Preview(name = "캡슐 발견", widthDp = 1024, heightDp = 600)
@Composable private fun DiscoveredPreview() = PreviewDrive(discovery = DiscoveryUiState.Discovered(previewCapsule))

@Preview(name = "본문 자막", widthDp = 1024, heightDp = 600)
@Composable private fun PlayingPreview() = PreviewDrive(discovery = DiscoveryUiState.Playing(previewCapsule))

@Preview(name = "수집 완료", widthDp = 1024, heightDp = 600)
@Composable private fun CollectedPreview() = PreviewDrive(discovery = DiscoveryUiState.Collected(previewCapsule))

@Preview(name = "작은 화면·큰 글씨", widthDp = 480, heightDp = 320, fontScale = 1.5f)
@Composable private fun SmallScreenPreview() = PreviewDrive(discovery = DiscoveryUiState.Playing(previewCapsule))

@Preview(name = "빈 도감", widthDp = 1024, heightDp = 600)
@Composable private fun EmptyCollectionPreview() {
    GilcapTheme { Surface { CollectionScreen(CollectionUiState.Content("user-b", emptyList()), {}) } }
}

@Preview(name = "수집한 도감", widthDp = 1024, heightDp = 600)
@Composable private fun CollectionPreview() {
    val record = CapsuleDiscovery(previewCapsule.id, "user-b", 1794300000000L,
        MatchedCondition.entries.toSet())
    GilcapTheme { Surface { CollectionScreen(CollectionUiState.Content("user-b",
        listOf(CollectionItem(record, previewCapsule))), {}) } }
}
