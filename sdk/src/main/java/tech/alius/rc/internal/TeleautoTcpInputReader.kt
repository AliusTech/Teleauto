package tech.alius.rc.internal

import android.util.Log
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.roundToInt

/**
 * 内部:固件 DUML 遥测通道。40007 使用 8 字节 TCP 封装并以约 10Hz 推送，
 * 40009 是低频裸 DUML 流。连续波轮必须走 40007，才能逐格可靠上报。
 *
 * rc701 系的 三段式开关 与 右侧无限连续波轮 不经过 Android 输入栈(evdev)
 * 与 dpad 广播,由固件经本机 DUML 遥测端口提供(普通应用无 root 即可连接,
 * 已实测)。40009 相比 40007 的优势:0xAE 状态帧多带一字段——无限波轮位置。
 *
 * 帧格式(逆向自实测抓包):
 *   裸 DUML 帧:pkt[0]=0x55(SOF), pkt[1]=帧总长(如 0x1e=30),
 *              pkt[9]=cmdSet, pkt[10]=cmdId, 尾部 2 字节 CRC
 *   0xAE 遥控器状态数据(pkt[11..帧长-2]):
 *     [0..1]=按键位域 u16(bit5=返航 bit6=拍照全按 bit7=半按 bit8=录像)
 *     [2..3]=模式 u16(低 2 位=三段开关档位 0/1/2,bit2=C1,bit3=C2)
 *     [4]=右侧无限连续波轮一圈内位置(u8,0-255 回绕)
 *
 * 注:33/0x06 帧为 1Hz 自增心跳计数器(不操作也持续 +1),与输入无关,已忽略。
 */
