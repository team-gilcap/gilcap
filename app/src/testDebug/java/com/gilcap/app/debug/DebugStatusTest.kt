package com.gilcap.app.debug

import com.gilcap.app.core.location.LocationFix
import com.gilcap.app.core.location.PleosLocationSource
import com.gilcap.app.core.model.Capsule
import com.gilcap.app.core.model.DemoUsers
import com.gilcap.app.core.model.Gear
import com.gilcap.app.core.model.TimeCondition
import com.gilcap.app.core.model.VehicleCondition
import com.gilcap.app.core.model.VehicleSnapshot
import com.gilcap.app.core.model.WiperState
import com.gilcap.app.core.vehicle.PleosVehicleStateSource
import com.gilcap.app.domain.matching.ConditionClassifier
import com.gilcap.app.feature.capsulediscovery.DiscoveryUiState
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugStatusTest {

    private val noon = Instant.parse("2026-11-12T12:00:00Z").toEpochMilli()

    private fun snapshot(wiper: WiperState = WiperState.OFF, timestamp: Long = noon) = VehicleSnapshot(
        latitude = 37.77, longitude = 128.95, headingDegrees = 90f, speed = 40f,
        gear = Gear.D, wiper = wiper, timestamp = timestamp,
    )

    private fun capsule() = Capsule(
        id = "c1", authorId = DemoUsers.A, messageText = "안녕", latitude = 37.77, longitude = 128.95,
        headingDegrees = 90f, placeName = "해안도로",
        timeCondition = TimeCondition.NIGHT, vehicleCondition = VehicleCondition.RAIN, createdAt = 0L,
    )

    @Test fun `실제 시각과 와이퍼로 판정 조건 표시`() {
        val status = DebugStatus.of(
            DemoUsers.B, timeOverride = null, snapshot = snapshot(WiperState.LOW),
            classifier = ConditionClassifier(zoneId = ZoneOffset.UTC),
            nearby = emptyList(), totalCapsules = 0, zoneId = ZoneOffset.UTC,
        )
        assertEquals(TimeCondition.DAY, status.time)
        assertEquals(VehicleCondition.RAIN, status.vehicle)
        assertEquals("비  (와이퍼 LOW)", status.weatherText)
        assertEquals("낮  (실제 시각)", status.timeText)
        assertEquals("12:00:00", status.snapshotTime)
        assertEquals("D", status.gear)
    }

    @Test fun `시간대 덮어쓰기 시 덮어쓴 시간대로 판정하고 표시`() {
        val override = TimeCondition.NIGHT
        val status = DebugStatus.of(
            DemoUsers.A, timeOverride = override, snapshot = snapshot(),
            classifier = ConditionClassifier(zoneId = ZoneOffset.UTC, timeOverride = { override }),
            nearby = emptyList(), totalCapsules = 0, zoneId = ZoneOffset.UTC,
        )
        assertEquals(TimeCondition.NIGHT, status.time)
        assertEquals("맑음  (와이퍼 OFF)", status.weatherText)
        assertEquals("밤  (덮어쓰기)", status.timeText)
    }

    @Test fun `위치 미수신 시 차량과 조건 항목 비움`() {
        val status = DebugStatus.of(
            DemoUsers.A, timeOverride = null, snapshot = null,
            classifier = ConditionClassifier(), nearby = emptyList(), totalCapsules = 3,
        )
        assertNull(status.location)
        assertNull(status.time)
        assertNull(status.weatherText)
        assertNull(status.timeText)
        assertEquals(3, status.totalCapsules)
    }

    @Test fun `근처 캡슐을 도감 이름과 작성자로 표시`() {
        val status = DebugStatus.of(
            DemoUsers.B, timeOverride = null, snapshot = snapshot(),
            classifier = ConditionClassifier(zoneId = ZoneOffset.UTC),
            nearby = listOf(capsule()), totalCapsules = 1, zoneId = ZoneOffset.UTC,
        )
        assertEquals(listOf("비 오는 밤의 해안도로 (user-a)"), status.nearby)
    }

    @Test fun `위치 권한이 없으면 맨 위 줄에 원인 표시`() {
        val status = DebugStatus.of(
            DemoUsers.A, timeOverride = null, snapshot = null,
            classifier = ConditionClassifier(), nearby = emptyList(), totalCapsules = 0,
            vehicleSourceStatus = PleosVehicleStateSource.Status.RUNNING,
            locationSourceStatus = PleosLocationSource.Status.NO_PERMISSION,
        )
        assertEquals("수신 중", status.vehicleSource)
        assertEquals("권한 없음", status.locationSource)
        assertEquals("위치 미수신  (권한 없음)", status.headerText)
        assertFalse(status.locationSourceOk)
        assertFalse(status.allOk)
    }

    @Test fun `차량, 위치 모두 수신 중이면 정상 표시`() {
        val status = DebugStatus.of(
            DemoUsers.A, timeOverride = null, snapshot = snapshot(),
            classifier = ConditionClassifier(zoneId = ZoneOffset.UTC),
            nearby = emptyList(), totalCapsules = 0,
            vehicleSourceStatus = PleosVehicleStateSource.Status.RUNNING,
            locationSourceStatus = PleosLocationSource.Status.RUNNING,
            zoneId = ZoneOffset.UTC,
        )
        assertEquals("차량 상태 수신 중  12:00:00", status.headerText)
        assertTrue(status.allOk)
    }

    @Test fun `Fake 구현 사용 시 연결 상태를 Fake로 표시`() {
        val status = DebugStatus.of(
            DemoUsers.A, timeOverride = null, snapshot = snapshot(),
            classifier = ConditionClassifier(zoneId = ZoneOffset.UTC),
            nearby = emptyList(), totalCapsules = 0, zoneId = ZoneOffset.UTC,
        )
        assertEquals("Fake", status.vehicleSource)
        assertEquals("Fake", status.locationSource)
        assertTrue(status.allOk)
    }

    @Test fun `발견 단계와 캡슐 이름 표시`() {
        val capsule = capsule()
        assertEquals("대기 중", DebugStatus.discoveryName(DiscoveryUiState.Idle))
        assertEquals("발견  (비 오는 밤의 해안도로)", DebugStatus.discoveryName(DiscoveryUiState.Discovered(capsule)))
        assertEquals("재생 중  (비 오는 밤의 해안도로)", DebugStatus.discoveryName(DiscoveryUiState.Playing(capsule)))
        assertEquals("도감 저장  (비 오는 밤의 해안도로)", DebugStatus.discoveryName(DiscoveryUiState.Collected(capsule)))
    }

    @Test fun `모든 SDK 상태 값을 빠짐없이 표시`() {
        PleosVehicleStateSource.Status.entries.forEach { assertTrue(DebugStatus.vehicleSourceName(it).isNotBlank()) }
        PleosLocationSource.Status.entries.forEach { assertTrue(DebugStatus.locationSourceName(it).isNotBlank()) }
    }

    @Test fun `위치가 없어도 차량 SDK 값은 표시`() {
        val status = DebugStatus.of(
            DemoUsers.A, timeOverride = null, snapshot = null,
            classifier = ConditionClassifier(), nearby = emptyList(), totalCapsules = 0,
            vehicleSourceStatus = PleosVehicleStateSource.Status.RUNNING,
            locationSourceStatus = PleosLocationSource.Status.NO_PERMISSION,
            gear = Gear.P, wiper = WiperState.LOW,
        )
        assertEquals("P", status.gear)
        assertEquals("LOW", status.wiper)
        assertNull(status.location)
        assertNull(status.weatherText)
    }

    @Test fun `좌표는 소수 3자리까지만 표시`() {
        val fix = LocationFix(latitude = 37.776543, longitude = 128.951234, headingDegrees = 45f, speedKph = 30f, timestamp = noon)
        val status = DebugStatus.of(
            DemoUsers.A, timeOverride = null, snapshot = snapshot(),
            classifier = ConditionClassifier(zoneId = ZoneOffset.UTC),
            nearby = emptyList(), totalCapsules = 0, fix = fix, zoneId = ZoneOffset.UTC,
        )
        assertEquals("37.777, 128.951", status.location)
        assertEquals("45°", status.heading)
        assertEquals("30 km/h", status.speed)
    }

    @Test fun `날씨 근거는 판정에 쓴 스냅샷 와이퍼로 표시`() {
        val status = DebugStatus.of(
            DemoUsers.A, timeOverride = null, snapshot = snapshot(WiperState.LOW),
            classifier = ConditionClassifier(zoneId = ZoneOffset.UTC),
            nearby = emptyList(), totalCapsules = 0,
            wiper = WiperState.HIGH, zoneId = ZoneOffset.UTC,
        )
        assertEquals("비  (와이퍼 LOW)", status.weatherText)
        assertEquals("HIGH", status.wiper)
    }
}
