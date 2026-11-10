package com.gilcap.app.core.vehicle

import com.gilcap.app.core.model.Gear
import com.gilcap.app.core.model.WiperState
import kotlinx.coroutines.flow.StateFlow

/**
 * F02 차량 상태 (② 구현: Pleos Vehicle SDK)
 * Virtual Console에서 값을 바꾸면 1초 안에 반영되어야 함.
 */
interface VehicleStateSource {
    val gear: StateFlow<Gear>
    val wiper: StateFlow<WiperState>

    fun start()   // SDK initialize + 구독 시작
    fun stop()    // 구독 해제 + SDK release
}