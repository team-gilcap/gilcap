package com.gilcap.app.feature.collection.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gilcap.app.feature.collection.CollectionItem
import com.gilcap.app.feature.collection.CollectionUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val collectionDateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")

@Composable
fun CollectionScreen(state: CollectionUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("나의 도감", style = MaterialTheme.typography.headlineLarge)
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)) {
                Text("주행 화면")
            }
        }
        when (state) {
            is CollectionUiState.Loading -> Text("도감을 불러오고 있어요")
            is CollectionUiState.Error -> Text("도감을 불러오지 못했어요. 잠시 후 다시 열어주세요.")
            is CollectionUiState.Content -> {
                Text("수집한 이야기 ${state.items.size}개 · P단에서만 볼 수 있어요",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.items.isEmpty()) {
                    Text("아직 만난 캡슐이 없어요", style = MaterialTheme.typography.headlineSmall)
                    Text("길 위에서 캡슐을 만나면 여기에 모아드릴게요.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(state.items, key = { it.discovery.capsuleId }) { item -> CollectionCard(item) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionCard(item: CollectionItem) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(item.capsule?.dexName ?: "캡슐 정보를 찾을 수 없어요",
                style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(item.capsule?.messageText ?: "수집 기록은 보관되어 있어요.",
                style = MaterialTheme.typography.bodyLarge)
            val date = Instant.ofEpochMilli(item.discovery.discoveredAt)
                .atZone(ZoneId.systemDefault()).format(collectionDateFormat)
            Text("$date 수집", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
