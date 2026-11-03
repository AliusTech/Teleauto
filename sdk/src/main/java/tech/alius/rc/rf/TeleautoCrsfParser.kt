package tech.alius.rc.rf

import java.io.ByteArrayOutputStream

/**
 * CRSF 字节流解析器:输入原始字节流 → 输出结构化帧。
 * 线程安全:单线程调用 feed(),回调在同一线程执行。
 *
 * 用法:
 * ```
 * val parser = TeleautoCrsfParser { frame -> handle(frame) }
 * parser.feed(byteArray, length)
 * ```
 */
class TeleautoCrsfParser(
    private val onFrame: (TeleautoCrsfFrame) -> Unit,
) {
    private val buffer = ByteArrayOutputStream(TeleautoCrsf.MAX_FRAME_SIZE * 4)
    private var frameCount = 0L
    private var crcErrorCount = 0L

    /** 喂入新字节(可任意分片)。 */
    fun feed(data: ByteArray, length: Int) {
        synchronized(buffer) {
            for (i in 0 until length) {
                buffer.write(data[i].toInt())
            }
            drain()
        }
    }

    fun feed(data: ByteArray) = feed(data, data.size)

    /** 重置解析器状态(断线重连时调用)。 */
    fun reset() {
        synchronized(buffer) { buffer.reset() }
    }

    val framesParsed get() = frameCount
    val crcErrors get() = crcErrorCount

    private fun drain() {
        val bytes = buffer.toByteArray()
        var offset = 0
        var consumed = 0

        while (offset < bytes.size) {
            val result = tryParseFrame(bytes, offset)
            if (result == null) break
            consumed = offset + result
            offset += result
        }

        if (consumed > 0) {
            buffer.reset()
            if (offset < bytes.size) {
                buffer.write(bytes, offset, bytes.size - offset)
            }
        }
    }

    /** 返回帧总长度(sync + len + payload),失败返回 null。 */
    private fun tryParseFrame(bytes: ByteArray, offset: Int): Int? {
        if (bytes.size - offset < TeleautoCrsf.MIN_FRAME_SIZE) return null

        // 找同步字节
        if (bytes[offset] != TeleautoCrsf.SYNC_BYTE) return null
        val len = bytes[offset + 1].toInt() and 0xFF
        if (len < 2 || len > TeleautoCrsf.MAX_FRAME_SIZE - 2) {
            // 无效长度,跳过这个 sync
            return 1
        }
        val frameSize = 2 + len // sync + len + (type + payload + crc)
        if (bytes.size - offset < frameSize) return null // 不完整,等待更多数据

        val type = bytes[offset + 2]
        val payload = bytes.copyOfRange(offset + 3, offset + 2 + len)
        val crc = bytes[offset + 2 + len]

        // CRC 校验(对 type + payload)
        val computed = crc8(bytes, offset + 2, len - 1)
        if (computed != (crc.toInt() and 0xFF)) {
            crcErrorCount++
            return 1 // 跳过这个 sync,继续找下一个
        }

        frameCount++
        onFrame(TeleautoCrsfFrame(type, payload))
        return frameSize
    }

    companion object {
        /** CRC-8 DVB-S2 (多项式 0xD5, 初始 0x00, 无反转)。 */
        fun crc8(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
            var crc = 0
            for (i in offset until offset + length) {
                crc = crc xor (data[i].toInt() and 0xFF)
                repeat(8) {
                    crc = if (crc and 0x80 != 0) {
                        (crc shl 1) xor TeleautoCrsf.CRC_POLY
                    } else {
                        crc shl 1
                    }
                    crc = crc and 0xFF
                }
            }
            return crc
        }
    }
}

