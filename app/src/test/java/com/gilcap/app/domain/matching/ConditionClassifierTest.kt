package com.gilcap.app.domain.matching

import com.gilcap.app.core.model.TimeCondition
import com.gilcap.app.core.model.VehicleCondition
import com.gilcap.app.core.model.WiperState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ConditionClassifierTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private val classifier = ConditionClassifier(zoneId = seoul)

    private fun at(hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(2026, 11, 10, hour, minute, 0, 0, seoul).toInstant().toEpochMilli()

    @Test fun `06시는 낮`() = assertEquals(TimeCondition.DAY, classifier.timeOf(at(6)))
    @Test fun `18시59분은 낮`() = assertEquals(TimeCondition.DAY, classifier.timeOf(at(18, 59)))
    @Test fun `19시는 밤`() = assertEquals(TimeCondition.NIGHT, classifier.timeOf(at(19)))
    @Test fun `03시59분은 밤`() = assertEquals(TimeCondition.NIGHT, classifier.timeOf(at(3, 59)))
    @Test fun `04시는 새벽`() = assertEquals(TimeCondition.DAWN, classifier.timeOf(at(4)))
    @Test fun `05시59분은 새벽`() = assertEquals(TimeCondition.DAWN, classifier.timeOf(at(5, 59)))

    @Test fun `시연용 덮어쓰기가 있으면 실제 시각보다 우선`() {
        val demo = ConditionClassifier(zoneId = seoul, timeOverride = { TimeCondition.NIGHT })
        assertEquals(TimeCondition.NIGHT, demo.timeOf(at(12)))
    }

    @Test fun `와이퍼 단계별 차량 조건`() {
        assertEquals(VehicleCondition.CLEAR, classifier.vehicleOf(WiperState.OFF))
        assertEquals(VehicleCondition.RAIN, classifier.vehicleOf(WiperState.AUTO))
        assertEquals(VehicleCondition.RAIN, classifier.vehicleOf(WiperState.LOW))
        assertEquals(VehicleCondition.HEAVY_RAIN, classifier.vehicleOf(WiperState.HIGH))
    }
}