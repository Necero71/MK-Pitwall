package com.consoletelemetry.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.consoletelemetry.model.DamageState
import com.consoletelemetry.model.DrsState
import com.consoletelemetry.model.ErsState
import com.consoletelemetry.model.Game
import com.consoletelemetry.model.SectorTimes
import com.consoletelemetry.model.TelemetryFrame
import com.consoletelemetry.net.ReceiverStatus
import com.valentinilk.shimmer.shimmer
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal val ThrottleGreen = Color(0xFF66BB6A)
internal val BrakeRed = Color(0xFFEF5350)
internal val Amber = Color(0xFFFFCA28)
internal val Muted = Color(0xFF8A93A3)
internal val PersonalBestPurple = Color(0xFFB388FF)
private val ShiftBlue = Color(0xFF448AFF)
private val Unlit = Color(0xFF3A4150)
private val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

internal val TelemetryFrame.revFraction: Float
    get() = if (maxRpm > 0) (rpm / maxRpm).coerceIn(0f, 1f) else 0f

internal val TelemetryFrame.atLimiter: Boolean
    get() = revFraction > 0.95f

/** Eases toward [target] so bars glide between packets instead of stepping, even at low send rates. */
@Composable
internal fun smooth(target: Float, label: String): Float =
    animateFloatAsState(target, spring(stiffness = Spring.StiffnessHigh), label = label).value

@Composable
internal fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), content = content)
    }
}

/** Fades and expands a panel in when its data first arrives (F1 sends some packets less often). */
@Composable
internal fun RevealPanel(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) { content() }
}

// ---- Controls ----

@Composable
internal fun TitleRow(status: ReceiverStatus, stacked: Boolean = false) {
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Title()
            LiveBadge(status)
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title(Modifier.weight(1f))
            LiveBadge(status)
        }
    }
}

@Composable
private fun Title(modifier: Modifier = Modifier) {
    Text("Console Telemetry", modifier, style = MaterialTheme.typography.headlineSmall, color = Color.White)
}

@Composable
internal fun LiveBadge(status: ReceiverStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        ReceiverStatus.Stopped -> "OFF" to Muted
        is ReceiverStatus.Listening -> if (status.packetsPerSecond > 0) "LIVE" to ThrottleGreen else "WAITING" to Amber
        is ReceiverStatus.Error -> "ERROR" to BrakeRed
    }
    val tint by animateColorAsState(color, label = "badge")
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseDot(tint, pulsing = status is ReceiverStatus.Listening)
        Spacer(Modifier.width(8.dp))
        Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun PulseDot(color: Color, pulsing: Boolean) {
    val ring = rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearOutSlowInEasing)),
        label = "ring",
    )
    Canvas(Modifier.size(8.dp)) {
        val radius = size.minDimension / 2
        if (pulsing) {
            val t = ring.value
            drawCircle(color.copy(alpha = 0.5f * (1f - t)), radius = radius * (1f + 1.2f * t))
        }
        drawCircle(color, radius = radius)
    }
}

@Composable
internal fun GameSelector(
    selected: Game,
    onSelect: (Game) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
) {
    Row(if (fill) modifier.fillMaxWidth() else modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Game.entries.forEach { g ->
            FilterChip(
                selected = g == selected,
                onClick = { onSelect(g) },
                label = { Text(g.displayName, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1) },
                modifier = if (fill) Modifier.weight(1f) else Modifier,
            )
        }
    }
}

