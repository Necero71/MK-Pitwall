package com.consoletelemetry.parser

import com.consoletelemetry.model.Game
import com.consoletelemetry.model.TelemetryFrame
import com.consoletelemetry.model.TelemetryParser

/**
 * Gran Turismo 7 "Simulator Interface" packets (296-byte type A).
 * Packets are Salsa20-encrypted; the nonce is derived from bytes 0x40..0x43.
 * The console only streams after receiving a heartbeat on port 33739 (see TelemetryReceiver).
 */
class Gt7Parser : TelemetryParser {

    override fun parse(data: ByteArray, length: Int): TelemetryFrame? {
        val plain = decrypt(data, length) ?: return null
        val b = plain.le()

        val fuelLevel = b.f32(0x44)
        val fuelCapacity = b.f32(0x48)
        val gearNibble = b.u8(0x90) and 0x0F
        val flags = b.u16(0x8E)

        return TelemetryFrame(
            game = Game.GT7,
            isLive = (flags and (FLAG_PAUSED or FLAG_LOADING)) == 0,
            rpm = b.f32(0x3C),
            fuel = if (fuelCapacity > 0f) fuelLevel / fuelCapacity else null,
            speedKph = b.f32(0x4C) * 3.6f,
            tyreTempsC = List(4) { b.f32(0x60 + it * 4) },
            lap = b.s16(0x74).takeIf { it > 0 },
            totalLaps = b.s16(0x76).takeIf { it > 0 },
            bestLapMs = b.getInt(0x78).toLong().takeIf { it > 0 },
            lastLapMs = b.getInt(0x7C).toLong().takeIf { it > 0 },
            position = b.s16(0x84).takeIf { it > 0 },
            maxRpm = b.u16(0x8A).toFloat(),
            gear = when (gearNibble) {
                0 -> -1
                15 -> 0
                else -> gearNibble
            },
            throttle = b.u8(0x91) / 255f,
            brake = b.u8(0x92) / 255f,
        )
    }

    companion object {
        const val PACKET_SIZE = 0x128
        const val MAGIC = 0x47375330 // "0S7G" little-endian
        const val FLAG_PAUSED = 1 shl 1
        const val FLAG_LOADING = 1 shl 2
        private val KEY = "Simulator Interface Packet GT7 ver 0.0".toByteArray(Charsets.US_ASCII).copyOf(32)

        fun nonceFor(packet: ByteArray): ByteArray {
            val iv1 = packet.le().getInt(0x40)
            val iv2 = iv1 xor 0xDEADBEAF.toInt()
            return ByteArray(8).also { out ->
                out.le().putInt(0, iv2).putInt(4, iv1)
            }
        }

        /** Decrypts and validates a packet; null if it's not a GT7 packet. */
        fun decrypt(data: ByteArray, length: Int): ByteArray? {
            if (length < PACKET_SIZE) return null
            val plain = Salsa20.xor(data, PACKET_SIZE, KEY, nonceFor(data))
            return if (plain.le().getInt(0) == MAGIC) plain else null
        }

        /** Inverse of [decrypt]; used by tests to build realistic packets. */
        fun encrypt(plain: ByteArray): ByteArray {
            // Bytes 0x40..0x43 travel in the clear, so the nonce is computable from plaintext too.
            return Salsa20.xor(plain, PACKET_SIZE, KEY, nonceFor(plain)).also { enc ->
                System.arraycopy(plain, 0x40, enc, 0x40, 4)
            }
        }
    }
}
