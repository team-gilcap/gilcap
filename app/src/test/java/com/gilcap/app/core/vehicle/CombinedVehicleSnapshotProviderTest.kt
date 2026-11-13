package com.gilcap.app.core.vehicle

import com.gilcap.app.core.location.FakeLocationSource
import com.gilcap.app.core.model.TimeCondition
import com.gilcap.app.core.model.WiperState
import com.gilcap.app.domain.matching.ConditionClassifier
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CombinedVehicleSnapshotProviderTest {

    /**
     * stateIn이 바로 반응하도록 즉시 실행 디스패처 사용
     * 1초 갱신이 테스트 종료 후에도 계속 돌지 않도록 backgroundScope에 묶어서 구성
     */
    private fun TestScope.immediateScope() =
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))

    @Test fun `와이퍼를 켜면 스냅샷에 바로 반영된다`() = runTest {
        val vehicle = FakeVehicleStateSource()
        val scoped = CombinedVehicleSnapshotProvider(vehicle, FakeLocationSource(), immediateScope())

        vehicle.setWiper(WiperState.LOW)

        assertEquals(WiperState.LOW, scoped.snapshot.value?.wiper)
    }

    @Test fun `값 변화 없이도 1초마다 시각 갱신`() = runTest {
        val provider = CombinedVehicleSnapshotProvider(
            FakeVehicleStateSource(), FakeLocationSource(), immediateScope(),
            now = { testScheduler.currentTime },
        )
        assertEquals(0L, provider.snapshot.value?.timestamp)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1_000L, provider.snapshot.value?.timestamp)

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(6_000L, provider.snapshot.value?.timestamp)
    }

    @Test fun `정차 상태로 19시 경과 시 밤으로 판정`() = runTest {
        val start = Instant.parse("2026-11-12T18:59:30Z").toEpochMilli()
        val provider = CombinedVehicleSnapshotProvider(
            FakeVehicleStateSource(), FakeLocationSource(), immediateScope(),
            now = { start + testScheduler.currentTime },
        )
        val classifier = ConditionClassifier(zoneId = ZoneOffset.UTC)
        assertEquals(TimeCondition.DAY, classifier.timeOf(provider.snapshot.value!!.timestamp))

        // 위치, 기어, 와이퍼는 그대로 두고 시간만 1분 경과
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(TimeCondition.NIGHT, classifier.timeOf(provider.snapshot.value!!.timestamp))
    }
}
