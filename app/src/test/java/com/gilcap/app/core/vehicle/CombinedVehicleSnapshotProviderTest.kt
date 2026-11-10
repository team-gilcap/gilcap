package com.gilcap.app.core.vehicle

import com.gilcap.app.core.location.FakeLocationSource
import com.gilcap.app.core.model.WiperState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CombinedVehicleSnapshotProviderTest {

    @Test fun `와이퍼를 켜면 스냅샷에 바로 반영된다`() = runTest {
        val vehicle = FakeVehicleStateSource()
        val provider = CombinedVehicleSnapshotProvider(
            vehicle, FakeLocationSource(), backgroundScope.apply { },
        )
        // stateIn이 바로 반응하도록 즉시 실행 디스패처 사용
        val scoped = CombinedVehicleSnapshotProvider(
            vehicle, FakeLocationSource(),
            kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )

        vehicle.setWiper(WiperState.LOW)

        assertEquals(WiperState.LOW, scoped.snapshot.value?.wiper)
    }
}