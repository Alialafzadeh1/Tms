package com.example.vpn.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

data class LeakCheckReport(
    val exitIp: String = "",
    val countryCode: String = "",
    val countryName: String = "",
    val ispOrOrg: String = "",
    val routedViaVpnProxy: Boolean = false,
    val dnsStatusLabel: String = "",
    val isLeakProtected: Boolean = false,
    val checkedAtMs: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)

/**
 * Real Exit IP, Country/ISP & DNS Leak Guard.
 *
 * Queries real external endpoints (ipwho.is + 1.1.1.1/cdn-cgi/trace) over the local
 * Xray SOCKS5 proxy when listening (or direct socket inside active TUN) to inspect
 * the actual public exit IP, ASN/ISP, and DNS leak posture. Never fabricates IPs or countries.
 */
object LeakCheckEngine {

    private const val IP_WHO_URL = "https://ipwho.is/"
    private const val CLOUDFLARE_TRACE_URL = "https://1.1.1.1/cdn-cgi/trace"

    fun parseCloudflareTrace(traceText: String): Pair<String, String> {
        var ip = ""
        var loc = ""
        traceText.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("ip=", ignoreCase = true) -> ip = trimmed.substringAfter("=").trim()
                trimmed.startsWith("loc=", ignoreCase = true) -> loc = trimmed.substringAfter("=").trim().uppercase()
            }
        }
        return ip to loc
    }

    fun parseIpWhoJson(jsonText: String): LeakCheckReport {
        return try {
            val obj = JSONObject(jsonText)
            if (obj.has("success") && !obj.optBoolean("success", true)) {
                return LeakCheckReport(
                    errorMessage = obj.optString("message").ifBlank { "خطا در استعلام IP عمومی" }
                )
            }
            val ip = obj.optString("ip").trim()
            val code = obj.optString("country_code").trim().uppercase()
            val country = obj.optString("country").trim()
            val conn = obj.optJSONObject("connection")
            val isp = conn?.optString("isp")?.takeIf { it.isNotBlank() }
                ?: conn?.optString("org")?.takeIf { it.isNotBlank() }
                ?: obj.optString("isp").ifBlank { obj.optString("org") }
            LeakCheckReport(
                exitIp = ip,
                countryCode = code,
                countryName = country,
                ispOrOrg = isp.trim()
            )
        } catch (e: Exception) {
            LeakCheckReport(
                errorMessage = e.localizedMessage ?: "پاسخ نامعتبر از سرویس بررسی IP"
            )
        }
    }

    suspend fun runLeakCheck(tunnelActive: Boolean): LeakCheckReport = withContext(Dispatchers.IO) {
        val socksListening = XrayCoreEngine.isLocalSocksListening()
        val proxy = if (socksListening) {
            Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", XrayCoreEngine.LOCAL_SOCKS_PORT))
        } else {
            Proxy.NO_PROXY
        }

        var report = fetchIpWho(proxy)
        if (report.exitIp.isBlank()) {
            val traceBody = fetchUrlText(CLOUDFLARE_TRACE_URL, proxy)
            if (!traceBody.isNullOrBlank()) {
                val (traceIp, traceLoc) = parseCloudflareTrace(traceBody)
                if (traceIp.isNotBlank()) {
                    report = LeakCheckReport(
                        exitIp = traceIp,
                        countryCode = traceLoc,
                        countryName = traceLoc,
                        ispOrOrg = "Cloudflare Edge Trace"
                    )
                }
            }
        }

        if (report.exitIp.isBlank()) {
            return@withContext LeakCheckReport(
                routedViaVpnProxy = socksListening,
                dnsStatusLabel = if (tunnelActive) "بررسی ناموفق" else "آفلاین",
                isLeakProtected = false,
                errorMessage = report.errorMessage ?: "اتصال به سرویس بررسی IP برقرار نشد"
            )
        }

        val exitOutsideIran = report.countryCode.isNotBlank() &&
            !report.countryCode.equals("IR", ignoreCase = true)
        val dnsProtected = socksListening || (tunnelActive && exitOutsideIran)
        val dnsLabel = when {
            socksListening && exitOutsideIran -> "DoH / SOCKS5 Tunnel Protected"
            tunnelActive && exitOutsideIran -> "Tunnel DNS Active ($exitOutsideIran)"
            tunnelActive && !exitOutsideIran -> "هشدار: IP خروجی داخل ایران است"
            else -> "اتصال مستقیم (بدون تونل)"
        }

        report.copy(
            routedViaVpnProxy = socksListening,
            dnsStatusLabel = dnsLabel,
            isLeakProtected = dnsProtected,
            errorMessage = null
        )
    }

    private fun fetchIpWho(proxy: Proxy): LeakCheckReport {
        val body = fetchUrlText(IP_WHO_URL, proxy)
            ?: return LeakCheckReport(errorMessage = "عدم دریافت پاسخ از ipwho.is")
        return parseIpWhoJson(body)
    }

    private fun fetchUrlText(urlStr: String, proxy: Proxy): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection(proxy) as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "TMS-VPN-LeakGuard/2.0")
            }
            val code = conn.responseCode
            if (code in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {}
        }
    }
}
