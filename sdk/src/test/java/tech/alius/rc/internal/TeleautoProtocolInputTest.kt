package tech.alius.rc.internal

import org.junit.Assert.*
import org.junit.Test
import tech.alius.rc.model.TeleautoKeyAction
import tech.alius.rc.model.TeleautoPhysicalKey
import tech.alius.rc.model.TeleautoRcKeyEvent
import tech.alius.rc.model.TeleautoStickSnapshot
import tech.alius.rc.profile.TeleautoRemoteControllerProfile
import tech.alius.rc.profile.rm500.TeleautoRm500Profile

class TeleautoProtocolInputTest {
    @Test fun `decode little endian wheels and high button bits`() {
        val data = ByteArray(17)
        data[2] = 12 // C1 + C2, bits 18/19
        fun axis(offset: Int, raw: Int) {
            data[offset] = raw.toByte()
            data[offset + 1] = (raw ushr 8).toByte()
        }
        axis(13, 364)
        axis(15, 1684)
        val input = TeleautoProtocolInput.parse(data)!!
        assertEquals(0xC0000, input.buttons)
        assertEquals(-1f, input.leftDial, 0f)
        assertEquals(1f, input.rightDial, 0f)
        axis(13, 1024)
        axis(15, 1354)
        assertEquals(0f, TeleautoProtocolInput.parse(data)!!.leftDial, 0f)
        assertEquals(0.5f, TeleautoProtocolInput.parse(data)!!.rightDial, 0f)
        axis(15, 65535)
        assertEquals(1f, TeleautoProtocolInput.parse(data)!!.rightDial, 0f)
        assertNull(TeleautoProtocolInput.parse(data.copyOf(16)))
        assertNull(TeleautoProtocolInput.parse(null))
    }

    @Test fun `firmware wheels and buttons resist fallback updates and release once`() {
        val profile = TeleautoRm500Profile()
        val events = mutableListOf<TeleautoRcKeyEvent>()
        val snapshots = mutableListOf<TeleautoStickSnapshot>()
        val callback = object : TeleautoRemoteControllerProfile.Callback {
            override fun onKeyEvent(event: TeleautoRcKeyEvent) {
                if (event.action != TeleautoKeyAction.TRIGGER) events += event
            }
            override fun onStickChanged(snapshot: TeleautoStickSnapshot) { snapshots += snapshot }
            override fun onHeldKeysChanged(held: Set<TeleautoPhysicalKey>) {}
            override fun onConnectionChanged(connected: Boolean) {}
        }
        profile.javaClass.getDeclaredField("callback").apply { isAccessible = true }.set(profile, callback)
        val receive = profile.javaClass.getDeclaredMethod("handleProtocolInput", TeleautoProtocolInput::class.java).apply { isAccessible = true }
        val tcp = profile.javaClass.getDeclaredMethod("handleDumlRcState", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }
        val axis = profile.javaClass.getDeclaredMethod("handleAxis", Int::class.javaPrimitiveType, Float::class.javaPrimitiveType).apply { isAccessible = true }
        val sync = profile.javaClass.getDeclaredMethod("publishAxes", Long::class.javaPrimitiveType).apply { isAccessible = true }
        receive.invoke(profile, TeleautoProtocolInput(0xC0000, -1f, 0.5f))
        receive.invoke(profile, TeleautoProtocolInput(0xC0000, -1f, 0.5f))
        tcp.invoke(profile, 0, 0)
        axis.invoke(profile, 2, 0f)
        axis.invoke(profile, 5, 0f)
        axis.invoke(profile, 0, 0.25f)
        sync.invoke(profile, 1L)
        assertEquals(2, events.size)
        assertEquals(-1f, snapshots.last().leftDial, 0f)
        assertEquals(0.5f, snapshots.last().rightDial, 0f)
        receive.invoke(profile, TeleautoProtocolInput(0, 0f, 0f))
        assertEquals(listOf(TeleautoKeyAction.DOWN, TeleautoKeyAction.DOWN, TeleautoKeyAction.UP, TeleautoKeyAction.UP), events.map { it.action })
        assertEquals(setOf(TeleautoPhysicalKey.C1, TeleautoPhysicalKey.C2), events.map { it.key }.toSet())
    }
    @Test fun `protocol reports all three switch positions without duplicate triggers`() {
        val profile = TeleautoRm500Profile()
        val events = mutableListOf<TeleautoRcKeyEvent>()
        val states = mutableListOf<tech.alius.rc.model.TeleautoThreePositionSwitchState>()
        val callback = object : TeleautoRemoteControllerProfile.Callback {
            override fun onKeyEvent(event: TeleautoRcKeyEvent) { events += event }
            override fun onStickChanged(snapshot: TeleautoStickSnapshot) {}
            override fun onHeldKeysChanged(held: Set<TeleautoPhysicalKey>) {}
            override fun onConnectionChanged(connected: Boolean) {}
            override fun onThreePositionSwitchChanged(state: tech.alius.rc.model.TeleautoThreePositionSwitchState) { states += state }
        }
        profile.javaClass.getDeclaredField("callback").apply { isAccessible = true }.set(profile, callback)
        val receive = profile.javaClass.getDeclaredMethod("handleProtocolInput", TeleautoProtocolInput::class.java).apply { isAccessible = true }
        for (position in listOf(0, 0, 1, 1, 2, 2)) {
            receive.invoke(profile, TeleautoProtocolInput(position shl 16, 0f, 0f))
        }
        assertEquals(listOf(TeleautoPhysicalKey.MODE_1, TeleautoPhysicalKey.MODE_2, TeleautoPhysicalKey.MODE_3), events.map { it.key })
        assertTrue(events.all { it.action == TeleautoKeyAction.TRIGGER })
        assertEquals(listOf(0, 1, 2), states.map { it.rawValue })
    }

}
