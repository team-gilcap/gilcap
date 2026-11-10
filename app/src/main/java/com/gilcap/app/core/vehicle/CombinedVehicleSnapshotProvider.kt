package com.gilcap.app.core.vehicle

import com.gilcap.app.core.location.LocationSource
import com.gilcap.app.core.model.VehicleSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 차량 + 위치 + 시각 → VehicleSnapshot. 셋 중 하나만 바뀌어도 새 스냅샷이 나온다 */
class CombinedVehicleSnapshotProvider(
    vehicle: VehicleStateSource,
    location: LocationSource,
    scope: CoroutineScope,
    private val now: () -> Long = { System.currentTimeMillis() },
) : VehicleSnapshotProvider {

    override val snapshot: StateFlow<VehicleSnapshot?> =
        combine(vehicle.gear, vehicle.wiper, location.fix) { gear, wiper, fix ->
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