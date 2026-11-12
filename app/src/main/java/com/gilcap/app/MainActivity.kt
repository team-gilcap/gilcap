package com.gilcap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.gilcap.app.debug.DebugOverlay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as GilcapApplication).container
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        Text("길캡", fontSize = 48.sp, modifier = Modifier.align(Alignment.Center))

                        // 2. 차량 담당: 시연용 디버그 패널 (release 빌드에서는 빈 구현이 들어감)
                        DebugOverlay(container, Modifier.align(Alignment.TopEnd))
                    }
                }
            }
        }
    }
}
