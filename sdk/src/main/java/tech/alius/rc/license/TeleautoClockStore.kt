package tech.alius.rc.license

import android.content.Context
import android.util.AtomicFile
import tech.alius.rc.license.internal.TeleautoLicenseClock
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** No AndroidKeyStore use for new or migrated software stores. */
internal class TeleautoClockStore(context: Context) {
    private val legacy = AtomicFile(File(context.noBackupFilesDir, "teleauto-license-clock"))
    private val packageName = context.packageName
    private val software = TeleautoSoftwareClockStore(
        File(context.noBackupFilesDir, "teleauto-license-clock-v2"), packageName,
        legacyReader = ::readLegacy,
    )

    fun read(): TeleautoLicenseClock.State? = software.read()
    fun write(state: TeleautoLicenseClock.State) = software.write(state)

    /** Read-only migration on devices that previously saved a hardware-backed record.
     * A missing legacy file means no time floor was committed. In particular the
     * orphaned alias from an RM500 first-write failure does not invoke KeyStore.
     * An existing but unreadable record fails closed instead of discarding its floor.
     */
    private fun readLegacy(): TeleautoLicenseClock.State? {
        if (!legacy.baseFile.exists() && !File(legacy.baseFile.path + ".bak").exists()) return null
        val bytes = legacy.openRead().use { stream ->
            val buffer = ByteArray(1025)
            var count = 0
            while (count < buffer.size) {
                val size = stream.read(buffer, count, buffer.size - count)
                if (size < 0) break
                count += size
            }
            buffer.copyOf(count)
        }
        check(bytes.size == 12 + 28 + 16) { "Invalid legacy clock record" }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = store.getKey("teleauto.license.clock.v1", null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(packageName.toByteArray(Charsets.UTF_8))
        return DataInputStream(ByteArrayInputStream(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))).use {
            check(it.readInt() == 1)
            TeleautoLicenseClock.State(it.readLong(), it.readLong(), it.readLong()).also { state ->
                check(it.available() == 0 && state.wall >= 0 && state.elapsed >= 0)
            }
        }
    }
}
