package com.gilcap.app.feature.capsulecreate

import com.gilcap.app.core.audio.SpeechListener
import com.gilcap.app.core.audio.SpeechSpeaker
import com.gilcap.app.core.model.Gear
import com.gilcap.app.core.model.TimeCondition
import com.gilcap.app.core.model.VehicleCondition
import com.gilcap.app.core.model.VehicleSnapshot
import com.gilcap.app.core.model.WiperState
import com.gilcap.app.core.vehicle.VehicleSnapshotProvider
import com.gilcap.app.data.local.InMemoryCapsuleRepository
import com.gilcap.app.domain.matching.ConditionClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapsuleCreateFlowTest {

    private val snapshot = VehicleSnapshot(
        latitude = 37.7700, longitude = 128.9500, headingDegrees = 90f, speed = 40f,
        gear = Gear.D, wiper = WiperState.LOW, timestamp = 1_000L,
    )

    /** 말한 문장을 모아 둔다 */
    private class RecordingSpeaker : SpeechSpeaker {
        val spoken = mutableListOf<String>()
        var stopped = 0
        var failing = false   // PleosSpeechSpeaker처럼 TTS 실패를 예외로 알린다
        override suspend fun speak(text: String) {
            spoken += text
            if (failing) throw IllegalStateException("TTS playback failed")
        }
        override fun stop() { stopped++ }
    }

    /** 준비한 대답을 순서대로 돌려준다. 다 쓰면 무응답. 듣는 순간 차가 움직이게 할 수도 있다 */
    private class ScriptedListener(vararg answers: String?) : SpeechListener {
        private val answers = ArrayDeque(answers.toList())
        val timeouts = mutableListOf<Long>()
        var onListen: () -> Unit = {}
        override suspend fun listen(timeoutMillis: Long): String? {
            timeouts += timeoutMillis
            onListen()
            return answers.removeFirstOrNull()
        }
    }

    private class Fixture(
        scope: CoroutineScope,
        initial: VehicleSnapshot?,
        vararg answers: String?,
    ) {
        val snapshots = MutableStateFlow(initial)
        val speaker = RecordingSpeaker()
        val listener = ScriptedListener(*answers)
        val repository = InMemoryCapsuleRepository()
        val running = mutableListOf<Boolean>()
        val flow = CapsuleCreateFlow(
            snapshotProvider = object : VehicleSnapshotProvider { override val snapshot = snapshots },
            speaker = speaker,
            listener = listener,
            classifier = ConditionClassifier(timeOverride = { TimeCondition.NIGHT }),
            capsuleRepository = repository,
            currentUserId = { "user-a" },
            scope = scope,
            now = { 9_999L },
            newId = { "capsule-1" },
            onRunningChanged = { running += it },
        )
        suspend fun saved() = repository.observeAll().first()
    }

    @Test fun `말하고 등록하면 캡슐이 저장된다`() = runTest {
        val f = Fixture(this, snapshot, "여기 노을이 예뻐요", "등록")

        val result = f.flow.runOnce()

        val capsule = f.saved().single()
        assertEquals(CreateUiState.Created(capsule), result)
        assertEquals("capsule-1", capsule.id)
        assertEquals("user-a", capsule.authorId)
        assertEquals("여기 노을이 예뻐요", capsule.messageText)
        assertEquals(37.7700, capsule.latitude, 0.0)
        assertEquals(128.9500, capsule.longitude, 0.0)
        assertEquals(90f, capsule.headingDegrees)
        assertEquals(TimeCondition.NIGHT, capsule.timeCondition)
        assertEquals(VehicleCondition.RAIN, capsule.vehicleCondition)
        assertEquals(9_999L, capsule.createdAt)
        assertEquals(
            listOf(
                "남길 말을 해주세요",
                "'여기 노을이 예뻐요' 이렇게 남길까요? 등록, 다시, 취소 중에 말씀해 주세요",
                "비 오는 밤의 해안도로에 캡슐을 남겼어요",
            ),
            f.speaker.spoken,
        )
    }

    @Test fun `듣기는 30초 확인은 8초를 기다린다`() = runTest {
        val f = Fixture(this, snapshot, "안녕", "등록")

        f.flow.runOnce()

        assertEquals(listOf(30_000L, 8_000L), f.listener.timeouts)
    }

    @Test fun `말하는 동안 이동해도 버튼을 누른 지점에 저장된다`() = runTest {
        val f = Fixture(this, snapshot, "안녕", "등록")
        f.listener.onListen = {
            f.snapshots.value = snapshot.copy(latitude = 38.0, headingDegrees = 270f, wiper = WiperState.OFF)
        }

        f.flow.runOnce()

        val capsule = f.saved().single()
        assertEquals(37.7700, capsule.latitude, 0.0)
        assertEquals(90f, capsule.headingDegrees)
        assertEquals(VehicleCondition.RAIN, capsule.vehicleCondition)
    }

    @Test fun `다시라고 하면 새로 말한 문장이 저장된다`() = runTest {
        val f = Fixture(this, snapshot, "첫 번째", "다시", "두 번째", "등록")

        f.flow.runOnce()

        assertEquals("두 번째", f.saved().single().messageText)
        assertTrue("다시 말씀해 주세요" in f.speaker.spoken)
    }

    @Test fun `다시는 두 번까지만 되고 세 번째에는 취소된다`() = runTest {
        val f = Fixture(this, snapshot, "하나", "다시", "둘", "다시", "셋", "다시", "넷", "등록")

        val result = f.flow.runOnce()

        assertEquals(CreateUiState.Cancelled, result)
        assertTrue(f.saved().isEmpty())
        assertEquals(CapsuleCreateFlow.MSG_RETRY_LIMIT, f.speaker.spoken.last())
    }

    @Test fun `다시 두 번 뒤에 등록하면 저장된다`() = runTest {
        val f = Fixture(this, snapshot, "하나", "다시", "둘", "다시", "셋", "등록")

        f.flow.runOnce()

        assertEquals("셋", f.saved().single().messageText)
    }

    @Test fun `취소라고 하면 저장하지 않는다`() = runTest {
        val f = Fixture(this, snapshot, "안녕", "취소")

        val result = f.flow.runOnce()

        assertEquals(CreateUiState.Cancelled, result)
        assertTrue(f.saved().isEmpty())
        assertEquals(CapsuleCreateFlow.MSG_CANCELLED, f.speaker.spoken.last())
    }

    @Test fun `확인에서 8초 무응답이면 저장하지 않는다`() = runTest {
        val f = Fixture(this, snapshot, "안녕", null)

        val result = f.flow.runOnce()

        assertEquals(CreateUiState.Cancelled, result)
        assertTrue(f.saved().isEmpty())
    }

    @Test fun `확인에서 엉뚱한 말이면 한 번 다시 묻는다`() = runTest {
        val f = Fixture(this, snapshot, "안녕", "오늘 날씨 좋다", "등록")

        f.flow.runOnce()

        assertEquals("안녕", f.saved().single().messageText)
        assertTrue(CapsuleCreateFlow.MSG_ASK_COMMAND_AGAIN in f.speaker.spoken)
    }

    @Test fun `확인에서 두 번 못 알아들으면 취소된다`() = runTest {
        val f = Fixture(this, snapshot, "안녕", "오늘 날씨 좋다", "배고파", "등록")

        val result = f.flow.runOnce()

        assertEquals(CreateUiState.Cancelled, result)
        assertTrue(f.saved().isEmpty())
    }

    @Test fun `남길 말을 못 들으면 다시 묻고 이어서 진행한다`() = runTest {
        val f = Fixture(this, snapshot, null, "안녕", "등록")

        f.flow.runOnce()

        assertEquals("안녕", f.saved().single().messageText)
        assertTrue(CapsuleCreateFlow.MSG_NOT_HEARD_RETRY in f.speaker.spoken)
    }

    @Test fun `남길 말을 계속 못 들으면 취소된다`() = runTest {
        val f = Fixture(this, snapshot)

        val result = f.flow.runOnce()

        assertEquals(CreateUiState.Cancelled, result)
        assertEquals(3, f.listener.timeouts.size)
        assertEquals(CapsuleCreateFlow.MSG_NOT_HEARD_CANCEL, f.speaker.spoken.last())
    }

    @Test fun `위치를 아직 못 받았으면 듣지 않고 끝난다`() = runTest {
        val f = Fixture(this, null, "안녕", "등록")

        val result = f.flow.runOnce()

        assertEquals(CreateUiState.Cancelled, result)
        assertTrue(f.listener.timeouts.isEmpty())
        assertTrue(f.saved().isEmpty())
    }

    @Test fun `TTS가 실패해도 말한 문장은 캡슐로 저장된다`() = runTest {
        val f = Fixture(this, snapshot, "안녕", "등록")
        f.speaker.failing = true

        val result = f.flow.runOnce()

        assertTrue(result is CreateUiState.Created)
        assertEquals("안녕", f.saved().single().messageText)
    }

    @Test fun `start는 상태를 순서대로 바꾸고 Idle로 돌아온다`() = runTest {
        val f = Fixture(this, snapshot, "안녕", "등록")
        val states = mutableListOf<String>()
        f.listener.onListen = { states += f.flow.state.value::class.simpleName!! }

        f.flow.start()
        f.flow.start()   // 진행 중 다시 눌러도 무시
        testScheduler.runCurrent()

        assertEquals(listOf("Listening", "Confirming"), states)
        assertTrue(f.flow.state.value is CreateUiState.Created)
        assertTrue(f.flow.isRunning)

        advanceUntilIdle()

        assertEquals(CreateUiState.Idle, f.flow.state.value)
        assertEquals(1, f.saved().size)
        assertEquals(false, f.flow.isRunning)
        assertEquals(listOf(true, false), f.running)   // 생성 중에만 발견 흐름을 멈춘다
    }

    @Test fun `cancel하면 말을 끊고 Idle로 돌아온다`() = runTest {
        val scope = TestScope(testScheduler)
        val f = Fixture(scope, snapshot, "안녕", "등록")

        f.flow.start()
        testScheduler.runCurrent()
        f.flow.cancel()
        testScheduler.runCurrent()

        assertEquals(CreateUiState.Idle, f.flow.state.value)
        assertEquals(1, f.speaker.stopped)
        assertEquals(listOf(true, false), f.running)
    }

    @Test fun `음성 명령 해석`() {
        assertEquals(VoiceCommand.REGISTER, VoiceCommand.parse("등록"))
        assertEquals(VoiceCommand.REGISTER, VoiceCommand.parse("등록해 줘."))
        assertEquals(VoiceCommand.REGISTER, VoiceCommand.parse("네"))
        assertEquals(VoiceCommand.REGISTER, VoiceCommand.parse("응, 좋아"))
        assertEquals(VoiceCommand.RETRY, VoiceCommand.parse("다시"))
        assertEquals(VoiceCommand.RETRY, VoiceCommand.parse("다시 할게요"))
        assertEquals(VoiceCommand.CANCEL, VoiceCommand.parse("취소"))
        assertEquals(VoiceCommand.CANCEL, VoiceCommand.parse("그만할래"))
        assertEquals(VoiceCommand.CANCEL, VoiceCommand.parse("등록 취소"))   // 섞이면 저장 안 하는 쪽
        assertNull(VoiceCommand.parse("그러네"))                              // '네'가 들어갔다고 등록 아님
        assertNull(VoiceCommand.parse("오늘 날씨 좋다"))
        assertNull(VoiceCommand.parse(""))
        assertNull(VoiceCommand.parse(null))
    }
}
