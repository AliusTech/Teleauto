package tech.alius.rc.profile

import org.junit.Assert.*
import org.junit.Test
import tech.alius.rc.internal.TeleautoProtocolInput
import tech.alius.rc.model.*
import tech.alius.rc.profile.rcpro.*

class TeleautoRcProProfileTest {
    @Test fun `consumer models remain distinct and exact`() {
        TeleautoProfileRegistry.register(TeleautoRm510Profile())
        TeleautoProfileRegistry.register(TeleautoRc520Profile())
        assertEquals(TeleautoHardwareProfile.RM510, TeleautoProfileRegistry.detect(" RM510 ")!!.hardwareProfile)
        assertEquals("DJI RC PRO 2", TeleautoProfileRegistry.detect("rc520")!!.displayName("rc520"))
        assertNull(TeleautoProfileRegistry.detect("rm510-unknown"))
        assertFalse(TeleautoRc520Profile().supportedInputs.contains(TeleautoPhysicalKey.L1))
    }

    @Test fun `consumer packet maps axes button edges and switch then resets`() {
        val profile = TeleautoRm510Profile()
        val events = mutableListOf<TeleautoRcKeyEvent>()
        val sticks = mutableListOf<TeleautoStickSnapshot>()
        val modes = mutableListOf<Int>()
        val connections = mutableListOf<Boolean>()
        val sink = object : TeleautoRemoteControllerProfile.Callback {
            override fun onKeyEvent(event: TeleautoRcKeyEvent) { events += event }
            override fun onStickChanged(snapshot: TeleautoStickSnapshot) { sticks += snapshot }
            override fun onHeldKeysChanged(held: Set<TeleautoPhysicalKey>) {}
            override fun onConnectionChanged(connected: Boolean) { connections += connected }
            override fun onThreePositionSwitchChanged(state: TeleautoThreePositionSwitchState) { modes += state.rawValue }
        }
        TeleautoRcProProfile::class.java.getDeclaredField("callback").apply { isAccessible = true }.set(profile, sink)
        val data = ByteArray(17)
        data[2] = 0x1e // C1/C2/C3 and switch position 2
        fun axis(offset: Int, value: Int) { data[offset] = value.toByte(); data[offset + 1] = (value ushr 8).toByte() }
        axis(5, 1684); axis(7, 364); axis(9, 1354); axis(11, 694); axis(13, 1024); axis(15, 1684)
        val packet = TeleautoProtocolInput.parse(data)!!
        profile.accept(packet); profile.accept(packet)
        assertEquals(listOf(true), connections)
        assertEquals(listOf(2), modes)
        assertEquals(setOf(TeleautoPhysicalKey.C1, TeleautoPhysicalKey.C2, TeleautoPhysicalKey.C3), events.filter { it.action == TeleautoKeyAction.DOWN }.map { it.key }.toSet())
        assertEquals(4, events.size)
        assertEquals(-0.5f, sticks.last().leftX, 0f)
        assertEquals(0.5f, sticks.last().leftY, 0f)
        assertEquals(1f, sticks.last().rightX, 0f)
        assertEquals(-1f, sticks.last().rightY, 0f)
        profile.stop()
        assertEquals(3, events.count { it.action == TeleautoKeyAction.UP })
        assertEquals(listOf(true, false), connections)
        val count = events.size
        profile.accept(packet)
        assertEquals(count, events.size)
    }
}
