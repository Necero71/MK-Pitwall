package com.consoletelemetry.ui

import androidx.compose.ui.unit.dp
import com.consoletelemetry.model.Game
import com.consoletelemetry.model.TelemetryFrame
import org.junit.Assert.assertEquals
import org.junit.Test

class DashLogicTest {

    @Test
    fun picksLayoutFromWindowSize() {
        assertEquals(DashLayout.Phone, dashLayoutFor(411.dp, 891.dp))
        assertEquals(DashLayout.PhoneLandscape, dashLayoutFor(891.dp, 411.dp))
        assertEquals(DashLayout.Tablet, dashLayoutFor(800.dp, 1280.dp))
        assertEquals(DashLayout.Tablet, dashLayoutFor(1280.dp, 800.dp))
        // A short split-screen window that's taller than it is wide still scrolls like a phone.
        assertEquals(DashLayout.Phone, dashLayoutFor(320.dp, 400.dp))
    }

    @Test
    fun historySamplesThirtyTimesASecondAndKeepsTheNewest() {
        val history = TelemetryHistory(capacity = 3)
        // 60 Hz input, so every other frame is dropped.
        for (i in 0 until 10) {
            history.add(TelemetryFrame(Game.FORZA, throttle = i / 10f), nowNanos = i * 16_666_667L)
        }
        val throttle = mutableListOf<Float>()
        history.forEach { _, _, t, _ -> throttle += t }
        assertEquals(listOf(0.4f, 0.6f, 0.8f), throttle)

        history.clear()
        assertEquals(0, history.size)
    }
}
