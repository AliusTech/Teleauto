package tech.alius.rc.internal

import android.util.Log
import java.io.FileInputStream
import java.io.FileNotFoundException
import kotlin.math.abs

/**
 * Reads a Linux input_event node directly from the application process.
 *
 * This reader never opens an ADB connection, changes adbd properties, or
 * launches a privileged child process. Device-node access must be provisioned
 * before the SDK starts.
 */
internal class TeleautoDirectInputEventReader(
    private val devicePath: String,
    private val onAxis: (code: Int, raw: Int, normalized: Float) -> Unit,
    private val onKey: (code: Int, down: Boolean) -> Unit,
    private val onSync: (timestampNs: Long) -> Unit,
    private val onState: (connected: Boolean) -> Unit,
    private val onPermissionRequired: (required: Boolean) -> Unit,
) {
    @Volatile private var running = false
    @Volatile private var input: FileInputStream? = null
    private var thread: Thread? = null

    fun start() {
        if (running) return
        require(EVENT_PATH.matches(devicePath)) { "invalid input event path: $devicePath" }
        running = true
        thread = Thread(::runLoop, "TeleautoDirectInputEventReader").apply {
            isDaemon = true
            start()
        }
    }

    private fun runLoop() {
        var delayMs = INITIAL_RETRY_MS
        var permissionRequired = false
        while (running) {
            var connected = false
            try {
                val source = FileInputStream(devicePath)
                input = source
                if (permissionRequired) {
                    permissionRequired = false
                    onPermissionRequired(false)
                }
                connected = true
                onState(true)
                delayMs = INITIAL_RETRY_MS
                Log.i(TAG, "direct input connected $devicePath")

                val parser = TeleautoEvdevBinaryParser { type, code, value ->
                    when (type) {
                        TeleautoEvdevBinaryParser.EV_ABS -> onAxis(code, value, normalize(code, value))
                        TeleautoEvdevBinaryParser.EV_KEY -> onKey(code, value != 0)
                        TeleautoEvdevBinaryParser.EV_SYN -> if (code == TeleautoEvdevBinaryParser.SYN_REPORT) {
                            onSync(System.nanoTime())
                        }
                    }
                }
                val buffer = ByteArray(BUFFER_SIZE)
                while (running) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    if (count > 0) parser.feed(buffer, count)
                }
                if (running) throw IllegalStateException("direct input stream ended")
            } catch (error: Exception) {
                val denied = error.isPermissionDenied()
                if (denied != permissionRequired) {
                    permissionRequired = denied
                    onPermissionRequired(denied)
                }
                if (running) Log.w(TAG, "direct input unavailable: ${error.message}")
            } finally {
                runCatching { input?.close() }
                input = null
                if (connected) onState(false)
            }
            if (running) {
                try {
                    Thread.sleep(delayMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                delayMs = (delayMs * 2).coerceAtMost(MAX_RETRY_MS)
            }
        }
    }

    private fun Exception.isPermissionDenied(): Boolean =
        this is SecurityException ||
            (this is FileNotFoundException && message.orEmpty().contains("Permission denied", true))

    private fun normalize(code: Int, raw: Int): Float {
        val (minimum, maximum, flat) = when (code) {
            0, 1, 3, 4 -> Range(-32768, 32767, 128)
            2, 5 -> Range(0, 1023, 0)
            16, 17 -> Range(-1, 1, 0)
            else -> Range(0, 1, 0)
        }
        val center = (minimum + maximum) / 2f
        val half = (maximum - minimum) / 2f
        if (half <= 0f) return 0f
        var value = (raw - center) / half
        val deadzone = maxOf(0.04f, flat / half)
        if (abs(value) < deadzone) value = 0f
        return value.coerceIn(-1f, 1f)
    }

    fun stop() {
        running = false
        runCatching { input?.close() }
        input = null
        thread?.interrupt()
        thread = null
    }

    private data class Range(val minimum: Int, val maximum: Int, val flat: Int)

    private companion object {
        const val TAG = "TeleautoSdk-DirectInput"
        const val BUFFER_SIZE = 4096
        const val INITIAL_RETRY_MS = 1_000L
        const val MAX_RETRY_MS = 30_000L
        val EVENT_PATH = Regex("/dev/input/event\\d+")
    }
}
