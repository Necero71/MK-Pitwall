package com.consoletelemetry.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.consoletelemetry.model.Game
import kotlinx.coroutines.delay
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit

private const val CELEBRATION_MS = 3_500L

/**
 * Returns a burst id for a few seconds after [bestLapMs] beats the best seen so far, otherwise null.
 * The first best lap of a session doesn't count: there's nothing to beat yet.
 */
@Composable
internal fun rememberPersonalBestBurst(bestLapMs: Long?, game: Game): Int? {
    var best by remember(game) { mutableStateOf(bestLapMs) }
    var burst by remember { mutableIntStateOf(0) }
    var celebrating by remember { mutableStateOf(false) }
    LaunchedEffect(bestLapMs) {
        val previous = best
        if (bestLapMs == null) return@LaunchedEffect
        best = bestLapMs
        if (previous != null && bestLapMs < previous) {
            burst++
            celebrating = true
            try {
                delay(CELEBRATION_MS)
            } finally {
                celebrating = false
            }
        }
    }
    return if (celebrating) burst else null
}

/**
 * Purple confetti (F1's colour for a personal best) from both bottom corners.
 * KonfettiView keeps its frame loop running while composed, so it only exists during a burst.
 */
@Composable
internal fun PersonalBestConfetti(burst: Int?, modifier: Modifier = Modifier) {
    if (burst == null) return
    key(burst) {
        KonfettiView(modifier = modifier, parties = remember { personalBestParties() })
    }
}

private fun personalBestParties(): List<Party> {
    val corner = Party(
        speed = 20f,
        maxSpeed = 50f,
        damping = 0.9f,
        spread = 50,
        colors = listOf(0xD2B48C, 0xA3AD6E, 0x81613C, 0xECE4D0),
        timeToLive = 2_500,
        emitter = Emitter(duration = 250, TimeUnit.MILLISECONDS).max(90),
    )
    return listOf(
        corner.copy(angle = 300, position = Position.Relative(0.0, 0.85)),
        corner.copy(angle = 240, position = Position.Relative(1.0, 0.85)),
    )
}
