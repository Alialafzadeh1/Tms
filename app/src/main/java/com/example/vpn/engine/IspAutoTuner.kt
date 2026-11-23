package com.example.vpn.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

enum class IspNetworkType(
    val labelFa: String,
    val labelEn: String
) {
    WIFI("وای‌فای / فیبر خانگی", "Wi-Fi / Broadband"),
    MCI("همراه اول (MCI)", "MCI (Hamrah-e-Aval)"),
    IRANCELL("ایرانسل (Irancell)", "MTN Irancell"),
    RIGHTEL("رایتل (Rightel)", "Rightel"),
    MOBILE_OTHER("اینترنت همراه", "Cellular Data"),
    OTHER("شبکه پیش‌فرض", "Default Network")
}

data class FragmentProfile(
    val packets: String = "tlshello",
    val length: String = "10-30",
    val interval: String = "10-20",
    val fingerprint: String = "chrome",
    val profileIndex: Int = 0
)

data class IspTuningReport(
    val networkType: IspNetworkType,
    val operatorLabel: String,
    val transportLabel: String,
    val bestCleanIp: String,
    val bestCleanIpLatencyMs: Long,
    val fragmentProfile: FragmentProfile,
    val probedIpsCount: Int,
    val reachableIpsCount: Int,
    val tunedAtMs: Long = System.currentTimeMillis()
)

/**
 * ISP-Aware Clean IP & TLS Fragment Optimizer.
 *
 * Uses zero sensitive permissions (only ConnectivityManager with already-declared
 * ACCESS_NETWORK_STATE + non-privileged TelephonyManager operator labels) to detect
 * the user's carrier/ISP, benchmark Anycast Clean IPs concurrently via TCP/443,
 * and persist per-ISP tuned Clean IPs & Xray TLS fragment / uTLS fingerprint parameters.
 */
object IspAutoTuner {

    private const val PREFS_NAME = "tms_isp_tuner_prefs"
    private const val KEY_PREFIX_IP = "best_ip_"
    private const val KEY_PREFIX_LEN = "frag_len_"
    private const val KEY_PREFIX_INT = "frag_int_"
    private const val KEY_PREFIX_FP = "frag_fp_"
    private const val KEY_PREFIX_ROTATION = "frag_rot_"

    val FINGERPRINT_ROTATION: List<String> = listOf(
        "chrome",
        "firefox",
        "safari",
        "randomized",
        "edge"
    )

    val CANDIDATE_CLEAN_IPS: List<String> = (
        ConfigAutoHealer.CLEAN_ANYCAST_IPS + listOf(
            "104.16.132.229",
            "104.17.147.22",
            "162.159.135.42",
            "172.67.188.144",
            "188.114.96.3",
            "188.114.97.3"
        )
    ).distinct()

    private val _latestReport = MutableStateFlow<IspTuningReport?>(null)
    val latestReport: StateFlow<IspTuningReport?> = _latestReport.asStateFlow()

    fun classifyNetwork(transport: String, operatorName: String): IspNetworkType {
        val t = transport.trim().lowercase()
        if (t == "wifi" || t == "ethernet") return IspNetworkType.WIFI
        if (t != "cellular") return IspNetworkType.OTHER

        val op = operatorName.trim().lowercase()
        return when {
            op.contains("mci") || op.contains("hamrah") || op.contains("همراه") || op.contains("43211") ->
                IspNetworkType.MCI
            op.contains("irancell") || op.contains("mtn") || op.contains("ایرانسل") || op.contains("43235") ->
                IspNetworkType.IRANCELL
            op.contains("rightel") || op.contains("رایتل") || op.contains("43220") ->
                IspNetworkType.RIGHTEL
            else -> IspNetworkType.MOBILE_OTHER
        }
    }

    fun recommendFragmentForNetwork(networkType: IspNetworkType): FragmentProfile {
        return when (networkType) {
            IspNetworkType.WIFI -> FragmentProfile(
                packets = "tlshello",
                length = "20-50",
                interval = "10-20",
                fingerprint = "safari",
                profileIndex = 0
            )
            IspNetworkType.MCI -> FragmentProfile(
                packets = "tlshello",
                length = "10-20",
                interval = "10-15",
                fingerprint = "chrome",
                profileIndex = 0
            )
            IspNetworkType.IRANCELL -> FragmentProfile(
                packets = "tlshello",
                length = "12-25",
                interval = "10-20",
                fingerprint = "firefox",
                profileIndex = 0
            )
            IspNetworkType.RIGHTEL, IspNetworkType.MOBILE_OTHER -> FragmentProfile(
                packets = "tlshello",
                length = "5-15",
                interval = "5-12",
                fingerprint = "randomized",
                profileIndex = 0
            )
            IspNetworkType.OTHER -> FragmentProfile(
                packets = "tlshello",
                length = "10-30",
                interval = "10-20",
                fingerprint = "chrome",
                profileIndex = 0
            )
        }
    }

    fun detectCurrentNetwork(context: Context): Pair<IspNetworkType, String> {
        var transport = "other"
        try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            transport = when {
                caps == null -> "other"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                else -> "other"
            }
        } catch (_: Exception) {
            transport = "other"
        }

        var operatorLabel = ""
        try {
            val tm = context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            operatorLabel = tm?.networkOperatorName?.takeIf { it.isNotBlank() }
                ?: tm?.simOperatorName?.takeIf { it.isNotBlank() }
                ?: ""
        } catch (_: Exception) {
            operatorLabel = ""
        }

