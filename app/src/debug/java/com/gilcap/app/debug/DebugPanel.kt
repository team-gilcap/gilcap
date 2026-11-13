package com.gilcap.app.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gilcap.app.core.location.PleosLocationSource
import com.gilcap.app.core.model.Capsule
import com.gilcap.app.core.model.DemoUsers
import com.gilcap.app.core.model.TimeCondition
import com.gilcap.app.core.vehicle.PleosVehicleStateSource
import com.gilcap.app.debug.DebugColors as C
import com.gilcap.app.di.AppContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 시연용 디버그 패널 (F02)
 * - 사용자 A/B 전환, 시간대 덮어쓰기, 전체 초기화, 현재 차량 상태와 판정 조건 표시
 * - 차량, 위치 SDK 연결 상태와 캡슐 발견 단계 표시
 * - DemoSettings를 바꾸면 생성, 발견 흐름이 바로 읽도록 구성 (별도 연결 불필요)
 * - 디버그 빌드 전용 폴더(src/debug)에만 두어 release APK에서 제외
 */
@Composable
fun DebugPanel(container: AppContainer, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val userId by container.demoSettings.currentUserId.collectAsState()
    val timeOverride by container.demoSettings.timeOverride.collectAsState()
    val snapshot by container.snapshotProvider.snapshot.collectAsState()
    val allCapsules by container.capsuleRepository.observeAll().collectAsState(initial = emptyList())
    val discovery by container.capsuleDiscoveryFlow.state.collectAsState()

    // 차량 상태는 스냅샷이 아니라 각 SDK 값을 직접 표시 (위치가 없어도 기어, 와이퍼 확인 가능)
    val gear by container.vehicleStateSource.gear.collectAsState()
    val wiper by container.vehicleStateSource.wiper.collectAsState()
    val fix by container.locationSource.fix.collectAsState()

    // 실제 SDK 구현일 때만 연결 상태 제공, Fake 사용 시 null
    val vehicleSourceStatus by remember<StateFlow<PleosVehicleStateSource.Status?>> {
        (container.vehicleStateSource as? PleosVehicleStateSource)?.status
            ?: MutableStateFlow<PleosVehicleStateSource.Status?>(null)
    }.collectAsState()
    val locationSourceStatus by remember<StateFlow<PleosLocationSource.Status?>> {
        (container.locationSource as? PleosLocationSource)?.status
            ?: MutableStateFlow<PleosLocationSource.Status?>(null)
    }.collectAsState()

    // 근처 캡슐은 위치나 캡슐 수가 바뀔 때만 다시 조회
    var nearby by remember { mutableStateOf(emptyList<Capsule>()) }
    LaunchedEffect(snapshot?.latitude, snapshot?.longitude, allCapsules.size) {
        val s = snapshot
        nearby = if (s == null) emptyList() else container.capsuleRepository.findNearby(s.latitude, s.longitude)
    }

    val status = DebugStatus.of(
        userId = userId,
        timeOverride = timeOverride,
        snapshot = snapshot,
        classifier = container.conditionClassifier,
        nearby = nearby,
        totalCapsules = allCapsules.size,
        vehicleSourceStatus = vehicleSourceStatus,
        locationSourceStatus = locationSourceStatus,
        discovery = discovery,
        gear = gear,
        wiper = wiper,
        fix = fix,
    )

    Row(modifier.fillMaxHeight()) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(C.border))
        Column(
            Modifier
                .width(560.dp)
                .fillMaxHeight()
                .background(C.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
            // 헤더
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("DEBUG", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = C.value, modifier = Modifier.weight(1f))
                TextButton("닫기", color = C.accent, onClick = onClose)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (status.allOk) C.ok else C.warn))
                Spacer(Modifier.width(8.dp))
                Text(
                    status.headerText,
                    fontSize = 18.sp,
                    color = C.label,
                )
            }

            // 1. 시연 설정
            GroupTitle("시연 설정")
            Text("사용자", fontSize = 20.sp, color = C.value)
            Spacer(Modifier.height(10.dp))
            Segmented(
                options = listOf("A  만드는 사람", "B  여는 사람"),
                selected = if (userId == DemoUsers.B) 1 else 0,
            ) { container.demoSettings.switchUser(if (it == 1) DemoUsers.B else DemoUsers.A) }
            Spacer(Modifier.height(20.dp))
            Text("시간대", fontSize = 20.sp, color = C.value)
            Spacer(Modifier.height(10.dp))
            val times = listOf(null, TimeCondition.DAY, TimeCondition.NIGHT, TimeCondition.DAWN)
            Segmented(
                options = listOf("실제", "낮", "밤", "새벽"),
                selected = times.indexOf(timeOverride).coerceAtLeast(0),
            ) { container.demoSettings.overrideTime(times[it]) }

            // 2. 지금 차 상태로 판정한 날씨와 시간대
            GroupTitle("지금 열리는 캡슐 조건")
            Item("날씨", status.weatherText)
            Item("시간대", status.timeText, valueColor = if (status.timeOverride != null) C.warn else C.value)
            Text(
                "이 날씨와 시간대에 만들어진 캡슐만 열림",
                fontSize = 18.sp,
                color = C.label,
                modifier = Modifier.padding(top = 10.dp),
            )

            // 3. SDK 연결 상태
            GroupTitle("연결 상태")
            Item("차량 SDK", status.vehicleSource, valueColor = if (status.vehicleSourceOk) C.value else C.warn)
            Item("위치 SDK", status.locationSource, valueColor = if (status.locationSourceOk) C.value else C.warn)

            // 4. 차량 상태
            GroupTitle("차량 상태")
            Item("기어", status.gear)
            Item("와이퍼", status.wiper)
            Item("속도", status.speed)
            Item("방향", status.heading)
            Item("위치", status.location)

            // 5. 캡슐
            GroupTitle("캡슐")
            Item("발견", status.discovery)
            Item("전체", "${status.totalCapsules}개")
            Item("근처", "${status.nearby.size}개")
            status.nearby.forEach {
                Text(it, fontSize = 18.sp, color = C.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }

            Spacer(Modifier.height(32.dp))
            ResetButton(container)
        }
    }
}

/** 실수로 지우지 않도록 3초 안에 두 번 눌러야 초기화하도록 구성 */
@Composable
private fun ResetButton(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3_000)
            armed = false
        }
    }
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(if (armed) C.danger else C.background)
            .border(1.dp, if (armed) C.danger else C.border, shape)
            .clickable {
                if (!armed) {
                    armed = true
                } else {
                    armed = false
                    scope.launch {
                        container.capsuleRepository.clear()
                        container.discoveryRepository.clear()
                        container.demoSettings.switchUser(DemoUsers.A)
                        container.demoSettings.overrideTime(null)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (armed) "한 번 더 누르면 초기화" else "전체 초기화  (캡슐, 도감, 설정)",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (armed) Color.White else C.danger,
        )
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = C.label,
        modifier = Modifier.padding(top = 36.dp, bottom = 12.dp),
    )
}

@Composable
private fun Item(label: String, value: String?, valueColor: Color = C.value) {
    Column {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 20.sp, color = C.value, modifier = Modifier.weight(1f))
            Text(
                value ?: "-",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.divider))
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(C.track)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val isSelected = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) C.accent else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 20.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) C.onAccent else C.value,
                )
            }
        }
    }
}

@Composable
internal fun TextButton(text: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}
