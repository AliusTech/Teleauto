package tech.alius.rc.video

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import java.nio.ByteBuffer

/** Hardware low-latency decoder that auto-detects AVC or HEVC parameter sets. */
class TeleautoLowLatencyDecoder(
    private val surface: Surface,
    private val latencyCollector: TeleautoVideoLatencyCollector? = null,
) {
    private enum class Codec(val mime: String) {
        AVC(MediaFormat.MIMETYPE_VIDEO_AVC),
        HEVC(MediaFormat.MIMETYPE_VIDEO_HEVC),
    }

    private var codec: MediaCodec? = null
    private var codecType: Codec? = null
    private var vps: ByteArray? = null
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null
    private val pending = ArrayDeque<ByteArray>()
    private var waitingForKeyFrame = true
    private var rockchipHevcFatalErrors = 0
    private var activeDecoderName: String? = null
    private var lastSpsInfo: TeleautoHevcSpsParser.Info? = null
    private var lastPresentationTimeUs = 0L
    private val frameTimings = HashMap<Long, FrameTiming>(32)
    private var lastAccessUnitSequence = -1L
    private var lastAccessUnitSize = 0
    private var lastAccessUnitRandomAccess = false
    private var fatalAtNanos = 0L
    private var rejectedRandomAccessSequence = -1L

    var renderedFrames = 0L
        private set
    var discardedStaleOutputFrames = 0L
        private set
    var submittedFrames = 0L
        private set
    @Volatile var decoderMode = TeleautoVideoDecoderMode.WAITING_KEYFRAME
        private set
    val decoderName: String? get() = activeDecoderName
    @Volatile var videoWidth = 0
        private set
    @Volatile var videoHeight = 0
        private set
    val isRunning get() = codec != null
    var fatalErrors = 0L
        private set
    var restarts = 0L
        private set
    var lastFatalSequence = -1L
        private set
    var fatalRecoveryMs = -1L
        private set

    fun feed(
        nal: ByteArray,
        frameEnd: Boolean,
        rtpIngressNanos: Long = 0L,
        accessUnitSequence: Long = -1L,
    ): Boolean {
        if (nal.size < 6) return isRunning
        val first = nal[4].toInt() and 0xFF
        val avcType = first and 0x1F
        val hevcType = (first ushr 1) and 0x3F
        when {
            hevcType == 32 -> { select(Codec.HEVC); updateVps(nal); return isRunning }
            hevcType == 33 && codecType == Codec.HEVC -> { updateSps(nal); return isRunning }
            hevcType == 34 && codecType == Codec.HEVC -> { updatePps(nal); return isRunning }
            avcType == 7 -> { select(Codec.AVC); updateAvcSps(nal); return isRunning }
            avcType == 8 && codecType == Codec.AVC -> { updatePps(nal); return isRunning }
            codecType == Codec.AVC && avcType == 9 -> return isRunning
            codecType == Codec.HEVC && hevcType == 35 -> return isRunning
        }

        val randomAccess = containsRandomAccessFrame(nal)
        if (randomAccess && accessUnitSequence == rejectedRandomAccessSequence) return isRunning
        if (waitingForKeyFrame && !randomAccess) return isRunning

        val accessUnit = if (
            randomAccess && codecType == Codec.HEVC && isRm500Rockchip()
        ) prependCurrentHevcParameterSets(nal) else nal

        val active = codec ?: run {
            // Keep only the latest random-access unit while waiting for codec
            // parameter sets. Inter frames cannot initialize a fresh decoder.
            pending.clear()
            pending.addLast(accessUnit)
            tryStart()
            return isRunning
        }
        lastAccessUnitSequence = accessUnitSequence
        lastAccessUnitSize = accessUnit.size
        lastAccessUnitRandomAccess = randomAccess
        try {
            queue(active, accessUnit, rtpIngressNanos)
            waitingForKeyFrame = false
            decoderMode = modeFor(activeDecoderName)
            drain(active, 0L)
        } catch (error: IllegalStateException) {
            handleFatal(error)
        }
        return true
    }

    /** Drains decoded frames independently from input arrival. */
    fun pumpOutput(timeoutUs: Long = 1_000L): Boolean {
        val active = codec ?: return false
        return try {
            drain(active, timeoutUs)
            true
        } catch (error: IllegalStateException) {
            handleFatal(error)
            false
        }
    }

    private fun select(type: Codec) {
        if (codecType == type) return
        releaseCodec()
        codecType = type
        vps = null; sps = null; pps = null
        waitingForKeyFrame = true
        decoderMode = TeleautoVideoDecoderMode.WAITING_KEYFRAME
    }

    private fun updateVps(value: ByteArray) {
        if (vps?.contentEquals(value) == true) return
        releaseCodec()
        vps = value.copyOf(); sps = null; pps = null; lastSpsInfo = null
        rockchipHevcFatalErrors = 0
    }

    private fun updateSps(value: ByteArray) {
        if (sps?.contentEquals(value) == true) return
        releaseCodec()
        sps = value.copyOf(); pps = null
        lastSpsInfo = TeleautoHevcSpsParser.parse(value)
        rockchipHevcFatalErrors = 0
    }

    private fun updateAvcSps(value: ByteArray) {
        if (sps?.contentEquals(value) == true) return
        releaseCodec()
        sps = value.copyOf(); pps = null
    }

    private fun updatePps(value: ByteArray) {
        if (pps?.contentEquals(value) == true) return
        releaseCodec()
        pps = value.copyOf()
        tryStart()
    }

    private fun tryStart() {
        if (codec != null) return
        val type = codecType ?: return
        val s = sps ?: return
        val p = pps ?: return
        if (type == Codec.HEVC && vps == null) return
        val requestedDecoder = decoderName(type)
        val active = try {
            if (requestedDecoder != null) {
                MediaCodec.createByCodecName(requestedDecoder)
            } else {
                MediaCodec.createDecoderByType(type.mime)
            }
        } catch (error: Exception) {
            android.util.Log.e(
                "TeleautoVideo",
                "No ${type.name} decoder name=${requestedDecoder ?: "platform-default"} on ${deviceDescription()}",
                error,
            )
            return
        }
        val dimensions = if (type == Codec.HEVC) lastSpsInfo else null
        val format = MediaFormat.createVideoFormat(
            type.mime,
            dimensions?.width ?: DEFAULT_WIDTH,
            dimensions?.height ?: DEFAULT_HEIGHT,
        ).apply {
            // Several Android 7 OMX implementations otherwise expose an input
            // buffer sized for an arbitrary default frame. A large HEVC IDR can
            // exceed it and destabilize OMX.rk instead of producing a useful
            // recoverable error.
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_COMPRESSED_ACCESS_UNIT_BYTES)
            if (type == Codec.AVC) {
                setByteBuffer("csd-0", ByteBuffer.wrap(s))
                setByteBuffer("csd-1", ByteBuffer.wrap(p))
            } else {
                val v = vps!!
                val config = ByteArray(v.size + s.size + p.size)
                System.arraycopy(v, 0, config, 0, v.size)
                System.arraycopy(s, 0, config, v.size, s.size)
                System.arraycopy(p, 0, config, v.size + s.size, p.size)
                setByteBuffer("csd-0", ByteBuffer.wrap(config))
            }
            // OMX.rk decoders shipped on Android 7 accept the codec itself but
            // some firmware revisions reject optional priority configuration.
            // It is only a scheduling hint, so omit it on the RM500 platform.
            if (!isLegacyRockchip()) setInteger(MediaFormat.KEY_PRIORITY, 0)
        }
        try {
            active.configure(format, surface, null, 0)
            active.start()
        } catch (error: Exception) {
            runCatching { active.release() }
            if (requestedDecoder == ROCKCHIP_HEVC_DECODER) rockchipHevcFatalErrors++
            decoderMode = TeleautoVideoDecoderMode.WAITING_KEYFRAME
            android.util.Log.e(
                "TeleautoVideo",
                "${type.name} decoder configure failed on ${deviceDescription()}",
                error,
            )
            return
        }
        codec = active
        activeDecoderName = active.name
        if (fatalAtNanos != 0L) {
            restarts++
            fatalRecoveryMs = (android.os.SystemClock.elapsedRealtimeNanos() - fatalAtNanos) / 1_000_000L
            fatalAtNanos = 0L
        }
        decoderMode = modeFor(active.name)
        android.util.Log.i(
            "TeleautoVideo",
            "${type.name} decoder=${active.name} started on ${deviceDescription()}" +
                " format=${dimensions ?: "unknown"}",
        )
        while (pending.isNotEmpty()) {
            queue(active, pending.removeFirst(), 0L)
            waitingForKeyFrame = false
        }
    }

    private fun queue(active: MediaCodec, nal: ByteArray, rtpIngressNanos: Long) {
        val index = active.dequeueInputBuffer(5_000)
        if (index < 0) return
        val input = active.getInputBuffer(index)
        if (input == null || nal.size > input.capacity()) {
            active.queueInputBuffer(index, 0, 0, 0, 0)
            android.util.Log.w(
                "TeleautoVideo",
                "drop oversized access unit bytes=${nal.size} capacity=${input?.capacity() ?: 0}",
            )
            return
        }
        input.clear()
        input.put(nal)
        val queuedNanos = android.os.SystemClock.elapsedRealtimeNanos()
        val presentationTimeUs = maxOf(lastPresentationTimeUs + 1, queuedNanos / 1_000)
        lastPresentationTimeUs = presentationTimeUs
        active.queueInputBuffer(index, 0, nal.size, presentationTimeUs, 0)
        if (rtpIngressNanos > 0L) {
            frameTimings[presentationTimeUs] = FrameTiming(rtpIngressNanos, queuedNanos)
            latencyCollector?.recordRtpToCodec(queuedNanos - rtpIngressNanos)
        }
        submittedFrames++
    }

    private fun drain(active: MediaCodec, initialTimeoutUs: Long) {
        val info = MediaCodec.BufferInfo()
        var timeoutUs = initialTimeoutUs
        var newestOutput = -1
        var newestPresentationTimeUs = 0L
        while (true) {
            when (val output = active.dequeueOutputBuffer(info, timeoutUs)) {
                in 0..Int.MAX_VALUE -> {
                    // OMX may release several decoded pictures at once after a
                    // scheduling or USB/FEC stall. Rendering every one would
                    // preserve history at the cost of a sudden latency spike.
                    // Keep draining and render only the newest ready picture.
                    if (newestOutput >= 0) {
                        active.releaseOutputBuffer(newestOutput, false)
                        frameTimings.remove(newestPresentationTimeUs)
                        discardedStaleOutputFrames++
                    }
                    newestOutput = output
                    newestPresentationTimeUs = info.presentationTimeUs
                    timeoutUs = 0L
                }
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> runCatching {
                    videoWidth = active.outputFormat.getInteger(MediaFormat.KEY_WIDTH)
                    videoHeight = active.outputFormat.getInteger(MediaFormat.KEY_HEIGHT)
                }
                MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (newestOutput >= 0) {
                        active.releaseOutputBuffer(newestOutput, true)
                        val outputNanos = android.os.SystemClock.elapsedRealtimeNanos()
                        frameTimings.remove(newestPresentationTimeUs)?.let { timing ->
                            latencyCollector?.recordCodecOutput(outputNanos - timing.queuedNanos)
                            latencyCollector?.recordGround(outputNanos - timing.rtpIngressNanos)
                        }
                        renderedFrames++
                    }
                    return
                }
            }
        }
    }

    private fun handleFatal(error: IllegalStateException) {
        val failedDecoder = activeDecoderName
        fatalErrors++
        lastFatalSequence = lastAccessUnitSequence
        if (lastAccessUnitRandomAccess) rejectedRandomAccessSequence = lastAccessUnitSequence
        if (fatalAtNanos == 0L) fatalAtNanos = android.os.SystemClock.elapsedRealtimeNanos()
        if (codecType == Codec.HEVC && isRm500Rockchip() && failedDecoder == ROCKCHIP_HEVC_DECODER) {
            rockchipHevcFatalErrors++
        }
        android.util.Log.w(
            "TeleautoVideo",
            "decoder fatal name=$failedDecoder rkFailures=$rockchipHevcFatalErrors " +
                "au=$lastAccessUnitSequence bytes=$lastAccessUnitSize random=$lastAccessUnitRandomAccess",
            error,
        )
        releaseCodec()
    }

    private fun releaseCodec() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        activeDecoderName = null
        pending.clear()
        frameTimings.clear()
        waitingForKeyFrame = true
        decoderMode = TeleautoVideoDecoderMode.WAITING_KEYFRAME
    }

    fun onDiscontinuity(requireKeyFrame: Boolean = false) {
        pending.clear()
        if (requireKeyFrame) {
            // A stream-level discontinuity invalidates Rockchip's internal
            // reference-picture state. Recreate the same hardware component;
            // cached VPS/SPS/PPS remain available for the clean random-access AU.
            releaseCodec()
            waitingForKeyFrame = true
            decoderMode = TeleautoVideoDecoderMode.WAITING_KEYFRAME
        } else if (codec == null) {
            waitingForKeyFrame = true
            decoderMode = TeleautoVideoDecoderMode.WAITING_KEYFRAME
        } else {
            // The assembler has already discarded the damaged access unit.
            // Keep the decoder session alive and submit the next complete AU,
            // accepting temporary concealment/artifacts instead of freezing
            // until the next IDR. This is especially important on RM500 where
            // intermittent USB/RTP loss is more visible than on RM700.
            waitingForKeyFrame = false
            decoderMode = modeFor(activeDecoderName)
        }
    }

    fun release() {
        releaseCodec()
        codecType = null
        vps = null; sps = null; pps = null
        rockchipHevcFatalErrors = 0
        fatalErrors = 0; restarts = 0; lastFatalSequence = -1L; fatalRecoveryMs = -1L
        fatalAtNanos = 0L; rejectedRandomAccessSequence = -1L
        discardedStaleOutputFrames = 0L
        lastSpsInfo = null
        decoderMode = TeleautoVideoDecoderMode.WAITING_KEYFRAME
    }

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
            if (start >= bytes.size) break
            val first = bytes[start].toInt() and 0xFF
            val avcType = first and 0x1F
            val hevcType = (first ushr 1) and 0x3F
            if (avcType == 5 || hevcType in 16..23) return true
            offset = start + 1
        }
        return false
    }

    private fun prependCurrentHevcParameterSets(accessUnit: ByteArray): ByteArray {
        val currentVps = vps ?: return accessUnit
        val currentSps = sps ?: return accessUnit
        val currentPps = pps ?: return accessUnit
        val result = ByteArray(currentVps.size + currentSps.size + currentPps.size + accessUnit.size)
        var offset = 0
        currentVps.copyInto(result, offset); offset += currentVps.size
        currentSps.copyInto(result, offset); offset += currentSps.size
        currentPps.copyInto(result, offset); offset += currentPps.size
        accessUnit.copyInto(result, offset)
        return result
    }

    private data class FrameTiming(val rtpIngressNanos: Long, val queuedNanos: Long)

    private fun isLegacyRockchip(): Boolean =
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1 &&
            Build.HARDWARE.lowercase(java.util.Locale.US).contains("rk")

    private fun isRm500Rockchip(): Boolean {
        val model = Build.MODEL.lowercase(java.util.Locale.US)
        val product = Build.PRODUCT.lowercase(java.util.Locale.US)
        return Build.VERSION.SDK_INT == Build.VERSION_CODES.N_MR1 &&
            Build.HARDWARE.equals("rk30board", ignoreCase = true) &&
            (model == "ag501" || model == "rm500" || product == "ag501" || product == "rm500")
    }

    private fun decoderName(type: Codec): String? =
        when {
            type != Codec.HEVC || !isRm500Rockchip() -> null
            else -> ROCKCHIP_HEVC_DECODER
        }

    private fun modeFor(name: String?): TeleautoVideoDecoderMode =
        TeleautoVideoDecoderMode.HARDWARE

    private fun deviceDescription(): String =
        "${Build.MODEL}/${Build.HARDWARE} sdk=${Build.VERSION.SDK_INT}"

    private companion object {
        const val ROCKCHIP_HEVC_DECODER = "OMX.rk.video_decoder.hevc"
        const val DEFAULT_WIDTH = 1920
        const val DEFAULT_HEIGHT = 1080
        const val MAX_COMPRESSED_ACCESS_UNIT_BYTES = 2 * 1024 * 1024
    }
}
