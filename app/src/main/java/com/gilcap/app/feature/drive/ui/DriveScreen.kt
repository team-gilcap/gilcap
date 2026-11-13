package com.gilcap.app.feature.drive.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gilcap.app.core.model.Gear
import com.gilcap.app.feature.capsulecreate.CreateUiState
import com.gilcap.app.feature.capsulediscovery.DiscoveryUiState

@Composable
fun DriveScreen(
    createState: CreateUiState,
    discoveryState: DiscoveryUiState,
    gear: Gear,
    canCreate: Boolean,
    onCreateCapsule: () -> Unit,
    onOpenCollection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = drivePresentation(createState, discoveryState)
    Column(
        modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("길 위의 캡슐", style = MaterialTheme.typography.titleLarge)
            Text("기어 ${if (gear == Gear.UNKNOWN) "—" else gear.name}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 720.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CapsuleMessage(presentation, Modifier.weight(1f))
                    DriveActions(presentation, gear, canCreate, onCreateCapsule, onOpenCollection,
                        Modifier.width(280.dp))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    CapsuleMessage(presentation, Modifier.fillMaxWidth())
                    DriveActions(presentation, gear, canCreate, onCreateCapsule, onOpenCollection,
                        Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun CapsuleMessage(presentation: DrivePresentation, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.heightIn(min = 220.dp).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) {
            Text(presentation.status, color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge)
            // 긴 발화도 생략하지 않고 읽을 수 있도록 화면 전체를 스크롤 가능하게 구성한다.
            Text(presentation.subtitle, style = MaterialTheme.typography.headlineLarge)
        }
    }
}

@Composable
private fun DriveActions(
    presentation: DrivePresentation,
    gear: Gear,
    canCreate: Boolean,
    onCreateCapsule: () -> Unit,
    onOpenCollection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = onCreateCapsule, enabled = canCreate && !presentation.isBusy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)) {
            Text("캡슐 남기기")
        }
        // P단에서만 도감 조작을 노출해 주행 중에는 시작 버튼 하나만 남긴다.
        if (gear == Gear.P) {
            OutlinedButton(onClick = onOpenCollection, enabled = !presentation.isBusy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Text("나의 도감")
            }
        }
    }
}
