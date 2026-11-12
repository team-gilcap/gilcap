package com.gilcap.app.core.vehicle

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import com.gilcap.app.core.model.Gear
import com.gilcap.app.core.model.WiperState

/**
 * Pleos Vehicle SDK 값 → 앱 모델 값
 * - SDK 의존을 이 파일에만 모아 값 변환을 JVM 단위 테스트로 확인하기 위함
 */
internal object VehicleValueMapper {

    /** 수동 단수(GEAR_1~9)는 전진 중이므로 D로 판정 */
    fun gear(sdk: VehicleGear?): Gear = when (sdk) {
        VehicleGear.GEAR_PARK -> Gear.P
        VehicleGear.GEAR_REVERSE -> Gear.R
        VehicleGear.GEAR_NEUTRAL -> Gear.N
        VehicleGear.GEAR_DRIVE,
        VehicleGear.GEAR_1, VehicleGear.GEAR_2, VehicleGear.GEAR_3,
        VehicleGear.GEAR_4, VehicleGear.GEAR_5, VehicleGear.GEAR_6,
        VehicleGear.GEAR_7, VehicleGear.GEAR_8, VehicleGear.GEAR_9 -> Gear.D
        VehicleGear.GEAR_UNKNOWN, null -> Gear.UNKNOWN
    }

    /**
     * 와이퍼 스위치 위치 → 앱의 4단계
     * - 연속 1단계까지는 비(LOW), 연속 2단계부터는 폭우(HIGH)로 판정
     * - 정비 위치(SERVICE), 알 수 없음(OTHER)은 비 신호가 아니므로 OFF로 판정
     */
    fun wiper(sdk: WiperSwitch?): WiperState = when (sdk) {
        WiperSwitch.AUTO -> WiperState.AUTO
        WiperSwitch.MIST,
        WiperSwitch.INTERMITTENT_LEVEL_1, WiperSwitch.INTERMITTENT_LEVEL_2,
        WiperSwitch.INTERMITTENT_LEVEL_3, WiperSwitch.INTERMITTENT_LEVEL_4,
        WiperSwitch.INTERMITTENT_LEVEL_5,
        WiperSwitch.CONTINUOUS_LEVEL_1 -> WiperState.LOW
        WiperSwitch.CONTINUOUS_LEVEL_2, WiperSwitch.CONTINUOUS_LEVEL_3,
        WiperSwitch.CONTINUOUS_LEVEL_4, WiperSwitch.CONTINUOUS_LEVEL_5 -> WiperState.HIGH
        WiperSwitch.OFF, WiperSwitch.SERVICE, WiperSwitch.OTHER, null -> WiperState.OFF
    }
}
