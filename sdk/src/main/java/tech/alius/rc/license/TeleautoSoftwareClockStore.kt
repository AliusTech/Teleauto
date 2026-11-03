package tech.alius.rc.license

import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import tech.alius.rc.license.internal.TeleautoLicenseClock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom

/** Software keys are protected by the app sandbox, not by tamper-resistant hardware. */
internal class TeleautoSoftwareClockStore(
    private val directory: File,
    packageName: String,
    private val legacyReader: () -> TeleautoLicenseClock.State? = { null },
) {
    private val random = SecureRandom()
    private var legacyLoaded = false
    private var legacyState: TeleautoLicenseClock.State? = null
    private val aad = "teleauto-license-clock-v2:$packageName".toByteArray(Charsets.UTF_8)
    private val keyFile get() = File(directory, "key.bin")
    private val stateFile get() = File(directory, "state.bin")

    @Synchronized
    fun read(): TeleautoLicenseClock.State? {
        if (!directory.exists()) return loadLegacy()
        check(directory.isDirectory && keyFile.isFile && stateFile.isFile) { "Incomplete software clock store" }
        return decode(boundedRead(stateFile), loadKey())
    }

    @Synchronized
    fun write(state: TeleautoLicenseClock.State) {
        check(state.wall >= 0 && state.elapsed >= 0)
        if (directory.exists()) {
            // Never regenerate a missing key or silently replace damaged state.
            read()
            val next = File(directory, "state.next")
            writePrivate(next, encode(state, loadKey()))
            check(next.renameTo(stateFile)) { "Cannot commit clock update" }
            return
        }
        val old = loadLegacy()
        check(old == null || state.wall >= old.wall) { "Cannot lower migrated clock floor" }
        // Commit both files with one same-filesystem directory rename. A crash
        // before this point leaves no accepted state and can safely be retried.
        val pending = File(directory.parentFile, "${directory.name}.pending")
        if (pending.exists()) {
            check(pending.isDirectory)
            check(pending.listFiles().orEmpty().all { it.name == "key.bin" || it.name == "state.bin" })
            pending.listFiles().orEmpty().forEach { check(it.delete()) }
            check(pending.delete())
        }
        check(pending.mkdir()) { "Cannot stage clock store" }
        check(pending.setReadable(false, false) && pending.setReadable(true, true))
        check(pending.setWritable(false, false) && pending.setWritable(true, true))
        check(pending.setExecutable(false, false) && pending.setExecutable(true, true))
        val key = ByteArray(32).also(random::nextBytes)
        try {
            writePrivate(File(pending, "key.bin"), key)
            writePrivate(File(pending, "state.bin"), encode(state, key))
            check(pending.renameTo(directory)) { "Cannot commit clock store" }
        } finally {
            key.fill(0)
        }
    }

    private fun loadLegacy(): TeleautoLicenseClock.State? {
        if (!legacyLoaded) {
            legacyState = legacyReader()
            legacyLoaded = true
        }
        return legacyState
    }

    private fun loadKey(): ByteArray = boundedRead(keyFile).also { check(it.size == 32) { "Invalid clock key" } }

    private fun encode(state: TeleautoLicenseClock.State, key: ByteArray): ByteArray {
        val plain = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use {
                it.writeInt(2); it.writeLong(state.wall); it.writeLong(state.elapsed); it.writeLong(state.boot)
            }
        }.toByteArray()
        val nonce = ByteArray(12).also(random::nextBytes)
        return nonce + crypt(true, key, nonce, plain)
    }

    private fun decode(record: ByteArray, key: ByteArray): TeleautoLicenseClock.State {
        check(record.size == 12 + 28 + 16) { "Invalid clock record size" }
        val plain = crypt(false, key, record.copyOfRange(0, 12), record.copyOfRange(12, record.size))
        return DataInputStream(ByteArrayInputStream(plain)).use {
            check(it.readInt() == 2) { "Unsupported clock record" }
            TeleautoLicenseClock.State(it.readLong(), it.readLong(), it.readLong()).also { state ->
                check(it.available() == 0 && state.wall >= 0 && state.elapsed >= 0)
            }
        }
    }

    private fun crypt(encrypt: Boolean, key: ByteArray, nonce: ByteArray, input: ByteArray): ByteArray {
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        cipher.init(encrypt, AEADParameters(KeyParameter(key), 128, nonce, aad))
        val output = ByteArray(cipher.getOutputSize(input.size))
        val count = cipher.processBytes(input, 0, input.size, output, 0)
        val total = count + cipher.doFinal(output, count)
        return output.copyOf(total)
    }

    private fun boundedRead(file: File): ByteArray = file.inputStream().use { stream ->
        val bytes = ByteArray(1025)
        var count = 0
        while (count < bytes.size) {
            val size = stream.read(bytes, count, bytes.size - count)
            if (size < 0) break
            count += size
        }
        check(count <= 1024) { "Clock file too large" }
        bytes.copyOf(count)
    }

    private fun writePrivate(file: File, bytes: ByteArray) {
        FileOutputStream(file).use {
            check(file.setReadable(false, false) && file.setReadable(true, true))
            check(file.setWritable(false, false) && file.setWritable(true, true))
            it.write(bytes)
            it.fd.sync()
        }
    }
}
