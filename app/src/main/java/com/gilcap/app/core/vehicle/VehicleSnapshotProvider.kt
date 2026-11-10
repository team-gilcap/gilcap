package com.gilcap.app.core.vehicle

import com.gilcap.app.core.model.VehicleSnapshot
import kotlinx.coroutines.flow.StateFlow

/**
 * F02 차량 + 위치 + 시각을 합친 "지금 차 상태 한 장" (② 구현)
 * 위치를 아직 못 받았으면 null.
 */
interface VehicleSnapshotProvider {
    val snapshot: StateFlow<VehicleSnapshot?>
}