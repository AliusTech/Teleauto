package tech.alius.rc.internal

import android.content.Context
import android.util.Base64
import android.util.Log
import com.tananaev.adblib.TeleautoAdbBase64
import com.tananaev.adblib.TeleautoAdbConnection
import com.tananaev.adblib.TeleautoAdbCrypto
import com.tananaev.adblib.TeleautoAdbStream
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs
import java.util.Arrays
import java.util.concurrent.TimeUnit
import tech.alius.rc.BuildConfig

private class TeleautoAndroidBase64 : TeleautoAdbBase64 {
    override fun encodeToString(data: ByteArray): String =
        Base64.encodeToString(data, Base64.NO_WRAP)
}

/** 内部:adb 自连跑 getevent -l,解析 evdev(摇杆 ABS / 拨轮 / 拍照 BTN_TR) */
internal class TeleautoAdbInputReader(
    private val onAxis: (code: Int, raw: Int, norm: Float) -> Unit,
    private val onKey: (code: Int, down: Boolean) -> Unit,
    private val onState: (connected: Boolean) -> Unit,
    private val onAuthorizationRequired: (required: Boolean) -> Unit = {},
    private val onTransportPreparationRequired: (required: Boolean) -> Unit = {},
    private val devicePath: String? = null,
    private val onSync: (timestampNs: Long) -> Unit = {},
    private val auxiliaryCommand: String? = null,
    private val onAuxiliaryData: (ByteArray) -> Unit = {},
) {
    private var thread: Thread? = null
    @Volatile private var running = false

    // 指数退避:连续失败时 1.5s→3s→…→30s 封顶,避免 adbd 不可用时 1.5s 重试刷屏空转
    private var failedAttempts = 0
    private fun reconnectDelayMs(): Long {
        val d = (1500L shl failedAttempts.coerceAtMost(5)).coerceAtMost(30_000L)
        failedAttempts++
        return d
    }
    @Volatile private var connection: TeleautoAdbConnection? = null
    @Volatile private var stream: TeleautoAdbStream? = null
    @Volatile private var auxiliaryStream: TeleautoAdbStream? = null
    private var auxiliaryThread: Thread? = null
    @Volatile private var socket: Socket? = null
    @Volatile private var connected = false

    private val skippedDevices = HashSet<String>()
    private var currentDevice = ""
    // 跨 chunk 的行缓冲:流数据任意切分,不能假设 chunk 边界对齐行边界
    private val pending = StringBuilder()
    private var useBinary = devicePath != null
    private val transportSamplesMs = LongArray(256)
    private var transportCursor = 0
    private var transportCount = 0
    private var transportFrames = 0L
    private var transportReportAtMs = System.currentTimeMillis()
    private val discoveredAbsRanges = HashMap<Int, AxisRange>()

    private val absRe = Regex("EV_ABS\\s+(ABS_\\w+)\\s+([0-9a-fA-F]+)")
    private val keyRe = Regex("EV_KEY\\s+(BTN_\\w+|KEY_\\w+)\\s+(DOWN|UP)")
    private val devRe = Regex("(/dev/input/event\\d+):\\s*(.*)")
    private val addRe = Regex("add device\\s+\\d+:\\s*(/dev/input/event\\d+)")
    private val nameRe = Regex("name:\\s*\"([^\"]+)\"")

    fun start(context: Context) {
        if (running) return
        if (!TeleautoLocalAdbTransportLease.acquire(this)) {
            Log.w(TAG, "single internal ADB transport already owned; duplicate reader suppressed")
            onState(false)
            return
        }
        running = true
        thread = Thread {
            try {
                while (running) {
                    try { connectAndRead(context) }
                    catch (e: Exception) {
                        Log.w(TAG, "adb error: ${e.message}")
                        if (connected) { connected = false; onState(false) }
                        closeQuietly()
                    }
                    if (running) try { Thread.sleep(reconnectDelayMs()) } catch (_: Exception) {}
                }
            } finally {
                closeQuietly()
                TeleautoLocalAdbTransportLease.release(this)
            }
        }.apply { isDaemon = true; name = "TeleautoAdbInputReader"; start() }
    }

    private fun connectAndRead(context: Context) {
        val localAdb = TeleautoLocalAdbBootstrap.ensureAvailable()
        if (localAdb != TeleautoLocalAdbStatus.AVAILABLE && localAdb != TeleautoLocalAdbStatus.STARTED) {
            if (failedAttempts >= 2) onTransportPreparationRequired(true)
            throw IllegalStateException("local adb unavailable: $localAdb")
        }
        val key = loadOrCreateKey(context)
        socket = Socket()
        socket!!.connect(InetSocketAddress("localhost", 5555), 3000)
        socket!!.soTimeout = ADB_HANDSHAKE_TIMEOUT_MS
        connection = TeleautoAdbConnection.create(socket!!, key).apply {
            // Do not abort after the first rejected signature. adbd must receive
            // our public key so Android can show its RSA authorization dialog.
            // Once the user accepts it, this same connection completes normally
            // and subsequent launches authenticate without UI.
            if (!connect(ADB_HANDSHAKE_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS, false)) {
                onAuthorizationRequired(true)
                throw java.io.IOException(
                    "local adb authentication timed out; accept the RSA authorization dialog",
                )
            }
        }
        socket!!.soTimeout = 0
        onAuthorizationRequired(false)
        onTransportPreparationRequired(false)
        connected = true; onState(true)
        failedAttempts = 0 // 连接成功,重置退避
        Log.i(TAG, "adb connected localhost:5555")
        if (devicePath != null) discoverAbsRanges(connection!!, devicePath)
        val command = when {
            useBinary && devicePath != null -> "shell:cat $devicePath"
            devicePath != null -> "shell:getevent -l $devicePath"
            else -> "shell:getevent -l"
        }
        stream = connection!!.open(command)
        startAuxiliaryStream(connection!!)
        lateinit var parser: TeleautoEvdevBinaryParser
        val binaryParser = if (useBinary) TeleautoEvdevBinaryParser { type, code, value ->
            when (type) {
                TeleautoEvdevBinaryParser.EV_ABS -> {
                    val normalized = normalizeAbs(code, value)
                    if (BuildConfig.DEBUG && code in 0..5) {
                        Log.d(TAG, "axis_event code=$code raw=$value normalized=$normalized")
                    }
                    onAxis(code, value, normalized)
                }
                TeleautoEvdevBinaryParser.EV_KEY -> onKey(code, value != 0)
                TeleautoEvdevBinaryParser.EV_SYN -> if (code == TeleautoEvdevBinaryParser.SYN_REPORT) {
                    recordTransportLatency(parser.lastEventTimestampMs)
                    onSync(System.nanoTime())
                }
            }
        }.also { parser = it } else null
        while (running) {
            val data = stream!!.read() ?: break
            if (binaryParser != null) {
                binaryParser.feed(data)
                if (binaryParser.invalidBytes > MAX_INVALID_BINARY_BYTES) {
                    Log.w(TAG, "unrecognized binary input_event layout; falling back to getevent -l")
                    useBinary = false
                    throw IllegalStateException("invalid input_event stream")
                }
            } else {
                parseText(String(data, Charsets.US_ASCII))
            }
        }
        if (running && binaryParser != null && binaryParser.parsedEvents == 0L) {
            useBinary = false
            throw IllegalStateException("binary input_event unavailable")
        }
        if (connected) { connected = false; onState(false) }
    }

    private fun parseText(text: String) {
        pending.append(text)
        while (true) {
            val idx = pending.indexOf('\n')
            if (idx < 0) break
            val line = pending.substring(0, idx)
            pending.delete(0, idx + 1)
            handleLine(line)
        }
    }

    private fun handleLine(line: String) {
        // 单设备模式(getevent -l <path>)输出行不带 "/dev/input/eventN:" 前缀,直接解析整行
        val rest: String = if (devicePath != null) {
            line
        } else {
            val addM = addRe.find(line)
            if (addM != null) { currentDevice = addM.groupValues[1]; return }
            val nameM = nameRe.find(line)
            if (nameM != null) {
                if (nameM.groupValues[1].contains("fts_ts")) skippedDevices.add(currentDevice)
                return
            }
            val dm = devRe.find(line) ?: return
            if (dm.groupValues[1] in skippedDevices) return
            dm.groupValues[2]
        }
        if (rest.contains("EV_SYN") && rest.contains("SYN_REPORT")) {
            onSync(System.nanoTime())
            return
        }
        absRe.find(rest)?.let { m ->
            val code = absCodeOf(m.groupValues[1])
            if (code != null) {
                val raw = m.groupValues[2].toLong(16).toInt()
                onAxis(code, raw, normalizeAbs(code, raw))
            }
        }
        keyRe.find(rest)?.let { m ->
            val code = keyCodeOf(m.groupValues[1])
            if (code >= 0) onKey(code, m.groupValues[2] == "DOWN")
        }
    }

    private fun closeQuietly() {
        try { auxiliaryStream?.close() } catch (_: Exception) {}
        try { stream?.close() } catch (_: Exception) {}
        try { connection?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
    }

    /** Open model-specific key logs on the same ADB transport; old DJI adbd drops competing clients. */
    private fun startAuxiliaryStream(adb: TeleautoAdbConnection) {
        val command = auxiliaryCommand ?: return
        val opened = adb.open(command)
        auxiliaryStream = opened
        auxiliaryThread = Thread {
            try {
                while (running && auxiliaryStream === opened) {
                    val data = opened.read() ?: break
                    onAuxiliaryData(data)
                }
            } catch (e: Exception) {
                if (running) Log.w(TAG, "auxiliary stream ended: ${e.message}")
            }
        }.apply { isDaemon = true; name = "AdbInputAuxiliary"; start() }
    }

    private fun normalizeAbs(code: Int, raw: Int): Float {
        val range = discoveredAbsRanges[code]
        val (mn, mx, flat) = range ?: when (code) {
            0, 1, 3, 4 -> AxisRange(-32768, 32767, 128)
            2, 5 -> AxisRange(0, 255, 0)
            16, 17 -> AxisRange(-1, 1, 0)
            else -> AxisRange(0, 1, 0)
        }
        if (mx == mn) return 0f
        val c = (mn + mx) / 2f; val h = (mx - mn) / 2f
        var v = (raw - c) / h
        val fn = if (h > 0) flat / h else 0f
        val dz = if (fn > 0.04f) fn else 0.04f
        if (abs(v) < dz) v = 0f
        return v.coerceIn(-1f, 1f)
    }

    /**
     * DJI 各代遥控器的回中滚轮分辨率并不一致（已见 0..255 与 0..1023）。
     * 连接时从 evdev 能力表读取真实 min/max/flat，避免把 511 中位误判成 +100%。
     */
    private fun discoverAbsRanges(adb: TeleautoAdbConnection, path: String) {
        val capabilityStream = try { adb.open("shell:getevent -lp $path") } catch (e: Exception) {
            Log.w(TAG, "cannot query axis ranges: ${e.message}")
            return
        }
        val output = StringBuilder(2048)
        try {
            while (output.length < MAX_CAPABILITY_TEXT) {
                val chunk = capabilityStream.read() ?: break
                output.append(String(chunk, Charsets.US_ASCII))
            }
        } catch (e: Exception) {
            // adblib may report CLSE as an exception after all command output was delivered.
            if (output.isEmpty()) Log.w(TAG, "axis range query interrupted: ${e.message}")
        } finally {
            try { capabilityStream.close() } catch (_: Exception) {}
        }
        discoveredAbsRanges.clear()
        ABS_RANGE_RE.findAll(output).forEach { match ->
            val code = absCodeOf("ABS_${match.groupValues[1]}") ?: return@forEach
            discoveredAbsRanges[code] = AxisRange(
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt(),
                match.groupValues[4].toInt(),
            )
        }
        if (discoveredAbsRanges.isNotEmpty()) {
            Log.i(TAG, "axis ranges: ${discoveredAbsRanges.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value.min}..${it.value.max}" }}")
        }
    }

    private data class AxisRange(val min: Int, val max: Int, val flat: Int)

    private fun recordTransportLatency(kernelTimestampMs: Long) {
        if (!BuildConfig.DEBUG || kernelTimestampMs <= 0L) return
        val now = System.currentTimeMillis()
        val latency = (now - kernelTimestampMs).coerceAtLeast(0L)
        transportSamplesMs[transportCursor] = latency
        transportCursor = (transportCursor + 1) % transportSamplesMs.size
        if (transportCount < transportSamplesMs.size) transportCount++
        transportFrames++
        if (now - transportReportAtMs < 5_000L) return
        val sorted = transportSamplesMs.copyOf(transportCount)
        Arrays.sort(sorted)
        fun p(fraction: Double) = sorted[((sorted.size - 1) * fraction).toInt()]
        val seconds = (now - transportReportAtMs).coerceAtLeast(1L) / 1_000.0
        Log.d(TAG, "adb_transport raw=${"%.1f".format(transportFrames / seconds)}Hz " +
            "latency_ms p50=${p(.50)} p95=${p(.95)} max=${sorted.last()}")
        transportFrames = 0; transportReportAtMs = now
    }

    private fun loadOrCreateKey(context: Context): TeleautoAdbCrypto {
        val base64 = TeleautoAndroidBase64()
        val dir = File(context.filesDir, "adbkey").apply { if (!exists()) mkdirs() }
        val priv = File(dir, "priv.key"); val pub = File(dir, "pub.key")
        return try { TeleautoAdbCrypto.loadAdbKeyPair(base64, priv, pub) }
        catch (e: Exception) { TeleautoAdbCrypto.generateAdbKeyPair(base64).apply { saveAdbKeyPair(priv, pub) } }
    }

    fun stop() {
        running = false; connected = false
        try { socket?.close() } catch (_: Exception) {}
        try { auxiliaryStream?.close() } catch (_: Exception) {}
        try { stream?.close() } catch (_: Exception) {}
        try { connection?.close() } catch (_: Exception) {}
        // All transport resources are closed synchronously above, so a
        // lifecycle replacement may safely acquire the sole lease now.
        TeleautoLocalAdbTransportLease.release(this)
    }

    companion object {
        private const val TAG = "TeleautoSdk-Evdev"
        private const val ADB_HANDSHAKE_TIMEOUT_MS = 8_000
        private const val MAX_INVALID_BINARY_BYTES = 96
        private const val MAX_CAPABILITY_TEXT = 64 * 1024
        private val ABS_RANGE_RE = Regex(
            "ABS_(X|Y|Z|RX|RY|RZ|HAT0X|HAT0Y)\\s*: value\\s+-?\\d+, min\\s+(-?\\d+), max\\s+(-?\\d+), fuzz\\s+\\d+, flat\\s+(\\d+)",
        )
        fun absCodeOf(name: String): Int? = when (name) {
            "ABS_X" -> 0; "ABS_Y" -> 1; "ABS_Z" -> 2; "ABS_RX" -> 3
            "ABS_RY" -> 4; "ABS_RZ" -> 5; "ABS_HAT0X" -> 16; "ABS_HAT0Y" -> 17
            else -> null
        }
        // GPIO 面板肩键(KEY_F1–F6,经 gpio-keys 设备)与 拍照(BTN_TR)、电源键(KEY_POWER)统一走 evdev
        fun keyCodeOf(name: String): Int = when (name) {
            "BTN_TR" -> 311
            "BTN_THUMBL" -> 317
            "BTN_DPAD_UP" -> 544
            "BTN_DPAD_DOWN" -> 545
            "BTN_DPAD_LEFT" -> 546
            "BTN_DPAD_RIGHT" -> 547
            "KEY_POWER" -> 116
            "KEY_F1" -> 187
            "KEY_F2" -> 188
            "KEY_F3" -> 189
            "KEY_F4" -> 190
            "KEY_F5" -> 191
            "KEY_F6" -> 192
            else -> -1
        }
    }
}
