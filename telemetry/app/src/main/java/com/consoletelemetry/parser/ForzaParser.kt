package com.consoletelemetry.parser

import com.consoletelemetry.model.Game
import com.consoletelemetry.model.TelemetryFrame
import com.consoletelemetry.model.TelemetryParser

/**
 * Forza "Data Out". Packet sizes identify the variant:
 *  - 232 bytes: Sled (basic engine data only)
 *  - 311 bytes: Dash, Forza Motorsport 7
 *  - 324 bytes: Dash, Forza Horizon 4/5 (12 extra bytes between Sled and Dash blocks)
 *  - 331 bytes: Dash, Forza Motorsport (2023) (tyre wear + track id appended)
 */
class ForzaParser : TelemetryParser {

    override fun parse(data: ByteArray, length: Int): TelemetryFrame? {
        if (length < SLED_SIZE) return null
        val b = data.le(length)

        val isRaceOn = b.getInt(0) != 0
        val maxRpm = b.f32(8)
        val rpm = b.f32(16)
        val sledOnly = TelemetryFrame(game = Game.FORZA, isLive = isRaceOn, rpm = rpm, maxRpm = maxRpm)

        val dash = when (length) {
            HORIZON_SIZE -> SLED_SIZE + 12
            in MOTORSPORT7_SIZE..Int.MAX_VALUE -> SLED_SIZE
            else -> return sledOnly
        }

        val bestLap = b.f32(dash + 52)
        val lastLap = b.f32(dash + 56)
        val gearRaw = b.u8(dash + 75)

        return sledOnly.copy(
            speedKph = b.f32(dash + 12) * 3.6f,
            tyreTempsC = List(4) { fahrenheitToC(b.f32(dash + 24 + it * 4)) },
            fuel = b.f32(dash + 44),
            bestLapMs = secondsToMs(bestLap),
            lastLapMs = secondsToMs(lastLap),
            currentLapMs = secondsToMs(b.f32(dash + 60)),
            lap = b.u16(dash + 68) + 1,
            position = b.u8(dash + 70).takeIf { it > 0 },
            throttle = b.u8(dash + 71) / 255f,
            brake = b.u8(dash + 72) / 255f,
            gear = if (gearRaw == 0) -1 else gearRaw,
            steer = b.s8(dash + 76) / 127f,
            tyreWearPct = if (length >= MOTORSPORT_2023_SIZE) {
                List(4) { b.f32(MOTORSPORT7_SIZE + it * 4) * 100f }
            } else null,
        )
    }

    private fun fahrenheitToC(f: Float) = (f - 32f) * 5f / 9f

    private fun secondsToMs(s: Float): Long? = if (s > 0f) (s * 1000).toLong() else null

    companion object {
        const val SLED_SIZE = 232
        const val MOTORSPORT7_SIZE = 311
        const val HORIZON_SIZE = 324
        const val MOTORSPORT_2023_SIZE = 331
    }
}