/** 解析后的 CRSF 帧(已通过 CRC 校验)。 */
data class TeleautoCrsfFrame(
    val type: Byte,
    val payload: ByteArray,
) {
    /** 解析为 16 通道数据。 */
    fun asChannels(): TeleautoCrsfChannels? {
        if (type.toInt() and 0xFF != TeleautoCrsf.Type.RC_CHANNELS_PACKED) return null
        if (payload.size < TeleautoCrsf.RC_PAYLOAD_SIZE) return null
        val raw = IntArray(TeleautoCrsf.RC_CHANNEL_COUNT)
        var bitIndex = 0
        for (ch in 0 until TeleautoCrsf.RC_CHANNEL_COUNT) {
            var value = 0
            for (bit in 0 until 11) {
                val byteIndex = bitIndex / 8
                val bitPos = bitIndex % 8
                val bit = (payload[byteIndex].toInt() shr bitPos) and 1
                value = value or (bit shl bit)
                bitIndex++
            }
            raw[ch] = value
        }
        return TeleautoCrsfChannels(raw)
    }

    /** 解析为链路统计。 */
    fun asLinkStats(): TeleautoCrsfLinkStats? {
        if (type.toInt() and 0xFF != TeleautoCrsf.Type.LINK_STATS) return null
        if (payload.size < 10) return null
        return TeleautoCrsfLinkStats(
            uplinkRssi1 = -(payload[0].toInt() and 0xFF),
            uplinkRssi2 = -(payload[1].toInt() and 0xFF),
            uplinkLinkQuality = payload[2].toInt() and 0xFF,
            uplinkSnr = payload[3].toInt(),
            activeAntenna = payload[4].toInt() and 0xFF,
            rfMode = payload[5].toInt() and 0xFF,
            uplinkTxPower = payload[6].toInt() and 0xFF,
            downlinkRssi = -(payload[7].toInt() and 0xFF),
            downlinkLinkQuality = payload[8].toInt() and 0xFF,
            downlinkSnr = payload[9].toInt(),
        )
    }

    /** 解析为电池遥测。 */
    fun asBattery(): TeleautoCrsfBattery? {
        if (type.toInt() and 0xFF != TeleautoCrsf.Type.BATTERY) return null
        if (payload.size < 8) return null
        val voltage = ((payload[0].toInt() and 0xFF) or ((payload[1].toInt() and 0xFF) shl 8)) * 0.1f
        val current = ((payload[2].toInt() and 0xFF) or ((payload[3].toInt() and 0xFF) shl 8)) * 0.1f
        val mAh = (payload[4].toInt() and 0xFF) or
            ((payload[5].toInt() and 0xFF) shl 8) or
            ((payload[6].toInt() and 0xFF) shl 16)
        return TeleautoCrsfBattery(voltage, current, mAh)
    }

    /** 解析为 GPS 遥测。 */
    fun asGps(): TeleautoCrsfGps? {
        if (type.toInt() and 0xFF != TeleautoCrsf.Type.GPS) return null
        if (payload.size < 15) return null
        val lat = readInt32(payload, 0) * 1e-7
        val lon = readInt32(payload, 4) * 1e-7
        val altRaw = ((payload[8].toInt() and 0xFF) or ((payload[9].toInt() and 0xFF) shl 8))
        val altitude = (if (altRaw > 32767) altRaw - 65536 else altRaw) * 0.01f - 1000f
        val speed = ((payload[10].toInt() and 0xFF) or ((payload[11].toInt() and 0xFF) shl 8)) * 0.1f
        val heading = ((payload[12].toInt() and 0xFF) or ((payload[13].toInt() and 0xFF) shl 8)) * 0.01f
        val sats = payload[14].toInt() and 0xFF
        return TeleautoCrsfGps(lat, lon, altitude, speed, heading, sats)
    }

    /** 解析为飞行模式。 */
    fun asFlightMode(): TeleautoCrsfFlightMode? {
        if (type.toInt() and 0xFF != TeleautoCrsf.Type.FLIGHT_MODE) return null
        // null-terminated string
        val end = payload.indexOf(0.toByte())
        val text = if (end >= 0) String(payload, 0, end, Charsets.US_ASCII)
                   else String(payload, Charsets.US_ASCII)
        return TeleautoCrsfFlightMode(text)
    }

    private fun readInt32(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
        ((data[offset + 1].toInt() and 0xFF) shl 8) or
        ((data[offset + 2].toInt() and 0xFF) shl 16) or
        ((data[offset + 3].toInt() and 0xFF) shl 24)
}
