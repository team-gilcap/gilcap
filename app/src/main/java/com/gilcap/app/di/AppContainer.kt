package com.gilcap.app.di

import android.content.Context
import com.gilcap.app.core.audio.FakeSpeechListener
import com.gilcap.app.core.audio.PleosSpeechSpeaker
import com.gilcap.app.core.audio.SpeechListener
import com.gilcap.app.core.audio.SpeechSpeaker
import com.gilcap.app.core.demo.DemoSettings
import com.gilcap.app.core.location.FakeLocationSource
import com.gilcap.app.core.location.LocationSource
import com.gilcap.app.core.vehicle.CombinedVehicleSnapshotProvider
import com.gilcap.app.core.vehicle.FakeVehicleStateSource
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
import com.gilcap.app.core.model.Capsule
import com.gilcap.app.core.model.DemoUsers
import com.gilcap.app.core.model.VehicleCondition
import kotlinx.coroutines.launch

/**
 * 앱 전체 조립.
 */
class AppContainer(private val context: Context) {

    /** 앱이 살아 있는 동안 도는 작업용 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val demoSettings = DemoSettings()

    // ───────── 교체 지점: core (Fake → 진짜) ─────────
    val vehicleStateSource: VehicleStateSource = FakeVehicleStateSource()   // ② PleosVehicleStateSource(context)
    val locationSource: LocationSource = FakeLocationSource()               // ③ PleosLocationSource(context)
    val speechSpeaker: SpeechSpeaker = PleosSpeechSpeaker(context)          // F05: 차량 내부 음성 엔진
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

    // 화면이 실행 시점을 제어할 수 있도록 조립만 하고, 여기서 감시를 시작하지 않는다.
    val discoveryFlow = CapsuleDiscoveryFlow(
        snapshotProvider, capsuleRepository, discoveryRepository,
        capsuleMatcher, speechSpeaker, demoSettings,
    )

    // 시연 확인 후 제거할 테스트 데이터
    init {
        appScope.launch {
            val now = System.currentTimeMillis()

            capsuleRepository.save(
                Capsule(
                    id = "f05-demo",
                    authorId = DemoUsers.B,
                    messageText = "안녕하세요. 이 길에서 좋은 하루 보내세요.",
                    latitude = FakeLocationSource.DEMO_LAT,
                    longitude = FakeLocationSource.DEMO_LNG,
                    headingDegrees = 90f,
                    placeName = "해안도로",
                    timeCondition = conditionClassifier.timeOf(now),
                    vehicleCondition = VehicleCondition.CLEAR,
                    createdAt = now,
                )
            )
        }
    }
}
