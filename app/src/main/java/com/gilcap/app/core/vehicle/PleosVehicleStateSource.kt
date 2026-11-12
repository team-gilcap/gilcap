package com.gilcap.app.core.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.gilcap.app.core.model.Gear
import com.gilcap.app.core.model.WiperState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ai.pleos.playground.vehicle.constant.control.WiperState as SdkWiperState

/**
 * F02 기어·와이퍼 (Pleos Vehicle SDK)
 *
 * - 기어: registerGearSelection 콜백으로 받도록 구성
 * - 와이퍼: SDK 콜백(registerWiperState)은 켜짐/꺼짐만 제공하고 단계(AUTO, LOW, HIGH)는 미제공
 *   - 단계는 getCurrentWiperSwitch로 읽도록 구성 (콜백 수신 시 즉시 조회)
 *   - 켜진 채 단계만 바뀌는 경우(LOW → HIGH)를 놓치지 않기 위해 [wiperPollMs]마다 추가 조회
 * - 권한(CAR_DRIVE: 기어, CAR_WINDOWS: 와이퍼)이 없는 항목은 구독하지 않고 [status]에 기록
 *   - 기본값(기어 UNKNOWN, 와이퍼 OFF) 유지로 캡슐이 잘못 열리지 않게 하기 위함
 * - SDK 콜백 스레드와 0.5초 조회 루프가 동시에 접근하므로 공유 상태는 @Volatile로 구성
 * - stop() 이후 늦게 도착한 콜백이나 SDK 예외가 앱을 종료시키지 않도록 조회를 예외 처리로 감쌈
 * - 디버그 로그는 디버그 빌드에서만 기록 (release APK에 차량 상태 로그 미기록)
 */
class PleosVehicleStateSource(
    context: Context,
    private val wiperPollMs: Long = 500L,
) : VehicleStateSource {

    enum class Status { STOPPED, RUNNING, NO_PERMISSION, ERROR }

    private val appContext = context.applicationContext
    private val debugLog = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    @Volatile private var vehicle: Vehicle? = null
    @Volatile private var scope: CoroutineScope? = null
    @Volatile private var wiperErrorLogged = false

    private val _gear = MutableStateFlow(Gear.UNKNOWN)
    override val gear: StateFlow<Gear> = _gear.asStateFlow()

    private val _wiper = MutableStateFlow(WiperState.OFF)
    override val wiper: StateFlow<WiperState> = _wiper.asStateFlow()

    private val _status = MutableStateFlow(Status.STOPPED)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = onGear(gear)
        override fun onFailed(e: Exception) = onError("기어 구독 실패", e)
    }

    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: SdkWiperState?) = readWiperSwitch()
        override fun onFailed(e: Exception) = onError("와이퍼 구독 실패", e)
    }

    @Synchronized
    override fun start() {
        if (vehicle != null) return

        val canReadGear = granted(PERMISSION_GEAR)
        val canReadWiper = granted(PERMISSION_WIPER)
        if (!canReadGear) log("권한 없음: $PERMISSION_GEAR")
        if (!canReadWiper) log("권한 없음: $PERMISSION_WIPER")
        if (!canReadGear && !canReadWiper) {
            _status.value = Status.NO_PERMISSION
            return
        }

        try {
            val sdk = Vehicle(appContext)
            sdk.initialize()
            vehicle = sdk
            val loop = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            scope = loop

            if (canReadGear) {
                sdk.getGear().getCurrentGearSelectionState(::onGear) { onError("기어 조회 실패", it) }
                sdk.getGear().registerGearSelection(gearListener)
            }
            if (canReadWiper) {
                readWiperSwitch()
                sdk.getWiper().registerWiperState(FRONT, wiperListener)
                loop.launch {
                    while (isActive) {
                        delay(wiperPollMs)
                        readWiperSwitch()
                    }
                }
            }

            _status.value = if (canReadGear && canReadWiper) Status.RUNNING else Status.NO_PERMISSION
            log("차량 구독 시작 (기어 $canReadGear, 와이퍼 $canReadWiper)")
        } catch (e: Exception) {
            onError("Vehicle SDK 시작 실패", e)
            stop()
            _status.value = Status.ERROR
        }
    }

    @Synchronized
    override fun stop() {
        scope?.cancel()
        scope = null
        vehicle?.let { sdk ->
            runCatching { sdk.getGear().unregisterGearSelection(gearListener) }
            runCatching { sdk.getWiper().unregisterWiperState(FRONT, wiperListener) }
            runCatching { sdk.release() }
        }
        vehicle = null
        if (_status.value != Status.ERROR) _status.value = Status.STOPPED
    }

    private fun readWiperSwitch() {
        val sdk = vehicle ?: return
        try {
            sdk.getWiper().getCurrentWiperSwitch(
                FRONT,
                { switch: WiperSwitch? -> if (vehicle != null) onWiperSwitch(switch) },
                ::onWiperReadFailed,
            )
        } catch (e: Exception) {
            // release() 직후 호출 등 SDK가 바로 예외를 던지는 경우 대비 (조회 루프의 앱 종료 방지)
            onWiperReadFailed(e)
        }
    }

    private fun onWiperReadFailed(e: Exception) {
        // 0.5초마다 조회하므로 같은 오류의 반복 로그 방지
        if (!wiperErrorLogged) onError("와이퍼 단계 조회 실패", e)
        wiperErrorLogged = true
    }

    private fun onGear(sdk: VehicleGear?) {
        val mapped = VehicleValueMapper.gear(sdk)
        if (_gear.value != mapped) log("기어 $sdk → $mapped")
        _gear.value = mapped
    }

    private fun onWiperSwitch(sdk: WiperSwitch?) {
        wiperErrorLogged = false
        val mapped = VehicleValueMapper.wiper(sdk)
        if (_wiper.value != mapped) log("와이퍼 $sdk → $mapped")
        _wiper.value = mapped
    }

    private fun onError(message: String, e: Exception) {
        // 오류는 release에서도 기록하되, 상세 스택은 디버그 빌드에서만 기록
        if (debugLog) Log.e(TAG, "$message: ${e.message}", e) else Log.e(TAG, message)
    }

    private fun granted(permission: String) =
        appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    // 차량 상태(기어, 와이퍼)만 기록, 위치 같은 개인정보는 미기록. 디버그 빌드에서만 기록
    private fun log(message: String) {
        if (debugLog) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "GILCAP/Vehicle"
        const val PERMISSION_GEAR = "pleos.car.permission.CAR_DRIVE"
        const val PERMISSION_WIPER = "pleos.car.permission.CAR_WINDOWS"
        val FRONT = WindowArea(WindowValue.FRONT_WINDSHIELD)
    }
}