@Composable
internal fun ConsoleIpField(
    visible: Boolean,
    ip: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        OutlinedTextField(
            value = ip,
            onValueChange = onChange,
            label = { Text("PlayStation IP address") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun StatusRow(
    status: ReceiverStatus,
    deviceIp: String,
    game: Game,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StatusText(status, deviceIp, game, Modifier.weight(1f))
        StartStopButton(status is ReceiverStatus.Listening, onStart, onStop)
    }
}

@Composable
internal fun StatusText(
    status: ReceiverStatus,
    deviceIp: String,
    game: Game,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
) {
    val (text, color) = when (status) {
        ReceiverStatus.Stopped -> "Tap Start to listen for ${game.displayName}" to Muted
        is ReceiverStatus.Listening ->
            "Listening on $deviceIp:${status.port} · ${status.packetsPerSecond} pkt/s" to
                (if (status.packetsPerSecond > 0) ThrottleGreen else Amber)
        is ReceiverStatus.Error -> status.message to BrakeRed
    }
    val tint by animateColorAsState(color, label = "status")
    Text(
        text, modifier, color = tint, fontSize = 13.sp,
        maxLines = if (singleLine) 1 else 3, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun StartStopButton(running: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    Crossfade(running, label = "startStop") { isRunning ->
        if (isRunning) {
            OutlinedButton(onClick = onStop) { Text("Stop") }
        } else {
            Button(onClick = onStart) { Text("Start") }
        }
    }
}

/**
 * Console setup help plus a ghost of the dashboard. [wide] puts the two side by side, so the
 * landscape race display and tablets don't spend a whole screen on instructions.
 */
@Composable
internal fun SetupCard(
    game: Game,
    deviceIp: String,
    waiting: Boolean,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    landscapeGhost: Boolean = false,
) {
    Panel(modifier) {
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { SetupHelp(game, deviceIp, waiting) }
                Box(Modifier.weight(1f)) { GhostDashboard(animate = waiting, landscape = landscapeGhost) }
            }
        } else {
            SetupHelp(game, deviceIp, waiting)
            Spacer(Modifier.height(16.dp))
            GhostDashboard(animate = waiting)
        }
    }
}

@Composable
private fun SetupHelp(game: Game, deviceIp: String, waiting: Boolean) {
    Text(
        if (waiting) "Waiting for data…" else "Set up your console",
        fontWeight = FontWeight.SemiBold, color = Color.White,
    )
    Spacer(Modifier.height(6.dp))
    Text(game.setupHint, Modifier.widthIn(max = 600.dp), color = Muted, fontSize = 14.sp)
    Spacer(Modifier.height(10.dp))
    Text(if (game == Game.GT7) "Listening on" else "Send telemetry to", color = Muted, fontSize = 12.sp)
    Text(
        "$deviceIp:${game.listenPort}",
        color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace,
        fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(4.dp))
    Text("Device and console must be on the same network.", color = Muted, fontSize = 12.sp)
}

/**
 * Outline of the dashboard, laid out like the real one so the swap to live data doesn't jump.
 * It shimmers once Start is tapped and the socket is waiting for the first packet.
 */
@Composable
private fun GhostDashboard(animate: Boolean, landscape: Boolean = false) {
    val shimmer = if (animate) Modifier.shimmer() else Modifier
    Column(shimmer.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Bone(Modifier.fillMaxWidth().height(12.dp))
        if (landscape) {
            // Lap times, rev dial, pedals, tyres: same weights as LandscapeDash.
            Row(Modifier.height(120.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Bone(Modifier.weight(1f).fillMaxHeight())
                Bone(Modifier.weight(1.15f).fillMaxHeight())
                Bone(Modifier.weight(0.4f).fillMaxHeight())
                Bone(Modifier.weight(0.6f).fillMaxHeight())
            }
        } else {
            // Gear + speed, rev bar, then pedals and tyres: same shape as the phone dashboard.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Bone(Modifier.size(64.dp, 72.dp))
                Bone(Modifier.weight(1f).height(52.dp))
            }
            Bone(Modifier.fillMaxWidth().height(10.dp))
            Row(Modifier.height(96.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Bone(Modifier.weight(1f).fillMaxHeight())
                Bone(Modifier.weight(1.4f).fillMaxHeight())
            }
        }
        Bone(Modifier.fillMaxWidth().height(8.dp))
    }
}

@Composable
private fun Bone(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
}

@Composable
internal fun PausedNote(isLive: Boolean) {
    RevealPanel(!isLive) {
        Text("Paused / not on track", color = Amber, fontSize = 13.sp)
    }
}

// ---- Revs, gear and speed ----

/** F1-style shift lights: green, red, then blue as the revs climb, all flashing at the limiter. */
@Composable
internal fun ShiftLights(f: TelemetryFrame, modifier: Modifier = Modifier) {
    val fraction = f.revFraction
    val lit = ((fraction - SHIFT_START) / (SHIFT_LIMIT - SHIFT_START) * SHIFT_LEDS).toInt().coerceIn(0, SHIFT_LEDS)
    val limiter = fraction >= SHIFT_LIMIT
    val flash = rememberInfiniteTransition(label = "limiter").animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(90), RepeatMode.Reverse),
        label = "flash",
    )
    val off = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (SHIFT_LEDS - 1)) / SHIFT_LEDS
        val radius = CornerRadius(min(w, size.height) / 2)
        for (i in 0 until SHIFT_LEDS) {
            val on = limiter || i < lit
            val color = when {
                !on -> off
                i < SHIFT_LEDS / 3 -> ThrottleGreen
                i < SHIFT_LEDS * 2 / 3 -> BrakeRed
                else -> ShiftBlue
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (w + gap), 0f),
                size = Size(w, size.height),
                cornerRadius = radius,
                alpha = if (limiter) flash.value else 1f,
            )
        }
    }
}

