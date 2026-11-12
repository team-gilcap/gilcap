package com.gilcap.app.core.vehicle

import com.gilcap.app.core.location.LocationSource
import com.gilcap.app.core.model.VehicleSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * 차량 + 위치 + 시각 → VehicleSnapshot
 *
 * - 셋 중 하나만 바뀌어도 새 스냅샷을 만들도록 구성
 * - 아무것도 안 바뀌어도 [tickMillis]마다 시각을 새로 찍도록 구성
 * - 정차해서 위치 및 와이퍼가 그대로여도 시간대(낮/밤/새벽) 판정이 실제 시각을 따라가게 하기 위함
 */
class CombinedVehicleSnapshotProvider(
    vehicle: VehicleStateSource,
    location: LocationSource,
    scope: CoroutineScope,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val tickMillis: Long = 1_000L,
) : VehicleSnapshotProvider {

    private val ticker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(tickMillis)
        }
    }

    override val snapshot: StateFlow<VehicleSnapshot?> =
        combine(vehicle.gear, vehicle.wiper, location.fix, ticker) { gear, wiper, fix, _ ->
            fix?.let {
                VehicleSnapshot(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    headingDegrees = it.headingDegrees,
                    speed = it.speedKph,
                    gear = gear,
                    wiper = wiper,
                    timestamp = now(),
                )
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)
}
