package com.gilcap.app

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.gilcap.app.core.location.PleosLocationSource
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

    /** 위치 권한을 받으면 위치 구독을 다시 시작한다 (③) */
    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) (application as GilcapApplication).container.locationSource.start()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(PleosLocationSource.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            locationPermission.launch(PleosLocationSource.PERMISSION)
        }
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
