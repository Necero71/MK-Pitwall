package com.consoletelemetry.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.consoletelemetry.TelemetryViewModel
import com.consoletelemetry.model.Game
import com.consoletelemetry.model.TelemetryFrame
import com.consoletelemetry.net.ReceiverStatus

/** Everything the dashboard draws, so the layouts can be rendered without a ViewModel. */
data class DashState(
    val game: Game,
    val status: ReceiverStatus,
    val frame: TelemetryFrame?,
    val consoleIp: String,
    val deviceIp: String,
) {
    val waiting: Boolean get() = status is ReceiverStatus.Listening && frame == null

    /** GT7 needs the console's IP to start; hide it once data is flowing. */
    val showConsoleIp: Boolean get() = game == Game.GT7 && (status !is ReceiverStatus.Listening || frame == null)
}

class DashActions(
    val onSelectGame: (Game) -> Unit = {},
    val onConsoleIpChange: (String) -> Unit = {},
    val onStart: () -> Unit = {},
    val onStop: () -> Unit = {},
)

enum class DashLayout { Phone, PhoneLandscape, Tablet }

/** Material window-size breakpoints: a compact height is a phone on its side, 600dp+ wide is a tablet or foldable. */
internal fun dashLayoutFor(width: Dp, height: Dp): DashLayout = when {
    height < 480.dp && width > height -> DashLayout.PhoneLandscape
    width < 600.dp -> DashLayout.Phone
    else -> DashLayout.Tablet
}

@Composable
fun TelemetryScreen(vm: TelemetryViewModel) {
    val game by vm.game.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val frame by vm.frame.collectAsStateWithLifecycle()
    val consoleIp by vm.consoleIp.collectAsStateWithLifecycle()

    val history = remember { TelemetryHistory() }
    LaunchedEffect(vm) {
        vm.frame.collect { f -> if (f == null) history.clear() else history.add(f) }
    }
    val actions = remember(vm) { DashActions(vm::selectGame, vm::setConsoleIp, vm::start, vm::stop) }

    TelemetryContent(DashState(game, status, frame, consoleIp, vm.deviceIp), history, actions)
}

@Composable
fun TelemetryContent(state: DashState, history: TelemetryHistory, actions: DashActions) {
    val burst = rememberPersonalBestBurst(state.frame?.bestLapMs, state.game)
    val personalBest = burst != null
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        val wide = maxWidth >= 840.dp
        Crossfade(dashLayoutFor(maxWidth, maxHeight), label = "layout") { layout ->
            when (layout) {
                DashLayout.Phone -> PhoneLayout(state, history, actions, personalBest)
                DashLayout.PhoneLandscape -> LandscapeLayout(state, history, actions, personalBest)
                DashLayout.Tablet -> TabletLayout(state, history, actions, personalBest, wide)
            }
        }
        PersonalBestConfetti(burst, Modifier.matchParentSize())
    }
}

/** Crossfades between the setup card and the live dashboard when the first packet lands. */
@Composable
private fun LiveOrSetup(
    state: DashState,
    modifier: Modifier = Modifier,
    setup: @Composable () -> Unit = { SetupCard(state.game, state.deviceIp, state.waiting) },
    live: @Composable ColumnScope.(TelemetryFrame) -> Unit,
) {
    AnimatedContent(
        targetState = state.frame != null,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.97f)) togetherWith fadeOut(tween(150))
        },
        label = "liveOrSetup",
    ) { hasFrame ->
        val f = state.frame
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (hasFrame && f != null) live(f) else setup()
        }
    }
}

// ---- Phone, portrait: one scrolling column ----

@Composable
private fun PhoneLayout(state: DashState, history: TelemetryHistory, actions: DashActions, personalBest: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TitleRow(state.status)
        GameSelector(state.game, actions.onSelectGame)
        ConsoleIpField(state.showConsoleIp, state.consoleIp, actions.onConsoleIpChange)
        StatusRow(state.status, state.deviceIp, state.game, actions.onStart, actions.onStop)
        LiveOrSetup(state) { f ->
            PausedNote(f.isLive)
            Panel {
                ShiftLights(f, Modifier.fillMaxWidth().height(12.dp))
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GearIndicator(f.gear, f.atLimiter, 72.sp, Modifier.width(80.dp))
                    SpeedReadout(f.speedKph, 56.sp, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                RpmBar(f)
            }
            Row(Modifier.height(196.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PedalsPanel(f, Modifier.weight(1f).fillMaxHeight())
                TyresPanel(f, Modifier.weight(1.4f).fillMaxHeight())
            }
            SteeringBar(f.steer)
            TracePanel(history)
            LapPanel(f, personalBest)
            ExtraPanels(f)
        }
    }
}

// ---- Phone, landscape: a wheel-mount race display that fills the first screen ----

@Composable
private fun LandscapeLayout(state: DashState, history: TelemetryHistory, actions: DashActions, personalBest: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LiveBadge(state.status)
            GameSelector(state.game, actions.onSelectGame)
            StatusText(state.status, state.deviceIp, state.game, Modifier.weight(1f), singleLine = true)
            StartStopButton(state.status is ReceiverStatus.Listening, actions.onStart, actions.onStop)
        }
        ConsoleIpField(state.showConsoleIp, state.consoleIp, actions.onConsoleIpChange)
        LiveOrSetup(
            state,
            Modifier.weight(1f),
            setup = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SetupCard(state.game, state.deviceIp, state.waiting)
                }
            },
        ) { f -> LandscapeDash(f, history, personalBest) }
    }
}

