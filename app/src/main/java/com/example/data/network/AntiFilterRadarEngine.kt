package com.example.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

data class ServiceTarget(
    val id: String,
    val nameFa: String,
    val nameEn: String,
    val iconKey: String,
    val host: String,
    val port: Int,
    var latencyMs: Int = -1,
    var isAccessible: Boolean = false,
    var statusText: String = "در حال بررسی..."
)

/**
 * Live Censorship Bypass & Anti-Filter Radar.
 * Tests real TCP handshakes to critical filtered platforms (Telegram, Instagram, WhatsApp, YouTube)
 * to verify if the VPN tunnel is successfully unblocking traffic in real-time.
 */
object AntiFilterRadarEngine {

    fun getInitialTargets(): List<ServiceTarget> {
        return listOf(
            ServiceTarget(
                id = "telegram",
                nameFa = "تلگرام (Telegram)",
                nameEn = "Telegram Messenger",
                iconKey = "telegram",
                host = "149.154.167.50",
                port = 443
            ),
            ServiceTarget(
                id = "instagram",
                nameFa = "اینستاگرام (Instagram)",
                nameEn = "Instagram & Threads",
                iconKey = "instagram",
                host = "157.240.241.35",
                port = 443
            ),
            ServiceTarget(
                id = "whatsapp",
                nameFa = "واتساپ (WhatsApp)",
                nameEn = "WhatsApp Voice & Chat",
                iconKey = "whatsapp",
                host = "157.240.241.60",
                port = 443
            ),
            ServiceTarget(
                id = "youtube",
                nameFa = "یوتیوب (YouTube)",
                nameEn = "YouTube Streaming",
                iconKey = "youtube",
                host = "142.250.185.206",
                port = 443
            ),
            ServiceTarget(
                id = "global_web",
                nameFa = "وب آزاد بین‌الملل (Cloudflare)",
                nameEn = "Global Uncensored Web",
                iconKey = "web",
                host = "1.1.1.1",
                port = 443
            )
        )
    }

    suspend fun testTarget(target: ServiceTarget, timeoutMs: Int = 3000): ServiceTarget = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        var reachable = false
        var latency = -1
        var status = "غیرقابل دسترس"

        try {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(target.host, target.port), timeoutMs)
                latency = (System.currentTimeMillis() - start).toInt().coerceAtLeast(1)
                reachable = true
                status = "متصل و بدون فیلتر (${latency}ms)"
            }
        } catch (e: Exception) {
            status = "مسدود توسط فیلترینگ یا قطعی سرور"
        }

        target.copy(
            latencyMs = latency,
            isAccessible = reachable,
            statusText = status
        )
    }
}
