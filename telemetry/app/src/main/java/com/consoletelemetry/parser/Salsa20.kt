package com.consoletelemetry.parser

/** Minimal Salsa20/20 stream cipher (256-bit key, 64-bit nonce), used by GT7. */
object Salsa20 {

    fun xor(input: ByteArray, length: Int, key: ByteArray, nonce: ByteArray): ByteArray {
        require(key.size == 32 && nonce.size == 8)
        val state = IntArray(16)
        state[0] = 0x61707865
        state[5] = 0x3320646e
        state[10] = 0x79622d32
        state[15] = 0x6b206574
        for (i in 0 until 4) {
            state[1 + i] = readLe(key, i * 4)
            state[11 + i] = readLe(key, 16 + i * 4)
        }
        state[6] = readLe(nonce, 0)
        state[7] = readLe(nonce, 4)

        val out = ByteArray(length)
        val block = ByteArray(64)
        var counter = 0L
        var pos = 0
        while (pos < length) {
            state[8] = counter.toInt()
            state[9] = (counter ushr 32).toInt()
            core(state, block)
            val n = minOf(64, length - pos)
            for (i in 0 until n) out[pos + i] = (input[pos + i].toInt() xor block[i].toInt()).toByte()
            pos += n
            counter++
        }
        return out
    }

    private fun core(input: IntArray, out: ByteArray) {
        val x = input.copyOf()
        repeat(10) {
            // Column round
            qr(x, 0, 4, 8, 12); qr(x, 5, 9, 13, 1); qr(x, 10, 14, 2, 6); qr(x, 15, 3, 7, 11)
            // Row round
            qr(x, 0, 1, 2, 3); qr(x, 5, 6, 7, 4); qr(x, 10, 11, 8, 9); qr(x, 15, 12, 13, 14)
        }
        for (i in 0 until 16) {
            val v = x[i] + input[i]
            out[i * 4] = v.toByte()
            out[i * 4 + 1] = (v ushr 8).toByte()
            out[i * 4 + 2] = (v ushr 16).toByte()
            out[i * 4 + 3] = (v ushr 24).toByte()
        }
    }

    private fun qr(x: IntArray, a: Int, b: Int, c: Int, d: Int) {
        x[b] = x[b] xor Integer.rotateLeft(x[a] + x[d], 7)
        x[c] = x[c] xor Integer.rotateLeft(x[b] + x[a], 9)
        x[d] = x[d] xor Integer.rotateLeft(x[c] + x[b], 13)
        x[a] = x[a] xor Integer.rotateLeft(x[d] + x[c], 18)
    }

    private fun readLe(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or
            ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or
            ((b[o + 3].toInt() and 0xFF) shl 24)
}
