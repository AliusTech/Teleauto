package tech.alius.rc.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TeleautoHevcSpsParserTest {
    @Test fun parsesDimensionsProfileAndBitDepth() {
        val bits = BitWriter().apply {
            write(0, 4) // vps id
            write(0, 3) // max sub layers minus one
            write(1, 1) // temporal nesting
            write(0, 2); write(0, 1); write(1, 5) // Main profile
            write(0, 32); write(0, 4); writeLong(0, 44)
            write(120, 8) // level 4.0
            writeUe(0) // sps id
            writeUe(1) // 4:2:0
            writeUe(1920)
            writeUe(1088)
            write(1, 1) // conformance window
            writeUe(0); writeUe(0); writeUe(0); writeUe(4)
            writeUe(0); writeUe(0) // 8-bit luma/chroma
        }
        val nal = byteArrayOf(0, 0, 0, 1, 0x42, 0x01) + bits.toByteArray()

        val info = TeleautoHevcSpsParser.parse(nal)

        assertNotNull(info)
        assertEquals(1920, info!!.width)
        assertEquals(1080, info.height)
        assertEquals(1, info.profileIdc)
        assertEquals(120, info.levelIdc)
        assertEquals(8, info.bitDepth)
    }

    @Test fun rejectsTruncatedSps() {
        assertEquals(null, TeleautoHevcSpsParser.parse(byteArrayOf(0, 0, 0, 1, 0x42, 1, 0)))
    }

    private class BitWriter {
        private val bits = ArrayList<Int>()
        fun write(value: Int, count: Int) = writeLong(value.toLong(), count)
        fun writeLong(value: Long, count: Int) {
            for (shift in count - 1 downTo 0) bits += ((value ushr shift) and 1).toInt()
        }
        fun writeUe(value: Int) {
            val code = value + 1
            val width = 32 - Integer.numberOfLeadingZeros(code)
            repeat(width - 1) { bits += 0 }
            write(code, width)
        }
        fun toByteArray(): ByteArray = ByteArray((bits.size + 7) / 8).also { output ->
            bits.forEachIndexed { index, value ->
                output[index / 8] = (output[index / 8].toInt() or (value shl (7 - index % 8))).toByte()
            }
        }
    }
}
