package com.example.vpn.engine

import android.content.Context
import android.util.Base64
import com.example.data.model.VpnConfig
import com.example.data.network.RealPingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import libXray.LibXray
import org.json.JSONObject
import java.io.File
import java.net.ServerSocket

data class NodeDelayResult(
    val config: VpnConfig,
    val delayMs: Int,
    val usedXrayCorePing: Boolean,
    val error: String? = null
)

data class RacingSummary(
    val winner: VpnConfig?,
    val winnerDelayMs: Int,
    val results: List<NodeDelayResult>,
    val testedCount: Int,
    val winnerCleanIp: String? = null,
    val completedAtMs: Long = System.currentTimeMillis()
)

/**
 * Happy-Eyeballs style Concurrent Racing & Real-Delay Selector.
 *
 * Probes candidate configs in parallel using native LibXray real HTTP-204 ping
 * (through ephemeral local SOCKS5 without interrupting the active VPN), falling
 * back cleanly to RealPingEngine TCP handshake latency on JVM / non-native runtimes.
 * Never fabricates latencies.
 */
object SmartRacingSelector {

    private const val PING_TEST_URL = "https://cp.cloudflare.com/generate_204"

    @Volatile
    private var nativePingSupported: Boolean = true

    suspend fun measureConfigRealDelay(
        context: Context,
        config: VpnConfig,
        timeoutSec: Int = 4,
        cleanIpOverride: String? = null,
        fragmentProfile: FragmentProfile = FragmentProfile(),
        enableDoH: Boolean = false,
        enableFakeDns: Boolean = false,
        alpnOverride: List<String>? = null,
        customPingProbe: (suspend (VpnConfig) -> Int)? = null
    ): NodeDelayResult = withContext(Dispatchers.IO) {
        val effectiveHost = cleanIpOverride?.trim()?.takeIf { it.isNotBlank() } ?: config.server
        if (effectiveHost.isBlank() || config.port !in 1..65535) {
            return@withContext NodeDelayResult(
                config = config,
                delayMs = -1,
                usedXrayCorePing = false,
                error = "آدرس یا پورت نود نامعتبر است"
            )
        }

        if (customPingProbe != null) {
            val probed = customPingProbe(config)
            return@withContext NodeDelayResult(
                config = config,
                delayMs = if (probed > 0) probed else -1,
                usedXrayCorePing = false,
                error = if (probed > 0) null else "پاسخی از نود دریافت نشد"
            )
        }

        // 1. Attempt native LibXray real-delay probe (HTTP 204 over proxy outbound)
        val nativeDelay = tryNativeLibXrayPing(
            context = context,
            config = config,
            timeoutSec = timeoutSec,
            cleanIpOverride = cleanIpOverride,
            fragmentProfile = fragmentProfile,
            enableDoH = enableDoH,
            enableFakeDns = enableFakeDns,
            alpnOverride = alpnOverride
        )
        if (nativeDelay > 0) {
            return@withContext NodeDelayResult(
                config = config,
                delayMs = nativeDelay,
                usedXrayCorePing = true,
                error = null
            )
        }

        // 2. Fallback to direct TCP handshake ping against dialed host
        val tcp = RealPingEngine.ping(
            host = effectiveHost,
            port = config.port,
            timeoutMs = (timeoutSec.coerceIn(1, 10) * 1000)
        )
        NodeDelayResult(
            config = config,
            delayMs = if (tcp.isReachable) tcp.latencyMs else -1,
            usedXrayCorePing = false,
            error = tcp.errorMessage
        )
    }

    suspend fun raceConfigs(
        context: Context,
        configs: List<VpnConfig>,
        maxParallel: Int = 6,
        timeoutSec: Int = 4,
        cleanIpOverride: String? = null,
        customPingProbe: (suspend (VpnConfig) -> Int)? = null
    ): RacingSummary = withContext(Dispatchers.IO) {
        if (configs.isEmpty()) {
            return@withContext RacingSummary(
                winner = null,
                winnerDelayMs = -1,
                results = emptyList(),
                testedCount = 0
            )
        }

        val semaphore = Semaphore(maxParallel.coerceIn(1, 12))
        val results = coroutineScope {
            configs.map { cfg ->
                async {
                    semaphore.withPermit {
                        measureConfigRealDelay(
                            context = context,
                            config = cfg,
                            timeoutSec = timeoutSec,
                            cleanIpOverride = cleanIpOverride,
                            customPingProbe = customPingProbe
                        )
                    }
                }
            }.awaitAll()
        }

        val sorted = results.sortedWith(
            compareBy<NodeDelayResult> { if (it.delayMs > 0) 0 else 1 }
                .thenBy { if (it.delayMs > 0) it.delayMs else Int.MAX_VALUE }
        )
        val best = sorted.firstOrNull { it.delayMs > 0 }
        RacingSummary(
            winner = best?.config,
            winnerDelayMs = best?.delayMs ?: -1,
            results = sorted,
            testedCount = configs.size,
            winnerCleanIp = cleanIpOverride
        )
    }

