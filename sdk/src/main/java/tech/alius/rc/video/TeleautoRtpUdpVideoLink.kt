package tech.alius.rc.video

import android.os.Build
import android.view.Surface
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * RTP/UDP 图传接收:OpenIPC Majestic / 转发桥常用形态。
 * UDP 收包 → RTP 解析 → H.264 重组 → 硬解渲染;统计帧率/码率/丢包。
 */
class TeleautoRtpUdpVideoLink(
    private val port: Int,
    private val onState: (TeleautoVideoState, String?) -> Unit,
) : TeleautoVideoLink {

    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var thread: Thread? = null
    @Volatile private var decoderThread: Thread? = null
    private var decoder: TeleautoLowLatencyDecoder? = null
    @Volatile private var decodingPaused = false
    @Volatile private var currentSurface: Surface? = null
    private val decoderLock = Any()
    private val recorderLock = Any()
    private var recorder: TeleautoEncodedVideoRecorder? = null
    private val decodeQueue = ArrayBlockingQueue<EncodedNal>(8)
    private val lowLatencyDropOldFrames = isRm500Rockchip()
    private val latencyCollector = TeleautoVideoLatencyCollector()
    @Volatile var diagnosticsEnabled: Boolean = false
    private fun newDecoderControl(): ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "TeleautoSdk-VideoControl").apply { isDaemon = true }
        }
    private var decoderControl = newDecoderControl()
    private val decoderTransitionQueued = AtomicBoolean(false)
    @Volatile private var decoderControlActive = false

    /** 热切换渲染目标:仅重建解码器,socket/接收线程/重组器保持不断。 */
    fun setSurface(surface: android.view.Surface) {
        currentSurface = surface
        synchronized(decoderLock) {
            decoder?.release()
            decoder = if (decodingPaused) null else TeleautoLowLatencyDecoder(surface, latencyCollector)
            firstFrameReported = false
        }
        onState(TeleautoVideoState.WAITING, if (decodingPaused) "已暂停" else "surface swapped")
    }

    /** Releases MediaCodec only. UDP/WFB transport and RTP accounting remain active. */
    fun setDecodingPaused(paused: Boolean) {
        if (decodingPaused == paused) return
        // Publish the requested state immediately. The RX hot path stops
        // enqueueing compressed frames without waiting for MediaCodec.stop(),
        // which can block for hundreds of milliseconds on Android 7 devices.
        decodingPaused = paused
        if (paused) decodeQueue.clear()
        onState(TeleautoVideoState.WAITING, if (paused) "已暂停" else "等待关键帧")
        scheduleDecoderTransition()
    }

    /**
     * Coalesces rapid pause/resume taps and performs the potentially blocking
     * MediaCodec stop/release away from the UI thread. Only the latest requested
     * state is retained; a reverse tap never waits behind a queue of old taps.
     */
    private fun scheduleDecoderTransition() {
        if (!decoderControlActive) return
        if (!decoderTransitionQueued.compareAndSet(false, true)) return
        try {
            decoderControl.execute {
                try {
                    while (true) {
                        val targetPaused = decodingPaused
                        decodeQueue.clear()
                        synchronized(decoderLock) {
                            decoder?.release()
                            decoder = if (!targetPaused && !decodingPaused) {
                                currentSurface?.let { TeleautoLowLatencyDecoder(it, latencyCollector) }
                            } else null
                            firstFrameReported = false
                        }
                        android.util.Log.i("TeleautoVideo", if (targetPaused) {
                            "MediaCodec paused; WFB and UDP remain active"
                        } else {
                            "MediaCodec resume requested; waiting for random-access frame"
                        })
                        if (targetPaused == decodingPaused) break
                    }
                } finally {
                    decoderTransitionQueued.set(false)
                    // Close the race where the requested state changed between
                    // the final comparison and clearing the queued flag.
                    val decoderPresent = synchronized(decoderLock) { decoder != null }
                    if (decoderControlActive && decodingPaused == decoderPresent) {
                        scheduleDecoderTransition()
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            decoderTransitionQueued.set(false)
        }
    }

    @Volatile override var stats: TeleautoVideoStats = TeleautoVideoStats()
        private set

    fun startRecording(outputPath: String): Boolean = synchronized(recorderLock) {
        if (!tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return false
        if (recorder != null || stats.renderedFrames <= 0L) return false
        recorder = TeleautoEncodedVideoRecorder(outputPath, stats.videoWidth, stats.videoHeight)
        true
    }

    fun stopRecording(): Boolean = synchronized(recorderLock) {
        val active = recorder ?: return false
        recorder = null
        active.stop()
    }

    private var renderedLast = 0L
    private var submittedLast = 0L
    private val droppedAccessUnits = AtomicLong()
    private val damagedAccessUnits = AtomicLong()
    private val completeAccessUnits = AtomicLong()
    private val discardedDamagedAccessUnits = AtomicLong()
    private var bytesWindow = 0L
    private var windowStart = 0L
    private var framesWindow = 0
    private var reorderBuffer = newReorderBuffer()
    private var lostAtWindowStart = 0L
    private var lastDiagnosticsLogAt = 0L

    override fun start(surface: Surface) {
        if (!tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return
        stop()
        decoderControl = newDecoderControl()
        decoderTransitionQueued.set(false)
        decoderControlActive = true
        currentSurface = surface
        decodingPaused = false
        val s = DatagramSocket(null).apply {
            reuseAddress = true
            receiveBufferSize = 4 * 1024 * 1024
            soTimeout = 2
            bind(java.net.InetSocketAddress(this@TeleautoRtpUdpVideoLink.port))
        }
        socket = s
        onState(TeleautoVideoState.WAITING, "udp://0.0.0.0:$port")
        decoder = TeleautoLowLatencyDecoder(surface, latencyCollector)
        renderedLast = 0; submittedLast = 0; droppedAccessUnits.set(0)
        damagedAccessUnits.set(0); completeAccessUnits.set(0); discardedDamagedAccessUnits.set(0)
        bytesWindow = 0; framesWindow = 0
        windowStart = System.currentTimeMillis(); reorderBuffer = newReorderBuffer()
        latencyCollector.reset(); lostAtWindowStart = 0; decodeQueue.clear()
        decoderThread = Thread({
            try {
                while (!Thread.currentThread().isInterrupted && socket === s && tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) {
                    val item = if (lowLatencyDropOldFrames) {
                        decodeQueue.poll(RM500_OUTPUT_PUMP_MS, TimeUnit.MILLISECONDS)
                    } else {
                        decodeQueue.take()
                    }
                    synchronized(decoderLock) {
                        if (item != null) {
                            if (item.discontinuity) decoder?.onDiscontinuity(item.requireKeyFrame)
                            else decoder?.feed(
                                item.bytes,
                                item.frameEnd,
                                item.rtpIngressNanos,
                                item.accessUnitSequence,
                            )
                        }
                        // Android 7 OMX.rk becomes unstable when repeatedly
                        // entered with a blocking dequeue timeout. The queue
                        // poll supplies the 1 ms cadence; MediaCodec itself is
                        // always drained non-blockingly.
                        if (lowLatencyDropOldFrames) decoder?.pumpOutput(0L)
                    }
                    synchronized(decoderLock) {
                        if (!firstFrameReported && (decoder?.renderedFrames ?: 0) > 0) {
                            firstFrameReported = true
                            onState(TeleautoVideoState.DECODING, null)
                        }
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }, "TeleautoSdk-VideoDecode").apply { isDaemon = true; start() }
        thread = Thread({
            val buf = ByteArray(65535)
            val pkt = DatagramPacket(buf, buf.size)
            val accessUnit = TeleautoH264AccessUnitBuilder()
            lateinit var assembler: TeleautoH264RtpAssembler
            var currentRtpTimestamp = -1L
            var currentAuIngressNanos = 0L
            var currentAuSequence = 0L
            var currentAuDamaged = false
            var gapBeforeNextPacket = false
            var damageNextTimestamp = false
            var dropDependentFramesUntilRandomAccess = false
            val currentParameterSets = ArrayList<ByteArray>(3)
            fun markDiscontinuity(requireKeyFrame: Boolean = false) {
                accessUnit.reset()
                droppedAccessUnits.incrementAndGet()
                val signal = if (requireKeyFrame) EncodedNal.STREAM_DISCONTINUITY
                    else EncodedNal.FRAME_DISCONTINUITY
                if (!decodeQueue.offer(signal)) {
                    decodeQueue.clear()
                    decodeQueue.offer(EncodedNal.STREAM_DISCONTINUITY)
                }
            }
            fun offerDecode(nal: ByteArray, end: Boolean, ingressNanos: Long, rtpTimestamp: Long, auSequence: Long) {
                if (!decodeQueue.offer(EncodedNal(nal, end, rtpIngressNanos = ingressNanos,
                        rtpTimestamp = rtpTimestamp, accessUnitSequence = auSequence))) {
                    // Inter pictures are references for later HEVC pictures, so
                    // never clear a few queued P frames and then continue with
                    // another P frame. That creates a syntactically valid but
                    // undecodable chain and makes the Android 7 Rockchip OMX
                    // report a fatal "Error frame". If the bounded queue really
                    // overflows, invalidate the whole reference chain and resume
                    // only from a clean random-access unit.
                    decodeQueue.clear()
                    droppedAccessUnits.incrementAndGet()
                    if (lowLatencyDropOldFrames) {
                        dropDependentFramesUntilRandomAccess = true
                        decodeQueue.offer(EncodedNal.STREAM_DISCONTINUITY)
                        if (!end || !containsRandomAccessFrame(nal)) return
                        dropDependentFramesUntilRandomAccess = false
                    }
                    decodeQueue.offer(
                        EncodedNal(
                            nal,
                            end,
                            rtpIngressNanos = ingressNanos,
                            rtpTimestamp = rtpTimestamp,
                            accessUnitSequence = auSequence,
                        ),
                    )
                }
            }
            fun finishAccessUnit(damaged: Boolean) {
                val frame = accessUnit.finish()
                val isDamaged = damaged || currentAuDamaged
                if (frame != null || currentParameterSets.isNotEmpty()) {
                    framesWindow++
                    if (isDamaged) {
                        damagedAccessUnits.incrementAndGet()
                        if (lowLatencyDropOldFrames) {
                            // The Android 7 Rockchip HEVC component treats an AU
                            // missing its RTP marker/packet as a fatal stream
                            // error. Dropping only this picture is lower latency
                            // than poisoning and rebuilding the hardware codec;
                            // the next complete AU is still submitted immediately.
                            droppedAccessUnits.incrementAndGet()
                            discardedDamagedAccessUnits.incrementAndGet()
                            dropDependentFramesUntilRandomAccess = true
                            currentParameterSets.clear()
                            currentAuDamaged = false
                            return
                        }
                    }
                    currentParameterSets.forEach {
                        synchronized(recorderLock) { recorder?.onParameterSet(it) }
                        offerDecode(it, false, currentAuIngressNanos, currentRtpTimestamp, currentAuSequence)
                    }
                    currentParameterSets.clear()
                    if (frame != null) {
                        if (lowLatencyDropOldFrames && dropDependentFramesUntilRandomAccess) {
                            if (containsRandomAccessFrame(frame)) {
                                dropDependentFramesUntilRandomAccess = false
                                // Reset Rockchip's decoded-picture buffer before
                                // the first clean random-access AU. Keeping the
                                // old DPB after a missing reference makes the
                                // vendor OMX fail asynchronously several frames
                                // later even though damaged bytes were dropped.
                                decodeQueue.clear()
                                decodeQueue.offer(EncodedNal.STREAM_DISCONTINUITY)
                            } else {
                                droppedAccessUnits.incrementAndGet()
                                return
                            }
                        }
                        synchronized(recorderLock) { recorder?.onAccessUnit(frame, currentRtpTimestamp) }
                        offerDecode(frame, true, currentAuIngressNanos, currentRtpTimestamp, currentAuSequence++)
                        completeAccessUnits.incrementAndGet()
                    }
                }
                currentAuDamaged = false
            }
            assembler = TeleautoH264RtpAssembler(
                onNal = { nal, end ->
                    if (decodingPaused) {
                        if (end) accessUnit.reset()
                        return@TeleautoH264RtpAssembler
                    }
                    val first = nal.getOrNull(4)?.toInt()?.and(0xFF) ?: return@TeleautoH264RtpAssembler
                    val avcType = first and 0x1F
                    val hevcType = (first ushr 1) and 0x3F
                    if (avcType == 7 || avcType == 8 || hevcType in 32..34) {
                        currentParameterSets.add(nal)
                    } else {
                        accessUnit.append(nal)
                        if (end) finishAccessUnit(damaged = false)
                    }
                },
                onDiscontinuity = {
                    currentAuDamaged = true
                    damagedAccessUnits.incrementAndGet()
                },
            )
            fun processOrdered(packet: TeleautoRtpReorderBuffer.Packet) {
                val timestampChanged = currentRtpTimestamp != -1L && packet.timestamp != currentRtpTimestamp
                if (timestampChanged) {
                    // A missing marker must not merge two pictures. Submit every
                    // syntactically complete NAL collected for the old timestamp.
                    finishAccessUnit(damaged = true)
                }
                if (packet.timestamp != currentRtpTimestamp) {
                    currentRtpTimestamp = packet.timestamp
                    currentAuIngressNanos = packet.receivedNanos
                    currentAuDamaged = gapBeforeNextPacket || damageNextTimestamp
                    damageNextTimestamp = false
                } else {
                    // A confirmed gap can occur in the middle of an AU, not
                    // only before its first received packet. Once damaged, the
                    // timestamp remains poisoned until marker/closure.
                    if (gapBeforeNextPacket) currentAuDamaged = true
                    if (currentAuIngressNanos == 0L) {
                        currentAuIngressNanos = packet.receivedNanos
                    }
                }
                gapBeforeNextPacket = false
                assembler.onPacket(packet.bytes, packet.length, packet.sequence, packet.marker)
                if (packet.marker) finishAccessUnit(damaged = false)
            }
            try {
                while (!Thread.currentThread().isInterrupted && socket === s && tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) {
                    // DatagramPacket.length becomes the size of the last packet
                    // after receive(). Restore its capacity or a short RTP packet
                    // permanently truncates every later packet.
                    pkt.length = buf.size
                    try {
                        s.receive(pkt)
                    } catch (_: SocketTimeoutException) {
                        reorderBuffer.flushExpired(sink = ::processOrdered) { gap ->
                            currentAuDamaged = true
                            gapBeforeNextPacket = true
                            damageNextTimestamp = true
                            damagedAccessUnits.incrementAndGet()
                            if (diagnosticsEnabled) {
                                val reorderWaitMs = if (lowLatencyDropOldFrames) {
                                    TimeUnit.NANOSECONDS.toMillis(RM500_REORDER_WAIT_NANOS)
                                } else {
                                    4L
                                }
                                android.util.Log.w(
                                    "TeleautoVideoLatency",
                                    "rtp gap=$gap timeout=${reorderWaitMs}ms",
                                )
                            }
                        }
                        maybeStats()
                        continue
                    }
                    val len = pkt.length
                    if (len < 12 || (buf[0].toInt() and 0xC0) != 0x80) continue
                    bytesWindow += len
                    val seq = ((buf[2].toInt() and 0xFF) shl 8) or (buf[3].toInt() and 0xFF)
                    val ssrc = ((buf[8].toLong() and 0xFF) shl 24) or
                        ((buf[9].toLong() and 0xFF) shl 16) or
                        ((buf[10].toLong() and 0xFF) shl 8) or
                        (buf[11].toLong() and 0xFF)
                    val marker = buf[1].toInt() and 0x80 != 0
                    val timestamp = ((buf[4].toLong() and 0xFF) shl 24) or
                        ((buf[5].toLong() and 0xFF) shl 16) or
                        ((buf[6].toLong() and 0xFF) shl 8) or (buf[7].toLong() and 0xFF)
                    val receivedNanos = android.os.SystemClock.elapsedRealtimeNanos()
                    val packet = TeleautoRtpReorderBuffer.Packet(buf, len, ssrc, seq, timestamp, marker, receivedNanos)
                    try {
                        reorderBuffer.offer(packet, receivedNanos, ::processOrdered, onGap = { gap ->
                            currentAuDamaged = true
                            gapBeforeNextPacket = true
                            damageNextTimestamp = true
                            damagedAccessUnits.incrementAndGet()
                            if (diagnosticsEnabled) android.util.Log.w("TeleautoVideoLatency", "rtp gap=$gap")
                        }, onStreamChanged = {
                            assembler.reset()
                            currentRtpTimestamp = -1L
                            markDiscontinuity(requireKeyFrame = true)
                        })
                    } catch (e: Exception) {
                        // 解码器瞬时异常不杀接收线程:丢本包,等下一个 SPS 重建
                        android.util.Log.w("TeleautoVideo", "decode tick: ${e.javaClass.simpleName}: ${e.message}")
                    }
                    maybeStats()
                }
            } catch (e: Exception) {
                android.util.Log.w("TeleautoVideo", "rx thread exit: ${e.javaClass.simpleName}: ${e.message}")
            }
            android.util.Log.i("TeleautoVideo", "rx thread ended")
        }, "TeleautoSdk-VideoRx").apply { isDaemon = true; start() }
    }

    @Volatile private var firstFrameReported = false

    private fun maybeStats() {
        val now = System.currentTimeMillis()
        if (now - windowStart >= 1000) {
            val elapsedMs = (now - windowStart).coerceAtLeast(1)
            val decoderSnapshot = synchronized(decoderLock) { decoder }
            val rendered = decoderSnapshot?.renderedFrames ?: 0
            val submitted = decoderSnapshot?.submittedFrames ?: 0
            val lostNow = reorderBuffer.lostPackets
            val latency = if (diagnosticsEnabled) latencyCollector.snapshot() else Triple(
                TeleautoVideoLatencySummary.UNAVAILABLE,
                TeleautoVideoLatencySummary.UNAVAILABLE,
                TeleautoVideoLatencySummary.UNAVAILABLE,
            )
            stats = TeleautoVideoStats(
                framesPerSecond = (framesWindow * 1000L / elapsedMs).toInt(),
                kilobitsPerSecond = (bytesWindow * 8 / elapsedMs).toInt(),
                // A live HUD must describe the current link, not retain a
                // startup burst forever. Late packets can repair the tracker,
                // therefore clamp the one-second delta at zero.
                lostPackets = (lostNow - lostAtWindowStart).coerceAtLeast(0),
                renderedFrames = rendered,
                videoWidth = synchronized(decoderLock) { decoder?.videoWidth ?: 0 },
                videoHeight = synchronized(decoderLock) { decoder?.videoHeight ?: 0 },
                decoderMode = decoderSnapshot?.decoderMode ?: TeleautoVideoDecoderMode.WAITING_KEYFRAME,
                decoderName = decoderSnapshot?.decoderName,
                inputFps = ((submitted - submittedLast).coerceAtLeast(0) * 1000L / elapsedMs).toInt(),
                renderedFps = ((rendered - renderedLast).coerceAtLeast(0) * 1000L / elapsedMs).toInt(),
                discardedStaleOutputFrames = decoderSnapshot?.discardedStaleOutputFrames ?: 0,
                droppedAccessUnits = droppedAccessUnits.get(),
                reorderedPackets = reorderBuffer.reorderedPackets,
                latePackets = reorderBuffer.latePackets,
                fecRecoveredPackets = TeleautoVideoTransportDiagnostics.fecRecoveredPackets(),
                damagedAccessUnits = damagedAccessUnits.get(),
                completeAccessUnits = completeAccessUnits.get(),
                discardedDamagedAccessUnits = discardedDamagedAccessUnits.get(),
                codecFatalErrors = decoderSnapshot?.fatalErrors ?: 0,
                codecRestarts = decoderSnapshot?.restarts ?: 0,
                lastFatalAccessUnitSequence = decoderSnapshot?.lastFatalSequence ?: -1,
                fatalRecoveryMs = decoderSnapshot?.fatalRecoveryMs ?: -1,
                rtpToCodecLatencyMs = latency.first,
                codecOutputLatencyMs = latency.second,
                groundPipelineLatencyMs = latency.third,
            )
            if (diagnosticsEnabled && now - lastDiagnosticsLogAt >= 5_000L) android.util.Log.d(
                "TeleautoVideo",
                "stats mode=${stats.decoderMode} decoder=${stats.decoderName} " +
                    "in=${stats.inputFps} out=${stats.renderedFps} source=${stats.framesPerSecond} " +
                    "size=${stats.videoWidth}x${stats.videoHeight} " +
                    "rtpLost=${stats.lostPackets} droppedAu=${stats.droppedAccessUnits} " +
                    "queue=${decodeQueue.size} reordered=${stats.reorderedPackets} late=${stats.latePackets} " +
                    "fec=${stats.fecRecoveredPackets} damagedAu=${stats.damagedAccessUnits} " +
                    "completeAu=${stats.completeAccessUnits} discardedDamaged=${stats.discardedDamagedAccessUnits} " +
                    "discardedOutput=${stats.discardedStaleOutputFrames} " +
                    "fatal=${stats.codecFatalErrors} restarts=${stats.codecRestarts} " +
                    "rtpToCodec=${stats.rtpToCodecLatencyMs} codecToSurface=${stats.codecOutputLatencyMs} " +
                    "ground=${stats.groundPipelineLatencyMs}",
            )
            if (diagnosticsEnabled && now - lastDiagnosticsLogAt >= 5_000L) lastDiagnosticsLogAt = now
            renderedLast = rendered
            submittedLast = submitted
            lostAtWindowStart = lostNow
            bytesWindow = 0; framesWindow = 0
            windowStart = now
        }
    }

    override fun stop() {
        android.util.Log.i("TeleautoVideo", "stop() called", Throwable("who"))
        decoderControlActive = false
        runCatching { socket?.close() }
        socket = null
        thread?.interrupt()
        thread = null
        decoderThread?.interrupt()
        decoderThread = null
        decodeQueue.clear()
        decoderControl.shutdownNow()
        decoderTransitionQueued.set(false)
        synchronized(recorderLock) {
            recorder?.stop()
            recorder = null
        }
        synchronized(decoderLock) {
            decoder?.release()
            decoder = null
        }
        currentSurface = null
        decodingPaused = false
        firstFrameReported = false
        onState(TeleautoVideoState.IDLE, null)
    }

    private data class EncodedNal(
        val bytes: ByteArray,
        val frameEnd: Boolean,
        val discontinuity: Boolean = false,
        val requireKeyFrame: Boolean = false,
        val rtpIngressNanos: Long = 0L,
        val rtpTimestamp: Long = 0L,
        val accessUnitSequence: Long = 0L,
    ) {
        companion object {
            val FRAME_DISCONTINUITY = EncodedNal(ByteArray(0), false, true, false)
            val STREAM_DISCONTINUITY = EncodedNal(ByteArray(0), false, true, true)
        }
    }

    private fun isRm500Rockchip(): Boolean {
        val model = Build.MODEL.lowercase(java.util.Locale.US)
        val product = Build.PRODUCT.lowercase(java.util.Locale.US)
        return Build.VERSION.SDK_INT == Build.VERSION_CODES.N_MR1 &&
            Build.HARDWARE.equals("rk30board", ignoreCase = true) &&
            (model == "ag501" || model == "rm500" || product == "ag501" || product == "rm500")
    }

    private fun newReorderBuffer(): TeleautoRtpReorderBuffer = TeleautoRtpReorderBuffer(
        maxWaitNanos = if (lowLatencyDropOldFrames) RM500_REORDER_WAIT_NANOS else 4_000_000L,
    )

    private fun containsRandomAccessFrame(bytes: ByteArray): Boolean {
        var offset = 0
        while (offset + 4 < bytes.size) {
            val start = when {
                bytes[offset] == 0.toByte() && bytes[offset + 1] == 0.toByte() &&
                    bytes[offset + 2] == 0.toByte() && bytes[offset + 3] == 1.toByte() -> offset + 4
                bytes[offset] == 0.toByte() && bytes[offset + 1] == 0.toByte() &&
                    bytes[offset + 2] == 1.toByte() -> offset + 3
                else -> { offset++; continue }
            }
            val first = bytes[start].toInt() and 0xFF
            if ((first and 0x1F) == 5 || ((first ushr 1) and 0x3F) in 16..23) return true
            offset = start + 1
        }
        return false
    }

    private companion object {
        const val RM500_OUTPUT_PUMP_MS = 4L
        const val RM500_REORDER_WAIT_NANOS = 12_000_000L
    }
}

/** RTP sequence accounting with late/out-of-order packet recovery and SSRC isolation. */
internal class TeleautoRtpSequenceTracker {
    private var ssrc = -1L
    private var expected = -1
    private val missing = HashSet<Int>()
    var lostPackets = 0L
        private set

    /** @return true when the packet starts a different SSRC stream. */
    fun offer(packetSsrc: Long, sequence: Int): Boolean {
        if (ssrc != packetSsrc) {
            val changed = ssrc != -1L
            ssrc = packetSsrc
            expected = (sequence + 1) and 0xFFFF
            missing.clear()
            return changed
        }
        val gap = (sequence - expected) and 0xFFFF
        when {
            gap == 0 -> expected = (sequence + 1) and 0xFFFF
            gap in 1..2000 -> {
                repeat(gap) { missing.add((expected + it) and 0xFFFF) }
                lostPackets += gap
                expected = (sequence + 1) and 0xFFFF
            }
            missing.remove(sequence) -> lostPackets--
        }
        return false
    }

    fun reset() {
        ssrc = -1L
        expected = -1
        missing.clear()
        lostPackets = 0
    }
}

/** Joins all Annex-B NAL units belonging to one RTP marker-delimited frame. */
internal class TeleautoH264AccessUnitBuilder {
    private var bytes = ByteArray(256 * 1024)
    private var size = 0

    fun append(nal: ByteArray) {
        ensureCapacity(size + nal.size)
        nal.copyInto(bytes, size)
        size += nal.size
    }

    fun finish(): ByteArray? = if (size == 0) null else bytes.copyOf(size).also { size = 0 }

    /** Compatibility helper retained for existing SDK tests and callers. */
    fun append(nal: ByteArray, frameEnd: Boolean): ByteArray? {
        append(nal)
        return if (frameEnd) finish() else null
    }

    fun reset() { size = 0 }

    private fun ensureCapacity(required: Int) {
        if (required <= bytes.size) return
        var capacity = bytes.size
        while (capacity < required) capacity *= 2
        bytes = bytes.copyOf(capacity)
    }
}
