package tech.alius.rc.license

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tech.alius.rc.license.internal.TeleautoLicenseClock
import java.io.DataInputStream
import java.io.ByteArrayInputStream
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class TeleautoSoftwareClockStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val directory get() = File(temporary.root, "clock-v2")
    private fun store(pkg: String = "tech.alius.test") = TeleautoSoftwareClockStore(directory, pkg)
    private fun state(wall: Long = 1000) = TeleautoLicenseClock.State(wall, 2000, 3)

    @Test fun `first commit and restart preserve encrypted clock floor`() {
        assertNull(store().read())
        assertFalse(directory.exists())
        store().write(state())
        val restored = store().read()!!
        assertEquals(1000, restored.wall)
        assertEquals(2000, restored.elapsed)
        assertEquals(3, restored.boot)
        assertEquals(1000, TeleautoLicenseClock.effectiveTime(restored, 990, 0, 4))
        assertThrows(IllegalArgumentException::class.java) { TeleautoLicenseClock.effectiveTime(restored, 500, 0, 4) }
        // Independently decode Bouncy Castle output with the JDK provider.
        val key = File(directory, "key.bin").readBytes()
        val record = File(directory, "state.bin").readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, record.copyOfRange(0, 12)))
        cipher.updateAAD("teleauto-license-clock-v2:tech.alius.test".toByteArray())
        DataInputStream(ByteArrayInputStream(cipher.doFinal(record.copyOfRange(12, record.size)))).use {
            assertEquals(2, it.readInt()); assertEquals(1000, it.readLong())
        }
    }

    @Test fun `updates use fresh nonces and preserve key`() {
        val subject = store()
        subject.write(state())
        val key = File(directory, "key.bin").readBytes()
        val before = File(directory, "state.bin").readBytes()
        subject.write(state(1010))
        val after = File(directory, "state.bin").readBytes()
        assertFalse(before.copyOfRange(0, 12).contentEquals(after.copyOfRange(0, 12)))
        assertArrayEquals(key, File(directory, "key.bin").readBytes())
        assertEquals(1010, store().read()!!.wall)
    }

    @Test fun `ciphertext tampering is rejected without replacement`() {
        store().write(state())
        val file = File(directory, "state.bin")
        val corrupt = file.readBytes().apply { this[20] = (this[20].toInt() xor 1).toByte() }
        file.writeBytes(corrupt)
        assertThrows(Exception::class.java) { store().read() }
        assertThrows(Exception::class.java) { store().write(state(1010)) }
        assertArrayEquals(corrupt, file.readBytes())
    }

    @Test fun `wrong key or package cannot authenticate existing record`() {
        store().write(state())
        assertThrows(Exception::class.java) { store("another.package").read() }
        File(directory, "key.bin").writeBytes(ByteArray(32) { 1 })
        assertThrows(Exception::class.java) { store().read() }
    }

    @Test fun `missing committed files do not regenerate a store`() {
        store().write(state())
        assertTrue(File(directory, "key.bin").delete())
        assertThrows(IllegalStateException::class.java) { store().write(state()) }
        assertFalse(File(directory, "key.bin").exists())
    }

    @Test fun `missing or truncated state fails closed`() {
        store().write(state())
        File(directory, "state.bin").writeBytes(ByteArray(10))
        assertThrows(IllegalStateException::class.java) { store().read() }
        assertTrue(File(directory, "state.bin").delete())
        assertThrows(IllegalStateException::class.java) { store().write(state()) }
    }

    @Test fun `interrupted first staging can retry without committing half a store`() {
        val pending = File(temporary.root, "clock-v2.pending")
        assertTrue(pending.mkdir())
        File(pending, "key.bin").writeBytes(ByteArray(32))
        assertNull(store().read())
        store().write(state())
        assertEquals(1000, store().read()!!.wall)
        assertFalse(pending.exists())
    }

    @Test fun `interrupted update retains last committed floor`() {
        store().write(state())
        File(directory, "state.next").writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(1000, store().read()!!.wall)
        store().write(state(1010))
        assertEquals(1010, store().read()!!.wall)
    }

    @Test fun `legacy floor is preserved and no longer accessed after migration`() {
        var reads = 0
        val subject = TeleautoSoftwareClockStore(directory, "tech.alius.test") { reads++; state(1500) }
        val old = subject.read()!!
        assertEquals(1500, old.wall)
        subject.write(old)
        assertEquals(1500, subject.read()!!.wall)
        assertEquals(1, reads)
    }

    @Test fun `migration cannot lower existing time floor`() {
        val subject = TeleautoSoftwareClockStore(directory, "tech.alius.test") { state(1500) }
        assertThrows(IllegalStateException::class.java) { subject.write(state(1000)) }
        assertFalse(directory.exists())
    }

    @Test fun `unreadable legacy record is not silently discarded`() {
        val subject = TeleautoSoftwareClockStore(directory, "tech.alius.test") { error("Unreadable legacy") }
        assertThrows(IllegalStateException::class.java) { subject.read() }
        assertThrows(IllegalStateException::class.java) { subject.write(state()) }
        assertFalse(directory.exists())
    }
}
