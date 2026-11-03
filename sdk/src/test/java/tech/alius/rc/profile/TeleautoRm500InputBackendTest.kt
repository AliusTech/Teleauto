package tech.alius.rc.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.alius.rc.model.TeleautoPhysicalKey
import tech.alius.rc.profile.rm500.TeleautoRm500Profile

class TeleautoRm500InputBackendTest {
    @Test
    fun `rm500 is a standalone current profile implementation`() {
        assertTrue(TeleautoRemoteControllerProfile::class.java.isAssignableFrom(TeleautoRm500Profile::class.java))
        assertFalse(TeleautoRm500Profile::class.java.superclass.name.contains("Adb", ignoreCase = true))
    }

    @Test
    fun `rm500 direct profile preserves confirmed controls`() {
        val inputs = TeleautoRm500Profile().supportedInputs
        assertTrue(TeleautoPhysicalKey.LEFT_STICK_X in inputs)
        assertTrue(TeleautoPhysicalKey.RIGHT_STICK_Y in inputs)
        assertTrue(TeleautoPhysicalKey.C1 in inputs)
        assertTrue(TeleautoPhysicalKey.C2 in inputs)
        assertTrue(TeleautoPhysicalKey.GOHOME in inputs)
        assertTrue(TeleautoPhysicalKey.FOCUS in inputs)
        assertTrue(TeleautoPhysicalKey.MODE_1 in inputs)
        assertTrue(TeleautoPhysicalKey.MODE_2 in inputs)
        assertTrue(TeleautoPhysicalKey.MODE_3 in inputs)
        assertEquals("C", TeleautoRm500Profile().threePositionSwitch.leftLabel)
        assertEquals("N", TeleautoRm500Profile().threePositionSwitch.centerLabel)
        assertEquals("S", TeleautoRm500Profile().threePositionSwitch.rightLabel)
        assertTrue(TeleautoRm500Profile().hardwareControls.any { it.inputs.contains(TeleautoPhysicalKey.FOCUS) })
    }

    /** DUML 0x06/0xAE 位域接线:bit5 返航、bit7 半按对焦、modeBits bit2/bit3=C1/C2;bit6/bit8 留 evdev。 */
    @Test
    fun `rm500 duml state bits map to held keys`() {
        val profile = TeleautoRm500Profile()
        val apply = TeleautoRm500Profile::class.java.getDeclaredMethod(
            "applyRcState", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        val heldField = TeleautoRm500Profile::class.java.getDeclaredField("held").apply { isAccessible = true }

        apply.invoke(profile, (1 shl 5) or (1 shl 7), (1 shl 2) or (1 shl 3))
        @Suppress("UNCHECKED_CAST")
        var held = heldField.get(profile) as Set<TeleautoPhysicalKey>
        assertEquals(setOf(TeleautoPhysicalKey.GOHOME, TeleautoPhysicalKey.FOCUS, TeleautoPhysicalKey.C1, TeleautoPhysicalKey.C2), held)

        // bit6 拍照全按 / bit8 录像与 evdev 重复,不得经 DUML 产生按键。
        apply.invoke(profile, (1 shl 6) or (1 shl 8), 0)
        @Suppress("UNCHECKED_CAST")
        held = heldField.get(profile) as Set<TeleautoPhysicalKey>
        assertTrue(held.isEmpty())
    }
}
