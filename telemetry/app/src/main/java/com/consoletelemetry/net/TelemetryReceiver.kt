package com.consoletelemetry.net

import com.consoletelemetry.model.Game
import com.consoletelemetry.model.TelemetryFrame
import com.consoletelemetry.parser.F1Parser
import com.consoletelemetry.parser.ForzaParser
import com.consoletelemetry.parser.Gt7Parser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

sealed interface ReceiverStatus {
    data object Stopped : ReceiverStatus
    data class Listening(val port: Int, val packetsPerSecond: Int, val totalPackets: Long) : ReceiverStatus
    data class Error(val message: String) : ReceiverStatus
}

/** Binds a UDP socket for the selected game and publishes parsed frames. */
class TelemetryReceiver(private val scope: CoroutineScope) {

    private val _frame = MutableStateFlow<TelemetryFrame?>(null)
    val frame: StateFlow<TelemetryFrame?> = _frame.asStateFlow()

    private val _status = MutableStateFlow<ReceiverStatus>(ReceiverStatus.Stopped)
    val status: StateFlow<ReceiverStatus> = _status.asStateFlow()

    private var job: Job? = null

    fun start(game: Game, consoleIp: String?) {
        stop()
        _frame.value = null
        if (game == Game.GT7 && consoleIp.isNullOrBlank()) {
            _status.value = ReceiverStatus.Error("Enter your PlayStation's IP address for GT7")
            return
        }
        job = scope.launch(Dispatchers.IO) {
            val parser = when (game) {
                Game.FORZA -> ForzaParser()
                Game.F1 -> F1Parser()
                Game.GT7 -> Gt7Parser()
            }
            val gt7Target = if (game == Game.GT7) {
                runCatching { InetAddress.getByName(consoleIp!!.trim()) }.getOrNull() ?: run {
                    _status.value = ReceiverStatus.Error("Invalid console IP: $consoleIp")
                    return@launch
                }
            } else null
            val socket = try {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 500
                    bind(InetSocketAddress(game.listenPort))
                }
            } catch (e: Exception) {
                _status.value = ReceiverStatus.Error("Can't bind port ${game.listenPort}: ${e.message}")
                return@launch
            }

            val heartbeat = gt7Target?.let { launchGt7Heartbeat(socket, it) }

            val buffer = ByteArray(2048)
            val packet = DatagramPacket(buffer, buffer.size)
            var total = 0L
            var windowCount = 0
            var windowStart = System.currentTimeMillis()
            _status.value = ReceiverStatus.Listening(game.listenPort, 0, 0)

            try {
                while (isActive) {
                    try {
                        packet.length = buffer.size
                        socket.receive(packet)
                        parser.parse(buffer, packet.length)?.let { _frame.value = it }
                        total++
                        windowCount++
                    } catch (_: SocketTimeoutException) {
                        // Loop so cancellation and the rate counter stay responsive.
                    }
                    val now = System.currentTimeMillis()
                    if (now - windowStart >= 1000) {
                        _status.value = ReceiverStatus.Listening(game.listenPort, windowCount, total)
                        windowCount = 0
                        windowStart = now
                    }
                }
            } catch (e: Exception) {
                if (isActive) _status.value = ReceiverStatus.Error(e.message ?: "Socket error")
            } finally {
                heartbeat?.cancel()
                socket.close()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _status.value = ReceiverStatus.Stopped
    }

    /** GT7 streams for ~100 packets after each heartbeat, so ping it regularly. */
    private fun CoroutineScope.launchGt7Heartbeat(socket: DatagramSocket, target: InetAddress): Job =
        launch(Dispatchers.IO) {
            val ping = "A".toByteArray()
            while (isActive) {
                runCatching { socket.send(DatagramPacket(ping, ping.size, target, GT7_HEARTBEAT_PORT)) }
                delay(1000)
            }
        }

    companion object {
        const val GT7_HEARTBEAT_PORT = 33739
    }
}
