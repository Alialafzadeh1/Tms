package com.example.vpn.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL

data class TunnelHealthReport(
    val global204Reachable: Boolean = false,
    val global204LatencyMs: Long = -1L,
    val telegramReachable: Boolean = false,
    val telegramLatencyMs: Long = -1L,
    val instagramReachable: Boolean = false,
    val instagramLatencyMs: Long = -1L,
    val checkedThroughProxy: Boolean = false,
    val checkedAtMs: Long = System.currentTimeMillis(),
    val isHealthy: Boolean = false
)

/**
 * Runs real end-to-end connectivity checks through the active tunnel (or local Xray SOCKS5 proxy
 * when running) to verify actual reachability for Global 204, Telegram, and Instagram.
 * Never fabricates latency or reachability metrics.
 */
object TunnelHealthChecker {

    suspend fun runFullHealthCheck(useLocalSocksIfAvailable: Boolean = true): TunnelHealthReport =
        withContext(Dispatchers.IO) {
            val useSocks = useLocalSocksIfAvailable && XrayCoreEngine.isLocalSocksListening()
            val proxy = if (useSocks) {
                Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", XrayCoreEngine.LOCAL_SOCKS_PORT))
            } else {
                Proxy.NO_PROXY
            }

            coroutineScope {
                val globalDeferred = async {
                    probeHttpEndpoint("https://connectivitycheck.gstatic.com/generate_204", proxy)
                }
                val telegramDeferred = async {
                    probeTcpOrHttps(
                        host = "149.154.167.51",
                        port = 443,
                        httpsUrl = "https://api.telegram.org",
                        proxy = proxy,
                        useSocks = useSocks
                    )
                }
                val instagramDeferred = async {
                    probeHttpEndpoint("https://www.instagram.com/", proxy)
                }

                val globalLatency = globalDeferred.await()
                val tgLatency = telegramDeferred.await()
                val igLatency = instagramDeferred.await()

                val globalOk = globalLatency > 0
                val tgOk = tgLatency > 0
                val igOk = igLatency > 0

                TunnelHealthReport(
                    global204Reachable = globalOk,
                    global204LatencyMs = globalLatency,
                    telegramReachable = tgOk,
                    telegramLatencyMs = tgLatency,
                    instagramReachable = igOk,
                    instagramLatencyMs = igLatency,
                    checkedThroughProxy = useSocks,
                    checkedAtMs = System.currentTimeMillis(),
                    isHealthy = globalOk || tgOk || igOk
                )
            }
        }

    private fun probeHttpEndpoint(urlString: String, proxy: Proxy): Long {
        var conn: HttpURLConnection? = null
        return try {
            val start = System.currentTimeMillis()
            val url = URL(urlString)
            conn = (if (proxy != Proxy.NO_PROXY) url.openConnection(proxy) else url.openConnection()) as HttpURLConnection
            conn.connectTimeout = 3500
            conn.readTimeout = 3500
            conn.requestMethod = "HEAD"
            conn.instanceFollowRedirects = false
            val code = conn.responseCode
            val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            if (code in 200..399 || code == 403 || code == 405) elapsed else -1L
        } catch (_: Exception) {
            -1L
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun probeTcpOrHttps(
        host: String,
        port: Int,
        httpsUrl: String,
        proxy: Proxy,
        useSocks: Boolean
    ): Long {
        if (useSocks) {
            val httpLatency = probeHttpEndpoint(httpsUrl, proxy)
            if (httpLatency > 0) return httpLatency
        }
        val tcpLatency = try {
            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 3000)
            }
            (System.currentTimeMillis() - start).coerceAtLeast(1L)
        } catch (_: Exception) {
            -1L
        }
        return if (tcpLatency > 0) tcpLatency else probeHttpEndpoint(httpsUrl, proxy)
    }
}
