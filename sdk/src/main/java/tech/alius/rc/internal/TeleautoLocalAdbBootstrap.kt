package tech.alius.rc.internal

import android.util.Log
import java.net.InetSocketAddress
import java.net.Socket

/** Result of preparing the loopback adbd endpoint used by embedded profiles. */
internal enum class TeleautoLocalAdbStatus {
    AVAILABLE,
    STARTED,
    PERMISSION_DENIED,
    START_TIMEOUT,
}

internal data class TeleautoLocalAdbCommandResult(val exitCode: Int, val output: String)

/**
 * Verifies localhost adbd and performs a best-effort start on controller firmware
 * that grants the host application access to Android's property service.
 */
internal class TeleautoLocalAdbStarter(
    private val isListening: () -> Boolean,
    private val runCommand: (List<String>) -> TeleautoLocalAdbCommandResult,
    private val pause: (Long) -> Unit,
) {
    fun ensureAvailable(): TeleautoLocalAdbStatus {
        if (isListening()) return TeleautoLocalAdbStatus.AVAILABLE

        val port = runCommand(listOf("/system/bin/setprop", "service.adb.tcp.port", "5555"))
        if (!port.succeeded()) return TeleautoLocalAdbStatus.PERMISSION_DENIED
        val restart = runCommand(listOf("/system/bin/setprop", "ctl.restart", "adbd"))
        if (!restart.succeeded()) return TeleautoLocalAdbStatus.PERMISSION_DENIED

        repeat(START_POLL_COUNT) {
            pause(START_POLL_MS)
            if (isListening()) return TeleautoLocalAdbStatus.STARTED
        }
        return TeleautoLocalAdbStatus.START_TIMEOUT
    }

    private fun TeleautoLocalAdbCommandResult.succeeded(): Boolean =
        exitCode == 0 && !output.contains("denied", ignoreCase = true) &&
            !output.contains("failed", ignoreCase = true) &&
            !output.contains("unable", ignoreCase = true) &&
            !output.contains("permission", ignoreCase = true) &&
            !output.contains("error", ignoreCase = true)

    private companion object {
        const val START_POLL_COUNT = 12
        const val START_POLL_MS = 250L
    }
}

/** Process-wide coordinator: only one reader may try to bootstrap adbd at a time. */
internal object TeleautoLocalAdbBootstrap {
    private const val TAG = "TeleautoSdk-Adb"
    private const val PORT = 5555

    // Restarting adbd tears down every transport, including a developer's USB
    // or Wi-Fi ADB session. Bootstrap at most once per app process; after that,
    // readers only reconnect as clients and never restart the system daemon.
    @Volatile private var bootstrapAttempted = false
    @Volatile private var lastStatus: TeleautoLocalAdbStatus? = null

    @Synchronized
    fun ensureAvailable(): TeleautoLocalAdbStatus {
        if (probe()) return report(TeleautoLocalAdbStatus.AVAILABLE)
        if (bootstrapAttempted) {
            return lastStatus ?: TeleautoLocalAdbStatus.START_TIMEOUT
        }
        bootstrapAttempted = true
        val status = TeleautoLocalAdbStarter(::probe, ::run, Thread::sleep).ensureAvailable()
        return report(status)
    }

    private fun probe(): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", PORT), 250) }
        true
    }.getOrDefault(false)

    private fun run(command: List<String>): TeleautoLocalAdbCommandResult = runCatching {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        TeleautoLocalAdbCommandResult(process.waitFor(), output)
    }.getOrElse { TeleautoLocalAdbCommandResult(-1, it.message.orEmpty()) }

    private fun report(status: TeleautoLocalAdbStatus): TeleautoLocalAdbStatus {
        if (status != lastStatus) {
            when (status) {
                TeleautoLocalAdbStatus.AVAILABLE -> Log.i(TAG, "localhost:5555 port is listening; protocol authentication pending")
                TeleautoLocalAdbStatus.STARTED -> Log.i(TAG, "localhost:5555 started; protocol authentication pending")
                TeleautoLocalAdbStatus.PERMISSION_DENIED -> Log.e(
                    TAG,
                    "cannot start local adbd: property service denied the app; provision TCP 5555 with Teleauto Assistant",
                )
                TeleautoLocalAdbStatus.START_TIMEOUT -> Log.e(
                    TAG,
                    "adbd start command completed but localhost:5555 did not become ready",
                )
            }
            lastStatus = status
        }
        return status
    }
}

/**
 * Process-wide ownership for the single loopback ADB transport.
 *
 * adbd on legacy DJI Android 7 firmware is easily starved by competing TCP
 * clients. Event and logcat are multiplexed as logical [TeleautoAdbStream] instances
 * by [TeleautoAdbInputReader]; this lease additionally prevents a second SDK/profile
 * instance from opening another physical connection during lifecycle overlap.
 */
internal object TeleautoLocalAdbTransportLease {
    private var owner: Any? = null

    @Synchronized
    fun acquire(candidate: Any): Boolean {
        if (owner != null && owner !== candidate) return false
        owner = candidate
        return true
    }

    @Synchronized
    fun release(candidate: Any) {
        if (owner === candidate) owner = null
    }
}
