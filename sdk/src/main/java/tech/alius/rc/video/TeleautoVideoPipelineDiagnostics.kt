package tech.alius.rc.video

import android.os.SystemClock
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

/** Shared transport counters. Values are snapshots, never used to drive the hot path. */
internal object TeleautoVideoTransportDiagnostics {
    private val fecRecovered = AtomicLong()
    fun addFecRecovered(value: Long) { if (value > 0) fecRecovered.addAndGet(value) }
    fun reset() { fecRecovered.set(0) }
    fun fecRecoveredPackets(): Long = fecRecovered.get()
}

/** Allocation-bounded latency collector. The decoder thread is its only writer. */
class TeleautoVideoLatencyCollector internal constructor(private val capacity: Int = 512) {
    private val rtpToCodec = LongArray(capacity)
    private val codecOutput = LongArray(capacity)
    private val ground = LongArray(capacity)
    private var rtpCount = 0
    private var codecCount = 0
    private var groundCount = 0

    @Synchronized fun recordRtpToCodec(nanos: Long) { rtpCount = put(rtpToCodec, rtpCount, nanos) }
    @Synchronized fun recordCodecOutput(nanos: Long) { codecCount = put(codecOutput, codecCount, nanos) }
    @Synchronized fun recordGround(nanos: Long) { groundCount = put(ground, groundCount, nanos) }

    @Synchronized fun snapshot(): Triple<TeleautoVideoLatencySummary, TeleautoVideoLatencySummary, TeleautoVideoLatencySummary> =
        Triple(summary(rtpToCodec, rtpCount), summary(codecOutput, codecCount), summary(ground, groundCount))

    @Synchronized fun reset() { rtpCount = 0; codecCount = 0; groundCount = 0 }

    private fun put(values: LongArray, count: Int, value: Long): Int {
        if (value < 0) return count
        values[count % values.size] = value
        return count + 1
    }

    private fun summary(values: LongArray, count: Int): TeleautoVideoLatencySummary {
        val size = minOf(count, values.size)
        if (size == 0) return TeleautoVideoLatencySummary.UNAVAILABLE
        val copy = LongArray(size)
        if (count <= values.size) {
            values.copyInto(copy, endIndex = size)
        } else {
            val start = count % values.size
            repeat(size) { copy[it] = values[(start + it) % values.size] }
        }
        copy.sort()
        fun ms(index: Int) = copy[index.coerceIn(0, size - 1)] / 1_000_000.0
        return TeleautoVideoLatencySummary(ms((size - 1) / 2), ms(((size - 1) * 95) / 100), ms(size - 1))
    }
}

/** Fixed-capacity, low-latency RTP reorder stage. */
internal class TeleautoRtpReorderBuffer(
    private val maxWaitNanos: Long = 4_000_000L,
    private val capacity: Int = 64,
) {
    data class Packet(
        val bytes: ByteArray,
        val length: Int,
        val ssrc: Long,
        val sequence: Int,
        val timestamp: Long,
        val marker: Boolean,
        val receivedNanos: Long,
    )

    private var ssrc = -1L
    private var expected = -1
    private var gapSinceNanos = 0L
    private val pending = HashMap<Int, Packet>(capacity)
    private val delivered = ArrayDeque<Int>(128)
    private val deliveredSet = HashSet<Int>(128)
    var reorderedPackets = 0L; private set
    var latePackets = 0L; private set
    var duplicatePackets = 0L; private set
    var lostPackets = 0L; private set

    fun offer(packet: Packet, nowNanos: Long = SystemClock.elapsedRealtimeNanos(), sink: (Packet) -> Unit,
              onGap: (Int) -> Unit = {}, onStreamChanged: () -> Unit = {}) {
        if (packet.ssrc != ssrc) {
            val changed = ssrc != -1L
            resetStream(packet.ssrc, packet.sequence)
            if (changed) onStreamChanged()
        }
        val distance = forward(expected, packet.sequence)
        when {
            distance == 0 -> {
                deliver(packet, sink)
                drain(sink)
            }
            distance < 0x8000 -> {
                // The UDP receiver reuses its read buffer. Copy only packets
                // that actually have to wait; the overwhelmingly common
                // in-order path remains byte-array zero-copy on Android 7.
                val owned = packet.copy(bytes = packet.bytes.copyOf(packet.length))
                if (pending.putIfAbsent(packet.sequence, owned) == null) {
                    reorderedPackets++
                    if (gapSinceNanos == 0L) gapSinceNanos = nowNanos
                } else duplicatePackets++
                if (pending.size >= capacity) flush(nowNanos, true, sink, onGap)
            }
            deliveredSet.contains(packet.sequence) -> duplicatePackets++
            else -> latePackets++
        }
        flush(nowNanos, false, sink, onGap)
    }

    fun flushExpired(nowNanos: Long = SystemClock.elapsedRealtimeNanos(), sink: (Packet) -> Unit,
                     onGap: (Int) -> Unit = {}) = flush(nowNanos, false, sink, onGap)

    fun reset() { ssrc = -1L; expected = -1; pending.clear(); clearDelivered(); gapSinceNanos = 0L }

    private fun flush(now: Long, forced: Boolean, sink: (Packet) -> Unit, onGap: (Int) -> Unit) {
        if (pending.isEmpty() || (!forced && now - gapSinceNanos < maxWaitNanos)) return
        val next = pending.values.minByOrNull { forward(expected, it.sequence) } ?: return
        val gap = forward(expected, next.sequence)
        if (gap in 1 until 0x8000) {
            lostPackets += gap
            onGap(gap)
            expected = next.sequence
        }
        pending.remove(next.sequence)
        deliver(next, sink)
        drain(sink)
    }

    private fun drain(sink: (Packet) -> Unit) {
        while (true) {
            val next = pending.remove(expected) ?: break
            deliver(next, sink)
        }
        gapSinceNanos = if (pending.isEmpty()) 0L else SystemClock.elapsedRealtimeNanos()
    }

    private fun deliver(packet: Packet, sink: (Packet) -> Unit) {
        expected = (packet.sequence + 1) and 0xFFFF
        remember(packet.sequence)
        sink(packet)
    }

    private fun remember(sequence: Int) {
        if (!deliveredSet.add(sequence)) return
        delivered.addLast(sequence)
        if (delivered.size > 128) deliveredSet.remove(delivered.removeFirst())
    }

    private fun resetStream(value: Long, firstSequence: Int) {
        ssrc = value; expected = firstSequence; pending.clear(); clearDelivered(); gapSinceNanos = 0L
    }
    private fun clearDelivered() { delivered.clear(); deliveredSet.clear() }
    private fun forward(from: Int, to: Int) = (to - from) and 0xFFFF
}
