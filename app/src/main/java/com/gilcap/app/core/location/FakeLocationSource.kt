package com.gilcap.app.core.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 가짜 위치. 기본값은 시연 캡슐 지점에서 정방향으로 달리는 중 ([DemoRoute]) */
class FakeLocationSource : LocationSource {
    private val _fix = MutableStateFlow<LocationFix?>(
        LocationFix(DEMO_LAT, DEMO_LNG, headingDegrees = DEMO_HEADING, speedKph = 40f, timestamp = System.currentTimeMillis())
    )
    override val fix: StateFlow<LocationFix?> = _fix.asStateFlow()

    override fun start() {}
    override fun stop() {}

    fun moveTo(latitude: Double, longitude: Double, headingDegrees: Float, speedKph: Float = 40f) {
        _fix.value = LocationFix(latitude, longitude, headingDegrees, speedKph, System.currentTimeMillis())
    }

    companion object {
        const val DEMO_LAT = DemoRoute.CAPSULE_LAT
        const val DEMO_LNG = DemoRoute.CAPSULE_LNG
        const val DEMO_HEADING = DemoRoute.FORWARD_HEADING
    }
}
