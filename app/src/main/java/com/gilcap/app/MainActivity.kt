package com.gilcap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.gilcap.app.ui.GilcapApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as GilcapApplication).container
        setContent {
            // 기존 F05 감시는 화면 생명주기에 맞춰 유지한다.
            LaunchedEffect(container.discoveryFlow) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    container.discoveryFlow.run()
                }
            }
            // 생성 담당자가 제공할 상태와 시작 콜백은 GilcapApp의 매개변수로 연결한다.
            GilcapApp(container)
        }
    }
}
