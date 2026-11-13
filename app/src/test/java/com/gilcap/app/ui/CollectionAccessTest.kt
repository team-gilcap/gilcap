package com.gilcap.app.ui

import com.gilcap.app.core.model.Gear
import com.gilcap.app.feature.capsulecreate.CreateUiState
import com.gilcap.app.feature.capsulediscovery.DiscoveryUiState
import com.gilcap.app.feature.drive.ui.drivePresentation
import org.junit.Assert.*
import org.junit.Test

class CollectionAccessTest {
    @Test fun `P단만 도감을 열 수 있다`() {
        Gear.entries.forEach { gear -> assertEquals(gear == Gear.P, canOpenCollection(gear, false)) }
    }

    @Test fun `음성 처리 중에는 P단도 도감을 열 수 없다`() {
        Gear.entries.forEach { assertFalse(canOpenCollection(it, true)) }
    }

    @Test fun `음성 입력과 확인 중에는 화면 전환을 막는다`() {
        listOf(CreateUiState.Listening, CreateUiState.Confirming("안녕하세요")).forEach {
            val presentation = drivePresentation(it, DiscoveryUiState.Idle)
            assertTrue(presentation.isBusy)
            assertFalse(canOpenCollection(Gear.P, presentation.isBusy))
        }
    }

    @Test fun `생성 확인 자막은 인식한 문장을 그대로 보여준다`() {
        val presentation = drivePresentation(CreateUiState.Confirming("안녕하세요"), DiscoveryUiState.Idle)
        assertEquals("안녕하세요", presentation.subtitle)
    }
}
