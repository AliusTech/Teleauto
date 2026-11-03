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
import tech.alius.rc.profile.config.TeleautoRcInputProfileConfig

/** Per-hardware logcat contract. Keep vendor tags and message formats out of the reader. */
internal data class TeleautoAdbLogcatConfig(
    val id: String,
    val command: String,
    val actionPattern: Regex,
    val heldListPattern: Regex,
    val keyItemPattern: Regex,
) {
    companion object {
        val RM500 = djiKeyCodeListener("rm500")
        val RM700 = djiKeyCodeListener("rm700")

        fun fromProfile(profile: TeleautoRcInputProfileConfig) = TeleautoAdbLogcatConfig(
            id = profile.id,
            command = profile.logcatCommand,
            actionPattern = Regex("send key action\\s+>>\\s+(\\S+)"),
            heldListPattern = Regex("mKeyCodeList == \\[([^\\]]*)\\]"),
            keyItemPattern = Regex("ACTION_KEY_(\\w+)"),
        )

        private fun djiKeyCodeListener(id: String) = TeleautoAdbLogcatConfig(
            id = id,
            command = "shell:logcat -T 1 -s KeyCodeListener:D *:S",
            actionPattern = Regex("send key action\\s+>>\\s+(\\S+)"),
            heldListPattern = Regex("mKeyCodeList == \\[([^\\]]*)\\]"),
            keyItemPattern = Regex("ACTION_KEY_(\\w+)"),
        )
    }
}

/** 内部:adb 自连跑 logcat,解析 dpad KeyCodeListener 的私有键事件 */
internal class TeleautoAdbLogcatReader(
    private val onAction: (action: String) -> Unit,
    private val onHeldKeys: (keys: Set<String>) -> Unit,
    private val onState: (connected: Boolean) -> Unit,
    private val config: TeleautoAdbLogcatConfig = TeleautoAdbLogcatConfig.RM700,
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
    @Volatile private var socket: Socket? = null
    @Volatile private var connected = false

    @Volatile private var heldKeys = emptySet<String>()
    @Volatile private var lastFocusMs = 0L
    private val pending = StringBuilder()
    @Volatile private var lastDataAt = 0L
    private var watchdog: Thread? = null

    /** FOCUS 双通道触发(mKeyCodeList 即时 + send 兜底),250ms 窗口去重 */
    private fun triggerFocus() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastFocusMs < 250) return
        lastFocusMs = now
        onAction("dpad-service.keys.ACTION_KEY_FOCUS")
    }

    fun start(context: Context) {
        if (running) return
        running = true
        ensureWatchdog()
        thread = Thread {
            while (running) {
                try { connectAndRead(context) }
                catch (e: Exception) {
                    Log.w(TAG, "logcat adb error: ${e.message}")
                    if (connected) { connected = false; onState(false) }
                    closeQuietly()
                }
                if (running) try { Thread.sleep(reconnectDelayMs()) } catch (_: Exception) {}
            }
        }.apply { isDaemon = true; name = "TeleautoAdbLogcatReader"; start() }
    }

    private fun connectAndRead(context: Context) {
        val key = loadOrCreateKey(context)
        socket = Socket()
        socket!!.connect(InetSocketAddress("localhost", 5555), 3000)
        socket!!.soTimeout = ADB_HANDSHAKE_TIMEOUT_MS
        connection = TeleautoAdbConnection.create(socket!!, key).apply { connect() }
        socket!!.soTimeout = 0
        connected = true; onState(true)
        failedAttempts = 0 // 连接成功,重置退避
        Log.i(TAG, "logcat adb connected localhost:5555")
        stream = connection!!.open(config.command)
        lastDataAt = System.currentTimeMillis()
        while (running) {
            val data = stream!!.read() ?: break
            lastDataAt = System.currentTimeMillis()
            // 流数据任意切分,跨 chunk 行缓冲后再逐行解析
            pending.append(String(data))
            while (true) {
                val nl = pending.indexOf('\n')
                if (nl < 0) break
                val line = pending.substring(0, nl)
                pending.delete(0, nl + 1)
                handleLine(line)
            }
        }
        if (connected) { connected = false; onState(false) }
    }

    private fun handleLine(line: String) {
        val lm = config.heldListPattern.find(line)
        if (lm != null) {
            // mKeyCodeList 行:dpad 当前按住键集合(比 send 早,可检测抬起;FOCUS 藉此绕过 ~200ms 判定延迟)
            val keys = config.keyItemPattern.findAll(lm.groupValues[1])
                .map { it.groupValues[1] }
                .filter { it != "RELEASE" }
                .toSet()
            if (keys != heldKeys) {
                val newly = keys - heldKeys
                heldKeys = keys
                onHeldKeys(keys)
                if ("FOCUS" in newly) triggerFocus()
            }
            return
        }
        // send key action 行:dpad 按键脉冲(按下+长按repeat,无抬起事件)
        config.actionPattern.find(line)?.let {
            if (it.groupValues[1].endsWith("ACTION_KEY_FOCUS")) triggerFocus()
            else onAction(it.groupValues[1])
        }
    }

    /** 空闲看门狗:与 TeleautoAdbInputReader 同理,防 adbd 静默杀流后读线程永久阻塞 */
    private fun ensureWatchdog() {
        if (watchdog?.isAlive == true) return
        watchdog = Thread {
            while (running) {
                try { Thread.sleep(5000) } catch (_: Exception) { break }
                if (!running) break
                val idle = System.currentTimeMillis() - lastDataAt
                if (connected && lastDataAt > 0 && idle > IDLE_RECONNECT_MS) {
                    Log.w(TAG, "logcat stream idle ${idle / 1000}s, force reconnect")
                    try { socket?.close() } catch (_: Exception) {}
                }
            }
        }.apply { isDaemon = true; name = "AdbLogcatWatchdog"; start() }
    }

    private fun closeQuietly() {
        try { stream?.close() } catch (_: Exception) {}
        try { connection?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
    }

    private fun loadOrCreateKey(context: Context): TeleautoAdbCrypto {
        val base64 = object : TeleautoAdbBase64 {
            override fun encodeToString(data: ByteArray): String =
                Base64.encodeToString(data, Base64.NO_WRAP)
        }
        val dir = File(context.filesDir, "adbkey").apply { if (!exists()) mkdirs() }
        val priv = File(dir, "priv.key"); val pub = File(dir, "pub.key")
        return try { TeleautoAdbCrypto.loadAdbKeyPair(base64, priv, pub) }
        catch (e: Exception) { TeleautoAdbCrypto.generateAdbKeyPair(base64).apply { saveAdbKeyPair(priv, pub) } }
    }

    fun stop() {
        running = false; connected = false
        try { socket?.close() } catch (_: Exception) {}
        try { stream?.close() } catch (_: Exception) {}
        try { connection?.close() } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "TeleautoSdk-Logcat"
        private const val ADB_HANDSHAKE_TIMEOUT_MS = 8_000
        private const val IDLE_RECONNECT_MS = 45_000L
    }
}
