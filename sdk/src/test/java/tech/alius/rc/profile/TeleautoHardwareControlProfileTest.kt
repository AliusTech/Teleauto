package tech.alius.rc.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.alius.rc.model.TeleautoContinuousDialControl
import tech.alius.rc.model.TeleautoHardwareControlCatalog
import tech.alius.rc.model.TeleautoHardwareControlSide
import tech.alius.rc.model.TeleautoControlSlot
import tech.alius.rc.model.TeleautoPhysicalKey
import tech.alius.rc.model.TeleautoThreePositionSwitchControl
import tech.alius.rc.model.TeleautoThreePositionSwitchMode
import tech.alius.rc.model.TeleautoThreePositionSwitchState
import tech.alius.rc.profile.rm500.TeleautoRm500Profile
import tech.alius.rc.profile.rm700.TeleautoRm700Profile

class TeleautoHardwareControlProfileTest {
    @Test fun `control ids are stable and unique per profile`() {
        listOf(TeleautoRm500Profile(), TeleautoRm700Profile()).forEach { profile ->
            val ids = profile.hardwareControls.map { it.id }
            assertEquals(ids.size, ids.toSet().size)
            assertTrue(profile.hardwareControls.all { it.inputs.all(profile.supportedInputs::contains) })
        }
    }

    @Test fun `rm500 exposes two sides and its physical three-position switch`() {
        val controls = TeleautoRm500Profile().hardwareControls
        assertTrue(controls.any { it.side == TeleautoHardwareControlSide.LEFT })
        assertTrue(controls.any { it.side == TeleautoHardwareControlSide.RIGHT })
        assertFalse(controls.any { it is TeleautoContinuousDialControl })
        val switch = controls.filterIsInstance<TeleautoThreePositionSwitchControl>().single()
        assertEquals(listOf("C", "N", "S"), switch.labels)
    }

    @Test fun `rm700 exposes typed continuous dial and physical three-position switch`() {
        val controls = TeleautoRm700Profile().hardwareControls
        assertTrue(controls.any { it is TeleautoContinuousDialControl && it.id == "RIGHT_CONTINUOUS_DIAL_01" })
        val switch = controls.filterIsInstance<TeleautoThreePositionSwitchControl>().single()
        assertEquals(listOf("C", "N", "S"), switch.labels)
        assertEquals(
            listOf(TeleautoThreePositionSwitchMode.CINE, TeleautoThreePositionSwitchMode.NORMAL, TeleautoThreePositionSwitchMode.SPORT),
            switch.modes,
        )
        assertEquals(TeleautoThreePositionSwitchMode.CINE, TeleautoThreePositionSwitchState.fromRawValue(0).mode)
        assertEquals(TeleautoThreePositionSwitchMode.NORMAL, TeleautoThreePositionSwitchState.fromRawValue(1).mode)
        assertEquals(TeleautoThreePositionSwitchMode.SPORT, TeleautoThreePositionSwitchState.fromRawValue(2).mode)
    }

    @Test fun `shared hardware reuses canonical catalog definitions across models`() {
        val rm500 = TeleautoRm500Profile().hardwareControls.associateBy { it.id }
        val rm700 = TeleautoRm700Profile().hardwareControls.associateBy { it.id }
        listOf(
            TeleautoHardwareControlCatalog.LEFT_JOYSTICK_01,
            TeleautoHardwareControlCatalog.RIGHT_JOYSTICK_01,
            TeleautoHardwareControlCatalog.LEFT_CENTERING_DIAL_01,
            TeleautoHardwareControlCatalog.RIGHT_CENTERING_DIAL_01,
            TeleautoHardwareControlCatalog.RTH_BUTTON,
            TeleautoHardwareControlCatalog.CUSTOM_BUTTON_C1,
            TeleautoHardwareControlCatalog.CUSTOM_BUTTON_C2,
            TeleautoHardwareControlCatalog.PHOTO_BUTTON,
            TeleautoHardwareControlCatalog.RECORD_BUTTON,
            TeleautoHardwareControlCatalog.RIGHT_THREE_POSITION_SWITCH_01,
        ).forEach { canonical ->
            assertTrue(rm500[canonical.id] === canonical)
            assertTrue(rm700[canonical.id] === canonical)
        }
    }

    @Test fun `console controls retain fixed physical slots across profiles`() {
        val expected = mapOf(
            TeleautoPhysicalKey.GOHOME to (TeleautoHardwareControlSide.LEFT to TeleautoControlSlot.ROUND_2),
            TeleautoPhysicalKey.PAUSE to (TeleautoHardwareControlSide.RIGHT to TeleautoControlSlot.ROUND_1),
            TeleautoPhysicalKey.C1 to (TeleautoHardwareControlSide.LEFT to TeleautoControlSlot.LEFT_3),
            TeleautoPhysicalKey.C2 to (TeleautoHardwareControlSide.RIGHT to TeleautoControlSlot.RIGHT_2),
            TeleautoPhysicalKey.C3 to (TeleautoHardwareControlSide.RIGHT to TeleautoControlSlot.RIGHT_3),
            TeleautoPhysicalKey.L1 to (TeleautoHardwareControlSide.LEFT to TeleautoControlSlot.RIGHT_1),
            TeleautoPhysicalKey.L2 to (TeleautoHardwareControlSide.LEFT to TeleautoControlSlot.RIGHT_2),
            TeleautoPhysicalKey.L3 to (TeleautoHardwareControlSide.LEFT to TeleautoControlSlot.RIGHT_3),
            TeleautoPhysicalKey.R1 to (TeleautoHardwareControlSide.RIGHT to TeleautoControlSlot.LEFT_1),
            TeleautoPhysicalKey.R2 to (TeleautoHardwareControlSide.RIGHT to TeleautoControlSlot.LEFT_2),
            TeleautoPhysicalKey.R3 to (TeleautoHardwareControlSide.RIGHT to TeleautoControlSlot.LEFT_3),
            TeleautoPhysicalKey.AUTH_LED to (TeleautoHardwareControlSide.LEFT to TeleautoControlSlot.ROUND_1),
        )
        listOf(TeleautoRm500Profile(), TeleautoRm700Profile()).forEach { profile ->
            profile.hardwareControls.forEach { control ->
                val key = control.inputs.singleOrNull() ?: return@forEach
                val expectedPlacement = expected[key] ?: return@forEach
                assertEquals(expectedPlacement.first, control.placement?.side)
                assertEquals(expectedPlacement.second, control.placement?.slot)
            }
        }
    }
}