private const val SHIFT_LEDS = 15
private const val SHIFT_START = 0.70f
private const val SHIFT_LIMIT = 0.97f

/** Upshifts roll the new gear in from below, downshifts from above. */
@Composable
internal fun GearIndicator(gear: Int, atLimiter: Boolean, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val color by animateColorAsState(if (atLimiter) BrakeRed else Color.White, tween(120), label = "gearColor")
    AnimatedContent(
        targetState = gear,
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.Center,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(tween(140)) { if (up) it else -it } + fadeIn(tween(140))) togetherWith
                (slideOutVertically(tween(140)) { if (up) -it else it } + fadeOut(tween(100)))
        },
        label = "gear",
    ) { g ->
        Text(gearLabel(g), fontSize = fontSize, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

@Composable
internal fun SpeedReadout(
    speedKph: Float,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.End,
) {
    Column(modifier, horizontalAlignment = alignment) {
        Text(
            "%.0f".format(speedKph), fontSize = fontSize, fontWeight = FontWeight.Bold,
            color = Color.White, style = TabularNumbers, maxLines = 1,
        )
        Text("km/h", color = Muted)
    }
}

@Composable
private fun rpmColor(fraction: Float): Color = when {
    fraction > 0.95f -> BrakeRed
    fraction > 0.85f -> Amber
    else -> MaterialTheme.colorScheme.primary
}

@Composable
internal fun RpmBar(f: TelemetryFrame) {
    val fill = smooth(f.revFraction, "rpm")
    val color by animateColorAsState(rpmColor(f.revFraction), tween(120), label = "rpmColor")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(color))
        }
        Text(
            "%,d rpm".format(f.rpm.toInt()),
            color = Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/** 240° rev arc with the gear and speed in the middle; sizes its text to the space it gets. */
@Composable
internal fun RevDial(f: TelemetryFrame, modifier: Modifier = Modifier) {
    val sweep = smooth(f.revFraction, "dial")
    val arcColor by animateColorAsState(rpmColor(f.revFraction), tween(120), label = "dialColor")
    val track = MaterialTheme.colorScheme.surfaceVariant
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val stroke = 14.dp
        val diameter: Dp = min((maxWidth - stroke).value, ((maxHeight - stroke) / DIAL_HEIGHT_RATIO).value).dp
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val d = diameter.toPx()
            val topLeft = Offset((size.width - d) / 2f, strokePx / 2f)
            val arc = Size(d, d)
            drawArc(track, DIAL_START, DIAL_SWEEP, false, topLeft, arc, style = Stroke(strokePx, cap = StrokeCap.Round))
            drawArc(
                BrakeRed.copy(alpha = 0.35f), DIAL_START + DIAL_SWEEP * 0.9f, DIAL_SWEEP * 0.1f, false,
                topLeft, arc, style = Stroke(strokePx),
            )
            if (sweep > 0f) {
                drawArc(
                    arcColor, DIAL_START, DIAL_SWEEP * sweep, false, topLeft, arc,
                    style = Stroke(strokePx, cap = StrokeCap.Round),
                )
            }
            val pivot = Offset(size.width / 2f, topLeft.y + d / 2f)
            val outer = d / 2f - strokePx * 1.2f
            val tick = 8.dp.toPx()
            for (i in 0..10) {
                val a = Math.toRadians((DIAL_START + DIAL_SWEEP * i / 10f).toDouble())
                val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
                drawLine(Muted.copy(alpha = 0.6f), pivot + dir * (outer - tick), pivot + dir * outer, 2.dp.toPx())
            }
        }
        val density = LocalDensity.current
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GearIndicator(f.gear, f.atLimiter, with(density) { (diameter * 0.3f).toSp() })
            Text(
                "%.0f".format(f.speedKph), fontSize = with(density) { (diameter * 0.11f).toSp() },
                fontWeight = FontWeight.Bold, color = Color.White, style = TabularNumbers, maxLines = 1,
            )
            Text("km/h · %,d rpm".format(f.rpm.toInt()), color = Muted, fontSize = 12.sp, maxLines = 1)
        }
    }
}

