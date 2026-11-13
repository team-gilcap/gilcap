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
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** F03의 실행 API와 수집 정책을 기준으로 화면에 전달되는 재생 상태를 검증한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class CapsulePlaybackIntegrationTest {
    private val capsule = Capsule(
        id = "capsule", authorId = DemoUsers.B, messageText = "이 길 정말 예뻐요",
        latitude = 37.77, longitude = 128.95, headingDegrees = 90f,
        placeName = "해안도로", timeCondition = TimeCondition.NIGHT,
        vehicleCondition = VehicleCondition.RAIN, createdAt = 0,
    )
    private val snapshot = VehicleSnapshot(37.77, 128.95, 90f, 40f, Gear.D, WiperState.LOW, 0)

    private class Speaker : SpeechSpeaker {
        val spoken = mutableListOf<String>()
        var stopped = false
        var body: suspend (String) -> Unit = {}
        override suspend fun speak(text: String) { spoken += text; body(text) }
        override fun stop() { stopped = true }
    }

    private inner class Fixture(scope: TestScope) {
        val settings = DemoSettings().apply { overrideTime(TimeCondition.NIGHT) }
        val snapshots = MutableStateFlow<VehicleSnapshot?>(snapshot)
        val capsules = InMemoryCapsuleRepository()
        val discoveries = InMemoryDiscoveryRepository()
        val speaker = Speaker()
        val flow = CapsuleDiscoveryFlow(
            snapshotProvider = object : VehicleSnapshotProvider { override val snapshot = snapshots },
            capsuleRepository = capsules,
            discoveryRepository = discoveries,
            matcher = CapsuleMatcher(ConditionClassifier(timeOverride = { settings.timeOverride.value })),
            speaker = speaker,
            currentUserId = { settings.currentUserId.value },
            scope = scope.backgroundScope,
            now = { scope.testScheduler.currentTime },
            collectedHoldMillis = 100,
        )
    }

    @Test fun `안내 본문 수집 안내를 순차 재생하고 이미 수집한 캡슐은 반복하지 않는다`() = runTest {
        val f = Fixture(this)
        val completed = CompletableDeferred<Unit>()
        f.speaker.body = { if (it == capsule.messageText) completed.await() }
        f.capsules.save(capsule)
        f.flow.start()
        f.flow.start()
        runCurrent()
        assertEquals(listOf(CapsuleDiscoveryFlow.ANNOUNCE_TEXT, capsule.messageText), f.speaker.spoken)
        assertEquals(DiscoveryUiState.Playing(capsule), f.flow.state.value)
        assertFalse(f.discoveries.isDiscovered(capsule.id, DemoUsers.A))
        f.snapshots.value = snapshot.copy(speed = 50f)
        advanceTimeBy(2_000); runCurrent()
        assertEquals(2, f.speaker.spoken.size)
        completed.complete(Unit)
        runCurrent()
        assertTrue(f.discoveries.isDiscovered(capsule.id, DemoUsers.A))
        assertEquals(DiscoveryUiState.Collected(capsule), f.flow.state.value)
        assertEquals(CapsuleDiscoveryFlow.collectedText(capsule), f.speaker.spoken.last())
        advanceTimeBy(5_000); runCurrent()
        assertEquals(3, f.speaker.spoken.size)
        assertEquals(DiscoveryUiState.Idle, f.flow.state.value)
    }

    @Test fun `자기 캡슐 조건 불일치 수집한 캡슐은 제외한다`() = runTest {
        val f = Fixture(this)
        f.capsules.save(capsule.copy(id = "own", authorId = DemoUsers.A))
        f.capsules.save(capsule.copy(id = "wrong", headingDegrees = 270f))
        f.capsules.save(capsule)
        f.discoveries.save(CapsuleDiscovery(capsule.id, DemoUsers.A, 0, emptySet()))
        f.flow.start(); runCurrent()
        assertTrue(f.speaker.spoken.isEmpty())
    }

    @Test fun `TTS 실패 시에도 수집하고 같은 캡슐을 반복 재생하지 않는다`() = runTest {
        val f = Fixture(this)
        f.speaker.body = { error("TTS failed") }
        f.capsules.save(capsule)
        f.flow.start(); runCurrent()
        assertTrue(f.discoveries.isDiscovered(capsule.id, DemoUsers.A))
        assertEquals(DiscoveryUiState.Collected(capsule), f.flow.state.value)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(3, f.speaker.spoken.size)
    }

    @Test fun `stop으로 중지한 재생도 수집 기록을 남긴다`() = runTest {
        val f = Fixture(this)
        var cancelled = false
        f.speaker.body = { try { awaitCancellation() } finally { cancelled = true } }
        f.capsules.save(capsule)
        f.flow.start(); runCurrent()
        f.flow.stop(); runCurrent()
        assertTrue(cancelled)
        assertTrue(f.speaker.stopped)
        assertTrue(f.discoveries.isDiscovered(capsule.id, DemoUsers.A))
        assertEquals(DiscoveryUiState.Idle, f.flow.state.value)
    }

    @Test fun `위치를 받지 못하면 대기하고 다음 주기에 추가한 캡슐을 찾는다`() = runTest {
        val f = Fixture(this)
        f.snapshots.value = null
        f.flow.start(); runCurrent()
        f.capsules.save(capsule)
        advanceTimeBy(1_000); runCurrent()
        assertTrue(f.speaker.spoken.isEmpty())
        f.snapshots.value = snapshot
        advanceTimeBy(1_000); runCurrent()
        assertTrue(f.discoveries.isDiscovered(capsule.id, DemoUsers.A))
    }

    @Test fun `시간대 변경을 다음 주기에 반영한다`() = runTest {
        val f = Fixture(this)
        f.settings.overrideTime(TimeCondition.DAY)
        f.capsules.save(capsule)
        f.flow.start(); runCurrent()
        assertTrue(f.speaker.spoken.isEmpty())
        f.settings.overrideTime(TimeCondition.NIGHT)
        advanceTimeBy(1_000); runCurrent()
        assertTrue(f.discoveries.isDiscovered(capsule.id, DemoUsers.A))
    }

    @Test fun `재생 중 사용자를 바꿔도 시작한 사용자에게 수집한다`() = runTest {
        val f = Fixture(this)
        val completed = CompletableDeferred<Unit>()
        f.speaker.body = { if (it == capsule.messageText) completed.await() }
        f.capsules.save(capsule)
        f.flow.start(); runCurrent()
        f.settings.switchUser(DemoUsers.B)
        completed.complete(Unit); runCurrent()
        assertTrue(f.discoveries.isDiscovered(capsule.id, DemoUsers.A))
        assertFalse(f.discoveries.isDiscovered(capsule.id, DemoUsers.B))
    }

    @Test fun `pause 중에는 찾지 않고 resume 후 발견한다`() = runTest {
        val f = Fixture(this)
        f.capsules.save(capsule)
        f.flow.pause()
        f.flow.start()
        advanceTimeBy(3_000); runCurrent()
        assertTrue(f.speaker.spoken.isEmpty())
        f.flow.resume()
        advanceTimeBy(1_000); runCurrent()
        assertTrue(f.discoveries.isDiscovered(capsule.id, DemoUsers.A))
    }

    @Test fun `가장 가까운 캡슐을 우선 재생한다`() = runTest {
        val f = Fixture(this)
        f.capsules.save(capsule.copy(id = "far", latitude = 37.7703, messageText = "멀리 있는 캡슐"))
        f.capsules.save(capsule)
        f.flow.start(); runCurrent()
        assertEquals(capsule.messageText, f.speaker.spoken[1])
        assertFalse(f.discoveries.isDiscovered("far", DemoUsers.A))
    }
}
