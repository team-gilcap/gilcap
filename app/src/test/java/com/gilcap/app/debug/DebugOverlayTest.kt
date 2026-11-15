package com.gilcap.app.debug

import com.gilcap.app.core.model.Gear
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugOverlayTest {

    @Test
    fun `release 빌드는 P단에서만 패널 사용 가능`() {
        assertTrue(debugPanelAvailable(debuggable = false, gear = Gear.P))
        listOf(Gear.R, Gear.N, Gear.D, Gear.UNKNOWN).forEach { gear ->
            assertFalse("$gear", debugPanelAvailable(debuggable = false, gear = gear))
        }
    }

    @Test
    fun `debug 빌드는 기어와 관계없이 패널 사용 가능`() {
        Gear.entries.forEach { gear ->
            assertTrue("$gear", debugPanelAvailable(debuggable = true, gear = gear))
        }
    }
}
