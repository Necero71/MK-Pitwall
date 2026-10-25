# Console Telemetry (Android)

A live dashboard on your phone for racing games' UDP telemetry, running on Xbox and PlayStation.

| Game | Platform | Port | Notes |
|---|---|---|---|
| Forza Motorsport (2023), FM7, Forza Horizon 4/5 | Xbox | 5300 (listen) | In-game "Data Out", Dash format |
| EA F1 23 / 24 / 25 | Xbox, PlayStation | 20777 (listen) | UDP format 2023+ |
| Gran Turismo 7 | PlayStation | 33740 listen, 33739 heartbeat | Salsa20-encrypted; the app pings the console |

Shows gear, speed, RPM/shift light, throttle and brake, steering, tyre temps, lap, position, fuel, and current, last and best lap times.

F1 also shows sector times (current, last and best), ERS charge and deploy mode, DRS state, tyre compound, age and wear, and damage to the wings, floor, gearbox, engine, tyres and brakes. Forza Motorsport (2023) shows tyre wear.

## Phones and tablets

The layout follows the window size, so it also adapts to split-screen and foldables:

| Window | Layout |
|---|---|
| Phone, portrait | One scrolling column |
| Phone, landscape (height under 480dp) | Race display for a wheel mount: shift lights, lap times, rev dial, pedals and tyres fill the first screen; scroll for inputs, sectors, ERS and damage |
| Tablet / foldable (600dp+ wide) | Two-column dashboard; controls go in a side panel from 840dp wide |

Animations: F1-style shift lights that flash at the limiter, a rev dial, gear changes that roll up or down, smoothed pedal, rev and steering bars, a scrolling throttle/brake trace, and panels that fade in when their data arrives. Two libraries from GitHub add to that:

- [Konfetti](https://github.com/DanielMartinus/Konfetti) fires purple confetti when you beat your best lap (and the Best time turns purple).
- [compose-shimmer](https://github.com/valentinilk/compose-shimmer) shimmers the placeholder dashboard while it waits for the first packet.

Both are pinned to older releases: newer ones are built with Kotlin 2.1+, which this Kotlin 2.0 project can't read.

## Build

1. Open this folder in **Android Studio** (Ladybug or newer). Let it sync. If it asks, let it create the Gradle wrapper (Gradle 8.9).
2. Run on a phone that's on the **same Wi-Fi/LAN** as the console.
3. Unit tests for the parsers: `./gradlew :app:testDebugUnitTest`

## Use

1. Pick the game, then tap **Start**. The screen shows the phone's IP.
2. Enter that IP and port in the game's telemetry settings (the app shows the steps). For GT7, enter the PlayStation's IP in the app instead.

## Layout

- `parser/`: one parser per game, each turning packets into a common `TelemetryFrame`
- `net/TelemetryReceiver.kt`: the UDP socket loop, the packets/sec counter, and the GT7 heartbeat
- `ui/TelemetryScreen.kt`: picks the phone, landscape or tablet layout and arranges it
- `ui/Widgets.kt`: the gauges and panels the layouts share
- `ui/TelemetryHistory.kt`: the rolling input history behind the trace chart
- `ui/Celebration.kt`: best-lap detection and the confetti
