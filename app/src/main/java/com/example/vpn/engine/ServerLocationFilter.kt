package com.example.vpn.engine

import com.example.data.model.VpnConfig
import com.example.data.model.VpnServer
import com.example.ui.components.inferCountryMeta

/**
 * Latency filter tiers for filtering VPN server locations.
 */
enum class LatencyFilterTier(
    val key: String,
    val maxLatencyMs: Int?,
    val labelEn: String,
    val labelFa: String
) {
    ALL("ALL", null, "All Pings", "همه پینگ‌ها"),
    ULTRA_FAST("FAST_80", 80, "< 80ms Ultra", "< 80ms عالی"),
    FAST("GOOD_150", 150, "< 150ms Fast", "< 150ms سریع"),
    MODERATE("MOD_300", 300, "< 300ms Fair", "< 300ms متوسط"),
    REACHABLE_ONLY("ONLINE", Int.MAX_VALUE, "Online Only", "فقط آنلاین")
}

/**
 * Country/location summary chip entry derived dynamically from available configs and servers.
 */
data class CountryLocationGroup(
    val code: String,
    val flag: String,
    val name: String,
    val count: Int,
    val bestLatencyMs: Int
)

/**
 * Pure filtering, ranking, and latency-tier evaluation helpers for VPN server locations.
 */
object ServerLocationFilterEngine {

    /**
     * Resolves the freshest known latency for a config:
     * prefers background coroutine live measurements in [liveLatencies] when present,
     * falling back to [VpnConfig.pingMs].
     */
    fun effectiveLatencyMs(config: VpnConfig, liveLatencies: Map<String, Int>): Int {
        return liveLatencies[config.id] ?: config.pingMs
    }

    /**
     * Resolves the effective online/offline/untested status using [liveLatencies] when available.
     */
    fun effectiveStatus(config: VpnConfig, liveLatencies: Map<String, Int>): String {
        val live = liveLatencies[config.id]
        return when {
            live != null && live > 0 -> "online"
            live != null && live <= 0 -> "offline"
            else -> config.status
        }
    }

    fun matchesLatencyTier(
        latencyMs: Int,
        status: String,
        tier: LatencyFilterTier
    ): Boolean {
        return when (tier) {
            LatencyFilterTier.ALL -> true
            LatencyFilterTier.REACHABLE_ONLY -> latencyMs > 0 && status != "offline"
            else -> {
                val ceiling = tier.maxLatencyMs ?: Int.MAX_VALUE
                latencyMs in 1..ceiling && status != "offline"
            }
        }
    }

    fun countByLatencyTier(
        configs: List<VpnConfig>,
        liveLatencies: Map<String, Int>
    ): Map<LatencyFilterTier, Int> {
        return LatencyFilterTier.entries.associateWith { tier ->
            configs.count { cfg ->
                matchesLatencyTier(
                    latencyMs = effectiveLatencyMs(cfg, liveLatencies),
                    status = effectiveStatus(cfg, liveLatencies),
                    tier = tier
                )
            }
        }
    }

    fun extractCountryLocations(
        configs: List<VpnConfig>,
        servers: List<VpnServer>,
        liveLatencies: Map<String, Int>
    ): List<CountryLocationGroup> {
        if (configs.isEmpty()) return emptyList()
        return configs
            .groupBy { cfg -> inferCountryMeta(cfg, servers) }
            .map { (meta, items) ->
                val (flag, code, countryName) = meta
                val bestPing = items
                    .map { effectiveLatencyMs(it, liveLatencies) }
                    .filter { it > 0 }
                    .minOrNull() ?: -1
                CountryLocationGroup(
                    code = code,
                    flag = flag,
                    name = countryName,
                    count = items.size,
                    bestLatencyMs = bestPing
                )
            }
            .sortedWith(
                compareBy<CountryLocationGroup> { if (it.bestLatencyMs > 0) 0 else 1 }
                    .thenBy { if (it.bestLatencyMs > 0) it.bestLatencyMs else Int.MAX_VALUE }
                    .thenBy { it.name }
            )
    }

    fun filterAndSortLocations(
        configs: List<VpnConfig>,
        servers: List<VpnServer>,
        liveLatencies: Map<String, Int>,
        searchQuery: String = "",
        protocolOrFavoriteFilter: String = "ALL",
        countryCodeFilter: String = "ALL",
        latencyTier: LatencyFilterTier = LatencyFilterTier.ALL,
        sortByLatency: Boolean = true
    ): List<VpnConfig> {
        val trimmedQuery = searchQuery.trim()
        val filtered = configs.filter { cfg ->
            val (_, countryCode, countryName) = inferCountryMeta(cfg, servers)
            val latencyMs = effectiveLatencyMs(cfg, liveLatencies)
            val status = effectiveStatus(cfg, liveLatencies)

            val matchesQuery = trimmedQuery.isBlank() ||
                cfg.name.contains(trimmedQuery, ignoreCase = true) ||
                cfg.server.contains(trimmedQuery, ignoreCase = true) ||
                cfg.protocol.contains(trimmedQuery, ignoreCase = true) ||
                countryName.contains(trimmedQuery, ignoreCase = true) ||
                countryCode.equals(trimmedQuery, ignoreCase = true)

            val matchesProtocol = when (protocolOrFavoriteFilter) {
                "ALL" -> true
                "FAV" -> cfg.isFavorite
                "VLESS" -> cfg.protocol.equals("vless", ignoreCase = true)
                "VMESS" -> cfg.protocol.equals("vmess", ignoreCase = true)
                "TROJAN" -> cfg.protocol.equals("trojan", ignoreCase = true)
                "SS" -> cfg.protocol.contains("shadow", ignoreCase = true) ||
                    cfg.protocol.equals("ss", ignoreCase = true)
                else -> true
            }

            val matchesCountry = countryCodeFilter == "ALL" ||
                countryCode.equals(countryCodeFilter, ignoreCase = true)

            val matchesLatency = matchesLatencyTier(
                latencyMs = latencyMs,
                status = status,
                tier = latencyTier
            )

            matchesQuery && matchesProtocol && matchesCountry && matchesLatency
        }

        if (!sortByLatency) return filtered

        return filtered.sortedWith(
            compareBy<VpnConfig> { cfg ->
                val lat = effectiveLatencyMs(cfg, liveLatencies)
                val status = effectiveStatus(cfg, liveLatencies)
                when {
                    lat > 0 && status != "offline" -> 0
                    status != "offline" -> 1
                    else -> 2
                }
            }.thenBy { cfg ->
                val lat = effectiveLatencyMs(cfg, liveLatencies)
                if (lat > 0) lat else Int.MAX_VALUE
            }.thenByDescending { it.isFavorite }
                .thenBy { it.name.lowercase() }
        )
    }
}
