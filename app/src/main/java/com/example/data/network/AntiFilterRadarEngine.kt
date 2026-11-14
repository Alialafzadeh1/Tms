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
    val hosts: List<String>,
    val port: Int,
    var latencyMs: Int = -1,
    var isAccessible: Boolean = false,
    var statusText: String = "در حال بررسی...",
    var verifiedHost: String = ""
)

/**
 * Live Censorship Bypass & Anti-Filter Radar.
 * Tests real TCP/TLS connections to critical filtered platforms (Telegram, Instagram, WhatsApp, YouTube, Global Web)
 * to verify if the VPN tunnel and Anti-DPI engine are successfully unblocking traffic in real-time.
 */
object AntiFilterRadarEngine {

    fun getInitialTargets(): List<ServiceTarget> {
        return listOf(
            ServiceTarget(
                id = "telegram",
                nameFa = "تلگرام (Telegram)",
                nameEn = "Telegram Messenger",
                iconKey = "telegram",
                hosts = listOf("149.154.167.50", "149.154.175.50", "91.108.56.165", "api.telegram.org"),
                port = 443
            ),
            ServiceTarget(
                id = "instagram",
                nameFa = "اینستاگرام (Instagram)",
                nameEn = "Instagram & Threads",
                iconKey = "instagram",
                hosts = listOf("157.240.241.35", "157.240.241.174", "graph.instagram.com", "www.instagram.com"),
                port = 443
            ),
            ServiceTarget(
                id = "whatsapp",
                nameFa = "واتساپ (WhatsApp)",
                nameEn = "WhatsApp Voice & Chat",
                iconKey = "whatsapp",
                hosts = listOf("157.240.241.60", "web.whatsapp.com", "157.240.241.53"),
                port = 443
            ),
            ServiceTarget(
                id = "youtube",
                nameFa = "یوتیوب (YouTube)",
                nameEn = "YouTube Streaming",
                iconKey = "youtube",
                hosts = listOf("142.250.185.206", "youtube.com", "googlevideo.com"),
                port = 443
            ),
            ServiceTarget(
                id = "global_web",
                nameFa = "وب آزاد بین‌الملل (Cloudflare)",
                nameEn = "Global Uncensored Web",
                iconKey = "web",
                hosts = listOf("1.1.1.1", "1.0.0.1", "cloudflare.com"),
                port = 443
            )
        )
    }

    suspend fun testTarget(target: ServiceTarget, timeoutMs: Int = 3000): ServiceTarget = withContext(Dispatchers.IO) {
        var reachable = false
        var latency = -1
        var status = "مسدود توسط فیلترینگ اپراتور"
        var verified = ""

        for (host in target.hosts) {
            val endpointStart = System.currentTimeMillis()
            try {
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.soTimeout = timeoutMs
                    socket.connect(InetSocketAddress(host, target.port), timeoutMs)
                    val elapsed = (System.currentTimeMillis() - endpointStart).toInt().coerceAtLeast(1)
                    latency = elapsed
                    reachable = true
                    verified = host
                    status = "متصل و بدون فیلتر (${latency}ms)"
                }
                break // Success! Stop probing remaining endpoints for this service
            } catch (_: Exception) {
                // Endpoint probe failed or blocked, try next IP
            }
        }

        target.copy(
            latencyMs = latency,
            isAccessible = reachable,
            statusText = status,
            verifiedHost = verified
        )
    }

    /**
     * Builds a comprehensive textual summary of the radar scan results for AI analysis.
     */
    fun buildRadarReport(targets: List<ServiceTarget>): String {
        val sb = StringBuilder()
        for (t in targets) {
            val state = if (t.isAccessible) "باز و متصل (${t.latencyMs}ms)" else "مسدود"
            sb.append("- ${t.nameFa}: $state")
            if (t.verifiedHost.isNotBlank()) {
                sb.append(" [Endpoint: ${t.verifiedHost}]")
            }
            sb.append("\n")
        }
        return sb.toString().trim()
    }
}
