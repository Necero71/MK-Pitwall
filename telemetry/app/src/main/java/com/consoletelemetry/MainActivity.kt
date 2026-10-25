package com.consoletelemetry

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.consoletelemetry.ui.ConsoleTelemetryTheme
import com.consoletelemetry.ui.TelemetryScreen

class MainActivity : ComponentActivity() {

    private val viewModel: TelemetryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Same inset behaviour on every Android version, with light bar icons on the dark dashboard.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // It's a dashboard: keep the screen on while it's in front.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            ConsoleTelemetryTheme {
                TelemetryScreen(viewModel)
            }
        }
    }
}
