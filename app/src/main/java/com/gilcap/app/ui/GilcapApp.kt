package com.gilcap.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gilcap.app.di.AppContainer
import com.gilcap.app.feature.capsulecreate.CreateUiState
import com.gilcap.app.feature.collection.CollectionUiState
import com.gilcap.app.feature.collection.CollectionViewModel
import com.gilcap.app.feature.collection.ui.CollectionScreen
import com.gilcap.app.feature.drive.ui.DriveScreen
import com.gilcap.app.feature.drive.ui.drivePresentation
import com.gilcap.app.ui.theme.GilcapTheme

@Composable
fun GilcapApp(
    container: AppContainer,
    createState: CreateUiState = CreateUiState.Idle,
    onCreateCapsule: (() -> Unit)? = null,
) {
    val discoveryState by container.capsuleDiscoveryFlow.state.collectAsStateWithLifecycle()
    val gear by container.vehicleStateSource.gear.collectAsStateWithLifecycle()
    val userId by container.demoSettings.currentUserId.collectAsStateWithLifecycle()
    var collectionRequested by rememberSaveable { mutableStateOf(false) }
    val busy = drivePresentation(createState, discoveryState).isBusy
    val collectionAllowed = canOpenCollection(gear, busy)

    // 화면 전환 처리를 기다리지 않고, 그리는 시점부터 P단 외에는 도감을 숨긴다.
    val showCollection = collectionRequested && collectionAllowed
    LaunchedEffect(collectionAllowed) {
        if (!collectionAllowed) collectionRequested = false
    }
    BackHandler(enabled = showCollection) { collectionRequested = false }

    val factory = remember(container) {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(CollectionViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return CollectionViewModel(container.demoSettings.currentUserId,
                    container.capsuleRepository, container.discoveryRepository) as T
            }
        }
    }
    val collectionViewModel: CollectionViewModel = viewModel(factory = factory)
    val collectionState by collectionViewModel.state.collectAsStateWithLifecycle()

    GilcapTheme {
        Surface {
            if (showCollection) {
                // 사용자 변경과 목록 갱신 사이에도 이전 사용자의 도감이 보이지 않게 한다.
                val visibleState = collectionState.takeIf { it.userId == userId }
                    ?: CollectionUiState.Loading(userId)
                CollectionScreen(visibleState, onBack = { collectionRequested = false })
            } else {
                DriveScreen(createState, discoveryState, gear,
                    canCreate = onCreateCapsule != null,
                    onCreateCapsule = { if (!busy) onCreateCapsule?.invoke() },
                    onOpenCollection = {
                        // 클릭 직전의 실제 기어를 다시 확인해 기어 변경과 클릭이 겹치는 경우도 차단한다.
                        if (canOpenCollection(container.vehicleStateSource.gear.value, busy)) {
                            collectionRequested = true
                        }
                    })
            }
        }
    }
}
