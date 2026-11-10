package com.gilcap.app.core.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 가짜 위치. 기본값은 시연 지점(임시 좌표, ③이 실제 시연 지점으로 교체) */
class FakeLocationSource : LocationSource {
    private val _fix = MutableStateFlow<LocationFix?>(
        LocationFix(DEMO_LAT, DEMO_LNG, headingDegrees = 90f, speedKph = 40f, timestamp = System.currentTimeMillis())
    )
    override val fix: StateFlow<LocationFix?> = _fix.asStateFlow()

    override fun start() {}
    override fun stop() {}

    fun moveTo(latitude: Double, longitude: Double, headingDegrees: Float, speedKph: Float = 40f) {
        _fix.value = LocationFix(latitude, longitude, headingDegrees, speedKph, System.currentTimeMillis())
    }

    companion object {
        const val DEMO_LAT = 37.7700
        const val DEMO_LNG = 128.9500
    }
}