        val networkType = classifyNetwork(transport, operatorLabel)
        val displayOp = operatorLabel.ifBlank { networkType.labelEn }
        return networkType to displayOp
    }

    fun getCachedCleanIp(context: Context, networkType: IspNetworkType? = null): String? {
        val type = networkType ?: detectCurrentNetwork(context).first
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("$KEY_PREFIX_IP${type.name}", null)?.takeIf { it.isNotBlank() }
    }

    fun getActiveFragmentProfile(context: Context): FragmentProfile {
        val (type, _) = detectCurrentNetwork(context)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val recommended = recommendFragmentForNetwork(type)
        val len = prefs.getString("$KEY_PREFIX_LEN${type.name}", null)?.takeIf { it.isNotBlank() } ?: recommended.length
        val interval = prefs.getString("$KEY_PREFIX_INT${type.name}", null)?.takeIf { it.isNotBlank() } ?: recommended.interval
        val fp = prefs.getString("$KEY_PREFIX_FP${type.name}", null)?.takeIf { it.isNotBlank() } ?: recommended.fingerprint
        val rot = prefs.getInt("$KEY_PREFIX_ROTATION${type.name}", 0)
        return FragmentProfile(
            packets = "tlshello",
            length = len,
            interval = interval,
            fingerprint = fp,
            profileIndex = rot
        )
    }

    fun rotateFragmentAndFingerprintForRetry(
        context: Context,
        attemptIndex: Int
    ): FragmentProfile {
        val (type, _) = detectCurrentNetwork(context)
        val base = recommendFragmentForNetwork(type)
        val step = attemptIndex.coerceAtLeast(1)
        val baseFpIdx = FINGERPRINT_ROTATION.indexOf(base.fingerprint).coerceAtLeast(0)
        val nextFp = FINGERPRINT_ROTATION[(baseFpIdx + step) % FINGERPRINT_ROTATION.size]
        val rotatedLengths = listOf("5-12", "8-18", "10-25", "4-10")
        val rotatedIntervals = listOf("5-10", "8-15", "10-18", "4-8")
        val idx = (step - 1) % rotatedLengths.size
        val rotated = FragmentProfile(
            packets = "tlshello",
            length = rotatedLengths[idx],
            interval = rotatedIntervals[idx],
            fingerprint = nextFp,
            profileIndex = step
        )
        persistTuningForNetwork(
            context = context,
            networkType = type,
            cleanIp = getCachedCleanIp(context, type),
            fragmentProfile = rotated
        )
        return rotated
    }

    fun persistTuningForNetwork(
        context: Context,
        networkType: IspNetworkType,
        cleanIp: String?,
        fragmentProfile: FragmentProfile
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (!cleanIp.isNullOrBlank()) {
                putString("$KEY_PREFIX_IP${networkType.name}", cleanIp)
            }
            putString("$KEY_PREFIX_LEN${networkType.name}", fragmentProfile.length)
            putString("$KEY_PREFIX_INT${networkType.name}", fragmentProfile.interval)
            putString("$KEY_PREFIX_FP${networkType.name}", fragmentProfile.fingerprint)
            putInt("$KEY_PREFIX_ROTATION${networkType.name}", fragmentProfile.profileIndex)
        }.apply()
    }

    suspend fun benchmarkAndTuneForCurrentIsp(
        context: Context,
        candidateIps: List<String> = CANDIDATE_CLEAN_IPS,
        timeoutMs: Int = 2000
    ): IspTuningReport = withContext(Dispatchers.IO) {
        val (networkType, operatorLabel) = detectCurrentNetwork(context)
        val recommendedFragment = recommendFragmentForNetwork(networkType)

        val probes = coroutineScope {
            candidateIps.distinct().map { ip ->
                async {
                    val latency = probeTcp443(ip, timeoutMs)
                    ip to latency
                }
            }.awaitAll()
        }

        val reachable = probes.filter { it.second > 0L }.sortedBy { it.second }
        val winner = reachable.firstOrNull()
        val bestIp = winner?.first.orEmpty()
        val bestLatency = winner?.second ?: -1L

        if (bestIp.isNotBlank()) {
            DynamicIpDnsRotator.promoteCleanIp(bestIp)
        }
        persistTuningForNetwork(
            context = context,
            networkType = networkType,
            cleanIp = bestIp.takeIf { it.isNotBlank() },
            fragmentProfile = recommendedFragment
        )

        val report = IspTuningReport(
            networkType = networkType,
            operatorLabel = operatorLabel,
            transportLabel = networkType.labelFa,
            bestCleanIp = bestIp,
            bestCleanIpLatencyMs = bestLatency,
            fragmentProfile = recommendedFragment,
            probedIpsCount = probes.size,
            reachableIpsCount = reachable.size
        )
        _latestReport.value = report
        report
    }

    private fun probeTcp443(ip: String, timeoutMs: Int): Long {
        var socket: Socket? = null
        return try {
            val start = System.currentTimeMillis()
            socket = Socket()
            socket.connect(InetSocketAddress(ip, 443), timeoutMs)
            (System.currentTimeMillis() - start).coerceAtLeast(1L)
        } catch (_: Exception) {
            -1L
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }
}