@Composable
private fun LandscapeDash(f: TelemetryFrame, history: TelemetryHistory, personalBest: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxHeight
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.height(viewport), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PausedNote(f.isLive)
                ShiftLights(f, Modifier.fillMaxWidth().height(14.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Panel(Modifier.weight(1f).fillMaxHeight()) { LapTimesColumn(f, personalBest) }
                    Panel(Modifier.weight(1.15f).fillMaxHeight()) { RevDial(f, Modifier.fillMaxSize()) }
                    PedalsPanel(f, Modifier.weight(0.4f).fillMaxHeight())
                    TyresPanel(f, Modifier.weight(0.6f).fillMaxHeight())
                }
                SteeringBar(f.steer)
            }
            // Below the fold: everything that doesn't need a glance at 250 km/h.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TracePanel(history)
                    RevealPanel(f.sectors != null) { f.sectors?.let { SectorsPanel(it) } }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RevealPanel(f.ers != null || f.drs != null) { ErsDrsPanel(f.ers, f.drs) }
                    RevealPanel(f.damage != null) { f.damage?.let { DamagePanel(it) } }
                }
            }
        }
    }
}

// ---- Tablet and foldable: controls beside or above a two-column dashboard ----

@Composable
private fun TabletLayout(
    state: DashState,
    history: TelemetryHistory,
    actions: DashActions,
    personalBest: Boolean,
    wide: Boolean,
) {
    if (wide) {
        Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TitleRow(state.status, stacked = true)
                Text("Game", color = Muted, fontSize = 12.sp)
                GameSelector(state.game, actions.onSelectGame)
                ConsoleIpField(state.showConsoleIp, state.consoleIp, actions.onConsoleIpChange)
                StatusRow(state.status, state.deviceIp, state.game, actions.onStart, actions.onStop)
            }
            TabletDashboard(state, history, personalBest, Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TitleRow(state.status)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GameSelector(state.game, actions.onSelectGame)
                StatusRow(state.status, state.deviceIp, state.game, actions.onStart, actions.onStop, Modifier.weight(1f))
            }
            ConsoleIpField(state.showConsoleIp, state.consoleIp, actions.onConsoleIpChange)
            TabletDashboard(state, history, personalBest, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TabletDashboard(state: DashState, history: TelemetryHistory, personalBest: Boolean, modifier: Modifier) {
    LiveOrSetup(state, modifier.verticalScroll(rememberScrollState())) { f ->
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PausedNote(f.isLive)
                Panel {
                    ShiftLights(f, Modifier.fillMaxWidth().height(14.dp))
                    Spacer(Modifier.height(12.dp))
                    RevDial(f, Modifier.fillMaxWidth().height(240.dp))
                }
                TracePanel(history, chartHeight = 120.dp)
                LapPanel(f, personalBest)
                RevealPanel(f.sectors != null) { f.sectors?.let { SectorsPanel(it) } }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.height(220.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PedalsPanel(f, Modifier.weight(0.8f).fillMaxHeight())
                    TyresPanel(f, Modifier.weight(1.4f).fillMaxHeight())
                }
                SteeringBar(f.steer)
                RevealPanel(f.ers != null || f.drs != null) { ErsDrsPanel(f.ers, f.drs) }
                RevealPanel(f.damage != null) { f.damage?.let { DamagePanel(it) } }
            }
        }
    }
}

@Composable
private fun ExtraPanels(f: TelemetryFrame) {
    RevealPanel(f.sectors != null) { f.sectors?.let { SectorsPanel(it) } }
    RevealPanel(f.ers != null || f.drs != null) { ErsDrsPanel(f.ers, f.drs) }
    RevealPanel(f.damage != null) { f.damage?.let { DamagePanel(it) } }
}
