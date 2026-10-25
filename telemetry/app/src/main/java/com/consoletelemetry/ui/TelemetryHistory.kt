package com.consoletelemetry.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.consoletelemetry.model.TelemetryFrame

/** Rolling window of pedal inputs for the trace chart, sampled at most [SAMPLES_PER_SECOND] times a second. */
@Stable
class TelemetryHistory(val capacity: Int = 10 * SAMPLES_PER_SECOND) {
    private val throttle = FloatArray(capacity)
    private val brake = FloatArray(capacity)
    private var oldest = 0
    private var lastSampleNanos: Long? = null

    // Bumped on every change so a draw block that reads the samples redraws.
    private var version by mutableIntStateOf(0)

    var size = 0
        private set

    fun add(frame: TelemetryFrame, nowNanos: Long = System.nanoTime()) {
        val last = lastSampleNanos
        if (last != null && nowNanos - last < SAMPLE_INTERVAL_NANOS) return
        lastSampleNanos = nowNanos
        val slot = (oldest + size) % capacity
        throttle[slot] = frame.throttle
        brake[slot] = frame.brake
        if (size < capacity) size++ else oldest = (oldest + 1) % capacity
        version++
    }

    fun clear() {
        size = 0
        oldest = 0
        lastSampleNanos = null
        version++
    }

    /** Calls [block] for each sample, oldest first. */
    fun forEach(block: (index: Int, count: Int, throttle: Float, brake: Float) -> Unit) {
        if (version < 0) return // Never true; the read subscribes the caller to new samples.
        for (i in 0 until size) {
            val slot = (oldest + i) % capacity
            block(i, size, throttle[slot], brake[slot])
        }
    }

    companion object {
        const val SAMPLES_PER_SECOND = 30
        private const val SAMPLE_INTERVAL_NANOS = 1_000_000_000L / SAMPLES_PER_SECOND
    }
}
