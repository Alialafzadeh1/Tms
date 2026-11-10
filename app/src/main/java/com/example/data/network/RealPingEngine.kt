package com.example.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

data class PingResult(
    val isReachable: Boolean,
    val latencyMs: Int,
    val errorMessage: String? = null
)

object RealPingEngine {

    /**
     * Executes a real TCP handshake ping against the target host and port.
     * Does NOT generate fake or simulated values.
     * Returns actual latency in ms if reachable, or -1 with error details if unreachable.
     */
    suspend fun ping(host: String, port: Int, timeoutMs: Int = 3000): PingResult = withContext(Dispatchers.IO) {
        if (host.isBlank() || port !in 1..65535) {
            return@withContext PingResult(
                isReachable = false,
                latencyMs = -1,
                errorMessage = "هاست یا پورت نامعتبر است / Invalid host or port"
            )
        }

        var socket: Socket? = null
        try {
            val startTime = System.currentTimeMillis()
            socket = Socket()
            val socketAddress = InetSocketAddress(host, port)
            socket.connect(socketAddress, timeoutMs)
            val elapsed = (System.currentTimeMillis() - startTime).toInt()

            PingResult(
                isReachable = true,
                latencyMs = elapsed.coerceAtLeast(1),
                errorMessage = null
            )
        } catch (e: java.net.SocketTimeoutException) {
            PingResult(
                isReachable = false,
                latencyMs = -1,
                errorMessage = "اتصال منقضی شد (تایم‌اوت در ${timeoutMs}ms) / Connection timed out"
            )
        } catch (e: java.net.ConnectException) {
            PingResult(
                isReachable = false,
                latencyMs = -1,
                errorMessage = "اتصال توسط سرور رد شد / Connection refused"
            )
        } catch (e: java.net.UnknownHostException) {
            PingResult(
                isReachable = false,
                latencyMs = -1,
                errorMessage = "هاست یا دامنه یافت نشد / Unknown host"
            )
        } catch (e: Exception) {
            PingResult(
                isReachable = false,
                latencyMs = -1,
                errorMessage = e.localizedMessage ?: "سرور در دسترس نیست / Server unreachable"
            )
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }
}
