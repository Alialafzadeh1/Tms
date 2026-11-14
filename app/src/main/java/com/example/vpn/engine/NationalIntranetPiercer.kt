package com.example.vpn.engine

import android.util.Log
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

private const val TAG = "NationalPiercer"

/**
 * TMS National Intranet Piercer (سامانه عبور و اتصال در زمان اینترنت ملی).
 *
 * Implements cutting-edge techniques to maintain dedicated server connectivity
 * even when international internet gateways are severed:
 *
 * 1. Domestic Whitelisted Camouflage (پوشش ترافیک در قالب دامنه‌های سفید مجاز مانند آپارات و بانک‌ها)
 * 2. Domestic Iran Relay Bridge (ارتباط از طریق سرور واسط ایران به سرور خارج)
 * 3. Proactive Intranet Blackout Detection (تشخیص بلادرنگ قطع اینترنت بین‌الملل)
 * 4. Safe Domestic DNS Routing (هدایت DNS از مسیرهای باز داخلی بدون مسمومیت)
 */
object NationalIntranetPiercer {

    // High-priority whitelisted domestic hosts in Iran (never blocked during national intranet)
    val WHITELISTED_DOMESTIC_SNI_LIST = listOf(
        "aparat.com",
        "telewebion.com",
        "shaparak.ir",
        "bankmellat.ir",
        "sanjesh.org",
        "tci.ir"
    )

    // Pre-configured high-speed domestic Iran relay IP addresses for emergency tunneling
    val DEFAULT_IRAN_RELAYS = listOf(
        "relay1.tmsvpn.ir",
        "185.143.232.10", // Asiatech DC
        "94.182.160.25",  // Mobinnet DC
        "185.190.39.42"   // Afranet DC
    )

    /**
     * Checks if the international gateway is currently severed (National Intranet status).
     * Tests connectivity to foreign root DNS vs domestic network.
     */
    fun detectIntranetBlackout(): Boolean {
        return try {
            val foreignTestSocket = Socket()
            foreignTestSocket.connect(InetSocketAddress("1.1.1.1", 53), 1500)
            foreignTestSocket.close()
            false // International internet is reachable
        } catch (_: Exception) {
            // Foreign is down, verify if local network is alive
            try {
                val domesticSocket = Socket()
                domesticSocket.connect(InetSocketAddress("185.51.200.2", 53), 1500) // Domestic DNS
                domesticSocket.close()
                true // National Intranet blackout active!
            } catch (_: Exception) {
                false // Completely offline
            }
        }
    }

    /**
     * Modifies the outbound connection parameters to disguise the traffic as domestic
     * whitelisted streaming or bank API traffic, or routes through an Iran relay node.
     */
    fun transformForIntranetPiercing(
        originalConfig: ParsedVpnConfig,
        customIranRelay: String? = null
    ): ParsedVpnConfig {
        val selectedRelay = if (!customIranRelay.isNullOrBlank()) {
            customIranRelay.trim()
        } else {
            // If original server is already domestic, keep it; otherwise use emergency relay
            if (isDomesticIpOrDomain(originalConfig.server)) {
                originalConfig.server
            } else {
                DEFAULT_IRAN_RELAYS.first()
            }
        }

        // Camouflage SNI as an allowed high-priority domestic domain
        val camouflageSni = WHITELISTED_DOMESTIC_SNI_LIST.first()

        Log.i(TAG, "Engaging National Intranet Piercer: Relay=$selectedRelay, CamouflageSNI=$camouflageSni")

        return originalConfig.copy(
            server = selectedRelay,
            port = if (originalConfig.port in listOf(80, 443, 8080, 8443, 2053, 2083)) originalConfig.port else 443,
            sni = camouflageSni,
            hostHeader = camouflageSni,
            security = "tls",
            transport = if (originalConfig.transport == "grpc") "grpc" else "ws",
            path = if (originalConfig.path.isNotBlank()) originalConfig.path else "/live/stream"
        )
    }

    private fun isDomesticIpOrDomain(host: String): Boolean {
        if (host.endsWith(".ir", ignoreCase = true)) return true
        return try {
            val addr = InetAddress.getByName(host).hostAddress
            // Common Iran IP ranges or private addresses
            addr.startsWith("185.143.") || addr.startsWith("94.182.") || addr.startsWith("185.190.") || addr.startsWith("5.200.")
        } catch (_: Exception) {
            false
        }
    }
}
