package com.consoletelemetry.parser

import com.consoletelemetry.model.DamageState
import com.consoletelemetry.model.DrsState
import com.consoletelemetry.model.ErsState
import com.consoletelemetry.model.Game
import com.consoletelemetry.model.SectorTimes
import com.consoletelemetry.model.TelemetryFrame
import com.consoletelemetry.model.TelemetryParser
import java.nio.ByteBuffer

/**
 * EA Sports F1 23 / 24 / 25 UDP spec (packet format 2023+).
 *
 * The game splits data across packet types, so this parser keeps the latest merged frame
 * and folds in each packet as it arrives:
 *  - Lap Data (2): lap, position, current/last lap time, current-lap sectors
 *  - Car Telemetry (6): speed, inputs, gear, RPM, tyre temps, DRS active
 *  - Car Status (7): max RPM, fuel, tyre compound/age, ERS, DRS allowed
 *  - Car Damage (10): tyre wear, tyre/brake/body damage, faults
 *  - Session History (11): best lap and last/best sector times
 * Per-car strides are derived from the packet length so minor per-year struct growth
 * doesn't break parsing.
 */
class F1Parser : TelemetryParser {

    private var frame = TelemetryFrame(game = Game.F1)
    private var currentSectors: List<Long?> = NO_SECTORS
    private var lastSectors: List<Long?> = NO_SECTORS
    private var bestSectors: List<Long?> = NO_SECTORS

    override fun parse(data: ByteArray, length: Int): TelemetryFrame? {
        if (length < HEADER_SIZE) return null
        val b = data.le(length)

        val packetFormat = b.u16(0)
        if (packetFormat < 2023) return null
        val packetId = b.u8(6)
        val player = b.u8(27)
        if (player >= MAX_CARS) return null

        frame = when (packetId) {
            PACKET_LAP_DATA -> parseLapData(b, length, player, packetFormat)
            PACKET_CAR_TELEMETRY -> parseCarTelemetry(b, length, player)
            PACKET_CAR_STATUS -> parseCarStatus(b, length, player)
            PACKET_CAR_DAMAGE -> parseCarDamage(b, length, player)
            PACKET_SESSION_HISTORY -> parseSessionHistory(b, length, player)
            else -> return null
        } ?: return null
        return frame
    }

    private fun parseCarTelemetry(b: ByteBuffer, length: Int, player: Int): TelemetryFrame? {
        val base = HEADER_SIZE + player * CAR_TELEMETRY_STRIDE
        if (base + CAR_TELEMETRY_STRIDE > length) return null
        val drsActive = b.u8(base + 18) == 1
        return frame.copy(
            speedKph = b.u16(base).toFloat(),
            throttle = b.f32(base + 2),
            steer = b.f32(base + 6),
            brake = b.f32(base + 10),
            gear = b.s8(base + 15),
            rpm = b.u16(base + 16).toFloat(),
            tyreTempsC = wheels { b.u8(base + 30 + it).toFloat() },
            drs = (frame.drs ?: DrsState(allowed = false, active = false, activationDistanceM = 0))
                .copy(active = drsActive),
        )
    }

    private fun parseLapData(b: ByteBuffer, length: Int, player: Int, format: Int): TelemetryFrame? {
        // Lap data is followed by 2 trailing bytes (time-trial PB / rival car indexes).
        val stride = (length - HEADER_SIZE - 2) / MAX_CARS
        if (stride < 40) return null
        val base = HEADER_SIZE + player * stride
        // F1 24 added minute bytes to the delta fields, shifting carPosition by 2.
        val positionOffset = if (format >= 2024) 32 else 30
        currentSectors = listOf(
            sectorMs(b, base + 8),
            sectorMs(b, base + 11),
            null, // S3 of the lap in progress only exists once the lap is done (see history).
        )
        return frame.copy(
            lastLapMs = b.u32(base).takeIf { it > 0 },
            currentLapMs = b.u32(base + 4),
            position = b.u8(base + positionOffset).takeIf { it > 0 },
            lap = b.u8(base + positionOffset + 1).takeIf { it > 0 },
            sectors = sectorTimes(),
        )
    }

    private fun parseCarStatus(b: ByteBuffer, length: Int, player: Int): TelemetryFrame? {
        val stride = (length - HEADER_SIZE) / MAX_CARS
        if (stride < CAR_STATUS_MIN_STRIDE) return null
        val base = HEADER_SIZE + player * stride
        val fuelInTank = b.f32(base + 5)
        val fuelCapacity = b.f32(base + 9)
        return frame.copy(
            maxRpm = b.u16(base + 17).toFloat(),
            fuel = if (fuelCapacity > 0f) fuelInTank / fuelCapacity else null,
            fuelLapsRemaining = b.f32(base + 13),
            tyreCompound = compoundName(b.u8(base + 26)),
            tyreAgeLaps = b.u8(base + 27),
            drs = DrsState(
                allowed = b.u8(base + 22) == 1,
                active = frame.drs?.active ?: false,
                activationDistanceM = b.u16(base + 23),
            ),
            ers = ErsState(
                storeFraction = (b.f32(base + 37) / ERS_MAX_J).coerceIn(0f, 1f),
                deployMode = ERS_MODES.getOrElse(b.u8(base + 41)) { "?" },
                harvestedThisLapJ = b.f32(base + 42) + b.f32(base + 46),
                deployedThisLapJ = b.f32(base + 50),
            ),
        )
    }

