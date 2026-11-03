package tech.alius.rc.mavlink

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class TeleautoMavlinkFrame(
    val version: Int,
    val sequence: Int,
    val systemId: Int,
    val componentId: Int,
    val messageId: Int,
    val payload: ByteArray,
)

/** Incremental MAVLink 1/2 framing with CRC validation for the supported common messages. */
internal class TeleautoMavlinkParser(private val onFrame: (TeleautoMavlinkFrame) -> Unit) {
    private var pending = ByteArray(8192)
    private var size = 0

    fun offer(bytes: ByteArray, length: Int) {
        if (length <= 0) return
        ensureCapacity(size + length)
        bytes.copyInto(pending, size, 0, length)
        size += length
        parse()
    }

    fun reset() { size = 0 }

    private fun parse() {
        var offset = 0
        while (offset < size) {
            val magic = pending[offset].toInt() and 0xff
            if (magic != 0xfe && magic != 0xfd) { offset++; continue }
            if (size - offset < if (magic == 0xfe) 8 else 12) break
            val payloadLength = pending[offset + 1].toInt() and 0xff
            val signed = magic == 0xfd && (pending[offset + 2].toInt() and 0x01) != 0
            val total = if (magic == 0xfe) payloadLength + 8 else payloadLength + 12 + if (signed) 13 else 0
            if (size - offset < total) break
            val headerLength = if (magic == 0xfe) 5 else 9
            val sequence = pending[offset + if (magic == 0xfe) 2 else 4].u8()
            val systemId = pending[offset + if (magic == 0xfe) 3 else 5].u8()
            val componentId = pending[offset + if (magic == 0xfe) 4 else 6].u8()
            val messageId = if (magic == 0xfe) pending[offset + 5].u8() else
                pending[offset + 7].u8() or (pending[offset + 8].u8() shl 8) or (pending[offset + 9].u8() shl 16)
            val payloadOffset = offset + if (magic == 0xfe) 6 else 10
            val crcOffset = payloadOffset + payloadLength
            val expected = pending[crcOffset].u8() or (pending[crcOffset + 1].u8() shl 8)
            val extra = CRC_EXTRA[messageId]
            val valid = if (extra == null) true else {
                var crc = 0xffff
                for (index in offset + 1 until crcOffset) crc = accumulate(pending[index].u8(), crc)
                crc = accumulate(extra, crc)
                crc == expected
            }
            if (valid) {
                onFrame(
                    TeleautoMavlinkFrame(
                        version = if (magic == 0xfe) 1 else 2,
                        sequence = sequence,
                        systemId = systemId,
                        componentId = componentId,
                        messageId = messageId,
                        payload = pending.copyOfRange(payloadOffset, payloadOffset + payloadLength),
                    )
                )
                offset += total
            } else {
                offset++
            }
        }
        if (offset > 0) {
            pending.copyInto(pending, 0, offset, size)
            size -= offset
        }
        if (size == pending.size) size = 0
    }

    private fun ensureCapacity(required: Int) {
        if (required <= pending.size) return
        pending = pending.copyOf(maxOf(required, pending.size * 2).coerceAtMost(65_536))
        if (required > pending.size) size = 0
    }

    private fun accumulate(value: Int, current: Int): Int {
        var tmp = value xor (current and 0xff)
        tmp = tmp xor (tmp shl 4)
        return ((current ushr 8) xor (tmp shl 8) xor (tmp shl 3) xor (tmp ushr 4)) and 0xffff
    }

    private fun Byte.u8() = toInt() and 0xff

    companion object {
        // common.xml CRC extras for the messages decoded by this SDK.
        val CRC_EXTRA = mapOf(
            0 to 50, 1 to 124, 24 to 24, 30 to 39, 33 to 104,
            105 to 93, 109 to 185, 147 to 154, 148 to 178, 396 to 50,
        )
    }
}

