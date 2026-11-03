package tech.alius.rc.internal

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/** Reads DJI KeyCodeListener logs locally on devices whose app uid belongs to the log group. */
internal class TeleautoLocalLogcatReader(
    private val onAction: (String) -> Unit,
    private val onHeldKeys: (Set<String>) -> Unit,
) {
    @Volatile private var running = false
    @Volatile private var process: Process? = null
    private var thread: Thread? = null
    private val actionRe = Regex("send key action\\s+>>\\s+(\\S+)")
    private val listRe = Regex("mKeyCodeList == \\[([^\\]]*)\\]")
    private val keyItemRe = Regex("ACTION_KEY_(\\w+)")

    fun start() {
        if (running) return
        running = true
        thread = Thread({
            while (running) {
                try {
                    process = ProcessBuilder("logcat", "-T", "1", "-s", "KeyCodeListener:D", "*:S")
                        .redirectErrorStream(true).start()
                    BufferedReader(InputStreamReader(process!!.inputStream)).useLines { lines ->
                        lines.takeWhile { running }.forEach(::handleLine)
                    }
                } catch (e: Exception) {
                    if (running) Log.w(TAG, "local logcat unavailable: ${e.message}")
                } finally { process = null }
                if (running) runCatching { Thread.sleep(1_500) }
            }
        }, "TeleautoLocalLogcatReader").apply { isDaemon = true; start() }
    }

    private fun handleLine(line: String) {
        listRe.find(line)?.let { match ->
            onHeldKeys(keyItemRe.findAll(match.groupValues[1]).map { it.groupValues[1] }
                .filter { it != "RELEASE" }.toSet())
            return
        }
        actionRe.find(line)?.let { onAction(it.groupValues[1]) }
    }

    fun stop() {
        running = false
        process?.destroy()
        process = null
    }

    companion object { private const val TAG = "TeleautoSdk-LocalLogcat" }
}
