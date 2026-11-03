package tech.alius.rc.internal

import android.util.Log
import java.util.Arrays
import tech.alius.rc.BuildConfig

/** Fixed-storage debug telemetry; release builds return immediately. */
internal class TeleautoDebugLatencyReporter(private val tag: String, private val stage: String) {
    private val samples = LongArray(256)
    private var cursor = 0
    private var count = 0
    private var offered = 0L
    private var delivered = 0L
    private var lastReportNs = System.nanoTime()

    @Synchronized fun onOffer() {
        if (BuildConfig.DEBUG) offered++
    }

    @Synchronized fun onDelivery(latencyNs: Long) {
        if (!BuildConfig.DEBUG) return
        samples[cursor] = latencyNs.coerceAtLeast(0L) / 1_000_000L
        cursor = (cursor + 1) % samples.size
        if (count < samples.size) count++
        delivered++
        val now = System.nanoTime()
        if (now - lastReportNs < REPORT_NS) return
        val sorted = samples.copyOf(count)
        Arrays.sort(sorted)
        fun p(fraction: Double) = sorted[((sorted.size - 1) * fraction).toInt()]
        Log.d(tag, "$stage offered=$offered delivered=$delivered merged=${offered - delivered} " +
            "latency_ms p50=${p(.50)} p95=${p(.95)} max=${sorted.last()}")
        offered = 0; delivered = 0; lastReportNs = now
    }

    companion object { private const val REPORT_NS = 5_000_000_000L }
}
