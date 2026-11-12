package com.gilcap.app.feature.capsulediscovery

import com.gilcap.app.core.audio.FakeSpeechSpeaker
import com.gilcap.app.core.audio.SpeechSpeaker
import com.gilcap.app.core.demo.DemoSettings
import com.gilcap.app.core.location.LocationFix
import com.gilcap.app.core.location.LocationSource
import com.gilcap.app.core.location.MotionEstimator
import com.gilcap.app.core.model.Capsule
import com.gilcap.app.core.model.DemoUsers
import com.gilcap.app.core.model.TimeCondition
import com.gilcap.app.core.model.WiperState
import com.gilcap.app.core.vehicle.CombinedVehicleSnapshotProvider
import com.gilcap.app.core.vehicle.FakeVehicleStateSource
import com.gilcap.app.data.local.InMemoryCapsuleRepository
import com.gilcap.app.data.local.InMemoryDiscoveryRepository
import com.gilcap.app.domain.matching.CapsuleMatcher
import com.gilcap.app.domain.matching.ConditionClassifier
import com.gilcap.app.domain.matching.GeoMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * 에뮬레이터 시연 재현: AppContainer와 같은 조립으로
 * "A가 정방향으로 달리다 캡슐을 남김 → B로 전환 → 같은 GPX를 처음부터 재생"을 가상 시간으로 돌린다.
 *
 * - 위치: GPX의 <time>대로 한 점씩 (에뮬레이터 Routes 재생처럼). 재생 배속 지원
 * - 발견: 실제 1초 주기 루프 (start()), TTS는 FakeSpeechSpeaker(말하는 시간만큼 대기)
 * - 시간대: 디버그 패널처럼 DemoSettings로 밤 고정, 와이퍼: Virtual Console처럼 LOW
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoScenarioTest {

    private data class GpxPoint(val lat: Double, val lng: Double, val offsetMillis: Long)

    /** 에뮬레이터 위치 주입을 흉내 내는 LocationSource: 점을 넣으면 SDK 콜백처럼 MotionEstimator를 거친다 */
    private class GpxLocationSource : LocationSource {
        private val estimator = MotionEstimator()
        private val _fix = MutableStateFlow<LocationFix?>(null)
        override val fix: StateFlow<LocationFix?> = _fix
        override fun start() {}
        override fun stop() {}
        fun push(lat: Double, lng: Double, time: Long) {
            _fix.value = estimator.update(lat, lng, sdkHeading = null, timestamp = time)
        }
    }

    private inner class Demo(private val test: TestScope) {
        val scope = CoroutineScope(test.coroutineContext)
        val settings = DemoSettings().apply { overrideTime(TimeCondition.NIGHT) }
        val vehicle = FakeVehicleStateSource().apply { setWiper(WiperState.LOW) }
        val location = GpxLocationSource()
        val snapshots = CombinedVehicleSnapshotProvider(
            vehicle, location, CoroutineScope(test.backgroundScope.coroutineContext + UnconfinedTestDispatcher(test.testScheduler)),   // ② 1초 타이머가 있어도 테스트가 끝나도록
            now = { test.testScheduler.currentTime },
        )
        val classifier = ConditionClassifier(timeOverride = { settings.timeOverride.value })
        val capsules = InMemoryCapsuleRepository()
        val discoveries = InMemoryDiscoveryRepository()
        val spokenLog = mutableListOf<String>()
        val speaker = object : SpeechSpeaker {
            private val fake = FakeSpeechSpeaker()
            override suspend fun speak(text: String) { spokenLog += text; fake.speak(text) }
            override fun stop() = fake.stop()
        }
        val flow = CapsuleDiscoveryFlow(
            snapshots, capsules, discoveries, CapsuleMatcher(classifier), speaker,
            currentUserId = { settings.currentUserId.value },
            scope = scope,
            now = { test.testScheduler.currentTime },
        )
        val states = mutableListOf<DiscoveryUiState>()

        init {
            test.backgroundScope.launch(UnconfinedTestDispatcher(test.testScheduler)) { flow.state.toList(states) }
        }

        /** GPX를 배속 재생. untilIndex까지만 재생하고 멈출 수 있다 (에뮬레이터 일시정지) */
        suspend fun play(points: List<GpxPoint>, speed: Double = 1.0, untilIndex: Int = points.lastIndex) {
            val start = test.testScheduler.currentTime
            for (p in points.take(untilIndex + 1)) {
                val due = start + (p.offsetMillis / speed).toLong()
                delay((due - test.testScheduler.currentTime).coerceAtLeast(0))
                location.push(p.lat, p.lng, test.testScheduler.currentTime)
            }
            delay(8_000)   // 끝난 뒤 재생 중인 캡슐이 마무리될 시간
        }

        /** ④ 생성 흐름이 하는 일: 버튼 누른 순간의 스냅샷으로 캡슐 저장 */
        suspend fun createCapsuleHere(message: String): Capsule {
            val s = snapshots.snapshot.value!!
            val capsule = Capsule(
                id = "demo-1",
                authorId = settings.currentUserId.value,
                messageText = message,
                latitude = s.latitude,
                longitude = s.longitude,
                headingDegrees = s.headingDegrees,
                placeName = "해안도로",
                timeCondition = classifier.timeOf(s.timestamp),
                vehicleCondition = classifier.vehicleOf(s.wiper),
                createdAt = s.timestamp,
            )
            capsules.save(capsule)
            return capsule
        }

        suspend fun collectedBy(user: String) = discoveries.observeByUser(user).first()
        fun timesSpoken(text: String) = spokenLog.count { it == text }
    }

    private fun readGpx(name: String): List<GpxPoint> {
        val file = listOf("../demo/gpx/$name", "demo/gpx/$name").map(::File).first { it.exists() }
        val raw = Regex("""lat="([\d.]+)" lon="([\d.]+)"><time>([^<]+)</time>""").findAll(file.readText())
            .map { Triple(it.groupValues[1].toDouble(), it.groupValues[2].toDouble(), Instant.parse(it.groupValues[3]).toEpochMilli()) }
            .toList()
        val t0 = raw.first().third
        return raw.map { (lat, lng, t) -> GpxPoint(lat, lng, t - t0) }
    }

    private val forward = readGpx("gilcap_forward.gpx")
    private val reverse = readGpx("gilcap_reverse.gpx")

    /** A가 캡슐을 남길 지점: 시연 캡슐 지점에 가장 가까운 GPX 점 */
    private val createIndex = forward.indices.minBy {
        GeoMath.distanceMeters(forward[it].lat, forward[it].lng, 37.77625, 128.9430)
    }

    private suspend fun Demo.aCreatesCapsule(): Capsule {
        settings.switchUser(DemoUsers.A)
        flow.start()
        play(forward, speed = 1.0, untilIndex = createIndex)
        return createCapsuleHere("비 오는 날 이 길 정말 예뻐요")
    }

    @Test fun `시연 - A가 남기고 B가 정방향으로 달리면 한 번만 열린다 (1배속)`() = runTest {
        val demo = Demo(this)
        val capsule = demo.aCreatesCapsule()

        // A가 계속 달려도 자기 캡슐은 안 열린다
        demo.play(forward.drop(createIndex + 1).map { it.copy(offsetMillis = it.offsetMillis - forward[createIndex].offsetMillis) })
        assertEquals(0, demo.collectedBy(DemoUsers.A).size)

        // B로 전환 → 처음부터 재생
        demo.settings.switchUser(DemoUsers.B)
        demo.play(forward)
        demo.flow.stop()

        assertEquals(1, demo.collectedBy(DemoUsers.B).size)
        assertEquals(1, demo.timesSpoken(capsule.messageText))
        assertEquals(
            listOf(
                DiscoveryUiState.Idle,
                DiscoveryUiState.Discovered(capsule),
                DiscoveryUiState.Playing(capsule),
                DiscoveryUiState.Collected(capsule),
                DiscoveryUiState.Idle,
            ),
            demo.states,
        )
    }

    @Test fun `시연 - B가 정방향을 두 번 달려도 두 번째는 안 열린다`() = runTest {
        val demo = Demo(this)
        val capsule = demo.aCreatesCapsule()
        demo.settings.switchUser(DemoUsers.B)

        demo.play(forward)
        demo.play(forward)   // GPX 되감기 (순간이동 포함)
        demo.flow.stop()

        assertEquals(1, demo.collectedBy(DemoUsers.B).size)
        assertEquals(1, demo.timesSpoken(capsule.messageText))
    }

    @Test fun `시연 - B가 역방향으로 달리면 안 열린다`() = runTest {
        val demo = Demo(this)
        demo.aCreatesCapsule()
        demo.settings.switchUser(DemoUsers.B)

        demo.play(reverse)
        demo.flow.stop()

        assertEquals(0, demo.collectedBy(DemoUsers.B).size)
    }

    @Test fun `시연 - 에뮬레이터 배속 재생(2·4·8배속)에서도 한 번만 열린다`() = runTest {
        for (speed in listOf(2.0, 4.0, 8.0)) {
            val demo = Demo(this)
            demo.aCreatesCapsule()
            demo.settings.switchUser(DemoUsers.B)

            demo.play(forward, speed = speed)
            demo.flow.stop()

            assertEquals("${speed}배속", 1, demo.collectedBy(DemoUsers.B).size)
        }
    }

    @Test fun `시연 - B 차례에 와이퍼를 끄면(맑음) 안 열린다`() = runTest {
        val demo = Demo(this)
        demo.aCreatesCapsule()
        demo.settings.switchUser(DemoUsers.B)
        demo.vehicle.setWiper(WiperState.OFF)

        demo.play(forward)
        demo.flow.stop()

        assertEquals(0, demo.collectedBy(DemoUsers.B).size)
    }
}
