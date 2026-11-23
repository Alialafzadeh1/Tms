package com.example.vpn.engine

import android.content.Context
import android.util.Log
import com.example.data.model.DefaultVpnConfigs
import com.example.data.model.VpnConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

private const val TAG = "AntiDpiComboEngine"

enum class AntiDpiFailureReason(val labelFa: String, val labelEn: String) {
    NONE("سالم", "Healthy"),
    DPI_RESET("ریست DPI روی TLS/SNI", "DPI TLS Reset"),
    TIMEOUT("اتمام زمان / مسیر کند", "Timeout / Slow Route"),
    UNREACHABLE("مسیر غیرقابل دسترس", "Unreachable")
}

data class AntiDpiComboPreset(
    val index: Int,
    val id: String,
    val titleFa: String,
    val titleEn: String,
    val fragmentPackets: String,
    val fragmentLength: String,
    val fragmentInterval: String,
    val fingerprint: String,
    val alpn: List<String>,
    val enableDoH: Boolean = true,
    val enableFakeDns: Boolean = true
) {
    fun toFragmentProfile(): FragmentProfile = FragmentProfile(
        packets = fragmentPackets,
        length = fragmentLength,
        interval = fragmentInterval,
        fingerprint = fingerprint,
        profileIndex = index + 1
    )

    fun shortSummary(isPersian: Boolean): String {
        val dnsPart = if (enableFakeDns && enableDoH) "DoH+FakeDNS" else if (enableDoH) "DoH" else "DNS"
        val fragPart = "Frag $fragmentLength/$fragmentInterval"
        return if (isPersian) {
            "$dnsPart • $fragPart • uTLS:${fingerprint}"
        } else {
            "$dnsPart • $fragPart • uTLS:${fingerprint}"
        }
    }
}

data class TlsHandshakeProbeResult(
    val host: String,
    val isReachable: Boolean,
    val latencyMs: Int,
    val failureReason: AntiDpiFailureReason,
    val detail: String = ""
)

data class ComboMemoryEntry(
    val networkType: String,
    val configId: String,
    val cleanIp: String,
    val presetIndex: Int,
    val score: Int,
    val lastLatencyMs: Int,
    val updatedAtMs: Long
)

data class ComboBypassSummary(
    val winnerConfig: VpnConfig?,
    val winnerCleanIp: String?,
    val preset: AntiDpiComboPreset,
    val delayMs: Int,
    val usedMemoryHit: Boolean,
    val testedCombosCount: Int,
    val lastFailureReason: AntiDpiFailureReason = AntiDpiFailureReason.NONE
)

/**
 * Unified 4-Layer Anti-DPI Combo Engine (Subscriber-Exclusive, Toggleable Anytime):
 * Combines Encrypted DoH + FakeDNS + Verified Clean IP + TLS Fragment/uTLS/ALPN + ISP Winning Combo Memory
 * with smart DPI_RESET vs TIMEOUT failure classification.
 */
object AntiDpiComboEngine {

    private const val PREFS_NAME = "tms_anti_dpi_combo_prefs"
    private const val KEY_COMBO_ENABLED = "combo_shield_enabled"
    private const val KEY_ACTIVE_PRESET = "combo_active_preset"
    private const val KEY_MEMORY_LIST = "combo_isp_memory_json"
    private const val MAX_MEMORY_ENTRIES = 24

    val PRESETS: List<AntiDpiComboPreset> = listOf(
        AntiDpiComboPreset(
            index = 0,
            id = "balanced_chrome",
            titleFa = "توازن هوشمند Chrome + DoH",
            titleEn = "Balanced Chrome + DoH",
            fragmentPackets = "tlshello",
            fragmentLength = "10-22",
            fragmentInterval = "8-16",
            fingerprint = "chrome",
            alpn = listOf("h2", "http/1.1")
        ),
        AntiDpiComboPreset(
            index = 1,
            id = "micro_split_safari",
            titleFa = "ضد-RST ریز Safari + http/1.1",
            titleEn = "Micro-Split Safari (Anti-RST)",
            fragmentPackets = "tlshello",
            fragmentLength = "2-6",
            fragmentInterval = "4-9",
            fingerprint = "safari",
            alpn = listOf("http/1.1")
        ),
        AntiDpiComboPreset(
            index = 2,
            id = "deep_record_firefox",
            titleFa = "شکست عمیق بسته‌ها Firefox",
            titleEn = "Deep Record Split Firefox",
            fragmentPackets = "1-3",
            fragmentLength = "5-15",
            fragmentInterval = "5-12",
            fingerprint = "firefox",
            alpn = listOf("h2", "http/1.1")
        ),
        AntiDpiComboPreset(
            index = 3,
            id = "randomized_edge",
            titleFa = "الگوی تصادفی شدید Randomized",
            titleEn = "Aggressive Randomized Burst",
            fragmentPackets = "tlshello",
            fragmentLength = "4-10",
            fragmentInterval = "3-8",
            fingerprint = "randomized",
            alpn = listOf("h2")
        )
    )

