package com.gilcap.app.di

import android.content.Context
import com.gilcap.app.core.audio.FakeSpeechListener
import com.gilcap.app.core.audio.FakeSpeechSpeaker
import com.gilcap.app.core.audio.SpeechListener
import com.gilcap.app.core.audio.SpeechSpeaker
import com.gilcap.app.core.demo.DemoSettings
import com.gilcap.app.core.location.LocationSource
import com.gilcap.app.core.location.PleosLocationSource
import com.gilcap.app.core.vehicle.CombinedVehicleSnapshotProvider
import com.gilcap.app.core.vehicle.PleosVehicleStateSource
import com.gilcap.app.core.vehicle.VehicleSnapshotProvider
import com.gilcap.app.core.vehicle.VehicleStateSource
import com.gilcap.app.data.local.InMemoryCapsuleRepository
import com.gilcap.app.data.local.InMemoryDiscoveryRepository
import com.gilcap.app.domain.matching.CapsuleMatcher
import com.gilcap.app.domain.matching.ConditionClassifier
import com.gilcap.app.domain.repository.CapsuleRepository
import com.gilcap.app.domain.repository.DiscoveryRepository
import com.gilcap.app.feature.capsulediscovery.CapsuleDiscoveryFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 앱 전체 조립.
 */
class AppContainer(private val context: Context) {

    /** 앱이 살아 있는 동안 도는 작업용 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val demoSettings = DemoSettings()

    // ───────── 교체 지점: core (Fake → 진짜) ─────────
    val vehicleStateSource: VehicleStateSource = PleosVehicleStateSource(context)   // 2. 차량 (Fake: FakeVehicleStateSource())
    val locationSource: LocationSource = PleosLocationSource(context)       // ③ (Fake: FakeLocationSource())
    val speechSpeaker: SpeechSpeaker = FakeSpeechSpeaker()                  // ④ PleosSpeechSpeaker(context)
    val speechListener: SpeechListener = FakeSpeechListener()               // ④ PleosSpeechListener(context)
    // ────────────────────────────────────────────

    val snapshotProvider: VehicleSnapshotProvider =
        CombinedVehicleSnapshotProvider(vehicleStateSource, locationSource, appScope)

    // domain
    val conditionClassifier = ConditionClassifier(timeOverride = { demoSettings.timeOverride.value })
    val capsuleMatcher = CapsuleMatcher(conditionClassifier)

    // data
    val capsuleRepository: CapsuleRepository = InMemoryCapsuleRepository()
    val discoveryRepository: DiscoveryRepository = InMemoryDiscoveryRepository()

    // feature
    val capsuleDiscoveryFlow = CapsuleDiscoveryFlow(
        snapshotProvider = snapshotProvider,
        capsuleRepository = capsuleRepository,
        discoveryRepository = discoveryRepository,
        matcher = capsuleMatcher,
        speaker = speechSpeaker,
        currentUserId = { demoSettings.currentUserId.value },
        scope = appScope,
    )
}
