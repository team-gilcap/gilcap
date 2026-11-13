package com.gilcap.app.feature.capsulediscovery

import com.gilcap.app.core.audio.SpeechSpeaker
import com.gilcap.app.core.location.DemoRoute
import com.gilcap.app.core.location.MotionEstimator
import com.gilcap.app.core.model.Capsule
import com.gilcap.app.core.model.DemoUsers
import com.gilcap.app.core.model.Gear
import com.gilcap.app.core.model.MatchedCondition
import com.gilcap.app.core.model.TimeCondition
import com.gilcap.app.core.model.VehicleCondition
import com.gilcap.app.core.model.VehicleSnapshot
import com.gilcap.app.core.model.WiperState
import com.gilcap.app.core.vehicle.VehicleSnapshotProvider
import com.gilcap.app.data.local.InMemoryCapsuleRepository
import com.gilcap.app.data.local.InMemoryDiscoveryRepository
import com.gilcap.app.domain.matching.CapsuleMatcher
import com.gilcap.app.domain.matching.ConditionClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CapsuleDiscoveryFlowTest {

    /** 말한 문장을 기록하는 TTS */
    private class RecordingSpeaker : SpeechSpeaker {
        val spoken = mutableListOf<String>()
        var stopped = false
        override suspend fun speak(text: String) { spoken += text; delay(1_000) }
        override fun stop() { stopped = true }
    }

    private class FakeSnapshots : VehicleSnapshotProvider {
        override val snapshot = MutableStateFlow<VehicleSnapshot?>(null)
    }

    /** A가 시연 지점에 정방향으로 남긴 "비 오는 밤" 캡슐 */
    private val rainyNightCapsule = Capsule(
        id = "c1",
        authorId = DemoUsers.A,
        messageText = "비 오는 날 이 길 정말 예뻐요",
        latitude = DemoRoute.CAPSULE_LAT,
        longitude = DemoRoute.CAPSULE_LNG,
        headingDegrees = DemoRoute.FORWARD_HEADING,
        placeName = DemoRoute.PLACE_NAME,
        timeCondition = TimeCondition.NIGHT,
        vehicleCondition = VehicleCondition.RAIN,
        createdAt = 0L,
    )

    private inner class Env(scope: TestScope, var time: TimeCondition = TimeCondition.NIGHT) {
        val snapshots = FakeSnapshots()
        val capsules = InMemoryCapsuleRepository()
        val discoveries = InMemoryDiscoveryRepository()
        val speaker = RecordingSpeaker()
        var user = DemoUsers.B
        var wiper = WiperState.LOW
        val flow = CapsuleDiscoveryFlow(
            snapshotProvider = snapshots,
            capsuleRepository = capsules,
            discoveryRepository = discoveries,
            matcher = CapsuleMatcher(ConditionClassifier(timeOverride = { time })),
            speaker = speaker,
            currentUserId = { user },
            scope = CoroutineScope(scope.coroutineContext),
            now = { scope.testScheduler.currentTime },
        )

        fun at(lat: Double, lng: Double, heading: Float) {
            snapshots.snapshot.value = VehicleSnapshot(lat, lng, heading, 40f, Gear.D, wiper, 0L)
        }

        /** GPX를 위치 SDK처럼 한 점씩 흘려보내며 매 점마다 발견 확인 */
        suspend fun drive(gpxName: String) {
            val estimator = MotionEstimator()
            for ((i, p) in readGpx(gpxName).withIndex()) {
                val fix = estimator.update(p.first, p.second, sdkHeading = null, timestamp = i * 1_000L)
                at(fix.latitude, fix.longitude, fix.headingDegrees)
                flow.checkOnce()
            }
        }

        suspend fun collectedCount() = discoveries.observeByUser(user).first().size
    }

    private fun readGpx(name: String): List<Pair<Double, Double>> {
        val file = listOf("../demo/gpx/$name", "demo/gpx/$name").map(::File).first { it.exists() }
        return Regex("""lat="([\d.]+)" lon="([\d.]+)"""").findAll(file.readText())
            .map { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }
            .toList()
    }

    // ───────── 완료 기준: GPX ─────────

    @Test fun `정방향 GPX는 캡슐 지점에서 한 번만 열린다`() = runTest {
        val env = Env(this)
        env.capsules.save(rainyNightCapsule)

        env.drive("gilcap_forward.gpx")

        assertEquals(1, env.collectedCount())
        assertEquals(1, env.speaker.spoken.count { it == rainyNightCapsule.messageText })
    }

    @Test fun `정방향 GPX를 두 번 달려도 다시 열리지 않는다`() = runTest {
        val env = Env(this)
        env.capsules.save(rainyNightCapsule)

        env.drive("gilcap_forward.gpx")
        env.drive("gilcap_forward.gpx")

        assertEquals(1, env.collectedCount())
    }

    @Test fun `역방향 GPX에서는 열리지 않는다`() = runTest {
        val env = Env(this)
        env.capsules.save(rainyNightCapsule)

        env.drive("gilcap_reverse.gpx")

        assertEquals(0, env.collectedCount())
        assertTrue(env.speaker.spoken.isEmpty())
    }

    // ───────── 조건·안전 규칙 ─────────

    @Test fun `맑은 밤에는 안 열린다`() = runTest {
        val env = Env(this).apply { wiper = WiperState.OFF }
        env.capsules.save(rainyNightCapsule)

        env.drive("gilcap_forward.gpx")

        assertEquals(0, env.collectedCount())
    }

    @Test fun `비 오는 낮에는 안 열린다`() = runTest {
        val env = Env(this, time = TimeCondition.DAY)
        env.capsules.save(rainyNightCapsule)

        env.drive("gilcap_forward.gpx")

        assertEquals(0, env.collectedCount())
    }

    @Test fun `자기가 만든 캡슐은 자기에게 안 열린다`() = runTest {
        val env = Env(this).apply { user = DemoUsers.A }
        env.capsules.save(rainyNightCapsule)

        env.drive("gilcap_forward.gpx")

        assertEquals(0, env.collectedCount())
    }

    @Test fun `열리면 안내 → 메시지 → 수집 순서로 말하고 4개 조건을 기록한다`() = runTest {
        val env = Env(this)
        env.capsules.save(rainyNightCapsule)
        env.at(DemoRoute.CAPSULE_LAT, DemoRoute.CAPSULE_LNG, DemoRoute.FORWARD_HEADING)

        env.flow.checkOnce()

        assertEquals(
            listOf(
                CapsuleDiscoveryFlow.ANNOUNCE_TEXT,
                rainyNightCapsule.messageText,
                CapsuleDiscoveryFlow.collectedText(rainyNightCapsule),
            ),
            env.speaker.spoken,
        )
        val saved = env.discoveries.observeByUser(DemoUsers.B).first().single()
        assertEquals(MatchedCondition.entries.toSet(), saved.matchedConditions)
        assertEquals(DiscoveryUiState.Idle, env.flow.state.value)
    }

    @Test fun `화면 상태는 Discovered → Playing → Collected → Idle`() = runTest {
        val env = Env(this)
        env.capsules.save(rainyNightCapsule)
        env.at(DemoRoute.CAPSULE_LAT, DemoRoute.CAPSULE_LNG, DemoRoute.FORWARD_HEADING)
        val states = mutableListOf<DiscoveryUiState>()
        val watcher = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { env.flow.state.toList(states) }

        env.flow.start()
        advanceTimeBy(10_000)
        runCurrent()
        env.flow.stop()
        watcher.cancel()

        assertEquals(
            listOf(
                DiscoveryUiState.Idle,
                DiscoveryUiState.Discovered(rainyNightCapsule),
                DiscoveryUiState.Playing(rainyNightCapsule),
                DiscoveryUiState.Collected(rainyNightCapsule),
                DiscoveryUiState.Idle,
            ),
            states,
        )
    }

    @Test fun `가까운 캡슐 두 개가 있어도 한 번에 하나씩 연다`() = runTest {
        val env = Env(this)
        val second = rainyNightCapsule.copy(id = "c2", messageText = "두 번째", latitude = DemoRoute.CAPSULE_LAT + 0.0003)
        env.capsules.save(rainyNightCapsule)
        env.capsules.save(second)
        env.at(DemoRoute.CAPSULE_LAT, DemoRoute.CAPSULE_LNG, DemoRoute.FORWARD_HEADING)

        env.flow.checkOnce()
        assertEquals(1, env.collectedCount())
        assertEquals(rainyNightCapsule.messageText, env.speaker.spoken[1])   // 더 가까운 것 먼저

        env.flow.checkOnce()
        assertEquals(2, env.collectedCount())
    }

    @Test fun `pause 중에는 찾지 않는다`() = runTest {
        val env = Env(this)
        env.capsules.save(rainyNightCapsule)
        env.at(DemoRoute.CAPSULE_LAT, DemoRoute.CAPSULE_LNG, DemoRoute.FORWARD_HEADING)

        env.flow.pause()
        env.flow.start()
        advanceTimeBy(5_000)
        assertEquals(0, env.collectedCount())

        env.flow.resume()
        advanceTimeBy(10_000)
        env.flow.stop()
        assertEquals(1, env.collectedCount())
    }

    @Test fun `TTS가 실패해도 수집은 남고 같은 캡슐이 반복해서 열리지 않는다`() = runTest {
        val env = Env(this)
        val broken = object : SpeechSpeaker {
            override suspend fun speak(text: String) = throw IllegalStateException("TTS 연결 끊김")
            override fun stop() {}
        }
        val flow = CapsuleDiscoveryFlow(
            env.snapshots, env.capsules, env.discoveries,
            CapsuleMatcher(ConditionClassifier(timeOverride = { TimeCondition.NIGHT })),
            broken, { DemoUsers.B }, CoroutineScope(coroutineContext),
        )
        env.capsules.save(rainyNightCapsule)
        env.at(DemoRoute.CAPSULE_LAT, DemoRoute.CAPSULE_LNG, DemoRoute.FORWARD_HEADING)

        flow.checkOnce()
        flow.checkOnce()

        assertEquals(1, env.collectedCount())
    }
}
