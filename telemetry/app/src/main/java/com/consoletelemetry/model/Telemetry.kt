package com.consoletelemetry.model

/** Console titles that expose a UDP telemetry feed. */
enum class Game(
    val displayName: String,
    val listenPort: Int,
    val setupHint: String,
) {
    FORZA(
        displayName = "Forza",
        listenPort = 5300,
        setupHint = "Forza Motorsport / Horizon 4 & 5: Settings › HUD and Gameplay › " +
            "Data Out = On, Data Out IP = this phone's IP, Data Out Port = 5300, " +
            "Packet Format = Dash (FM).",
    ),
    F1(
        displayName = "EA F1",
        listenPort = 20777,
        setupHint = "F1 23 / 24 / 25: Settings › Telemetry Settings › UDP Telemetry = On, " +
            "UDP Broadcast Mode = Off, UDP IP Address = this phone's IP, Port = 20777, " +
            "UDP Format = 2023 or newer, Send Rate = 30–60 Hz.",
    ),
    GT7(
        displayName = "GT7",
        listenPort = 33740,
        setupHint = "Gran Turismo 7: no in-game setting needed. Enter your PlayStation's " +
            "IP address below; the app pings it so the console starts streaming.",
    ),
}

/** One normalized snapshot of car state, independent of the source game. */
data class TelemetryFrame(
    val game: Game,
    val isLive: Boolean = true,
    val speedKph: Float = 0f,
    val rpm: Float = 0f,
    val maxRpm: Float = 0f,
    /** -1 = reverse, 0 = neutral, 1..n = forward gears. */
    val gear: Int = 0,
    /** 0..1 */
    val throttle: Float = 0f,
    /** 0..1 */
    val brake: Float = 0f,
    /** -1 (full left) .. 1 (full right) */
    val steer: Float = 0f,
    /** Celsius, ordered FL, FR, RL, RR. */
    val tyreTempsC: List<Float>? = null,
    val lap: Int? = null,
    val totalLaps: Int? = null,
    val position: Int? = null,
    val currentLapMs: Long? = null,
    val lastLapMs: Long? = null,
    val bestLapMs: Long? = null,
    /** 0..1 fraction of the tank. */
    val fuel: Float? = null,
    val fuelLapsRemaining: Float? = null,
    /** Percent worn (0..100), ordered FL, FR, RL, RR. */
    val tyreWearPct: List<Float>? = null,
    val tyreCompound: String? = null,
    val tyreAgeLaps: Int? = null,
    val sectors: SectorTimes? = null,
    val ers: ErsState? = null,
    val drs: DrsState? = null,
    val damage: DamageState? = null,
)

/** Each list is S1, S2, S3; null where a sector isn't known yet. */
data class SectorTimes(
    val current: List<Long?>,
    val last: List<Long?>,
    val best: List<Long?>,
)

data class ErsState(
    /** 0..1 of the 4 MJ store. */
    val storeFraction: Float,
    val deployMode: String,
    val harvestedThisLapJ: Float,
    val deployedThisLapJ: Float,
)

data class DrsState(
    val allowed: Boolean,
    val active: Boolean,
    /** Metres until DRS becomes available, 0 when not counting down. */
    val activationDistanceM: Int,
)

/** Damage values are percentages (0..100). Per-wheel lists are ordered FL, FR, RL, RR. */
data class DamageState(
    val frontLeftWing: Int,
    val frontRightWing: Int,
    val rearWing: Int,
    val floor: Int,
    val diffuser: Int,
    val sidepod: Int,
    val gearbox: Int,
    val engine: Int,
    val tyres: List<Int>,
    val brakes: List<Int>,
    val drsFault: Boolean,
    val ersFault: Boolean,
)

interface TelemetryParser {
    /** Returns an updated frame, or null if the datagram isn't one this parser understands. */
    fun parse(data: ByteArray, length: Int): TelemetryFrame?
}
