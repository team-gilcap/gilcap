package com.gilcap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.gilcap.app.ui.GilcapApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as GilcapApplication).container
        setContent {
            // 생성 담당자가 제공할 상태와 시작 콜백은 GilcapApp의 매개변수로 연결한다.
            GilcapApp(container)
        }
    }
}