internal object TeleautoMavlinkDecoder {
    data class Heartbeat(
        val mavType: Int, val autopilot: Int, val baseMode: Int,
        val customMode: Long, val systemStatus: Int,
    )
    data class AutopilotVersion(
        val capabilities: ULong, val flightSoftwareVersion: Long, val boardVersion: Long,
        val vendorId: Int, val productId: Int, val uid: ULong,
    )
    data class ComponentInformationBasic(
        val capabilities: ULong,
        val vendorName: String?,
        val modelName: String?,
        val softwareVersion: String?,
        val hardwareVersion: String?,
        val serialNumber: String?,
    )
    data class Battery(val levelPercent: Int?, val voltageMv: Int?, val currentMa: Int?, val temperatureC: Float?)
    data class Gps(
        val fixType: Int, val satellites: Int?, val latitude: Double, val longitude: Double,
        val altitudeM: Double, val ephM: Float?, val epvM: Float?,
    )
    data class Position(
        val latitude: Double, val longitude: Double, val altitudeM: Double, val relativeAltitudeM: Double,
        val northMps: Float, val eastMps: Float, val downMps: Float, val headingDegrees: Float?,
    )
    data class Attitude(
        val rollDegrees: Float, val pitchDegrees: Float, val yawDegrees: Float,
        val rollRate: Float, val pitchRate: Float, val yawRate: Float,
    )
    data class Imu(
        val ax: Float, val ay: Float, val az: Float,
        val gx: Float, val gy: Float, val gz: Float, val temperatureC: Float?,
    )
    data class Radio(val rssi: Int, val remoteRssi: Int, val txBuffer: Int, val rxErrors: Int, val fixed: Int)

    fun heartbeat(frame: TeleautoMavlinkFrame): Heartbeat? = frame.payload.takeIf { frame.messageId == 0 && it.size >= 9 }?.let {
        val b = it.le()
        Heartbeat(it[4].u8(), it[5].u8(), it[6].u8(), b.getUInt(0), it[7].u8())
    }

    fun autopilotVersion(frame: TeleautoMavlinkFrame): AutopilotVersion? = frame.payload.takeIf { frame.messageId == 148 && it.size >= 60 }?.let {
        val b = it.le()
        AutopilotVersion(b.getULong(0), b.getUInt(8), b.getUInt(20), b.getUShort(48), b.getUShort(50), b.getULong(52))
    }

    fun componentInformationBasic(frame: TeleautoMavlinkFrame): ComponentInformationBasic? =
        frame.payload.takeIf { frame.messageId == 396 && it.size >= 160 }?.let {
            val b = it.le()
            ComponentInformationBasic(
                capabilities = b.getULong(0),
                vendorName = it.cString(16, 32),
                modelName = it.cString(48, 32),
                softwareVersion = it.cString(80, 24),
                hardwareVersion = it.cString(104, 24),
                serialNumber = it.cString(128, 32),
            )
        }

    fun battery(frame: TeleautoMavlinkFrame): Battery? = when (frame.messageId) {
        1 -> frame.payload.takeIf { it.size >= 31 }?.let {
            val b = it.le(); Battery(it[30].signedPercent(), b.getUShort(14).validU16(), b.getShort(16).toInt().takeIf { v -> v >= 0 }?.times(10), null)
        }
        147 -> frame.payload.takeIf { it.size >= 36 }?.let {
            val b = it.le(); Battery(it[35].signedPercent(), b.getUShort(10).validU16(), b.getShort(30).toInt().takeIf { v -> v >= 0 }?.times(10), b.getShort(8).toInt().takeIf { v -> v != 32767 }?.div(100f))
        }
        else -> null
    }

    fun gps(frame: TeleautoMavlinkFrame): Gps? = frame.payload.takeIf { frame.messageId == 24 && it.size >= 30 }?.let {
        val b = it.le()
        Gps(it[28].u8(), it[29].u8().takeIf { v -> v != 255 }, b.getInt(8) / 1e7, b.getInt(12) / 1e7,
            b.getInt(16) / 1000.0, b.getUShort(20).takeIf { v -> v != 65535 }?.div(100f), b.getUShort(22).takeIf { v -> v != 65535 }?.div(100f))
    }

