package com.gilcap.app.debug

import com.gilcap.app.core.model.Capsule
import com.gilcap.app.core.model.DemoUsers
import com.gilcap.app.core.model.Gear
import com.gilcap.app.core.model.TimeCondition
import com.gilcap.app.core.model.VehicleCondition
import com.gilcap.app.core.model.VehicleSnapshot
import com.gilcap.app.core.model.WiperState
import com.gilcap.app.domain.matching.ConditionClassifier
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
