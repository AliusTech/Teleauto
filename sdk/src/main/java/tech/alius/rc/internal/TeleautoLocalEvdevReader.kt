package tech.alius.rc.internal

import android.view.InputDevice
import android.view.MotionEvent
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reads an embedded Linux input node without requiring an ADB daemon. */
internal class TeleautoLocalEvdevReader(
    private val name: String,
    private val vendorId: Int,
    private val productId: Int,
    private val onAxis: (code: Int, normalized: Float) -> Unit,
    private val onKey: (code: Int, down: Boolean) -> Unit,
    private val onState: (Boolean) -> Unit,
) {
    @Volatile private var running = false
    @Volatile private var stream: FileInputStream? = null
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread(::runLoop, "TeleautoLocalEvdevReader").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        runCatching { stream?.close() }
        stream = null
    }

    private fun runLoop() {
        var connected = false
        while (running) {
            try {
                val node = findNode() ?: error("embedded joystick node not found")
                val ranges = motionRanges()
                FileInputStream(node).use { input ->
                    stream = input
                    connected = true
                    onState(true)
                    Log.i(TAG, "reading ${node.absolutePath} ($name ${vendorId.toString(16)}:${productId.toString(16)})")
                    readEvents(input, ranges)
                }
            } catch (e: Exception) {
                if (running) Log.w(TAG, "evdev unavailable: ${e.message}")
            } finally {
                stream = null
                if (connected) {
                    connected = false
                    onState(false)
                }
            }
            if (running) runCatching { Thread.sleep(RETRY_MS) }
        }
    }

    private fun findNode(): File? = File("/sys/class/input").listFiles()
        ?.asSequence()
        ?.filter { it.name.startsWith("event") }
        ?.firstOrNull { entry ->
            val device = File(entry, "device")
            readText(File(device, "name")).equals(name, ignoreCase = true) &&
                readHex(File(device, "id/vendor")) == vendorId &&
                readHex(File(device, "id/product")) == productId
        }
        ?.let { File("/dev/input", it.name) }

    private fun readEvents(input: FileInputStream, ranges: Map<Int, ClosedFloatingPointRange<Float>>) {
        val bytes = ByteArray(INPUT_EVENT_SIZE)
        while (running) {
            var offset = 0
            while (offset < bytes.size) {
                val count = input.read(bytes, offset, bytes.size - offset)
                if (count < 0) return
                offset += count
            }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
            buffer.position(16) // timeval is two 64-bit longs on the supported arm64 ABI
            val type = buffer.short.toInt() and 0xffff
            val code = buffer.short.toInt() and 0xffff
            val value = buffer.int
            when (type) {
                EV_KEY -> onKey(code, value != 0)
                EV_ABS -> onAxis(code, normalize(value, ranges[code]))
            }
        }
    }

    private fun motionRanges(): Map<Int, ClosedFloatingPointRange<Float>> {
        val device = InputDevice.getDeviceIds().asSequence()
            .mapNotNull(InputDevice::getDevice)
            .firstOrNull { it.vendorId == vendorId && it.productId == productId && it.name.equals(name, true) }
            ?: return emptyMap()
        return LINUX_TO_ANDROID_AXIS.mapNotNull { (linux, android) ->
            device.getMotionRange(android)?.let { range ->
                linux to (range.min..range.max)
            }
        }.toMap()
    }

    private fun normalize(value: Int, range: ClosedFloatingPointRange<Float>?): Float {
        if (range == null || range.endInclusive <= range.start) return 0f
        val center = (range.start + range.endInclusive) / 2f
        val half = (range.endInclusive - range.start) / 2f
        return ((value - center) / half).coerceIn(-1f, 1f)
    }

    private fun readText(file: File) = runCatching { file.readText().trim() }.getOrDefault("")
    private fun readHex(file: File) = readText(file).toIntOrNull(16) ?: -1

    companion object {
        private const val TAG = "TeleautoSdk-LocalEvdev"
        private const val INPUT_EVENT_SIZE = 24
        private const val RETRY_MS = 1_000L
        private const val EV_KEY = 0x01
        private const val EV_ABS = 0x03
        private val LINUX_TO_ANDROID_AXIS = mapOf(
            0 to MotionEvent.AXIS_X, 1 to MotionEvent.AXIS_Y, 2 to MotionEvent.AXIS_Z,
            3 to MotionEvent.AXIS_RX, 4 to MotionEvent.AXIS_RY, 5 to MotionEvent.AXIS_RZ,
            16 to MotionEvent.AXIS_HAT_X, 17 to MotionEvent.AXIS_HAT_Y,
        )
    }
}
