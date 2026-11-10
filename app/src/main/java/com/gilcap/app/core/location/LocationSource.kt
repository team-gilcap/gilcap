package com.gilcap.app.core.location

import kotlinx.coroutines.flow.StateFlow

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val headingDegrees: Float,   // 진행 방향 (0=북, 90=동)
    val speedKph: Float,
    val timestamp: Long,
)

/**
 * F02 위치·방향·속도

 */
interface LocationSource {
    val fix: StateFlow<LocationFix?>

    fun start()
    fun stop()
}