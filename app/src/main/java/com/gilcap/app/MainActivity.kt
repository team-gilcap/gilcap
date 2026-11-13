package com.gilcap.app

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.gilcap.app.ui.GilcapApp
import androidx.activity.result.contract.ActivityResultContracts
import com.gilcap.app.core.location.PleosLocationSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.gilcap.app.debug.DebugOverlay


class MainActivity : ComponentActivity() {

    /** 위치 권한을 받으면 위치 구독을 다시 시작한다 (③) */
    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) (application as GilcapApplication).container.locationSource.start()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as GilcapApplication).container

        if (
            checkSelfPermission(PleosLocationSource.PERMISSION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            locationPermission.launch(PleosLocationSource.PERMISSION)
        }

        setContent {
            Box(Modifier.fillMaxSize()) {
                // 주행 화면과 도감
                GilcapApp(container)

                // debug 빌드에서만 표시되는 패널
                DebugOverlay(
                    container = container,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .safeDrawingPadding(),
                )
            }
        }
    }
}
