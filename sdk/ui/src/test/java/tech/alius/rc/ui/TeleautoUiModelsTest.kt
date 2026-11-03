package tech.alius.rc.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import tech.alius.rc.model.TeleautoThreePositionSwitchMode

class TeleautoUiModelsTest {
    @Test fun joystickStateIsImmutableAndValueBased() {
        assertEquals(TeleautoJoystickUiState(x = .5f, y = -.5f), TeleautoJoystickUiState(x = .5f, y = -.5f))
    }

    @Test fun moduleStateCarriesAvailabilityAndBattery() {
        val state = TeleautoModuleUiState("发射模块", TeleautoModuleUiAvailability.AVAILABLE, "可用", 72)
        assertEquals(TeleautoModuleUiAvailability.AVAILABLE, state.availability)
        assertEquals(72, state.batteryPercent)
    }

    @Test fun threePositionModesHaveStableDistinctColours() {
        assertEquals(TeleautoUiTokens.MODE_CINE, TeleautoUiTokens.modeColor(TeleautoThreePositionSwitchMode.CINE))
        assertEquals(TeleautoUiTokens.MODE_NORMAL, TeleautoUiTokens.modeColor(TeleautoThreePositionSwitchMode.NORMAL))
        assertEquals(TeleautoUiTokens.MODE_SPORT, TeleautoUiTokens.modeColor(TeleautoThreePositionSwitchMode.SPORT))
        assertEquals(TeleautoUiTokens.MODE_NORMAL, TeleautoUiTokens.modeColor(TeleautoThreePositionSwitchMode.UNKNOWN))
        assertNotEquals(TeleautoUiTokens.MODE_CINE, TeleautoUiTokens.MODE_NORMAL)
        assertNotEquals(TeleautoUiTokens.MODE_NORMAL, TeleautoUiTokens.MODE_SPORT)
        assertNotEquals(TeleautoUiTokens.MODE_CINE, TeleautoUiTokens.MODE_SPORT)
    }
}
