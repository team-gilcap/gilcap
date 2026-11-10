package com.gilcap.app.core.vehicle

import com.gilcap.app.core.model.Gear
import com.gilcap.app.core.model.WiperState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 가짜 차량 상태. 디버그 패널이나 테스트에서 set으로 바꾼다 */
class FakeVehicleStateSource : VehicleStateSource {
    private val _gear = MutableStateFlow(Gear.P)
    private val _wiper = MutableStateFlow(WiperState.OFF)

    override val gear: StateFlow<Gear> = _gear.asStateFlow()
    override val wiper: StateFlow<WiperState> = _wiper.asStateFlow()

    override fun start() {}
    override fun stop() {}

    fun setGear(gear: Gear) { _gear.value = gear }
    fun setWiper(wiper: WiperState) { _wiper.value = wiper }
}