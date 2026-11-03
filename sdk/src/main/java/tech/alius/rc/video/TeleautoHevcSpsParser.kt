package tech.alius.rc.video

/** Minimal HEVC SPS parser for the fields required to configure MediaCodec. */
internal object TeleautoHevcSpsParser {
    data class Info(
        val width: Int,
        val height: Int,
        val profileIdc: Int,
        val levelIdc: Int,
        val bitDepth: Int,
    )

    fun parse(annexB: ByteArray): Info? = runCatching {
        val nalOffset = startCodeLength(annexB)
        require(annexB.size >= nalOffset + 3)
        val rbsp = removeEmulationPrevention(annexB, nalOffset + 2)
        val bits = BitReader(rbsp)
        bits.skip(4) // sps_video_parameter_set_id
        val maxSubLayersMinus1 = bits.read(3)
        bits.skip(1) // sps_temporal_id_nesting_flag

        bits.skip(2) // general_profile_space
        bits.skip(1) // general_tier_flag
        val profileIdc = bits.read(5)
        bits.skip(32 + 4 + 44) // compatibility, constraints, reserved
        val levelIdc = bits.read(8)
        val subLayerProfilePresent = BooleanArray(maxSubLayersMinus1) { bits.read(1) != 0 }
        val subLayerLevelPresent = BooleanArray(maxSubLayersMinus1) { bits.read(1) != 0 }
        if (maxSubLayersMinus1 > 0) repeat(8 - maxSubLayersMinus1) { bits.skip(2) }
        repeat(maxSubLayersMinus1) { layer ->
            if (subLayerProfilePresent[layer]) bits.skip(88)
            if (subLayerLevelPresent[layer]) bits.skip(8)
        }

        bits.readUe() // sps_seq_parameter_set_id
        val chromaFormatIdc = bits.readUe()
        if (chromaFormatIdc == 3) bits.skip(1) // separate_colour_plane_flag
        var width = bits.readUe()
        var height = bits.readUe()
        if (bits.read(1) != 0) {
            val left = bits.readUe()
            val right = bits.readUe()
            val top = bits.readUe()
            val bottom = bits.readUe()
            val subWidth = if (chromaFormatIdc == 1 || chromaFormatIdc == 2) 2 else 1
            val subHeight = if (chromaFormatIdc == 1) 2 else 1
            width -= (left + right) * subWidth
            height -= (top + bottom) * subHeight
        }
        val bitDepth = 8 + bits.readUe()
        bits.readUe() // bit_depth_chroma_minus8
        require(width in 16..8192 && height in 16..8192 && bitDepth in 8..16)
        Info(width, height, profileIdc, levelIdc, bitDepth)
    }.getOrNull()

    private fun startCodeLength(bytes: ByteArray): Int = when {
        bytes.size >= 4 && bytes[0] == 0.toByte() && bytes[1] == 0.toByte() &&
            bytes[2] == 0.toByte() && bytes[3] == 1.toByte() -> 4
        bytes.size >= 3 && bytes[0] == 0.toByte() && bytes[1] == 0.toByte() &&
            bytes[2] == 1.toByte() -> 3
        else -> 0
    }

    private fun removeEmulationPrevention(bytes: ByteArray, from: Int): ByteArray {
        val result = ByteArray(bytes.size - from)
        var output = 0
        var zeros = 0
        for (index in from until bytes.size) {
            val value = bytes[index]
            if (zeros >= 2 && value == 3.toByte()) {
                zeros = 0
                continue
            }
            result[output++] = value
            zeros = if (value == 0.toByte()) zeros + 1 else 0
        }
        return result.copyOf(output)
    }

    private class BitReader(private val bytes: ByteArray) {
        private var bit = 0

        fun read(count: Int): Int {
            require(count in 0..32 && bit + count <= bytes.size * 8)
            var value = 0
            repeat(count) {
                value = (value shl 1) or ((bytes[bit ushr 3].toInt() ushr (7 - (bit and 7))) and 1)
                bit++
            }
            return value
        }

        fun skip(count: Int) { require(bit + count <= bytes.size * 8); bit += count }

        fun readUe(): Int {
            var leadingZeros = 0
            while (read(1) == 0) {
                leadingZeros++
                require(leadingZeros < 31)
            }
            return if (leadingZeros == 0) 0 else (1 shl leadingZeros) - 1 + read(leadingZeros)
        }
    }
}
