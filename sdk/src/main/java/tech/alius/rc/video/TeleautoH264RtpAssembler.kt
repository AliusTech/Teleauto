package tech.alius.rc.video

/** Auto-detecting RTP H.264/H.265 assembler. Historical name is retained for compatibility. */
class TeleautoH264RtpAssembler(
    private val onNal: (nal: ByteArray, frameEnd: Boolean) -> Unit,
    private val onDiscontinuity: () -> Unit = {},
) {
    private enum class Codec { AVC, HEVC }
    private var codec: Codec? = null
    private var expectedSeq = -1
    private var fu = ByteArray(2048)
    private var fuSize = 0
    private var fuDamageReported = false

    fun onPacket(pkt: ByteArray, len: Int, seq: Int, marker: Boolean) {
        if (expectedSeq != -1 && seq != expectedSeq) {
            val forward = (seq - expectedSeq) and 0xFFFF
            // WFB/FEC may recover an older RTP packet after newer packets.
            // It cannot repair the current FU anymore, but must not move the
            // sequence backwards or create a second discontinuity.
            if (forward > 0x8000) return
            if (fuSize > 0) {
                resetFu()
                if (!fuDamageReported) onDiscontinuity()
                fuDamageReported = true
            }
        }
        expectedSeq = (seq + 1) and 0xFFFF
        val payloadEnd = payloadEnd(pkt, len) ?: run { onDiscontinuity(); return }
        val off = payloadOffset(pkt, payloadEnd) ?: run { onDiscontinuity(); return }
        val first = pkt[off].toInt() and 0xFF
        if (codec == null) {
            codec = when {
                ((first ushr 1) and 0x3F) in 32..34 -> Codec.HEVC
                (first and 0x1F) in 7..8 -> Codec.AVC
                else -> return
            }
        }
        when (codec) {
            Codec.AVC -> onAvc(pkt, off, payloadEnd, marker)
            Codec.HEVC -> onHevc(pkt, off, payloadEnd, marker)
            null -> Unit
        }
    }

    fun reset() {
        expectedSeq = -1
        resetFu()
        fuDamageReported = false
    }

    private fun onAvc(pkt: ByteArray, off: Int, len: Int, marker: Boolean) {
        when (pkt[off].toInt() and 0x1F) {
            in 1..23 -> emit(pkt, off, len, marker)
            24 -> emitAggregate(pkt, off + 1, len, marker)
            28 -> {
                if (off + 2 > len) { onDiscontinuity(); return }
                val header = pkt[off + 1].toInt() and 0xFF
                if (header and 0x80 != 0) {
                    fuDamageReported = false
                    beginFu(1)
                    fu[4] = ((pkt[off].toInt() and 0xE0) or (header and 0x1F)).toByte()
                } else if (fuSize == 0) {
                    if (!fuDamageReported) onDiscontinuity()
                    fuDamageReported = true
                    return
                }
                appendFu(pkt, off + 2, len)
                if (header and 0x40 != 0) endFu(marker)
            }
        }
    }

    private fun onHevc(pkt: ByteArray, off: Int, len: Int, marker: Boolean) {
        if (off + 2 > len) { onDiscontinuity(); return }
        when ((pkt[off].toInt() ushr 1) and 0x3F) {
            in 0..47 -> emit(pkt, off, len, marker)
            48 -> emitAggregate(pkt, off + 2, len, marker)
            49 -> {
                if (off + 3 > len) { onDiscontinuity(); return }
                val header = pkt[off + 2].toInt() and 0xFF
                if (header and 0x80 != 0) {
                    fuDamageReported = false
                    beginFu(2)
                    fu[4] = ((pkt[off].toInt() and 0x81) or ((header and 0x3F) shl 1)).toByte()
                    fu[5] = pkt[off + 1]
                } else if (fuSize == 0) {
                    if (!fuDamageReported) onDiscontinuity()
                    fuDamageReported = true
                    return
                }
                appendFu(pkt, off + 3, len)
                if (header and 0x40 != 0) endFu(marker)
            }
        }
    }

    private fun emitAggregate(pkt: ByteArray, start: Int, len: Int, marker: Boolean) {
        var offset = start
        while (offset + 2 <= len) {
            val size = be16(pkt, offset)
            offset += 2
            if (size <= 0 || offset + size > len) {
                onDiscontinuity()
                return
            }
            emit(pkt, offset, offset + size, marker && offset + size == len)
            offset += size
        }
        if (offset != len) onDiscontinuity()
    }

    private fun payloadOffset(pkt: ByteArray, len: Int): Int? {
        if (len < 12 || (pkt[0].toInt() and 0xC0) != 0x80) return null
        var offset = 12 + (pkt[0].toInt() and 0x0F) * 4
        if (offset > len) return null
        if (pkt[0].toInt() and 0x10 != 0) {
            if (offset + 4 > len) return null
            offset += 4 + be16(pkt, offset + 2) * 4
        }
        return offset.takeIf { it < len }
    }

    private fun payloadEnd(pkt: ByteArray, len: Int): Int? {
        if (len < 12) return null
        if (pkt[0].toInt() and 0x20 == 0) return len
        val padding = pkt[len - 1].toInt() and 0xFF
        return (len - padding).takeIf { padding > 0 && it >= 12 }
    }

    private fun beginFu(headerBytes: Int) {
        fuSize = 4 + headerBytes
        fu[0] = 0; fu[1] = 0; fu[2] = 0; fu[3] = 1
    }

    private fun appendFu(pkt: ByteArray, from: Int, to: Int) {
        if (fuSize == 0 || from > to) return
        val count = to - from
        if (fuSize + count > fu.size) fu = fu.copyOf(maxOf(fuSize + count, fu.size * 2))
        System.arraycopy(pkt, from, fu, fuSize, count)
        fuSize += count
    }

    private fun endFu(marker: Boolean) {
        if (fuSize == 0) return
        onNal(fu.copyOf(fuSize), marker)
        fuSize = 0
        fuDamageReported = false
    }

    private fun resetFu() { fuSize = 0 }

    private fun emit(pkt: ByteArray, from: Int, to: Int, marker: Boolean) {
        val out = ByteArray(4 + to - from)
        out[3] = 1
        System.arraycopy(pkt, from, out, 4, to - from)
        onNal(out, marker)
    }

    private fun be16(bytes: ByteArray, offset: Int) =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
}
