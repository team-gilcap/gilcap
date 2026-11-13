package com.gilcap.app.feature.capsulediscovery

import com.gilcap.app.core.audio.SpeechSpeaker
import com.gilcap.app.core.demo.DemoSettings
import com.gilcap.app.core.model.*
import com.gilcap.app.core.vehicle.VehicleSnapshotProvider
import com.gilcap.app.data.local.InMemoryCapsuleRepository
import com.gilcap.app.data.local.InMemoryDiscoveryRepository
import com.gilcap.app.domain.matching.CapsuleMatcher
import com.gilcap.app.domain.matching.ConditionClassifier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** 실제 음성 장치 없이 발화 완료·실패·취소와 차량 상태 변경을 재현한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class CapsuleDiscoveryFlowTest {
    private val capsule = Capsule(
        id = "capsule", authorId = DemoUsers.B, messageText = "이 길 정말 예뻐요",
        latitude = 37.77, longitude = 128.95, headingDegrees = 90f,
        placeName = "해안도로", timeCondition = TimeCondition.NIGHT,
        vehicleCondition = VehicleCondition.RAIN, createdAt = 0,
    )
    private val snapshot = VehicleSnapshot(37.77, 128.95, 90f, 40f, Gear.D, WiperState.LOW, 0)

    // 호출된 문장을 기록하고, 테스트마다 발화 대기·실패·취소 동작을 지정한다.
    private class Speaker : SpeechSpeaker {
        val spoken = mutableListOf<String>()
        var body: suspend (String) -> Unit = {}
        override suspend fun speak(text: String) { spoken += text; body(text) }
        override fun stop() = Unit
    }
    // 테스트별 저장소와 상태를 분리하고 재시도 시계도 테스트의 가상 시간에 맞춘다.
    private inner class Fixture(scope: TestScope) {
        val settings = DemoSettings().apply { overrideTime(TimeCondition.NIGHT) }
        val snapshots = MutableStateFlow<VehicleSnapshot?>(snapshot)
        val capsules = InMemoryCapsuleRepository()
        val discoveries = InMemoryDiscoveryRepository()
        val speaker = Speaker()
        val flow = CapsuleDiscoveryFlow(
            object : VehicleSnapshotProvider { override val snapshot = snapshots },
            capsules, discoveries,
            CapsuleMatcher(ConditionClassifier(timeOverride = { settings.timeOverride.value })),
            speaker, settings, now = { scope.testScheduler.currentTime },
        )
    }

    @Test fun `안내와 본문이 끝나야 수집하고 다시 재생하지 않는다`() = runTest {
        val f = Fixture(this)
        val completed = CompletableDeferred<Unit>()
        f.speaker.body = { if (it == capsule.messageText) completed.await() }
        f.capsules.save(capsule)
        backgroundScope.launch { f.flow.run() }
        runCurrent()
        assertEquals(listOf("누군가 남긴 캡슐이 있어요", capsule.messageText), f.speaker.spoken)
        assertTrue(f.flow.state.value is DiscoveryUiState.Playing)
        assertFalse(f.discoveries.isDiscovered(capsule.id, DemoUsers.A))
        // 새 스냅샷을 받아도 본문 재생이 끝나기 전에는 취소하거나 다시 시작하지 않아야 한다.
        f.snapshots.value = snapshot.copy(speed = 50f)
        advanceTimeBy(2_000); runCurrent()
        assertEquals(2, f.speaker.spoken.size)
        // 본문 완료 신호를 보내 실제 발화가 끝난 상황을 재현한다.
        completed.complete(Unit)
        runCurrent()
        assertTrue(f.discoveries.isDiscovered(capsule.id, DemoUsers.A))
        assertTrue(f.flow.state.value is DiscoveryUiState.Collected)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(2, f.speaker.spoken.size)
    }

    @Test fun `자기 캡슐과 조건 불일치와 수집한 캡슐은 제외한다`() = runTest {
        val f = Fixture(this)
        f.capsules.save(capsule.copy(id = "own", authorId = DemoUsers.A))
        f.capsules.save(capsule.copy(id = "wrong", headingDegrees = 270f))
        f.capsules.save(capsule)
        f.discoveries.save(CapsuleDiscovery(capsule.id, DemoUsers.A, 0, emptySet()))
        backgroundScope.launch { f.flow.run() }
        runCurrent()
        assertTrue(f.speaker.spoken.isEmpty())
    }

    @Test fun `재생 실패는 수집하지 않고 즉시 반복하지 않는다`() = runTest {
        val f = Fixture(this)
        f.speaker.body = { if (it == capsule.messageText) error("TTS failed") }
        f.capsules.save(capsule)
        backgroundScope.launch { f.flow.run() }
        runCurrent()
        assertTrue(f.flow.state.value is DiscoveryUiState.Error)
        assertFalse(f.discoveries.isDiscovered(capsule.id, DemoUsers.A))
        advanceTimeBy(5_000); runCurrent()
        assertEquals(2, f.speaker.spoken.size)
        f.speaker.body = {}
        // 재시도 제한 시간이 지나면 같은 캡슐을 다시 재생할 수 있어야 한다.
        advanceTimeBy(30_000); runCurrent()
        assertTrue(f.discoveries.isDiscovered(capsule.id, DemoUsers.A))
    }

    @Test fun `화면 종료로 취소한 재생은 수집하지 않는다`() = runTest {
        val f = Fixture(this)
        var cancelled = false
        f.speaker.body = {
            try { awaitCancellation() } finally { cancelled = true }
        }
        f.capsules.save(capsule)
        val job = backgroundScope.launch { f.flow.run() }
        runCurrent()
        job.cancel(); runCurrent()
        assertTrue(cancelled)
        assertFalse(f.discoveries.isDiscovered(capsule.id, DemoUsers.A))
        assertEquals(DiscoveryUiState.Idle, f.flow.state.value)
    }

    @Test fun `위치 미수신 중에는 대기하고 캡슐 추가도 감지한다`() = runTest {
        val f = Fixture(this)
        f.snapshots.value = null
        backgroundScope.launch { f.flow.run() }
        runCurrent()
        f.capsules.save(capsule)
        runCurrent()
        assertTrue(f.speaker.spoken.isEmpty())
        f.snapshots.value = snapshot
        runCurrent()
        assertTrue(f.discoveries.isDiscovered(capsule.id, DemoUsers.A))
    }

    @Test fun `시연 시간대 변경을 감지한다`() = runTest {
        val f = Fixture(this)
        f.settings.overrideTime(TimeCondition.DAY)
        f.capsules.save(capsule)
        backgroundScope.launch { f.flow.run() }
        runCurrent()
        assertTrue(f.speaker.spoken.isEmpty())
        f.settings.overrideTime(TimeCondition.NIGHT)
        runCurrent()
        assertTrue(f.discoveries.isDiscovered(capsule.id, DemoUsers.A))
    }
    @Test fun `재생 중 사용자 전환 후에도 시작한 사용자에게 수집한다`() = runTest {
        val f = Fixture(this)
        val completed = CompletableDeferred<Unit>()
        f.speaker.body = { if (it == capsule.messageText) completed.await() }
        f.capsules.save(capsule)
        backgroundScope.launch { f.flow.run() }
        runCurrent()
        f.settings.switchUser(DemoUsers.B)
        runCurrent()
        // 본문 완료 신호를 보내 실제 발화가 끝난 상황을 재현한다.
        completed.complete(Unit)
        runCurrent()
        assertTrue(f.discoveries.isDiscovered(capsule.id, DemoUsers.A))
        assertFalse(f.discoveries.isDiscovered(capsule.id, DemoUsers.B))
    }

    @Test fun `재생이 끝나면 최신 위치로 다음 캡슐을 판단한다`() = runTest {
        val f = Fixture(this)
        val completed = CompletableDeferred<Unit>()
        f.speaker.body = { if (it == capsule.messageText) completed.await() }
        f.capsules.save(capsule)
        f.capsules.save(capsule.copy(id = "second"))
        backgroundScope.launch { f.flow.run() }
        runCurrent()
        f.snapshots.value = snapshot.copy(latitude = 38.0)
        runCurrent()
        // 본문 완료 신호를 보내 실제 발화가 끝난 상황을 재현한다.
        completed.complete(Unit)
        runCurrent()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(2, f.speaker.spoken.size)
        assertFalse(f.discoveries.isDiscovered("second", DemoUsers.A))
    }

}
