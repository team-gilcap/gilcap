package com.gilcap.app.debug

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gilcap.app.core.model.Gear
import com.gilcap.app.debug.DebugColors as C
import com.gilcap.app.di.AppContainer

/**
 * 시연 패널 진입점: "DEBUG" 버튼과 디버그 패널
 * - 심사, 시연에서 사용자 A/B 전환과 시간대, 차량 상태를 release APK로도 확인할 수 있게 하기 위함
 * - release 빌드는 P단에서만 버튼을 표시하고, P단을 벗어나면 패널을 닫아 주행 중 조작을 막기 위함
 * - debug 빌드는 개발 확인을 위해 기어와 관계없이 표시
 */
@Composable
fun DebugOverlay(container: AppContainer, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val debuggable = remember(context) {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    val gear by container.vehicleStateSource.gear.collectAsState()
    val available = debugPanelAvailable(debuggable, gear)

    var open by remember { mutableStateOf(false) }
    LaunchedEffect(available) {
        if (!available) open = false
    }
    if (!available) return

    Box(modifier) {
        if (open) {
            DebugPanel(container, onClose = { open = false })
        } else {
            val shape = RoundedCornerShape(8.dp)
            Box(
                Modifier
                    .padding(20.dp)
                    .height(56.dp)
                    .clip(shape)
                    .background(C.background)
                    .border(1.dp, C.border, shape)
                    .clickable { open = true }
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("DEBUG", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = C.accent)
            }
        }
    }
}

/** debug 빌드는 항상, release 빌드는 P단에서만 패널 사용 가능 */
internal fun debugPanelAvailable(debuggable: Boolean, gear: Gear): Boolean = debuggable || gear == Gear.P