    private fun parseCarDamage(b: ByteBuffer, length: Int, player: Int): TelemetryFrame? {
        val stride = (length - HEADER_SIZE) / MAX_CARS
        if (stride < CAR_DAMAGE_MIN_STRIDE) return null
        val base = HEADER_SIZE + player * stride
        // F1 25 inserted tyre blisters[4] after brake damage; anything extra shifts the body fields.
        val body = base + 24 + (stride - CAR_DAMAGE_MIN_STRIDE)
        return frame.copy(
            tyreWearPct = wheels { b.f32(base + it * 4) },
            damage = DamageState(
                frontLeftWing = b.u8(body),
                frontRightWing = b.u8(body + 1),
                rearWing = b.u8(body + 2),
                floor = b.u8(body + 3),
                diffuser = b.u8(body + 4),
                sidepod = b.u8(body + 5),
                drsFault = b.u8(body + 6) == 1,
                ersFault = b.u8(body + 7) == 1,
                gearbox = b.u8(body + 8),
                engine = b.u8(body + 9),
                tyres = wheels { b.u8(base + 16 + it) },
                brakes = wheels { b.u8(base + 20 + it) },
            ),
        )
    }

    private fun parseSessionHistory(b: ByteBuffer, length: Int, player: Int): TelemetryFrame? {
        // History is sent for every car in rotation; only the player's matters here.
        if (length < HISTORY_LAPS_OFFSET || b.u8(HEADER_SIZE) != player) return null
        val numLaps = b.u8(HEADER_SIZE + 1).coerceAtMost(MAX_HISTORY_LAPS)
        val bestLapNum = b.u8(HEADER_SIZE + 3)

        fun lapBase(lapNum: Int): Int? {
            if (lapNum !in 1..numLaps) return null
            val o = HISTORY_LAPS_OFFSET + (lapNum - 1) * HISTORY_LAP_SIZE
            return o.takeIf { it + HISTORY_LAP_SIZE <= length }
        }
        fun sector(lapNum: Int, s: Int): Long? = lapBase(lapNum)?.let { sectorMs(b, it + 4 + s * 3) }

        // The lap in progress is included with a zero lap time; the last completed one precedes it.
        val lastCompleted = (numLaps downTo 1).firstOrNull { n -> lapBase(n)?.let { b.u32(it) > 0 } == true }
        lastSectors = List(3) { s -> lastCompleted?.let { sector(it, s) } }
        bestSectors = List(3) { s -> sector(b.u8(HEADER_SIZE + 4 + s), s) }

        return frame.copy(
            bestLapMs = lapBase(bestLapNum)?.let { b.u32(it) }?.takeIf { it > 0 } ?: frame.bestLapMs,
            sectors = sectorTimes(),
        )
    }

    private fun sectorTimes() = SectorTimes(current = currentSectors, last = lastSectors, best = bestSectors)

    /** Sector times are split into a uint16 milliseconds part and a uint8 minutes part. */
    private fun sectorMs(b: ByteBuffer, offset: Int): Long? {
        val ms = b.u16(offset) + b.u8(offset + 2) * 60_000L
        return ms.takeIf { it > 0 }
    }

    /** F1 per-wheel arrays are RL, RR, FL, FR; this returns FL, FR, RL, RR. */
    private fun <T> wheels(read: (Int) -> T): List<T> = WHEEL_REMAP.map(read)

    private fun compoundName(visual: Int): String? = when (visual) {
        16 -> "Soft"
        17 -> "Medium"
        18 -> "Hard"
        7 -> "Inter"
        8 -> "Wet"
        else -> null
    }

    companion object {
        const val HEADER_SIZE = 29
        const val MAX_CARS = 22
        const val CAR_TELEMETRY_STRIDE = 60
        const val CAR_STATUS_MIN_STRIDE = 55
        const val CAR_DAMAGE_MIN_STRIDE = 42
        const val HISTORY_LAPS_OFFSET = HEADER_SIZE + 7
        const val HISTORY_LAP_SIZE = 14
        const val MAX_HISTORY_LAPS = 100
        const val PACKET_LAP_DATA = 2
        const val PACKET_CAR_TELEMETRY = 6
        const val PACKET_CAR_STATUS = 7
        const val PACKET_CAR_DAMAGE = 10
        const val PACKET_SESSION_HISTORY = 11
        const val ERS_MAX_J = 4_000_000f
        private val ERS_MODES = listOf("None", "Medium", "Hotlap", "Overtake")
        private val WHEEL_REMAP = listOf(2, 3, 0, 1)
        private val NO_SECTORS: List<Long?> = listOf(null, null, null)
    }
}
