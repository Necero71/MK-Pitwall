package com.consoletelemetry.ui

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.consoletelemetry.R

// Mavro Kinetic palette. Base values match the shared MK Tactical set used across the app lineup;
// gauge colors are brighter than the UI ones so thin strokes stay readable on the dark background.
object Mk {
    val Base = Color(0xFF0F110C)
    val Surface = Color(0xFF1A1E16)
    val Raised = Color(0xFF252A1F)
    val Line = Color(0xFF3A4030)
    val Olive = Color(0xFF556B2F)
    val FieldOlive = Color(0xFFA3AD6E)
    val Coyote = Color(0xFF81613C)
    val Tan = Color(0xFFD2B48C)
    val Text = Color(0xFFECE4D0)
    val TextMuted = Color(0xFFA39C88)

    val SignalOk = Color(0xFF9BBF4A)
    val SignalCaution = Color(0xFFE0A526)
    val SignalAlert = Color(0xFFE8682A)
    val SignalCold = Color(0xFF6FA3B8)
    val ChannelSpeed = Color(0xFFC9A06A)
}

val HeadingFont = FontFamily(
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold, FontWeight.Bold),
)
val BodyFont = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
)

/** Live numbers: semi-condensed with fixed-width digits so values don't jitter as they update. */
val ReadoutFont = FontFamily(Font(R.font.barlow_semicondensed_medium, FontWeight.Medium))
val Readout = TextStyle(fontFamily = ReadoutFont, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")

private val MkColors = darkColorScheme(
    primary = Color(0xFFB5C17A),
    onPrimary = Color(0xFF232A05),
    primaryContainer = Mk.Olive,
    onPrimaryContainer = Color(0xFFDCE6A0),
    secondary = Color(0xFFD9B98F),
    onSecondary = Color(0xFF3D2A10),
    secondaryContainer = Mk.Coyote,
    onSecondaryContainer = Color(0xFFFFE0C0),
    tertiary = Color(0xFFE8DCC4),
    onTertiary = Color(0xFF2E2718),
    background = Mk.Base,
    onBackground = Mk.Text,
    surface = Mk.Surface,
    onSurface = Mk.Text,
    surfaceVariant = Mk.Raised,
    onSurfaceVariant = Mk.TextMuted,
    surfaceContainerLow = Color(0xFF151810),
    surfaceContainer = Mk.Surface,
    surfaceContainerHigh = Mk.Raised,
    outline = Color(0xFF8F8C78),
    outlineVariant = Mk.Line,
    error = Color(0xFFC0532F),
    onError = Color(0xFFFFFFFF),
)

private fun heading(size: Int, line: Int, weight: FontWeight = FontWeight.SemiBold, tracking: Double = 0.015) =
    TextStyle(fontFamily = HeadingFont, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.em)

private fun body(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) =
    TextStyle(fontFamily = BodyFont, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.em)

private val MkTypography = Typography(
    displaySmall = heading(36, 44, FontWeight.Bold),
    headlineMedium = heading(28, 36),
    headlineSmall = heading(24, 32),
    titleLarge = heading(22, 28),
    titleMedium = heading(18, 24),
    titleSmall = heading(15, 20),
    bodyLarge = body(16, 24),
    bodyMedium = body(14, 20),
    bodySmall = body(12, 16),
    labelLarge = body(14, 20, FontWeight.Medium, 0.05),
    labelMedium = body(12, 16, FontWeight.Medium, 0.05),
    labelSmall = body(11, 16, FontWeight.Medium, 0.05),
)

// Two opposite corners cut at 45°: the Mavro Kinetic signature shape.
private val MkShapes = Shapes(
    extraSmall = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
    small = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
    medium = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
    large = CutCornerShape(topStart = 16.dp),
    extraLarge = CutCornerShape(topStart = 24.dp),
)

@Composable
fun ConsoleTelemetryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MkColors,
        typography = MkTypography,
        shapes = MkShapes,
        content = content,
    )
}