    fun isComboShieldEnabled(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_COMBO_ENABLED, false)
    }

    fun setComboShieldEnabled(context: Context, enabled: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_COMBO_ENABLED, enabled).apply()
    }

    fun getActivePresetIndex(context: Context): Int {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_ACTIVE_PRESET, 0).coerceIn(0, PRESETS.lastIndex)
    }

    fun setActivePresetIndex(context: Context, index: Int): AntiDpiComboPreset {
        val safe = ((index % PRESETS.size) + PRESETS.size) % PRESETS.size
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_ACTIVE_PRESET, safe).apply()
        return PRESETS[safe]
    }

    fun getActivePreset(context: Context): AntiDpiComboPreset {
        return PRESETS[getActivePresetIndex(context)]
    }

    fun recommendPresetForNetwork(
        networkType: IspNetworkType,
        attemptIndex: Int = 0,
        lastFailure: AntiDpiFailureReason = AntiDpiFailureReason.NONE
    ): AntiDpiComboPreset {
        val baseIdx = when (networkType) {
            IspNetworkType.MCI -> 0
            IspNetworkType.IRANCELL -> 2
            IspNetworkType.RIGHTEL, IspNetworkType.MOBILE_OTHER -> 1
            IspNetworkType.WIFI, IspNetworkType.OTHER -> 0
        }
        val offset = when {
            lastFailure == AntiDpiFailureReason.DPI_RESET -> maxOf(1, attemptIndex)
            else -> attemptIndex
        }
        return PRESETS[(baseIdx + offset) % PRESETS.size]
    }

    fun classifyThrowable(t: Throwable): AntiDpiFailureReason {
        val msg = t.message.orEmpty().lowercase()
        return when {
            t is SocketTimeoutException || msg.contains("timed out") || msg.contains("timeout") ->
                AntiDpiFailureReason.TIMEOUT
            t is SSLHandshakeException || t is EOFException || t is SocketException ||
                msg.contains("reset") || msg.contains("rst") || msg.contains("eof") ||
                msg.contains("broken pipe") || msg.contains("connection aborted") ->
                AntiDpiFailureReason.DPI_RESET
            else -> AntiDpiFailureReason.UNREACHABLE
        }
    }

    /**
     * Performs a real TCP + TLS ClientHello/ServerHello handshake against [host]:[port]
     * using [sni] so DPI resets during TLS negotiation are caught instead of masked by TCP ping.
     */
    fun verifyTlsHandshake(
        host: String,
        sni: String,
        port: Int = 443,
        timeoutMs: Int = 2000
    ): TlsHandshakeProbeResult {
        val start = System.currentTimeMillis()
        var rawSocket: Socket? = null
        var sslSocket: SSLSocket? = null
        return try {
            rawSocket = Socket()
            rawSocket.connect(InetSocketAddress(host, port), timeoutMs)
            rawSocket.soTimeout = timeoutMs

            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            sslSocket = factory.createSocket(rawSocket, sni.ifBlank { host }, port, true) as SSLSocket
            val sslParams = SSLParameters().apply {
                if (sni.isNotBlank() && !sni.all { it.isDigit() || it == '.' || it == ':' }) {
                    serverNames = listOf(SNIHostName(sni))
                }
            }
            sslSocket.sslParameters = sslParams
            sslSocket.startHandshake()

            val elapsed = (System.currentTimeMillis() - start).toInt().coerceAtLeast(1)
            TlsHandshakeProbeResult(
                host = host,
                isReachable = true,
                latencyMs = elapsed,
                failureReason = AntiDpiFailureReason.NONE,
                detail = "TLS OK"
            )
        } catch (t: Throwable) {
            TlsHandshakeProbeResult(
                host = host,
                isReachable = false,
                latencyMs = -1,
                failureReason = classifyThrowable(t),
                detail = t.message.orEmpty()
            )
        } finally {
            try { sslSocket?.close() } catch (_: Exception) {}
            try { rawSocket?.close() } catch (_: Exception) {}
        }
    }

    // --- ISP WINNING COMBO MEMORY ---

    fun getMemoryEntries(context: Context, networkType: IspNetworkType? = null): List<ComboMemoryEntry> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_MEMORY_LIST, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = ArrayList<ComboMemoryEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val entry = ComboMemoryEntry(
                    networkType = obj.optString("networkType"),
                    configId = obj.optString("configId"),
                    cleanIp = obj.optString("cleanIp"),
                    presetIndex = obj.optInt("presetIndex", 0).coerceIn(0, PRESETS.lastIndex),
                    score = obj.optInt("score", 1),
                    lastLatencyMs = obj.optInt("lastLatencyMs", -1),
                    updatedAtMs = obj.optLong("updatedAtMs", 0L)
                )
                if (networkType == null || entry.networkType == networkType.name) {
                    list.add(entry)
                }
            }
            list.sortedWith(
                compareByDescending<ComboMemoryEntry> { it.score }
                    .thenBy { if (it.lastLatencyMs > 0) it.lastLatencyMs else Int.MAX_VALUE }
            )
        }.getOrDefault(emptyList())
    }

    fun recordWinningCombo(
        context: Context,
        networkType: IspNetworkType,
        configId: String,
        cleanIp: String?,
        presetIndex: Int,
        latencyMs: Int,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val ipKey = cleanIp.orEmpty().trim()
        val safePreset = presetIndex.coerceIn(0, PRESETS.lastIndex)
        val existing = getMemoryEntries(context, null).toMutableList()
        val idx = existing.indexOfFirst {
            it.networkType == networkType.name &&
                it.configId == configId &&
                it.cleanIp == ipKey &&
                it.presetIndex == safePreset
        }
        val updatedEntry = if (idx >= 0) {
            val prev = existing[idx]
            prev.copy(
                score = (prev.score + 2).coerceAtMost(100),
                lastLatencyMs = if (latencyMs > 0) latencyMs else prev.lastLatencyMs,
                updatedAtMs = nowMs
            )
        } else {
            ComboMemoryEntry(
                networkType = networkType.name,
                configId = configId,
                cleanIp = ipKey,
                presetIndex = safePreset,
                score = 3,
                lastLatencyMs = latencyMs,
                updatedAtMs = nowMs
            )
        }
        if (idx >= 0) existing[idx] = updatedEntry else existing.add(updatedEntry)
        saveMemoryEntries(context, existing)
    }

    fun penalizeCombo(
        context: Context,
        networkType: IspNetworkType,
        configId: String,
        cleanIp: String?,
        presetIndex: Int
    ) {
        val ipKey = cleanIp.orEmpty().trim()
        val existing = getMemoryEntries(context, null).toMutableList()
        val idx = existing.indexOfFirst {
            it.networkType == networkType.name &&
                it.configId == configId &&
                it.cleanIp == ipKey &&
                it.presetIndex == presetIndex
        }
        if (idx >= 0) {
            val nextScore = existing[idx].score - 2
            if (nextScore <= 0) {
                existing.removeAt(idx)
            } else {
                existing[idx] = existing[idx].copy(score = nextScore)
            }
            saveMemoryEntries(context, existing)
        }
    }

    private fun saveMemoryEntries(context: Context, entries: List<ComboMemoryEntry>) {
        val trimmed = entries
            .sortedWith(
                compareByDescending<ComboMemoryEntry> { it.score }
                    .thenByDescending { it.updatedAtMs }
            )
            .take(MAX_MEMORY_ENTRIES)
        val arr = JSONArray()
        trimmed.forEach { item ->
            arr.put(JSONObject().apply {
                put("networkType", item.networkType)
                put("configId", item.configId)
                put("cleanIp", item.cleanIp)
                put("presetIndex", item.presetIndex)
                put("score", item.score)
                put("lastLatencyMs", item.lastLatencyMs)
                put("updatedAtMs", item.updatedAtMs)
            })
        }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MEMORY_LIST, arr.toString()).apply()
    }

    /**
     * Races unified 4-layer Combos (ISP Memory hit first -> DE/TR nodes x Clean IPs x Fragment/uTLS/DoH/FakeDNS presets),
     * distinguishes DPI_RESET vs TIMEOUT, adapts preset/Clean IP, and persists the winning combo.
     */
    suspend fun runSmartComboBypass(
        context: Context,
        configs: List<VpnConfig>,
        cleanIpCandidates: List<String>,
        forceNextPreset: Boolean = false,
        customComboProbe: (suspend (VpnConfig, String?, AntiDpiComboPreset) -> TlsHandshakeProbeResult)? = null
    ): ComboBypassSummary = withContext(Dispatchers.IO) {
        val (networkType, _) = IspAutoTuner.detectCurrentNetwork(context)
        val defaultDeTr = DefaultVpnConfigs.getDefaultConfigs().filter { DefaultVpnConfigs.isDeOrTrNode(it) }
        val userDeTr = configs.filter { DefaultVpnConfigs.isDeOrTrNode(it) }
        val nodePool = (userDeTr + defaultDeTr + configs).distinctBy { it.id }.take(8)

        val currentIdx = getActivePresetIndex(context)
        var testedCount = 0
        var lastFailure = AntiDpiFailureReason.NONE

        // Step 1: Fast-path ISP Combo Memory check (unless user explicitly requested Re-Roll)
        if (!forceNextPreset) {
            val remembered = getMemoryEntries(context, networkType).firstOrNull()
            if (remembered != null) {
                val memNode = nodePool.firstOrNull { it.id == remembered.configId }
                if (memNode != null) {
                    val memPreset = PRESETS[remembered.presetIndex]
                    val memIp = remembered.cleanIp.takeIf { it.isNotBlank() }
                    testedCount++
                    val probe = probeComboCandidate(
                        context = context,
                        config = memNode,
                        cleanIp = memIp,
                        preset = memPreset,
                        customComboProbe = customComboProbe
                    )
                    if (probe.isReachable && probe.latencyMs > 0) {
                        setActivePresetIndex(context, memPreset.index)
                        recordWinningCombo(context, networkType, memNode.id, memIp, memPreset.index, probe.latencyMs)
                        return@withContext ComboBypassSummary(
                            winnerConfig = memNode,
                            winnerCleanIp = memIp,
                            preset = memPreset,
                            delayMs = probe.latencyMs,
                            usedMemoryHit = true,
                            testedCombosCount = testedCount
                        )
                    } else {
                        lastFailure = probe.failureReason
                        penalizeCombo(context, networkType, memNode.id, memIp, memPreset.index)
                    }
                }
            }
        }

        // Step 2: Adaptive preset ordering based on DPI_RESET vs TIMEOUT and ISP profile
        val startPreset = recommendPresetForNetwork(
            networkType = networkType,
            attemptIndex = if (forceNextPreset) currentIdx + 1 else 0,
            lastFailure = lastFailure
        )
        val orderedPresets = listOf(
            startPreset,
            PRESETS[(startPreset.index + 1) % PRESETS.size],
            PRESETS[(startPreset.index + 2) % PRESETS.size]
        ).distinctBy { it.index }

        val topIps = cleanIpCandidates.distinct().take(6)
        val candidateIpsForNodes: List<String?> = if (topIps.isEmpty()) listOf(null) else (listOf(null) + topIps)

        // Build balanced matrix of (Config, CleanIP?, Preset) pairs prioritizing DE/TR WS+TLS & Reality
        val tasks = ArrayList<Triple<VpnConfig, String?, AntiDpiComboPreset>>()
        val wsNode = nodePool.firstOrNull {
            it.transport.equals("ws", ignoreCase = true) || it.transport.equals("xhttp", ignoreCase = true)
        } ?: nodePool.firstOrNull()

        if (lastFailure == AntiDpiFailureReason.DPI_RESET) {
            // DPI reset SNI/TLS -> try stronger Fragment + uTLS presets first across top Clean IPs
            for (preset in orderedPresets) {
                for (ip in candidateIpsForNodes.take(4)) {
                    val targetNode = if (ip == null) nodePool.firstOrNull() else wsNode
                    if (targetNode != null) tasks.add(Triple(targetNode, ip, preset))
                }
            }
        } else {
            // Timeout or fresh run -> race top nodes & Clean IPs across starting preset, then fallback presets
            for (node in nodePool.take(4)) {
                tasks.add(Triple(node, null, orderedPresets.first()))
            }
            if (wsNode != null) {
                for (ip in topIps.take(5)) {
                    tasks.add(Triple(wsNode, ip, orderedPresets.first()))
                }
                for (preset in orderedPresets.drop(1)) {
                    for (ip in topIps.take(3)) {
                        tasks.add(Triple(wsNode, ip, preset))
                    }
                }
            }
        }

        val distinctTasks = tasks.distinctBy { Triple(it.first.id, it.second.orEmpty(), it.third.index) }.take(14)
        val semaphore = Semaphore(6)
        val results = coroutineScope {
            distinctTasks.map { (cfg, ip, preset) ->
                async {
                    semaphore.withPermit {
                        val res = probeComboCandidate(context, cfg, ip, preset, customComboProbe)
                        Triple(cfg, ip, preset) to res
                    }
                }
            }.awaitAll()
        }
        testedCount += results.size

        val best = results
            .filter { it.second.isReachable && it.second.latencyMs > 0 }
            .minByOrNull { it.second.latencyMs }

        if (best != null) {
            val (combo, probeRes) = best
            val (winCfg, winIp, winPreset) = combo
            setActivePresetIndex(context, winPreset.index)
            recordWinningCombo(
                context = context,
                networkType = networkType,
                configId = winCfg.id,
                cleanIp = winIp,
                presetIndex = winPreset.index,
                latencyMs = probeRes.latencyMs
            )
            IspAutoTuner.persistTuningForNetwork(
                context = context,
                networkType = networkType,
                cleanIp = winIp,
                fragmentProfile = winPreset.toFragmentProfile()
            )
            Log.i(TAG, "Combo bypass won: node=${winCfg.name}, ip=${winIp ?: "direct"}, preset=${winPreset.id}, ${probeRes.latencyMs}ms")
            ComboBypassSummary(
                winnerConfig = winCfg,
                winnerCleanIp = winIp,
                preset = winPreset,
                delayMs = probeRes.latencyMs,
                usedMemoryHit = false,
                testedCombosCount = testedCount
            )
        } else {
            val detectedFailure = results.firstOrNull { it.second.failureReason != AntiDpiFailureReason.NONE }
                ?.second?.failureReason ?: lastFailure
            ComboBypassSummary(
                winnerConfig = null,
                winnerCleanIp = null,
                preset = orderedPresets.first(),
                delayMs = -1,
                usedMemoryHit = false,
                testedCombosCount = testedCount,
                lastFailureReason = detectedFailure
            )
        }
    }

    private suspend fun probeComboCandidate(
        context: Context,
        config: VpnConfig,
        cleanIp: String?,
        preset: AntiDpiComboPreset,
        customComboProbe: (suspend (VpnConfig, String?, AntiDpiComboPreset) -> TlsHandshakeProbeResult)?
    ): TlsHandshakeProbeResult {
        if (customComboProbe != null) {
            return customComboProbe(config, cleanIp, preset)
        }
        val delayResult = SmartRacingSelector.measureConfigRealDelay(
            context = context,
            config = config,
            timeoutSec = 3,
            cleanIpOverride = cleanIp,
            fragmentProfile = preset.toFragmentProfile(),
            enableDoH = preset.enableDoH,
            enableFakeDns = preset.enableFakeDns,
            alpnOverride = preset.alpn
        )
        if (delayResult.usedXrayCorePing && delayResult.delayMs > 0) {
            return TlsHandshakeProbeResult(
                host = cleanIp ?: config.server,
                isReachable = true,
                latencyMs = delayResult.delayMs,
                failureReason = AntiDpiFailureReason.NONE,
                detail = "Xray 204 OK"
            )
        }
        val parsed = ParsedVpnConfig.from(config.rawConfig, config.server, config.port, config.protocol)
        val dialHost = cleanIp?.takeIf { it.isNotBlank() } ?: parsed.server
        val sni = parsed.sni.ifBlank { parsed.server }
        if (parsed.security.equals("tls", ignoreCase = true) || parsed.security.equals("reality", ignoreCase = true)) {
            return verifyTlsHandshake(host = dialHost, sni = sni, port = parsed.port)
        }
        return if (delayResult.delayMs > 0) {
            TlsHandshakeProbeResult(
                host = dialHost,
                isReachable = true,
                latencyMs = delayResult.delayMs,
                failureReason = AntiDpiFailureReason.NONE
            )
        } else {
            TlsHandshakeProbeResult(
                host = dialHost,
                isReachable = false,
                latencyMs = -1,
                failureReason = AntiDpiFailureReason.TIMEOUT,
                detail = delayResult.error.orEmpty()
            )
        }
    }
}
