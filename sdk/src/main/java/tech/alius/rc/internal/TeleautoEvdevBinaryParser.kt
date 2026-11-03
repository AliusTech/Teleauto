package tech.alius.rc.internal

/**
 * Incremental parser for Linux input_event streams. Both 32-bit (16 byte) and
 * 64-bit (24 byte) layouts are accepted. The parser owns one fixed buffer and
 * never allocates per event.
 */
internal class TeleautoEvdevBinaryParser(
    eventSizeHint: Int = 0,
    private val onEvent: (type: Int, code: Int, value: Int) -> Unit,
) {
    private val buffer = ByteArray(BUFFER_SIZE)
    private var start = 0
    private var end = 0
    private var eventSize = eventSizeHint.takeIf { it == EVENT32_SIZE || it == EVENT64_SIZE } ?: 0
    var invalidBytes: Int = 0
        private set
    var parsedEvents: Long = 0
        private set
    var lastEventTimestampMs: Long = 0L
        private set

    fun feed(data: ByteArray, length: Int = data.size) {
        var sourceOffset = 0
        var remaining = length.coerceAtMost(data.size)
        while (remaining > 0) {
            compactIfNeeded()
            val copied = minOf(remaining, buffer.size - end)
            if (copied == 0) {
                discardOne()
                continue
            }
            data.copyInto(buffer, end, sourceOffset, sourceOffset + copied)
            end += copied
            sourceOffset += copied
            remaining -= copied
            parseAvailable()
        }
    }

    private fun parseAvailable() {
        while (true) {
            if (eventSize == 0) {
                eventSize = detectEventSize()
                if (eventSize == 0) return
            }
            if (end - start < eventSize) return
            val typeOffset = start + eventSize - 8
            val type = u16(typeOffset)
            val code = u16(typeOffset + 2)
            if (!plausible(type, code)) {
                eventSize = 0
                discardOne()
                continue
            }
            val seconds = if (eventSize == EVENT64_SIZE) i64(start) else i32(start).toLong()
            val micros = if (eventSize == EVENT64_SIZE) i64(start + 8) else i32(start + 4).toLong()
            lastEventTimestampMs = seconds * 1_000L + micros / 1_000L
            onEvent(type, code, i32(typeOffset + 4))
            parsedEvents++
            start += eventSize
            if (start == end) {
                start = 0
                end = 0
            }
        }
    }

    private fun detectEventSize(): Int {
        val available = end - start
        if (available >= EVENT64_SIZE) {
            val valid64 = plausible(u16(start + 16), u16(start + 18))
            val valid32 = plausible(u16(start + 8), u16(start + 10))
            if (valid64 && !valid32) return EVENT64_SIZE
            if (valid32 && !valid64) return EVENT32_SIZE
            if (valid64) return EVENT64_SIZE
        }
        if (available >= EVENT32_SIZE && plausible(u16(start + 8), u16(start + 10))) {
            // Wait for 24 bytes when still ambiguous; a 64-bit timestamp can
            // occasionally resemble an EV_SYN header at offset 8.
            if (available < EVENT64_SIZE) return 0
            return EVENT32_SIZE
        }
        if (available >= EVENT64_SIZE) discardOne()
        return 0
    }

    private fun plausible(type: Int, code: Int): Boolean = when (type) {
        EV_SYN -> code <= 0x0f
        EV_KEY -> code <= 0x3ff
        EV_ABS -> code <= 0x3f
        EV_MSC -> code <= 0x0f
        else -> false
    }

    private fun discardOne() {
        if (start < end) {
            start++
            invalidBytes++
        }
        if (start == end) { start = 0; end = 0 }
    }

    private fun compactIfNeeded() {
        if (end < buffer.size || start == 0) return
        buffer.copyInto(buffer, 0, start, end)
        end -= start
        start = 0
    }

    private fun u16(offset: Int): Int =
        (buffer[offset].toInt() and 0xff) or ((buffer[offset + 1].toInt() and 0xff) shl 8)

    private fun i32(offset: Int): Int =
        (buffer[offset].toInt() and 0xff) or
            ((buffer[offset + 1].toInt() and 0xff) shl 8) or
            ((buffer[offset + 2].toInt() and 0xff) shl 16) or
            (buffer[offset + 3].toInt() shl 24)

    private fun i64(offset: Int): Long {
        var value = 0L
        for (i in 0..7) value = value or ((buffer[offset + i].toLong() and 0xffL) shl (i * 8))
        return value
    }

    companion object {
        const val EV_SYN = 0
        const val EV_KEY = 1
        const val EV_ABS = 3
        private const val EV_MSC = 4
        const val SYN_REPORT = 0
        const val EVENT32_SIZE = 16
        const val EVENT64_SIZE = 24
        private const val BUFFER_SIZE = 4096
    }
}