    /**
     * Prioritizes Germany (DE) & Turkey (TR) nodes for automatic Iranian DPI/censorship bypass.
     * Tests clean Anycast IP overrides if direct DE/TR servers are throttled, and falls back
     * to other nodes if all DE/TR routes fail.
     */
    suspend fun raceAutoBypassDeTr(
        context: Context,
        configs: List<VpnConfig>,
        cleanIpCandidates: List<String> = emptyList(),
        maxParallel: Int = 6,
        timeoutSec: Int = 3,
        customPingProbe: (suspend (VpnConfig) -> Int)? = null
    ): RacingSummary = withContext(Dispatchers.IO) {
        val defaultDeTr = com.example.data.model.DefaultVpnConfigs.getDefaultConfigs()
            .filter { com.example.data.model.DefaultVpnConfigs.isDeOrTrNode(it) }
        val userDeTr = configs.filter { com.example.data.model.DefaultVpnConfigs.isDeOrTrNode(it) }
        val deTrPool = (userDeTr + defaultDeTr).distinctBy { it.id }

        // 1. Race direct DE/TR endpoints first
        val directDeTr = raceConfigs(
            context = context,
            configs = deTrPool,
            maxParallel = maxParallel,
            timeoutSec = timeoutSec,
            customPingProbe = customPingProbe
        )
        if (directDeTr.winner != null) {
            return@withContext directDeTr
        }

        // 2. Retry DE/TR WS+TLS nodes fronted via verified Clean IP candidates
        val wsDeTr = deTrPool.firstOrNull {
            it.transport.equals("ws", ignoreCase = true) || it.transport.equals("xhttp", ignoreCase = true)
        }
        val probeCleanIps = cleanIpCandidates.distinct().take(8)
        if (wsDeTr != null && probeCleanIps.isNotEmpty()) {
            val semaphore = Semaphore(maxParallel.coerceIn(1, 8))
            val ipResults = coroutineScope {
                probeCleanIps.map { ip ->
                    async {
                        semaphore.withPermit {
                            ip to measureConfigRealDelay(
                                context = context,
                                config = wsDeTr,
                                timeoutSec = timeoutSec,
                                cleanIpOverride = ip,
                                customPingProbe = customPingProbe
                            )
                        }
                    }
                }.awaitAll()
            }
            val bestViaClean = ipResults
                .filter { it.second.delayMs > 0 }
                .minByOrNull { it.second.delayMs }
            if (bestViaClean != null) {
                return@withContext RacingSummary(
                    winner = wsDeTr,
                    winnerDelayMs = bestViaClean.second.delayMs,
                    results = listOf(bestViaClean.second),
                    testedCount = deTrPool.size + probeCleanIps.size,
                    winnerCleanIp = bestViaClean.first
                )
            }
        }

        // 3. Fallback to non-DE/TR pool so user still gets working connection if available
        val others = configs.filterNot { com.example.data.model.DefaultVpnConfigs.isDeOrTrNode(it) }
        raceConfigs(
            context = context,
            configs = others.ifEmpty { configs },
            maxParallel = maxParallel,
            timeoutSec = timeoutSec,
            customPingProbe = customPingProbe
        )
    }

    private fun tryNativeLibXrayPing(
        context: Context,
        config: VpnConfig,
        timeoutSec: Int,
        cleanIpOverride: String? = null,
        fragmentProfile: FragmentProfile = FragmentProfile(),
        enableDoH: Boolean = false,
        enableFakeDns: Boolean = false,
        alpnOverride: List<String>? = null
    ): Int {
        if (!nativePingSupported) return -1
        var tempConfigFile: File? = null
        return try {
            if (!XrayCoreEngine.ensureNativeRuntimeInitialized(context)) {
                nativePingSupported = false
                return -1
            }

            val socksPort = allocateFreePort()
            val httpPort = allocateFreePort()
            if (socksPort <= 0 || httpPort <= 0) return -1

            val parsed = ParsedVpnConfig.from(
                rawText = config.rawConfig,
                fallbackHost = config.server,
                fallbackPort = config.port,
                fallbackProto = config.protocol
            )
            val xrayJson = XrayCoreEngine.buildXrayJson(
                parsed = parsed,
                rawConfig = config,
                tunFd = -1,
                enableFragment = true,
                socksPort = socksPort,
                httpPort = httpPort,
                fragmentProfile = fragmentProfile,
                cleanIpOverride = cleanIpOverride,
                enableDoH = enableDoH,
                enableFakeDns = enableFakeDns,
                alpnOverride = alpnOverride
            )

            val datDir = File(context.filesDir, "xray_assets").apply { mkdirs() }.absolutePath
            tempConfigFile = File(context.cacheDir, "xray_ping_${config.id.hashCode()}_${System.nanoTime()}.json")
            tempConfigFile.writeText(xrayJson)

            val requestJson = JSONObject().apply {
                put("datDir", datDir)
                put("configPath", tempConfigFile.absolutePath)
                put("timeout", timeoutSec.coerceIn(2, 10))
                put("url", PING_TEST_URL)
                put("proxy", "socks5://127.0.0.1:$socksPort")
            }.toString()

            val requestB64 = Base64.encodeToString(requestJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val responseB64 = LibXray.ping(requestB64)
            parseLibXrayPingResponse(responseB64)
        } catch (_: UnsatisfiedLinkError) {
            nativePingSupported = false
            -1
        } catch (_: Throwable) {
            -1
        } finally {
            try {
                tempConfigFile?.delete()
            } catch (_: Exception) {}
        }
    }

    internal fun parseLibXrayPingResponse(raw: String?): Int {
        if (raw.isNullOrBlank()) return -1
        return try {
            val trimmed = raw.trim()
            val jsonStr = if (trimmed.startsWith("{")) {
                trimmed
            } else {
                String(Base64.decode(trimmed, Base64.DEFAULT), Charsets.UTF_8)
            }
            val obj = JSONObject(jsonStr)
            if (obj.optBoolean("success", false)) {
                val delay = obj.optInt("data", -1)
                if (delay > 0) delay else -1
            } else {
                -1
            }
        } catch (_: Exception) {
            -1
        }
    }

    private fun allocateFreePort(): Int {
        return try {
            ServerSocket(0).use { it.localPort }
        } catch (_: Exception) {
            -1
        }
    }
}
