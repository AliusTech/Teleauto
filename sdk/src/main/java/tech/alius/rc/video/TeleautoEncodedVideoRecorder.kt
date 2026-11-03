package tech.alius.rc.video

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** Writes the received compressed stream directly to MP4 without re-encoding. */
internal class TeleautoEncodedVideoRecorder(
    private val outputPath: String,
    private val fallbackWidth: Int,
    private val fallbackHeight: Int,
) {
    private enum class Codec(val mime: String) { AVC(MediaFormat.MIMETYPE_VIDEO_AVC), HEVC(MediaFormat.MIMETYPE_VIDEO_HEVC) }

    private var codec: Codec? = null
    private var vps: ByteArray? = null
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null
    private var muxer: MediaMuxer? = null
    private var track = -1
    private var baseRtpTimestamp = -1L
    private var lastPtsUs = -1L
    private var samples = 0L

    @Synchronized fun onParameterSet(nal: ByteArray) {
        val type = nalType(nal) ?: return
        when {
            type.hevcType == 32 -> { codec = Codec.HEVC; vps = nal.copyOf() }
            type.hevcType == 33 -> { codec = Codec.HEVC; sps = nal.copyOf() }
            type.hevcType == 34 -> { codec = Codec.HEVC; pps = nal.copyOf() }
            type.avcType == 7 -> { codec = Codec.AVC; sps = nal.copyOf() }
            type.avcType == 8 -> { codec = Codec.AVC; pps = nal.copyOf() }
        }
    }

    @Synchronized fun onAccessUnit(bytes: ByteArray, rtpTimestamp: Long) {
        val randomAccess = containsRandomAccess(bytes)
        if (muxer == null) {
            if (!randomAccess || !startMuxer()) return
            baseRtpTimestamp = rtpTimestamp
        }
        val delta = (rtpTimestamp - baseRtpTimestamp) and 0xFFFF_FFFFL
        val ptsUs = maxOf(lastPtsUs + 1L, delta * 1_000_000L / RTP_CLOCK_HZ)
        lastPtsUs = ptsUs
        val info = MediaCodec.BufferInfo().apply {
            set(0, bytes.size, ptsUs, if (randomAccess) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
        }
        muxer?.writeSampleData(track, ByteBuffer.wrap(bytes), info)
        samples++
    }

    @Synchronized fun stop(): Boolean {
        val active = muxer
        muxer = null
        if (active != null) {
            runCatching { active.stop() }
            runCatching { active.release() }
        }
        val valid = samples > 0L && File(outputPath).length() > 0L
        if (!valid) File(outputPath).delete()
        return valid
    }

    private fun startMuxer(): Boolean {
        val selected = codec ?: return false
        val s = sps ?: return false
        val p = pps ?: return false
        val dimensions = if (selected == Codec.HEVC) TeleautoHevcSpsParser.parse(s) else null
        val width = dimensions?.width ?: fallbackWidth.takeIf { it > 0 } ?: DEFAULT_WIDTH
        val height = dimensions?.height ?: fallbackHeight.takeIf { it > 0 } ?: DEFAULT_HEIGHT
        val format = MediaFormat.createVideoFormat(selected.mime, width, height).apply {
            if (selected == Codec.AVC) {
                setByteBuffer("csd-0", ByteBuffer.wrap(s))
                setByteBuffer("csd-1", ByteBuffer.wrap(p))
            } else {
                val v = vps ?: return false
                setByteBuffer("csd-0", ByteBuffer.wrap(v + s + p))
            }
        }
        return runCatching {
            File(outputPath).parentFile?.mkdirs()
            MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also {
                track = it.addTrack(format)
                it.start()
                muxer = it
            }
            true
        }.getOrElse {
            android.util.Log.e("TeleautoVideo", "recording muxer start failed", it)
            false
        }
    }

    private data class NalType(val avcType: Int, val hevcType: Int)

    private fun nalType(bytes: ByteArray): NalType? {
        var offset = 0
        while (offset + 4 < bytes.size) {
            val start = when {
                bytes[offset] == 0.toByte() && bytes[offset + 1] == 0.toByte() &&
                    bytes[offset + 2] == 0.toByte() && bytes[offset + 3] == 1.toByte() -> offset + 4
                bytes[offset] == 0.toByte() && bytes[offset + 1] == 0.toByte() &&
                    bytes[offset + 2] == 1.toByte() -> offset + 3
                else -> { offset++; continue }
            }
            val first = bytes.getOrNull(start)?.toInt()?.and(0xFF) ?: return null
            return NalType(first and 0x1F, (first ushr 1) and 0x3F)
        }
        return null
    }

    private fun containsRandomAccess(bytes: ByteArray): Boolean {
        var offset = 0
        while (offset + 4 < bytes.size) {
            val start = when {
                bytes[offset] == 0.toByte() && bytes[offset + 1] == 0.toByte() &&
                    bytes[offset + 2] == 0.toByte() && bytes[offset + 3] == 1.toByte() -> offset + 4
                bytes[offset] == 0.toByte() && bytes[offset + 1] == 0.toByte() &&
                    bytes[offset + 2] == 1.toByte() -> offset + 3
                else -> { offset++; continue }
            }
            val first = bytes.getOrNull(start)?.toInt()?.and(0xFF) ?: return false
            if ((first and 0x1F) == 5 || ((first ushr 1) and 0x3F) in 16..23) return true
            offset = start + 1
        }
        return false
    }

    companion object {
        private const val RTP_CLOCK_HZ = 90_000L
        private const val DEFAULT_WIDTH = 1280
        private const val DEFAULT_HEIGHT = 720
    }
}