    fun position(frame: TeleautoMavlinkFrame): Position? = frame.payload.takeIf { frame.messageId == 33 && it.size >= 28 }?.let {
        val b = it.le()
        Position(b.getInt(4) / 1e7, b.getInt(8) / 1e7, b.getInt(12) / 1000.0, b.getInt(16) / 1000.0,
            b.getShort(20) / 100f, b.getShort(22) / 100f, b.getShort(24) / 100f,
            b.getUShort(26).takeIf { v -> v != 65535 }?.div(100f))
    }

    fun attitude(frame: TeleautoMavlinkFrame): Attitude? = frame.payload.takeIf { frame.messageId == 30 && it.size >= 28 }?.let {
        val b = it.le(); val d = (180.0 / Math.PI).toFloat()
        Attitude(b.getFloat(4) * d, b.getFloat(8) * d, b.getFloat(12) * d, b.getFloat(16), b.getFloat(20), b.getFloat(24))
    }

    fun imu(frame: TeleautoMavlinkFrame): Imu? = frame.payload.takeIf { frame.messageId == 105 && it.size >= 58 }?.let {
        val b = it.le(); Imu(b.getFloat(8), b.getFloat(12), b.getFloat(16), b.getFloat(20), b.getFloat(24), b.getFloat(28), b.getFloat(56))
    }

    fun radio(frame: TeleautoMavlinkFrame): Radio? = frame.payload.takeIf { frame.messageId == 109 && it.size >= 9 }?.let {
        val b = it.le(); Radio(it[4].u8(), it[5].u8(), it[6].u8(), b.getUShort(0), b.getUShort(2))
    }

    private fun ByteArray.le() = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    private fun ByteArray.cString(offset: Int, length: Int): String? {
        val end = (offset until offset + length).firstOrNull { this[it] == 0.toByte() } ?: offset + length
        return copyOfRange(offset, end).toString(Charsets.UTF_8).trim().takeIf(String::isNotEmpty)
    }
    private fun Byte.u8() = toInt() and 0xff
    private fun Byte.signedPercent(): Int? = toInt().takeIf { it in 0..100 }
    private fun Int.validU16(): Int? = takeIf { it != 0xffff }
    private fun ByteBuffer.getUShort(offset: Int) = getShort(offset).toInt() and 0xffff
    private fun ByteBuffer.getUInt(offset: Int) = getInt(offset).toLong() and 0xffffffffL
    private fun ByteBuffer.getULong(offset: Int) = getLong(offset).toULong()
}

internal object TeleautoMavlinkEncoder {
    private var sequence = 0

    /** MAV_CMD_REQUEST_MESSAGE encoded as MAVLink 2 COMMAND_LONG. */
    @Synchronized
    fun requestMessage(targetSystem: Int, targetComponent: Int, messageId: Int): ByteArray {
        val payload = ByteBuffer.allocate(33).order(ByteOrder.LITTLE_ENDIAN).apply {
            putFloat(0, messageId.toFloat())
            putShort(28, 512.toShort())
            put(30, targetSystem.toByte())
            put(31, targetComponent.toByte())
            put(32, 0)
        }.array()
        val result = ByteArray(12 + payload.size)
        result[0] = 0xfd.toByte(); result[1] = payload.size.toByte(); result[2] = 0; result[3] = 0
        result[4] = (sequence++ and 0xff).toByte(); result[5] = 255.toByte(); result[6] = 190.toByte()
        result[7] = 76; result[8] = 0; result[9] = 0
        payload.copyInto(result, 10)
        var crc = 0xffff
        for (index in 1 until 10 + payload.size) crc = accumulate(result[index].toInt() and 0xff, crc)
        crc = accumulate(152, crc)
        result[10 + payload.size] = (crc and 0xff).toByte()
        result[11 + payload.size] = (crc ushr 8).toByte()
        return result
    }

    private fun accumulate(value: Int, current: Int): Int {
        var tmp = value xor (current and 0xff); tmp = tmp xor (tmp shl 4)
        return ((current ushr 8) xor (tmp shl 8) xor (tmp shl 3) xor (tmp ushr 4)) and 0xffff
    }
}