private const val DIAL_START = 150f
private const val DIAL_SWEEP = 240f

/** A 240° arc is three quarters as tall as it is wide. */
private const val DIAL_HEIGHT_RATIO = 0.75f

// ---- Inputs and tyres ----

@Composable
internal fun PedalsPanel(f: TelemetryFrame, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly) {
            PedalBar("THR", f.throttle, ThrottleGreen)
            PedalBar("BRK", f.brake, BrakeRed)
        }
    }
}

@Composable
private fun PedalBar(label: String, value: Float, color: Color) {
    val fill = smooth(value.coerceIn(0f, 1f), label)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) {
        Box(
            Modifier
                .weight(1f)
                .width(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(fill).background(color))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

/** Needs a bounded height: the tyre tiles share whatever is left under the title. */
@Composable
internal fun TyresPanel(f: TelemetryFrame, modifier: Modifier = Modifier) {
    Panel(modifier) {
        val compound = listOfNotNull(f.tyreCompound, f.tyreAgeLaps?.let { "$it laps" }).joinToString(" · ")
        Text(
            if (compound.isEmpty()) "Tyres °C" else "Tyres °C · $compound",
            color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in 0..1) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (col in 0..1) {
                        val i = row * 2 + col
                        TyreTile(f.tyreTempsC?.getOrNull(i), f.tyreWearPct?.getOrNull(i), Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TyreTile(tempC: Float?, wearPct: Float?, modifier: Modifier) {
    val color by animateColorAsState(tyreColor(tempC), tween(400), label = "tyre")
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(color),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(tempC?.let { "%.0f".format(it) } ?: "–", color = Color.Black, fontWeight = FontWeight.Bold)
        if (wearPct != null) {
            Text("%.0f%% worn".format(wearPct), color = Color.Black, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
internal fun SteeringBar(steer: Float, modifier: Modifier = Modifier) {
    val pos = smooth((steer.coerceIn(-1f, 1f) + 1f) / 2f, "steer")
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
        drawLine(Muted, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx())
        val w = 6.dp.toPx()
        drawRect(Color.White, topLeft = Offset((size.width - w) * pos, 0f), size = Size(w, size.height))
    }
}

/** Throttle and brake traces scrolling right to left, newest sample at the right edge. */
@Composable
internal fun TracePanel(history: TelemetryHistory, modifier: Modifier = Modifier, chartHeight: Dp = 96.dp) {
    val grid = MaterialTheme.colorScheme.surfaceVariant
    Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Inputs", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            LegendKey("Throttle", ThrottleGreen)
            Spacer(Modifier.width(12.dp))
            LegendKey("Brake", BrakeRed)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(chartHeight)) {
            for (q in 1..3) {
                val y = size.height * q / 4f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            val step = size.width / (history.capacity - 1)
            val throttle = Path()
            val brake = Path()
            history.forEach { i, count, t, b ->
                val x = size.width - (count - 1 - i) * step
                val ty = size.height * (1f - t.coerceIn(0f, 1f))
                val by = size.height * (1f - b.coerceIn(0f, 1f))
                if (i == 0) {
                    throttle.moveTo(x, ty)
                    brake.moveTo(x, by)
                } else {
                    throttle.lineTo(x, ty)
                    brake.lineTo(x, by)
                }
            }
            val line = Stroke(2.dp.toPx(), join = StrokeJoin.Round)
            drawPath(brake, BrakeRed, style = line)
            drawPath(throttle, ThrottleGreen, style = line)
        }
    }
}

@Composable
private fun LegendKey(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Text(label, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

// ---- Laps, sectors, ERS and damage ----

@Composable
internal fun LapPanel(f: TelemetryFrame, personalBest: Boolean, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Lap", lapLabel(f))
            Stat("Pos", f.position?.let { "P$it" })
            Stat("Fuel", fuelLabel(f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Current", f.currentLapMs?.let(::formatLap))
            Stat("Last", f.lastLapMs?.let(::formatLap))
            Stat("Best", f.bestLapMs?.let(::formatLap), highlight = personalBest)
        }
    }
}

/** Lap times stacked for the narrow side column of the landscape dash. Needs a bounded height. */
@Composable
internal fun ColumnScope.LapTimesColumn(f: TelemetryFrame, personalBest: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat("Lap", lapLabel(f))
        Stat("Pos", f.position?.let { "P$it" })
    }
    Spacer(Modifier.weight(1f))
    Stat("Current", f.currentLapMs?.let(::formatLap), valueSize = 28.sp)
    Spacer(Modifier.weight(1f))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat("Last", f.lastLapMs?.let(::formatLap), valueSize = 16.sp)
        Stat("Best", f.bestLapMs?.let(::formatLap), highlight = personalBest, valueSize = 16.sp)
    }
    f.fuel?.let { fuel ->
        Spacer(Modifier.height(10.dp))
        FuelBar(fuel, f.fuelLapsRemaining, fuelLabel(f))
    }
}

@Composable
private fun FuelBar(fuel: Float, lapsRemaining: Float?, label: String?) {
    val fill = smooth(fuel.coerceIn(0f, 1f), "fuel")
    val short = (lapsRemaining != null && lapsRemaining < 0f) || fuel < 0.1f
    val color by animateColorAsState(if (short) BrakeRed else MaterialTheme.colorScheme.primary, label = "fuel")
    Text("Fuel ${label ?: ""}", color = Muted, fontSize = 12.sp, maxLines = 1)
    Spacer(Modifier.height(4.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(color))
    }
}

private fun lapLabel(f: TelemetryFrame): String? = f.lap?.let { lap -> f.totalLaps?.let { "$lap/$it" } ?: "$lap" }

private fun fuelLabel(f: TelemetryFrame): String? = f.fuel?.let { pct ->
    "%.0f%%".format(pct * 100) + (f.fuelLapsRemaining?.let { " (%+.1f)".format(it) } ?: "")
}

@Composable
internal fun SectorsPanel(s: SectorTimes) {
    Panel {
        Text("Sectors", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        SectorRow("", listOf("S1", "S2", "S3"), header = true)
        SectorRow("Now", s.current.map(::formatSector))
        SectorRow("Last", s.last.map(::formatSector))
        SectorRow("Best", s.best.map(::formatSector))
    }
}

@Composable
private fun SectorRow(label: String, cells: List<String>, header: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.width(44.dp))
        cells.forEach {
            Text(
                it,
                color = if (header) Muted else Color.White,
                fontSize = if (header) 12.sp else 15.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun ErsDrsPanel(ers: ErsState?, drs: DrsState?) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ERS", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (drs != null) DrsBadge(drs)
        }
        if (ers != null) {
            val store = smooth(ers.storeFraction.coerceIn(0f, 1f), "ers")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(store).background(Color(0xFFFFD54F)))
                }
                Text(
                    "%.0f%%".format(ers.storeFraction * 100),
                    color = Color.White, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("Mode", ers.deployMode)
                Stat("Harvested", "%.2f MJ".format(ers.harvestedThisLapJ / 1e6f))
                Stat("Deployed", "%.2f MJ".format(ers.deployedThisLapJ / 1e6f))
            }
        }
    }
}

@Composable
private fun DrsBadge(drs: DrsState) {
    val (label, color) = when {
        drs.active -> "DRS OPEN" to ThrottleGreen
        drs.allowed -> "DRS AVAILABLE" to Amber
        drs.activationDistanceM > 0 -> "DRS in ${drs.activationDistanceM} m" to Muted
        else -> "DRS" to Unlit
    }
    val lit = drs.active || drs.allowed
    val background by animateColorAsState(
        if (lit) color else MaterialTheme.colorScheme.surfaceVariant, tween(200), label = "drsBg",
    )
    val text by animateColorAsState(if (lit) Color.Black else color, tween(200), label = "drsText")
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = text)
    }
}

@Composable
internal fun DamagePanel(d: DamageState) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Damage", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (d.drsFault) FaultTag("DRS FAULT")
            if (d.ersFault) FaultTag("ERS FAULT")
        }
        Spacer(Modifier.height(6.dp))
        val parts = listOf(
            "FL wing" to d.frontLeftWing, "FR wing" to d.frontRightWing, "Rear wing" to d.rearWing,
            "Floor" to d.floor, "Diffuser" to d.diffuser, "Sidepod" to d.sidepod,
            "Gearbox" to d.gearbox, "Engine" to d.engine,
        )
        parts.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (name, pct) -> DamageCell(name, pct, Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DamageCell("Tyres", d.tyres.max(), Modifier.weight(1f), detail = d.tyres.joinToString("/"))
            DamageCell("Brakes", d.brakes.max(), Modifier.weight(1f), detail = d.brakes.joinToString("/"))
        }
    }
}

