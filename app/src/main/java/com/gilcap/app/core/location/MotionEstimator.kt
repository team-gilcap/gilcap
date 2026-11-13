package com.gilcap.app.core.location

import com.gilcap.app.domain.matching.GeoMath
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * SDK 위치 값 → LocationFix. Pleos `LocationData`에는 속도가 없어서 직접 계산한다.
 *
 * - 속도: 직전 위치와의 거리 / 시간
 * - 진행 방향: 기준점에서 [minMoveMeters] 이상 움직이면 그 이동 방향(bearing)을 쓴다.
 *   덜 움직였으면(정차, 미세 흔들림) SDK가 준 heading → 그것도 없으면 직전 방향 유지.
 *   에뮬레이터 GPX 재생은 heading을 안 줄 수 있어서 이동 방향을 우선한다.
 * - 순간이동(GPX 처음으로 되감기 등, [maxSpeedKph] 초과)은 속도 0, 방향은 SDK 값으로 리셋한다.
 *
 * Android 의존 없음 → JUnit으로 테스트.
 */
class MotionEstimator(
    private val minMoveMeters: Double = 5.0,
    private val maxSpeedKph: Float = 250f,
) {
    private var last: Sample? = null
    private var anchor: Sample? = null
    private var lastHeading: Float = 0f

    private data class Sample(val lat: Double, val lng: Double, val time: Long)

    @Synchronized
    fun update(latitude: Double, longitude: Double, sdkHeading: Float?, timestamp: Long): LocationFix {
        val current = Sample(latitude, longitude, timestamp)
        val prev = last
        val sdk = sdkHeading?.takeIf { it.isFinite() }?.let(::normalize)

        var speedKph = 0f
        var teleported = false
        if (prev != null && timestamp > prev.time) {
            val meters = GeoMath.distanceMeters(prev.lat, prev.lng, latitude, longitude)
            val kph = (meters / ((timestamp - prev.time) / 1000.0) * 3.6).toFloat()
            if (kph > maxSpeedKph) teleported = true else speedKph = kph
        }

        val heading: Float
        if (teleported || anchor == null) {
            anchor = current
            heading = sdk ?: lastHeading
        } else {
            val a = anchor!!
            if (GeoMath.distanceMeters(a.lat, a.lng, latitude, longitude) >= minMoveMeters) {
                heading = bearingDegrees(a.lat, a.lng, latitude, longitude)
                anchor = current
            } else {
                heading = sdk ?: lastHeading
            }
        }

        last = current
        lastHeading = heading
        return LocationFix(latitude, longitude, heading, speedKph, timestamp)
    }

    @Synchronized
    fun reset() {
        last = null
        anchor = null
        lastHeading = 0f
    }

    companion object {
        /** a → b 방향 (0=북, 90=동, 0~360) */
        fun bearingDegrees(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val dl = Math.toRadians(lng2 - lng1)
            val y = sin(dl) * cos(p2)
            val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
            return normalize(Math.toDegrees(atan2(y, x)).toFloat())
        }

        private fun normalize(deg: Float): Float = ((deg % 360f) + 360f) % 360f
    }
}
