package com.gilcap.app.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gilcap.app.debug.DebugColors as C
import com.gilcap.app.di.AppContainer

/**
 * 디버그 빌드용 진입점: "DEBUG" 버튼과 디버그 패널
 * - release 빌드에는 같은 이름의 빈 구현(src/release)이 들어가도록 구성
 * - 패널 코드가 release APK에 아예 포함되지 않게 하기 위함
 */
@Composable
fun DebugOverlay(container: AppContainer, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
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
