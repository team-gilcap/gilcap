package com.gilcap.app.core.model

data class VehicleSnapshot(
    val latitude: Double,
    val longitude: Double,
    val headingDegrees: Float,   // 진행 방향 (0=북, 90=동, 180=남, 270=서)
    val speed: Float,            // km/h
    val gear: Gear,
    val wiper: WiperState,
    val timestamp: Long,         // epoch millis
)