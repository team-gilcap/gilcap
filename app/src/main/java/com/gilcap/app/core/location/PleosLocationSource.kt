package com.gilcap.app.core.location

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * F02 위치·방향·속도 (Pleos FusedLocation SDK)
 *
 * - start(): initialize → 마지막 위치로 바로 채우고 → 콜백 구독
 * - SDK는 속도를 안 주므로 [MotionEstimator]가 위치 변화로 계산한다
 * - 권한 `pleos.car.permission.FUSED_LOCATION`이 없으면 시작하지 않고 [status]에 남긴다.
 *   권한을 받은 뒤 start()를 다시 부르면 된다.
 */
class PleosLocationSource(
    context: Context,
    private val estimator: MotionEstimator = MotionEstimator(),
) : LocationSource {

    enum class Status { STOPPED, RUNNING, NO_PERMISSION, UNSUPPORTED, ERROR }

    private val appContext = context.applicationContext
    private val debugLog = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    private var fusedLocation: FusedLocation? = null

    private val _fix = MutableStateFlow<LocationFix?>(null)
    override val fix: StateFlow<LocationFix?> = _fix.asStateFlow()

    private val _status = MutableStateFlow(Status.STOPPED)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            location?.let(::onLocation)
        }

        override fun onError(exception: Exception) {
            log("위치 오류: ${exception.message}")
            _status.value = Status.ERROR
        }
    }

    @Synchronized
    override fun start() {
        if (fusedLocation != null) return
        if (appContext.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            log("권한 없음: $PERMISSION")
            _status.value = Status.NO_PERMISSION
            return
        }
        try {
            val sdk = FusedLocation(appContext)
            sdk.initialize()
            if (!sdk.isSupported()) {
                log("FusedLocation 미지원 기기")
                sdk.release()
                _status.value = Status.UNSUPPORTED
                return
            }
            estimator.reset()
            sdk.getLastKnownLocation(listener)
            sdk.registerFusedLocationCallback(listener)
            fusedLocation = sdk
            _status.value = Status.RUNNING
            log("위치 구독 시작")
        } catch (e: Exception) {
            log("위치 시작 실패: ${e.message}")
            _status.value = Status.ERROR
        }
    }

    @Synchronized
    override fun stop() {
        val sdk = fusedLocation ?: return
        runCatching { sdk.unregisterFusedLocationCallback(listener) }
        runCatching { sdk.release() }
        fusedLocation = null
        _status.value = Status.STOPPED
        log("위치 구독 해제")
    }

    private fun onLocation(data: LocationData) {
        val fix = estimator.update(
            latitude = data.latitude,
            longitude = data.longitude,
            sdkHeading = data.heading,
            timestamp = data.timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
        _fix.value = fix
        // 에뮬레이터 확인용: 방향은 계산값(SDK 원본값), 속도는 위치 변화로 계산한 값
        // 좌표는 debug 빌드에서만 남긴다
        if (debugLog) log(
            "위치 %.6f, %.6f | 방향 %.1f° (SDK %.1f°) | 속도 %.1fkm/h".format(
                fix.latitude, fix.longitude, fix.headingDegrees, data.heading, fix.speedKph,
            )
        )
        if (_status.value != Status.RUNNING) _status.value = Status.RUNNING
    }

    private fun log(msg: String) = println("GILCAP [Location] $msg")

    companion object {
        const val PERMISSION = "pleos.car.permission.FUSED_LOCATION"
    }
}