internal class TeleautoTcpInputReader(
    private val port: Int,
    private val onMode: (pos: Int) -> Unit,
    private val onFreeDial: (norm: Float) -> Unit,
    private val onState: (connected: Boolean) -> Unit,
    private val onRcState: (buttonBits: Int, modeBits: Int) -> Unit = { _, _ -> },
    private val freeDialStepsPerFullScale: Int = DEFAULT_DIAL_STEPS_FULL_SCALE,
) {
    init { require(freeDialStepsPerFullScale > 0) }
    @Volatile private var running = false
    @Volatile private var connected = false
    private var thread: Thread? = null
    private var socket: Socket? = null

    // 无限波轮:脉冲语义(每帧 = 方向 + 步进格数),累计成连续值
    @Volatile internal var dialAccum = 0
    @Volatile private var dialValue = 0f
    internal var dbgFrames = 0
    @Volatile var frameCount = 0L

    fun start() {
        if (running) return
        running = true
        thread = Thread {
            while (running) {
                try { connectAndRead() }
                catch (e: Exception) {
                    if (connected) { connected = false; onState(false) }
                    closeQuietly()
                }
                if (running) try { Thread.sleep(3000) } catch (_: Exception) {}
            }
        }.apply { isDaemon = true; name = "TeleautoTcpInputReader"; start() }
    }

    fun stop() {
        running = false
        connected = false
        dialAccum = 0
        dialValue = 0f
        try { socket?.close() } catch (_: Exception) {}
    }

    /** 指定当前标准化位置；后续脉冲从该值继续累计。 */
    fun setFreeDialValue(value: Float): Boolean {
        if (!value.isFinite() || value !in -1f..1f) return false
        synchronized(this) {
            dialValue = value
            dialAccum = (value * freeDialStepsPerFullScale).roundToInt()
        }
        onFreeDial(value)
        return true
    }

    private fun connectAndRead() {
        val s = Socket()
        socket = s
        s.connect(InetSocketAddress("127.0.0.1", port), 3000)
        connected = true; onState(true)
        // TCP 重连只代表传输恢复，不应丢失宿主通过 setFreeDialValue()
        // 指定的逻辑位置。先重发当前位置，再继续接收相对脉冲。
        onFreeDial(dialValue)
        Log.i(TAG, "connected to port " + port)
        val input: InputStream = s.getInputStream()
        val buf = ByteArray(4096)
        var acc = ByteArray(0)
        var lastLog = 0L; var bytes = 0L
        while (running) {
            val n = input.read(buf)
            if (n < 0) break
            if (n > 0) { bytes += n; acc = parse(acc + buf.sliceArray(0 until n)) }
            val now = System.currentTimeMillis()
            if (now - lastLog > 5000) {
                lastLog = now
                Log.i(TAG, "alive: total=" + bytes + "B frames=" + frameCount + " acc=" + acc.size)
            }
        }
        Log.i(TAG, "stream ended, total=" + bytes + "B frames=" + frameCount)
    }

    /** 从累积缓冲提取完整 DUML 帧，返回尚未闭合的数据。 */
    internal fun parse(acc: ByteArray): ByteArray =
        if (port == FRAMED_TELEMETRY_PORT) parseFramed(acc) else parseRaw(acc)

    private fun parseFramed(acc: ByteArray): ByteArray {
        var scan = 0
        while (true) {
            while (scan + 4 <= acc.size && !hasTcpMagic(acc, scan)) scan++
            if (scan + 4 > acc.size) {
                return acc.copyOfRange(maxOf(scan, acc.size - 3), acc.size)
            }
            if (scan + TCP_HEADER_SIZE > acc.size) return acc.copyOfRange(scan, acc.size)
            val length = (acc[scan + 4].toInt() and 0xFF) or
                ((acc[scan + 5].toInt() and 0xFF) shl 8) or
                ((acc[scan + 6].toInt() and 0xFF) shl 16) or
                ((acc[scan + 7].toInt() and 0xFF) shl 24)
            if (length !in MIN_FRAME_LEN..MAX_WRAPPED_FRAME_LEN) {
                scan++
                continue
            }
            val frameStart = scan + TCP_HEADER_SIZE
            val frameEnd = frameStart + length
            if (frameEnd > acc.size) return acc.copyOfRange(scan, acc.size)
            handleFrame(acc.copyOfRange(frameStart, frameEnd))
            scan = frameEnd
        }
    }

    private fun hasTcpMagic(bytes: ByteArray, offset: Int): Boolean =
        bytes[offset] == 0x55.toByte() && bytes[offset + 1] == 0xCC.toByte() &&
            bytes[offset + 2] == 0x30.toByte() && bytes[offset + 3] == 0x75.toByte()

    private fun parseRaw(acc: ByteArray): ByteArray {
        var scan = 0
        while (true) {
            while (scan < acc.size && acc[scan] != 0x55.toByte()) scan++
            if (scan >= acc.size) return ByteArray(0)
            if (scan + 2 > acc.size) return acc.copyOfRange(scan, acc.size)
            val flen = acc[scan + 1].toInt() and 0xFF
            if (flen < MIN_FRAME_LEN || flen > MAX_FRAME_LEN) { scan++; continue }
            if (scan + flen > acc.size) return acc.copyOfRange(scan, acc.size)
            val pkt = acc.copyOfRange(scan, scan + flen)
            scan += flen
            handleFrame(pkt)
        }
    }

    private fun handleFrame(pkt: ByteArray) {
        frameCount++
        if (dbgFrames < 40) {
            dbgFrames++
            val hex = pkt.slice(11..minOf(pkt.size - 1, 27)).joinToString("") { String.format("%02x", it) }
            Log.d(TAG, "frame#" + dbgFrames + " len=" + pkt.size + " cs=" + pkt[9] + " cid=0x" +
                String.format("%02x", pkt[10]) + " data=" + hex)
        }
        // 33/0x06 为 1Hz 心跳计数器(非输入),忽略
        if (pkt[9] == CMD_SET_HEARTBEAT.toByte() && pkt[10] == CMD_ID_HEARTBEAT.toByte()) return
        if (pkt[0] != 0x55.toByte()) return
        if (pkt[9].toInt() != CMD_SET_RC || pkt[10] != CMD_ID_RC_STATE) return
        val dataLen = pkt.size - 13
        if (dataLen < 4) return
        val data = pkt.copyOfRange(11, 11 + dataLen)

        val modeRaw = (data[2].toInt() and 0xFF) or ((data[3].toInt() and 0xFF) shl 8)
        val buttonRaw = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
        onRcState(buttonRaw, modeRaw)
        val modePos = modeRaw and 0x3
        onMode(modePos)

        if (data.size >= 5) handleFreeDialPulse(data[4].toInt() and 0xFF)
    }

    /** 无限波轮事件脉冲:位7=标志,位6=方向,低 6 位=步进格数 */
    internal fun handleFreeDialPulse(raw: Int) {
        if (raw == 0 || (raw and 0x80) == 0) return          // 0x00=无事件
        val dir = if ((raw and 0x40) != 0) 1 else -1         // 方向位
        val steps = raw and 0x3F                            // 步进格数
        if (steps == 0) return
        val norm = synchronized(this) {
            dialValue = (dialValue + dir * steps.toFloat() / freeDialStepsPerFullScale).coerceIn(-1f, 1f)
            // 累计计数也保持在有效量程内，避免到达端点后反向旋转时
            // 必须先抵消一段不可见的越界计数。
            dialAccum = (dialValue * freeDialStepsPerFullScale).roundToInt()
            dialValue
        }
        onFreeDial(norm)
    }

    private fun closeQuietly() {
        try { socket?.close() } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "TeleautoTcpInputReader"
        private const val MIN_FRAME_LEN = 13
        private const val MAX_FRAME_LEN = 64
        private const val MAX_WRAPPED_FRAME_LEN = 4096
        private const val TCP_HEADER_SIZE = 8
        private const val FRAMED_TELEMETRY_PORT = 40007
        private const val CMD_SET_RC = 6
        private const val CMD_ID_RC_STATE = 0xAE.toByte()
        private const val CMD_SET_HEARTBEAT = 33
        private const val CMD_ID_HEARTBEAT = 0x06
        private const val DEFAULT_DIAL_STEPS_FULL_SCALE = 10
    }
}
