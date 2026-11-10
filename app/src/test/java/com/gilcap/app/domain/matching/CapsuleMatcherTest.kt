package com.gilcap.app.domain.matching

import com.gilcap.app.core.model.Capsule
import com.gilcap.app.core.model.Gear
import com.gilcap.app.core.model.MatchedCondition
import com.gilcap.app.core.model.TimeCondition
import com.gilcap.app.core.model.VehicleCondition
import com.gilcap.app.core.model.VehicleSnapshot
import com.gilcap.app.core.model.WiperState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class CapsuleMatcherTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private val matcher = CapsuleMatcher(ConditionClassifier(zoneId = seoul))

    private val night = ZonedDateTime.of(2026, 11, 10, 21, 0, 0, 0, seoul).toInstant().toEpochMilli()
    private val noon = ZonedDateTime.of(2026, 11, 10, 12, 0, 0, 0, seoul).toInstant().toEpochMilli()

    /** 기준 캡슐: 비 오는 밤, 동쪽(90°) 방향, 반경 150m */
    private val rainyNightCapsule = Capsule(
        id = "c1",
        authorId = "user-a",
        messageText = "비 오는 날 이 길 정말 예뻐요",
        latitude = 37.7700,
        longitude = 128.9500,
        headingDegrees = 90f,
        placeName = "해안도로",
        timeCondition = TimeCondition.NIGHT,
        vehicleCondition = VehicleCondition.RAIN,
        createdAt = 0L,
    )

    /** 기준 스냅샷: 캡슐과 같은 지점, 같은 방향, 밤, 와이퍼 LOW → 4개 다 맞음 */
    private fun snapshot(
        latitude: Double = 37.7700,
        longitude: Double = 128.9500,
        heading: Float = 90f,
        wiper: WiperState = WiperState.LOW,
        timestamp: Long = night,
    ) = VehicleSnapshot(latitude, longitude, heading, 60f, Gear.D, wiper, timestamp)

    @Test fun `4개 조건이 모두 맞으면 열린다`() {
        val result = matcher.match(rainyNightCapsule, snapshot())
        assertTrue(result.isMatched)
        assertEquals(MatchedCondition.entries.toSet(), result.matchedConditions)
    }

    @Test fun `반경 밖이면 안 열린다`() {
        // 위도 0.002도 ≈ 222m 북쪽
        val result = matcher.match(rainyNightCapsule, snapshot(latitude = 37.7720))
        assertFalse(result.isMatched)
        assertFalse(MatchedCondition.LOCATION in result.matchedConditions)
    }

    @Test fun `반경 안쪽 경계 근처는 열린다`() {
        // 위도 0.001도 ≈ 111m
        assertTrue(matcher.match(rainyNightCapsule, snapshot(latitude = 37.7710)).isMatched)
    }

    @Test fun `반대 방향이면 안 열린다`() {
        assertFalse(matcher.match(rainyNightCapsule, snapshot(heading = 270f)).isMatched)
    }

    @Test fun `45도 차이까지는 같은 방향`() {
        assertTrue(matcher.match(rainyNightCapsule, snapshot(heading = 135f)).isMatched)
        assertFalse(matcher.match(rainyNightCapsule, snapshot(heading = 136f)).isMatched)
    }

    @Test fun `북쪽을 사이에 둔 350도와 10도는 같은 방향`() {
        val northCapsule = rainyNightCapsule.copy(headingDegrees = 350f)
        assertTrue(matcher.match(northCapsule, snapshot(heading = 10f)).isMatched)
    }

    @Test fun `맑은 밤이면 안 열린다`() {
        val result = matcher.match(rainyNightCapsule, snapshot(wiper = WiperState.OFF))
        assertFalse(result.isMatched)
        assertFalse(MatchedCondition.VEHICLE in result.matchedConditions)
    }

    @Test fun `비 오는 낮이면 안 열린다`() {
        val result = matcher.match(rainyNightCapsule, snapshot(timestamp = noon))
        assertFalse(result.isMatched)
        assertFalse(MatchedCondition.TIME in result.matchedConditions)
    }

    @Test fun `폭우는 비 캡슐을 열지 않는다`() {
        assertFalse(matcher.match(rainyNightCapsule, snapshot(wiper = WiperState.HIGH)).isMatched)
    }
}