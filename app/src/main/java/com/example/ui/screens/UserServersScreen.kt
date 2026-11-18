package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VpnConfig
import com.example.data.model.VpnServer
import com.example.ui.components.CyberGlassCard
import com.example.ui.components.CyberWorldNodeMap
import com.example.ui.components.MapRegionNode
import com.example.ui.components.inferCountryMeta
import com.example.ui.components.mapCoordinatesForCode
import com.example.ui.theme.DeepGraphite
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.GlassBorderSubtle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MetallicSilver
import com.example.ui.theme.PremiumTeal
import com.example.ui.theme.SecondarySurface
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusConnecting
import com.example.ui.theme.StatusDisconnected
import com.example.ui.theme.StatusError
import com.example.ui.theme.TextCyberPrimary
import com.example.ui.theme.TextCyberSecondary
import com.example.vpn.engine.LatencyFilterTier
import com.example.vpn.engine.ServerLocationFilterEngine

@Composable
fun UserServersScreen(
    configs: List<VpnConfig>,
    servers: List<VpnServer>,
    selectedConfig: VpnConfig?,
    isPinging: Boolean,
    isPersian: Boolean,
    onSelectConfig: (VpnConfig) -> Unit,
    onConnectSelected: (VpnConfig) -> Unit,
    onPingConfig: (VpnConfig) -> Unit,
    onToggleFavorite: (VpnConfig) -> Unit,
    onAutoSelectBest: () -> Unit,
    modifier: Modifier = Modifier,
    isRacingConnect: Boolean = false,
    onRacingConnectAll: () -> Unit = {},
    liveLatencies: Map<String, Int> = emptyMap(),
    isRefreshingLatencies: Boolean = false,
    latencyScanProgress: Pair<Int, Int> = 0 to 0,
    selectedLatencyTier: LatencyFilterTier? = null,
    sortByLatency: Boolean? = null,
    onRefreshLatencies: () -> Unit = {},
    onLatencyTierSelected: ((LatencyFilterTier) -> Unit)? = null,
    onToggleSortByLatency: (() -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedProtocolFilter by remember { mutableStateOf("ALL") }
    var selectedCountryCode by remember { mutableStateOf("ALL") }
    var localLatencyTier by remember { mutableStateOf(LatencyFilterTier.ALL) }
    var localSortByLatency by remember { mutableStateOf(true) }

    val activeLatencyTier = selectedLatencyTier ?: localLatencyTier
    val activeSortByLatency = sortByLatency ?: localSortByLatency

    val selectTier: (LatencyFilterTier) -> Unit = { tier ->
        localLatencyTier = tier
        onLatencyTierSelected?.invoke(tier)
    }
    val toggleSort: () -> Unit = {
        localSortByLatency = !localSortByLatency
        onToggleSortByLatency?.invoke()
    }

    val protocolFilters = listOf(
        "ALL" to (if (isPersian) "همه" else "All"),
        "FAV" to (if (isPersian) "علاقه‌مندی‌ها" else "Favorites"),
        "VLESS" to "VLESS",
        "VMESS" to "VMess",
        "TROJAN" to "Trojan",
        "SS" to "Shadowsocks"
    )

    val countryLocations = remember(configs, servers, liveLatencies) {
        ServerLocationFilterEngine.extractCountryLocations(
            configs = configs,
            servers = servers,
            liveLatencies = liveLatencies
        )
    }

    val tierCounts = remember(configs, liveLatencies) {
        ServerLocationFilterEngine.countByLatencyTier(
            configs = configs,
            liveLatencies = liveLatencies
        )
    }

    val filteredConfigs = remember(
        configs,
        servers,
        liveLatencies,
        searchQuery,
        selectedProtocolFilter,
        selectedCountryCode,
        activeLatencyTier,
        activeSortByLatency
    ) {
        ServerLocationFilterEngine.filterAndSortLocations(
            configs = configs,
            servers = servers,
            liveLatencies = liveLatencies,
            searchQuery = searchQuery,
            protocolOrFavoriteFilter = selectedProtocolFilter,
            countryCodeFilter = selectedCountryCode,
            latencyTier = activeLatencyTier,
            sortByLatency = activeSortByLatency
        )
    }

    val recommendedConfig = remember(configs, liveLatencies) {
        configs
            .filter { cfg ->
                val lat = ServerLocationFilterEngine.effectiveLatencyMs(cfg, liveLatencies)
                val status = ServerLocationFilterEngine.effectiveStatus(cfg, liveLatencies)
                status == "online" && lat > 0
            }
            .minByOrNull { ServerLocationFilterEngine.effectiveLatencyMs(it, liveLatencies) }
            ?: configs.firstOrNull { it.isFavorite }
    }

    val verifiedOnlineCount = remember(configs, liveLatencies) {
        configs.count { cfg ->
            val lat = ServerLocationFilterEngine.effectiveLatencyMs(cfg, liveLatencies)
            val status = ServerLocationFilterEngine.effectiveStatus(cfg, liveLatencies)
            status == "online" && lat > 0
        }
    }

    val mapNodes = remember(configs, servers, selectedConfig, liveLatencies) {
        configs.take(12).map { cfg ->
            val (_, code, _) = inferCountryMeta(cfg, servers)
            val (x, y) = mapCoordinatesForCode(code)
            val lat = ServerLocationFilterEngine.effectiveLatencyMs(cfg, liveLatencies)
            val status = ServerLocationFilterEngine.effectiveStatus(cfg, liveLatencies)
            MapRegionNode(
                code = code,
                xFraction = x,
                yFraction = y,
                isOnline = status == "online" && lat > 0,
                isSelected = selectedConfig?.id == cfg.id
            )
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .testTag("servers_location_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Interactive Cyber World Node Radar
            item {
                CyberGlassCard(
                    accentColor = ElectricCyan.copy(alpha = 0.4f),
                    contentPadding = 12.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isPersian) "انتخاب لوکیشن و نقشه نودهای فعال" else "VPN Server Locations & Topology",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextCyberPrimary
                            )
                            Text(
                                text = if (isPersian) {
                                    "${configs.size} لوکیشن • $verifiedOnlineCount نود با پینگ زنده تاییدشده"
                                } else {
                                    "${configs.size} locations • $verifiedOnlineCount online verified"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextCyberSecondary
                            )
                        }
                        Text(
                            text = "XRAY/VLESS",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(DeepGraphite.copy(alpha = 0.75f))
                            .border(BorderStroke(0.8.dp, GlassBorderSubtle), RoundedCornerShape(14.dp))
                            .padding(8.dp)
                    ) {
                        CyberWorldNodeMap(nodes = mapNodes)
                    }
                }
            }

            // Recommended Server & Background Scan Card
            item {
                CyberGlassCard(
                    accentColor = PremiumTeal.copy(alpha = 0.7f),
                    elevated = true,
                    contentPadding = 14.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Bolt,
                                    contentDescription = null,
                                    tint = PremiumTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isPersian) "سریع‌ترین لوکیشن پیشنهادی" else "Recommended Fastest Location",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = PremiumTeal
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = recommendedConfig?.name ?: if (isPersian) "برای یافتن بهترین نود پینگ بگیرید" else "Scan latencies to discover fastest node",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextCyberPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (recommendedConfig != null) {
                                val bestPing = ServerLocationFilterEngine.effectiveLatencyMs(recommendedConfig, liveLatencies)
                                if (bestPing > 0) {
                                    Text(
                                        text = "${recommendedConfig.protocol.uppercase()} • ${bestPing}ms",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 11.sp,
                                        color = StatusConnected
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { onAutoSelectBest() },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("servers_auto_select_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PremiumTeal, contentColor = DeepGraphite),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (isPersian) "انتخاب خودکار" else "Auto Pick",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRefreshLatencies,
                            enabled = !isRefreshingLatencies && configs.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("servers_refresh_latencies_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan)
                        ) {
                            if (isRefreshingLatencies) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = ElectricCyan
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = if (isPersian) "بروزرسانی تاخیرها" else "Refresh Latencies",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRefreshingLatencies) {
                                    val (done, total) = latencyScanProgress
                                    if (isPersian) "اسکن ($done/$total)" else "Scanning $done/$total"
                                } else {
                                    if (isPersian) "اسکن پینگ‌ها" else "Refresh Pings"
                                },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = onRacingConnectAll,
                            enabled = !isRacingConnect && configs.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("servers_racing_all_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = DeepGraphite
                            )
                        ) {
                            Icon(imageVector = Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isRacingConnect) {
                                    if (isPersian) "تست مسابقه‌ای..." else "Racing..."
                                } else {
                                    if (isPersian) "اتصال مسابقه‌ای" else "Race & Connect"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    AnimatedVisibility(visible = isRefreshingLatencies) {
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))
                            val (done, total) = latencyScanProgress
                            val progressFraction = if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .testTag("latency_scan_progress_bar"),
                                color = ElectricCyan,
                                trackColor = DeepGraphite
                            )
                        }
                    }
                }
            }

            // Latency Threshold Filter & Sort Controls
            item {
                CyberGlassCard(
                    accentColor = ElectricCyan.copy(alpha = 0.25f),
                    contentPadding = 12.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Speed,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isPersian) "فیلتر بر اساس پینگ (Latency)" else "Filter by Latency",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextCyberPrimary
                            )
                        }

                        FilterChip(
                            selected = activeSortByLatency,
                            onClick = toggleSort,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("latency_sort_toggle"),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.SwapVert,
                                    contentDescription = if (isPersian) "مرتب‌سازی بر اساس پینگ" else "Sort by latency",
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = if (isPersian) {
                                        if (activeSortByLatency) "کمترین تاخیر" else "ترتیب پیش‌فرض"
                                    } else {
                                        if (activeSortByLatency) "Fastest First" else "Default Order"
                                    },
                                    fontSize = 11.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PremiumTeal.copy(alpha = 0.18f),
                                selectedLabelColor = PremiumTeal,
                                selectedLeadingIconColor = PremiumTeal,
                                containerColor = ElevatedSurface,
                                labelColor = MetallicSilver
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = activeSortByLatency,
                                borderColor = GlassBorderSubtle,
                                selectedBorderColor = PremiumTeal
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LatencyFilterTier.entries.forEach { tier ->
                            val selected = activeLatencyTier == tier
                            val count = tierCounts[tier] ?: 0
                            val dotColor = when (tier) {
                                LatencyFilterTier.ULTRA_FAST -> StatusConnected
                                LatencyFilterTier.FAST -> ElectricCyan
                                LatencyFilterTier.MODERATE -> StatusConnecting
                                LatencyFilterTier.REACHABLE_ONLY -> PremiumTeal
                                LatencyFilterTier.ALL -> MetallicSilver
                            }
                            val baseLabel = if (isPersian) tier.labelFa else tier.labelEn

                            FilterChip(
                                selected = selected,
                                onClick = { selectTier(tier) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("latency_filter_chip_${tier.key}"),
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                },
                                label = {
                                    Text(
                                        text = "$baseLabel ($count)",
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = dotColor.copy(alpha = 0.18f),
                                    selectedLabelColor = dotColor,
                                    containerColor = ElevatedSurface,
                                    labelColor = MetallicSilver
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = GlassBorderSubtle,
                                    selectedBorderColor = dotColor
                                )
                            )
                        }
                    }

                    if (countryLocations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = selectedCountryCode == "ALL",
                                onClick = { selectedCountryCode = "ALL" },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("location_filter_chip_ALL"),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Public,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (isPersian) "همه کشورها" else "All Regions",
                                        fontSize = 11.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan.copy(alpha = 0.18f),
                                    selectedLabelColor = ElectricCyan,
                                    selectedLeadingIconColor = ElectricCyan,
                                    containerColor = ElevatedSurface,
                                    labelColor = MetallicSilver
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selectedCountryCode == "ALL",
                                    borderColor = GlassBorderSubtle,
                                    selectedBorderColor = ElectricCyan
                                )
                            )

                            countryLocations.forEach { group ->
                                val selected = selectedCountryCode.equals(group.code, ignoreCase = true)
                                val pingSuffix = if (group.bestLatencyMs > 0) " • ${group.bestLatencyMs}ms" else ""
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedCountryCode = if (selected) "ALL" else group.code
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("location_filter_chip_${group.code}"),
                                    label = {
                                        Text(
                                            text = "${group.flag} ${group.name}$pingSuffix",
                                            fontSize = 11.sp
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricCyan.copy(alpha = 0.18f),
                                        selectedLabelColor = ElectricCyan,
                                        containerColor = ElevatedSurface,
                                        labelColor = MetallicSilver
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        borderColor = GlassBorderSubtle,
                                        selectedBorderColor = ElectricCyan
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Search Field & Protocol Filter Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("server_search_input"),
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.Search, contentDescription = null, tint = ElectricCyan)
                        },
                        placeholder = {
                            Text(
                                text = if (isPersian) "جستجوی سرور، کشور یا پروتکل..." else "Search server, location, or protocol...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextCyberSecondary
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = GlassBorderSubtle,
                            focusedContainerColor = SecondarySurface,
                            unfocusedContainerColor = SecondarySurface,
                            focusedTextColor = TextCyberPrimary,
                            unfocusedTextColor = TextCyberPrimary
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        protocolFilters.forEach { (key, label) ->
                            val selected = selectedProtocolFilter == key
                            FilterChip(
                                selected = selected,
                                onClick = { selectedProtocolFilter = key },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("protocol_filter_chip_$key"),
                                label = { Text(text = label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan.copy(alpha = 0.18f),
                                    selectedLabelColor = ElectricCyan,
                                    containerColor = SecondarySurface,
                                    labelColor = MetallicSilver
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = GlassBorderSubtle,
                                    selectedBorderColor = ElectricCyan
                                )
                            )
                        }
                    }
                }
            }

            if (filteredConfigs.isEmpty()) {
                item {
                    CyberGlassCard(
                        modifier = Modifier.testTag("servers_empty_filtered_state"),
                        contentPadding = 24.dp
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FilterListOff,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (isPersian) "هیچ سروری با این فیلتر پینگ یا لوکیشن پیدا نشد" else "No VPN locations match these latency filters",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextCyberPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isPersian) "می‌توانید فیلتر پینگ را بازنشانی کنید یا اسکن زنده پینگ را اجرا نمایید." else "Reset your latency or region filters or run a live ping scan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextCyberSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    searchQuery = ""
                                    selectedProtocolFilter = "ALL"
                                    selectedCountryCode = "ALL"
                                    selectTier(LatencyFilterTier.ALL)
                                },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("reset_latency_filters_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ElectricCyan),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan)
                            ) {
                                Text(
                                    text = if (isPersian) "نمایش همه لوکیشن‌ها" else "Reset Filters",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                items(filteredConfigs, key = { it.id }) { cfg ->
                    val isSelected = selectedConfig?.id == cfg.id
                    val (flag, _, countryName) = inferCountryMeta(cfg, servers)
                    val matchingServer = servers.firstOrNull {
                        it.host.equals(cfg.server, ignoreCase = true)
                    }
                    val effectivePingMs = ServerLocationFilterEngine.effectiveLatencyMs(cfg, liveLatencies)
                    val effectiveStatus = ServerLocationFilterEngine.effectiveStatus(cfg, liveLatencies)

                    val pingColor = when {
                        effectivePingMs in 1..140 && effectiveStatus != "offline" -> StatusConnected
                        effectivePingMs in 141..300 && effectiveStatus != "offline" -> StatusConnecting
                        effectivePingMs > 300 || effectiveStatus == "offline" -> StatusError
                        else -> StatusDisconnected
                    }

                    val signalBars = when {
                        effectiveStatus == "offline" || effectivePingMs <= 0 -> 0
                        effectivePingMs <= 80 -> 4
                        effectivePingMs <= 150 -> 3
                        effectivePingMs <= 300 -> 2
                        else -> 1
                    }

                    CyberGlassCard(
                        modifier = Modifier.testTag("server_item_${cfg.id}"),
                        accentColor = if (isSelected) ElectricCyan else null,
                        elevated = isSelected,
                        onClick = { onSelectConfig(cfg) },
                        contentPadding = 14.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ElevatedSurface)
                                        .border(
                                            BorderStroke(1.dp, if (isSelected) ElectricCyan else GlassBorderSubtle),
                                            RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = flag, fontSize = 20.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = cfg.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = TextCyberPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = ElectricCyan,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "$countryName • ${cfg.protocol.uppercase()} • ${cfg.server}:${cfg.port}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 10.sp,
                                        color = TextCyberSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Real Ping Badge + Signal Strength Bars
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(pingColor.copy(alpha = 0.14f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .testTag("latency_badge_${cfg.id}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    LatencySignalBars(
                                        activeBars = signalBars,
                                        activeColor = pingColor
                                    )
                                    Text(
                                        text = when {
                                            effectivePingMs > 0 -> "${effectivePingMs}ms"
                                            effectiveStatus == "offline" -> if (isPersian) "قطع" else "Down"
                                            isRefreshingLatencies -> "..."
                                            else -> "--"
                                        },
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = pingColor
                                    )
                                }

                                IconButton(
                                    onClick = { onPingConfig(cfg) },
                                    enabled = !isPinging && !isRefreshingLatencies,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("ping_config_button_${cfg.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.NetworkPing,
                                        contentDescription = if (isPersian) "تست پینگ واقعی" else "Test Real Ping",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onToggleFavorite(cfg) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("favorite_config_button_${cfg.id}")
                                ) {
                                    Icon(
                                        imageVector = if (cfg.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                        contentDescription = if (isPersian) "علاقه‌مندی" else "Favorite",
                                        tint = if (cfg.isFavorite) StatusConnecting else MetallicSilver,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        if (matchingServer != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val loadRatio = if (matchingServer.bandwidthLimitGb > 0) {
                                (matchingServer.bandwidthUsageGb / matchingServer.bandwidthLimitGb).toFloat().coerceIn(0f, 1f)
                            } else 0f
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { loadRatio },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(999.dp)),
                                    color = if (loadRatio < 0.7f) PremiumTeal else StatusConnecting,
                                    trackColor = ElevatedSurface
                                )
                                Text(
                                    text = "${matchingServer.stabilityScore}% Q",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = MetallicSilver
                                )
                            }
                        }

                        if (isSelected) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { onConnectSelected(cfg) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("connect_server_button_${cfg.id}"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepGraphite)
                            ) {
                                Text(
                                    text = if (isPersian) "اتصال با این لوکیشن" else "Connect to this Location",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun LatencySignalBars(
    activeBars: Int,
    activeColor: Color
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val heights = listOf(4.dp, 7.dp, 10.dp, 13.dp)
        heights.forEachIndexed { idx, barHeight ->
            val filled = idx < activeBars
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (filled) activeColor else GlassBorderSubtle)
            )
        }
    }
}
