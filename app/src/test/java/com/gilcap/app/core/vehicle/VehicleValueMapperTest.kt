package com.gilcap.app.core.vehicle

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import com.gilcap.app.core.model.Gear
import com.gilcap.app.core.model.WiperState
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleValueMapperTest {

    @Test fun `P, R, N, D 그대로 변환`() {
        assertEquals(Gear.P, VehicleValueMapper.gear(VehicleGear.GEAR_PARK))
        assertEquals(Gear.R, VehicleValueMapper.gear(VehicleGear.GEAR_REVERSE))
        assertEquals(Gear.N, VehicleValueMapper.gear(VehicleGear.GEAR_NEUTRAL))
        assertEquals(Gear.D, VehicleValueMapper.gear(VehicleGear.GEAR_DRIVE))
    }

    @Test fun `수동 단수는 모두 D로 판정`() {
        val manual = listOf(
            VehicleGear.GEAR_1, VehicleGear.GEAR_2, VehicleGear.GEAR_3,
            VehicleGear.GEAR_4, VehicleGear.GEAR_5, VehicleGear.GEAR_6,
            VehicleGear.GEAR_7, VehicleGear.GEAR_8, VehicleGear.GEAR_9,
        )
        manual.forEach { assertEquals(it.name, Gear.D, VehicleValueMapper.gear(it)) }
    }

    @Test fun `알 수 없는 기어는 UNKNOWN으로 판정`() {
        assertEquals(Gear.UNKNOWN, VehicleValueMapper.gear(VehicleGear.GEAR_UNKNOWN))
        assertEquals(Gear.UNKNOWN, VehicleValueMapper.gear(null))
    }

    @Test fun `와이퍼 AUTO는 AUTO로 변환`() {
        assertEquals(WiperState.AUTO, VehicleValueMapper.wiper(WiperSwitch.AUTO))
    }

    @Test fun `간헐과 연속 1단계는 LOW, 연속 2단계부터 HIGH로 판정`() {
        assertEquals(WiperState.LOW, VehicleValueMapper.wiper(WiperSwitch.MIST))
        assertEquals(WiperState.LOW, VehicleValueMapper.wiper(WiperSwitch.INTERMITTENT_LEVEL_3))
        assertEquals(WiperState.LOW, VehicleValueMapper.wiper(WiperSwitch.CONTINUOUS_LEVEL_1))
        assertEquals(WiperState.HIGH, VehicleValueMapper.wiper(WiperSwitch.CONTINUOUS_LEVEL_2))
        assertEquals(WiperState.HIGH, VehicleValueMapper.wiper(WiperSwitch.CONTINUOUS_LEVEL_5))
    }

    @Test fun `꺼짐, 정비 위치, 알 수 없음은 OFF로 판정해 비 캡슐 미개봉`() {
        assertEquals(WiperState.OFF, VehicleValueMapper.wiper(WiperSwitch.OFF))
        assertEquals(WiperState.OFF, VehicleValueMapper.wiper(WiperSwitch.SERVICE))
        assertEquals(WiperState.OFF, VehicleValueMapper.wiper(WiperSwitch.OTHER))
        assertEquals(WiperState.OFF, VehicleValueMapper.wiper(null))
    }

    @Test fun `SDK의 모든 값을 예외 없이 변환`() {
        VehicleGear.entries.forEach { VehicleValueMapper.gear(it) }
        WiperSwitch.entries.forEach { VehicleValueMapper.wiper(it) }
    }
}
