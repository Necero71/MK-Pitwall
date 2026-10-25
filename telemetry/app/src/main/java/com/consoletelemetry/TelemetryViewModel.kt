package com.consoletelemetry

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.consoletelemetry.model.Game
import com.consoletelemetry.net.ReceiverStatus
import com.consoletelemetry.net.TelemetryReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

class TelemetryViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val receiver = TelemetryReceiver(viewModelScope)
    // Some devices drop broadcast datagrams (F1 broadcast mode) unless this is held.
    private val multicastLock = (app.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
        .createMulticastLock("telemetry").apply { setReferenceCounted(false) }

    val frame = receiver.frame
    val status = receiver.status

    private val _game = MutableStateFlow(
        runCatching { Game.valueOf(prefs.getString(KEY_GAME, null)!!) }.getOrDefault(Game.FORZA)
    )
    val game: StateFlow<Game> = _game.asStateFlow()

    private val _consoleIp = MutableStateFlow(prefs.getString(KEY_CONSOLE_IP, "") ?: "")
    val consoleIp: StateFlow<String> = _consoleIp.asStateFlow()

    val deviceIp: String = findDeviceIp() ?: "unknown — connect to Wi-Fi"

    fun selectGame(game: Game) {
        val wasRunning = status.value !is ReceiverStatus.Stopped
        _game.value = game
        prefs.edit().putString(KEY_GAME, game.name).apply()
        if (wasRunning) start()
    }

    fun setConsoleIp(ip: String) {
        _consoleIp.value = ip
        prefs.edit().putString(KEY_CONSOLE_IP, ip).apply()
    }

    fun start() {
        multicastLock.acquire()
        receiver.start(_game.value, _consoleIp.value)
    }

    fun stop() {
        receiver.stop()
        if (multicastLock.isHeld) multicastLock.release()
    }

    override fun onCleared() {
        stop()
    }

    private fun findDeviceIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    private companion object {
        const val KEY_GAME = "game"
        const val KEY_CONSOLE_IP = "console_ip"
    }
}
