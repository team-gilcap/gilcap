package com.gilcap.app.core.location

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionEstimatorTest {

    // 위도 0.0001° ≈ 11.1m
    private val lat = 37.7700
    private val lng = 128.9500

    @Test fun `북쪽으로 움직이면 방향 0도, 속도는 거리 나누기 시간`() {
        val e = MotionEstimator()
        e.update(lat, lng, sdkHeading = null, timestamp = 0)
        val fix = e.update(lat + 0.0001, lng, sdkHeading = null, timestamp = 1_000)

        assertEquals(0f, fix.headingDegrees, 1f)
        assertEquals(40f, fix.speedKph, 1f)   // 11.1m/s ≈ 40km/h
    }

    @Test fun `동쪽·서쪽 방향`() {
        assertEquals(90f, MotionEstimator.bearingDegrees(lat, lng, lat, lng + 0.001), 1f)
        assertEquals(270f, MotionEstimator.bearingDegrees(lat, lng, lat, lng - 0.001), 1f)
        assertEquals(180f, MotionEstimator.bearingDegrees(lat, lng, lat - 0.001, lng), 1f)
    }

    @Test fun `거의 안 움직이면 SDK 방향을 쓴다`() {
        val e = MotionEstimator()
        e.update(lat, lng, sdkHeading = 123f, timestamp = 0)
        val fix = e.update(lat + 0.00001, lng, sdkHeading = 130f, timestamp = 1_000)   // 1m

        assertEquals(130f, fix.headingDegrees, 0.01f)
    }

    @Test fun `SDK 방향이 없고 정차 중이면 직전 방향 유지`() {
        val e = MotionEstimator()
        e.update(lat, lng, null, 0)
        e.update(lat, lng + 0.0002, null, 1_000)               // 동쪽으로 이동
        val stopped = e.update(lat, lng + 0.0002, Float.NaN, 2_000)

        assertEquals(90f, stopped.headingDegrees, 1f)
        assertEquals(0f, stopped.speedKph, 0.01f)
    }

    @Test fun `순간이동(GPX 되감기)은 속도 0, 방향은 이동으로 계산하지 않는다`() {
        val e = MotionEstimator()
        e.update(lat, lng, null, 0)
        e.update(lat + 0.0001, lng, null, 1_000)              // 북쪽
        val jump = e.update(lat - 0.05, lng, null, 2_000)      // 5.5km 남쪽으로 1초 만에

        assertEquals(0f, jump.speedKph, 0.01f)
        assertEquals(0f, jump.headingDegrees, 1f)               // 남쪽(180)으로 판정하지 않음
    }

    @Test fun `350도와 10도 사이처럼 북쪽을 지나는 방향도 0~360으로 나온다`() {
        val heading = MotionEstimator.bearingDegrees(lat, lng, lat + 0.001, lng - 0.0002)
        assertEquals(true, heading in 340f..360f)
    }
}
