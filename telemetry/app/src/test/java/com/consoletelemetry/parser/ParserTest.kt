package com.consoletelemetry.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ParserTest {

    private fun buf(size: Int): ByteBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

    @Test
    fun salsa20MatchesEcryptVector() {
        // ECRYPT Salsa20/20 256-bit, Set 1 vector 0: key = 80 00..00, IV = 0.
        val key = ByteArray(32).also { it[0] = 0x80.toByte() }
        val out = Salsa20.xor(ByteArray(64), 64, key, ByteArray(8))
        val hex = out.joinToString("") { "%02X".format(it) }
        assertEquals(
            "E3BE8FDD8BECA2E3EA8EF9475B29A6E7003951E1097A5C38D23B7A5FAD9F6844" +
                "B22C97559E2723C7CBBD3FE4FC8D9A0744652A83E72A9C461876AF4D7EF1A117",
            hex,
        )
    }

    @Test
    fun forzaHorizonDash() {
        val b = buf(ForzaParser.HORIZON_SIZE)
        val dash = ForzaParser.SLED_SIZE + 12
        b.putInt(0, 1)
        b.putFloat(8, 8000f)
        b.putFloat(16, 6500f)
        b.putFloat(dash + 12, 50f) // m/s
        b.putFloat(dash + 24, 212f) // °F
        b.putFloat(dash + 56, 83.456f)
        b.putShort(dash + 68, 2)
        b.put(dash + 70, 3)
        b.put(dash + 71, 255.toByte())
        b.put(dash + 75, 4)

        val f = ForzaParser().parse(b.array(), b.capacity())!!
        assertEquals(180f, f.speedKph, 0.01f)
        assertEquals(6500f, f.rpm)
        assertEquals(100f, f.tyreTempsC!![0], 0.01f)
        assertEquals(83456L, f.lastLapMs)
        assertEquals(3, f.lap)
        assertEquals(3, f.position)
        assertEquals(1f, f.throttle)
        assertEquals(4, f.gear)
    }

    @Test
    fun forzaReverseGearAndMotorsportSize() {
        val b = buf(331)
        b.put(ForzaParser.SLED_SIZE + 75, 0)
        b.putFloat(ForzaParser.MOTORSPORT7_SIZE + 4, 0.25f) // FR wear
        val f = ForzaParser().parse(b.array(), 331)!!
        assertEquals(-1, f.gear)
        assertEquals(25f, f.tyreWearPct!![1], 0.001f)
        assertNull(ForzaParser().parse(buf(324).array(), 324)!!.tyreWearPct)
    }

    @Test
    fun f1MergesTelemetryAndLapData() {
        val parser = F1Parser()
        val player = 5

        val tel = buf(F1Parser.HEADER_SIZE + 22 * 60 + 3)
        tel.putShort(0, 2024)
        tel.put(6, F1Parser.PACKET_CAR_TELEMETRY.toByte())
        tel.put(27, player.toByte())
        val base = F1Parser.HEADER_SIZE + player * 60
        tel.putShort(base, 287)
        tel.putFloat(base + 2, 0.75f)
        tel.put(base + 15, 7)
        tel.putShort(base + 16, 11000)
        tel.put(base + 32, 95) // FL (index 2 in F1 order)
        assertNotNull(parser.parse(tel.array(), tel.capacity()))

        val stride = 57
        val lap = buf(F1Parser.HEADER_SIZE + 22 * stride + 2)
        lap.putShort(0, 2024)
        lap.put(6, F1Parser.PACKET_LAP_DATA.toByte())
        lap.put(27, player.toByte())
        val lb = F1Parser.HEADER_SIZE + player * stride
        lap.putInt(lb, 92_345)
        lap.putInt(lb + 4, 12_000)
        lap.put(lb + 32, 4)
        lap.put(lb + 33, 9)
        val f = parser.parse(lap.array(), lap.capacity())!!

        assertEquals(287f, f.speedKph)
        assertEquals(7, f.gear)
        assertEquals(95f, f.tyreTempsC!![0])
        assertEquals(92_345L, f.lastLapMs)
        assertEquals(4, f.position)
        assertEquals(9, f.lap)
    }

    private fun f1Packet(packetId: Int, bodySize: Int, player: Int, format: Int = 2024): ByteBuffer =
        buf(F1Parser.HEADER_SIZE + bodySize).apply {
            putShort(0, format.toShort())
            put(6, packetId.toByte())
            put(27, player.toByte())
        }

    @Test
    fun f1CarStatusErsDrsAndCompound() {
        val player = 3
        val stride = 55
        val b = f1Packet(F1Parser.PACKET_CAR_STATUS, 22 * stride, player)
        val base = F1Parser.HEADER_SIZE + player * stride
        b.putFloat(base + 5, 50f)
        b.putFloat(base + 9, 110f)
        b.putFloat(base + 13, 1.5f)
        b.putShort(base + 17, 13000)
        b.put(base + 22, 1)
        b.put(base + 26, 17)
        b.put(base + 27, 8)
        b.putFloat(base + 37, 3_000_000f)
        b.put(base + 41, 3)
        b.putFloat(base + 42, 200_000f)
        b.putFloat(base + 46, 100_000f)
        b.putFloat(base + 50, 400_000f)

        val f = F1Parser().parse(b.array(), b.capacity())!!
        assertEquals(13000f, f.maxRpm)
        assertEquals(1.5f, f.fuelLapsRemaining!!, 0.001f)
        assertEquals("Medium", f.tyreCompound)
        assertEquals(8, f.tyreAgeLaps)
        assertEquals(0.75f, f.ers!!.storeFraction, 0.001f)
        assertEquals("Overtake", f.ers!!.deployMode)
        assertEquals(300_000f, f.ers!!.harvestedThisLapJ, 0.1f)
        assertEquals(400_000f, f.ers!!.deployedThisLapJ, 0.1f)
        assertEquals(true, f.drs!!.allowed)
    }

    private fun damagePacket(stride: Int, player: Int): ByteBuffer {
        val b = f1Packet(F1Parser.PACKET_CAR_DAMAGE, 22 * stride, player)
        val base = F1Parser.HEADER_SIZE + player * stride
        b.putFloat(base + 8, 12.5f) // FL wear (index 2 in F1 order)
        b.put(base + 16 + 3, 40) // FR tyre damage
        b.put(base + 20 + 0, 15) // RL brake damage
        val body = base + 24 + (stride - 42)
        b.put(body, 30) // FL wing
        b.put(body + 2, 5) // rear wing
        b.put(body + 6, 1) // DRS fault
        b.put(body + 9, 22) // engine
        return b
    }

    @Test
    fun f1CarDamageF124AndF125Layouts() {
        for (stride in listOf(42, 46)) {
            val b = damagePacket(stride, player = 7)
            val f = F1Parser().parse(b.array(), b.capacity())!!
            assertEquals("stride $stride", 12.5f, f.tyreWearPct!![0], 0.001f)
            val d = f.damage!!
            assertEquals(30, d.frontLeftWing)
            assertEquals(5, d.rearWing)
            assertEquals(22, d.engine)
            assertEquals(true, d.drsFault)
            assertEquals(40, d.tyres[1])
            assertEquals(15, d.brakes[2])
        }
    }

    @Test
    fun f1SessionHistoryBestLapAndSectors() {
        val player = 2
        val b = f1Packet(F1Parser.PACKET_SESSION_HISTORY, 7 + 100 * 14 + 8 * 3, player)
        val h = F1Parser.HEADER_SIZE
        b.put(h, player.toByte())
        b.put(h + 1, 3) // laps 1-2 complete, lap 3 in progress
        b.put(h + 3, 1) // best lap = lap 1
        b.put(h + 4, 1); b.put(h + 5, 2); b.put(h + 6, 1) // best sector lap numbers

        fun lap(n: Int, time: Int, s1: Int, s2: Int, s3: Int) {
            val o = F1Parser.HISTORY_LAPS_OFFSET + (n - 1) * 14
            b.putInt(o, time)
            b.putShort(o + 4, s1.toShort())
            b.putShort(o + 7, s2.toShort())
            b.putShort(o + 10, s3.toShort())
        }
        lap(1, 90_000, 30_000, 31_000, 29_000)
        lap(2, 91_000, 30_500, 30_800, 29_700)
        lap(3, 0, 30_100, 0, 0)

        val f = F1Parser().parse(b.array(), b.capacity())!!
        assertEquals(90_000L, f.bestLapMs)
        assertEquals(listOf(30_500L, 30_800L, 29_700L), f.sectors!!.last)
        assertEquals(listOf(30_000L, 30_800L, 29_000L), f.sectors!!.best)

        // Another car's history must be ignored.
        b.put(h, 9)
        assertNull(F1Parser().parse(b.array(), b.capacity()))
    }

    @Test
    fun f1LapDataCurrentSectors() {
        val player = 0
        val stride = 57
        val b = f1Packet(F1Parser.PACKET_LAP_DATA, 22 * stride + 2, player)
        val base = F1Parser.HEADER_SIZE
        b.putShort(base + 8, 5_123)
        b.put(base + 10, 1) // +1 minute
        b.putShort(base + 11, 28_456)
        val f = F1Parser().parse(b.array(), b.capacity())!!
        assertEquals(listOf(65_123L, 28_456L, null), f.sectors!!.current)
    }

    @Test
    fun f1RejectsOldFormats() {
        val b = buf(100)
        b.putShort(0, 2022)
        assertNull(F1Parser().parse(b.array(), 100))
    }

    @Test
    fun gt7DecryptsAndParses() {
        val plain = buf(Gt7Parser.PACKET_SIZE)
        plain.putInt(0, Gt7Parser.MAGIC)
        plain.putInt(0x40, 0x12345678)
        plain.putFloat(0x3C, 7200f)
        plain.putFloat(0x44, 30f)
        plain.putFloat(0x48, 60f)
        plain.putFloat(0x4C, 25f)
        plain.putShort(0x74, 2)
        plain.putShort(0x76, 5)
        plain.putInt(0x7C, 101_234)
        plain.putShort(0x8A, 8000)
        plain.put(0x90, 0x34) // suggested 3, current 4
        plain.put(0x92, 128.toByte())

        val encrypted = Gt7Parser.encrypt(plain.array())
        assertNotEquals(Gt7Parser.MAGIC, ByteBuffer.wrap(encrypted).order(ByteOrder.LITTLE_ENDIAN).getInt(0))

        val f = Gt7Parser().parse(encrypted, encrypted.size)!!
        assertEquals(90f, f.speedKph, 0.01f)
        assertEquals(7200f, f.rpm)
        assertEquals(0.5f, f.fuel!!, 0.001f)
        assertEquals(2, f.lap)
        assertEquals(5, f.totalLaps)
        assertEquals(101_234L, f.lastLapMs)
        assertEquals(4, f.gear)
        assertEquals(128 / 255f, f.brake, 0.001f)
    }

    @Test
    fun gt7RejectsGarbage() {
        assertNull(Gt7Parser().parse(ByteArray(Gt7Parser.PACKET_SIZE), Gt7Parser.PACKET_SIZE))
    }
}
