package tech.alius.rc.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeleautoRtpSequenceTrackerTest {
    @Test fun countsGapsAndRecoversLatePackets() {
        val tracker = TeleautoRtpSequenceTracker()
        assertFalse(tracker.offer(1, 100))
        tracker.offer(1, 103)
        assertEquals(2, tracker.lostPackets)
        tracker.offer(1, 101)
        tracker.offer(1, 102)
        assertEquals(0, tracker.lostPackets)
    }

    @Test fun handlesWraparoundWithoutLoss() {
        val tracker = TeleautoRtpSequenceTracker()
        tracker.offer(1, 65535)
        tracker.offer(1, 0)
        assertEquals(0, tracker.lostPackets)
    }

    @Test fun isolatesDifferentSsrcStreams() {
        val tracker = TeleautoRtpSequenceTracker()
        tracker.offer(1, 40000)
        assertTrue(tracker.offer(2, 12))
        tracker.offer(2, 13)
        assertEquals(0, tracker.lostPackets)
    }

    @Test fun joinsNalUnitsIntoOneAccessUnit() {
        val builder = TeleautoH264AccessUnitBuilder()
        assertEquals(null, builder.append(byteArrayOf(0, 0, 0, 1, 1, 2), false))
        val frame = builder.append(byteArrayOf(0, 0, 0, 1, 1, 3), true)!!
        assertEquals(listOf<Byte>(0, 0, 0, 1, 1, 2, 0, 0, 0, 1, 1, 3), frame.toList())
    }

    @Test fun assemblesHevcFuAndRestoresTwoByteNalHeader() {
        val output = mutableListOf<ByteArray>()
        val assembler = TeleautoH264RtpAssembler({ nal, _ -> output += nal })
        // A VPS selects HEVC mode.
        assembler.onPacket(rtp(10, true, byteArrayOf(0x40, 0x01, 0x55)), 15, 10, true)
        // FU indicator(type 49), layer/tid byte, start/end FU headers.
        assembler.onPacket(rtp(11, false, byteArrayOf(0x62, 0x01, 0x93.toByte(), 1, 2)), 17, 11, false)
        assembler.onPacket(rtp(12, true, byteArrayOf(0x62, 0x01, 0x53, 3, 4)), 17, 12, true)

        assertEquals(2, output.size)
        assertEquals(listOf<Byte>(0, 0, 0, 1, 0x26, 0x01, 1, 2, 3, 4), output[1].toList())
    }

    @Test fun stripsRtpPaddingFromHevcNal() {
        val output = mutableListOf<ByteArray>()
        val assembler = TeleautoH264RtpAssembler({ nal, _ -> output += nal })
        val packet = rtp(10, true, byteArrayOf(0x40, 0x01, 0x55)).copyOf(19)
        packet[0] = 0xA0.toByte()
        packet[15] = 0x66
        packet[16] = 0x66
        packet[17] = 0x66
        packet[18] = 4

        assembler.onPacket(packet, packet.size, 10, true)

        assertEquals(listOf<Byte>(0, 0, 0, 1, 0x40, 0x01, 0x55), output.single().toList())
    }

    @Test fun reportsHevcFuSequenceDiscontinuityAndDropsPartialFu() {
        val output = mutableListOf<ByteArray>()
        var discontinuities = 0
        val assembler = TeleautoH264RtpAssembler(
            { nal, _ -> output += nal },
            { discontinuities++ },
        )
        assembler.onPacket(rtp(10, true, byteArrayOf(0x40, 0x01, 0x55)), 15, 10, true)
        assembler.onPacket(rtp(11, false, byteArrayOf(0x62, 0x01, 0x93.toByte(), 1)), 16, 11, false)
        assembler.onPacket(rtp(13, true, byteArrayOf(0x62, 0x01, 0x53, 3)), 16, 13, true)

        assertEquals(1, discontinuities)
        assertEquals(1, output.size) // VPS only; the damaged FU is never emitted.
    }

    @Test fun ignoresLateRecoveredPacketWithoutMovingSequenceBackwards() {
        val output = mutableListOf<ByteArray>()
        var discontinuities = 0
        val assembler = TeleautoH264RtpAssembler(
            { nal, _ -> output += nal },
            { discontinuities++ },
        )
        assembler.onPacket(rtp(10, true, byteArrayOf(0x40, 0x01, 0x55)), 15, 10, true)
        assembler.onPacket(rtp(12, true, byteArrayOf(0x02, 0x01, 0x22)), 15, 12, true)
        assembler.onPacket(rtp(11, true, byteArrayOf(0x02, 0x01, 0x11)), 15, 11, true)
        assembler.onPacket(rtp(13, true, byteArrayOf(0x02, 0x01, 0x33)), 15, 13, true)

        // A gap between complete NAL units no longer invalidates the entire
        // access unit. Only an unfinished FU reports damage.
        assertEquals(0, discontinuities)
        assertEquals(3, output.size)
    }

    @Test fun reordersPacketRecoveredInsideFourMillisecondWindow() {
        val reorder = TeleautoRtpReorderBuffer(maxWaitNanos = 4_000_000)
        val delivered = mutableListOf<Int>()
        val sink: (TeleautoRtpReorderBuffer.Packet) -> Unit = { delivered += it.sequence }
        reorder.offer(packet(10), 0, sink)
        reorder.offer(packet(12), 1_000_000, sink)
        reorder.offer(packet(11), 3_000_000, sink)

        assertEquals(listOf(10, 11, 12), delivered)
        assertEquals(0, reorder.lostPackets)
        assertEquals(1, reorder.reorderedPackets)
    }

    @Test fun retainedOutOfOrderPacketOwnsItsBytes() {
        val reorder = TeleautoRtpReorderBuffer(maxWaitNanos = 4_000_000)
        val deliveredPayloads = mutableListOf<Byte>()
        val sink: (TeleautoRtpReorderBuffer.Packet) -> Unit = { deliveredPayloads += it.bytes[14] }
        reorder.offer(packet(10), 0, sink)
        val shared = packet(12)
        reorder.offer(shared, 1_000_000, sink)
        shared.bytes[14] = 99
        reorder.offer(packet(11), 2_000_000, sink)

        assertEquals(listOf<Byte>(1, 1, 1), deliveredPayloads)
    }

    @Test fun expiresGapWithoutWaitingForKeyFrame() {
        val reorder = TeleautoRtpReorderBuffer(maxWaitNanos = 4_000_000)
        val delivered = mutableListOf<Int>()
        var gap = 0
        val sink: (TeleautoRtpReorderBuffer.Packet) -> Unit = { delivered += it.sequence }
        reorder.offer(packet(20), 0, sink)
        reorder.offer(packet(22), 1_000_000, sink)
        reorder.flushExpired(5_000_001, sink) { gap += it }

        assertEquals(listOf(20, 22), delivered)
        assertEquals(1, gap)
        assertEquals(1, reorder.lostPackets)
    }

    @Test fun discardsOnlyDamagedFuAndKeepsFollowingNal() {
        val output = mutableListOf<ByteArray>()
        var damaged = 0
        val assembler = TeleautoH264RtpAssembler({ nal, _ -> output += nal }, { damaged++ })
        assembler.onPacket(rtp(30, true, byteArrayOf(0x40, 0x01, 0x55)), 15, 30, true)
        assembler.onPacket(rtp(31, false, byteArrayOf(0x62, 0x01, 0x93.toByte(), 1)), 16, 31, false)
        assembler.onPacket(rtp(33, true, byteArrayOf(0x02, 0x01, 0x44)), 15, 33, true)

        assertEquals(1, damaged)
        assertEquals(2, output.size)
        assertEquals(listOf<Byte>(0, 0, 0, 1, 0x02, 0x01, 0x44), output.last().toList())
    }

    private fun packet(sequence: Int, ssrc: Long = 1L) = TeleautoRtpReorderBuffer.Packet(
        bytes = rtp(sequence, true, byteArrayOf(0x02, 0x01, 1)),
        length = 15,
        ssrc = ssrc,
        sequence = sequence,
        timestamp = sequence.toLong(),
        marker = true,
        receivedNanos = 0,
    )

    private fun rtp(seq: Int, marker: Boolean, payload: ByteArray): ByteArray =
        ByteArray(12 + payload.size).also { packet ->
            packet[0] = 0x80.toByte()
            packet[1] = ((if (marker) 0x80 else 0) or 96).toByte()
            packet[2] = (seq ushr 8).toByte()
            packet[3] = seq.toByte()
            System.arraycopy(payload, 0, packet, 12, payload.size)
        }
}