@Composable
private fun DamageCell(name: String, pct: Int, modifier: Modifier, detail: String? = null) {
    val dot by animateColorAsState(damageColor(pct), tween(400), label = "damage")
    Row(modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(dot))
        Text(name, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp).weight(1f))
        Text(
            detail ?: "$pct%",
            color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun FaultTag(text: String) {
    Box(
        Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(BrakeRed)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun Stat(label: String, value: String?, highlight: Boolean = false, valueSize: TextUnit = 18.sp) {
    val color by animateColorAsState(if (highlight) PersonalBestPurple else Color.White, tween(300), label = "stat")
    Column {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(value ?: "–", color = color, fontSize = valueSize, fontFamily = FontFamily.Monospace, maxLines = 1)
    }
}

private fun damageColor(pct: Int): Color = when {
    pct <= 0 -> Unlit
    pct < 20 -> Color(0xFF81C784)
    pct < 50 -> Color(0xFFFFD54F)
    else -> Color(0xFFE57373)
}

private fun gearLabel(gear: Int) = when {
    gear < 0 -> "R"
    gear == 0 -> "N"
    else -> gear.toString()
}

/** Cold = blue, working window = green, hot = red. Rough window that suits all three games. */
private fun tyreColor(t: Float?): Color = when {
    t == null -> Unlit
    t < 60f -> Color(0xFF64B5F6)
    t < 75f -> Color(0xFF80DEEA)
    t < 100f -> Color(0xFF81C784)
    t < 115f -> Color(0xFFFFD54F)
    else -> Color(0xFFE57373)
}

private fun formatSector(ms: Long?): String = when {
    ms == null -> "–"
    ms >= 60_000 -> formatLap(ms)
    else -> "%d.%03d".format(ms / 1000, ms % 1000)
}

internal fun formatLap(ms: Long): String {
    val minutes = ms / 60_000
    val seconds = (ms % 60_000) / 1000
    val millis = ms % 1000
    return "%d:%02d.%03d".format(minutes, seconds, millis)
}
