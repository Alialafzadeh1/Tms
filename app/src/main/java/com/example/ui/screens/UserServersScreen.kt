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
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.material3.TextButton
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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.example.data.local.CleanIpAddressType
import com.example.data.local.CleanIpEntity
import com.example.data.local.CleanIpHealthStatus
import com.example.data.model.ConfigOrigin
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
    onToggleSortByLatency: (() -> Unit)? = null,
    selectedConfigIds: Set<String> = emptySet(),
    onToggleConfigSelection: (String) -> Unit = {},
    onSelectAllConfigs: (List<String>) -> Unit = {},
    onDeselectAllConfigs: () -> Unit = {},
    onDeleteSingleConfig: (String) -> Unit = {},
    onDeleteSelectedConfigs: () -> Unit = {},
    onDeleteAllPermittedConfigs: (List<VpnConfig>) -> Unit = {},
    cleanIpPool: List<CleanIpEntity> = emptyList(),
    isProbingCleanIps: Boolean = false,
    initialShowCleanIpTab: Boolean = false,
    onProbeCleanIpPool: (CleanIpAddressType?) -> Unit = {},
    onImportBulkCleanIps: (String) -> Unit = {},
    onApplyCleanIp: (String, Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var showCleanIpTab by remember { mutableStateOf(initialShowCleanIpTab) }
    var cleanIpFilter by remember { mutableStateOf("ALL") }
    var cleanIpSearch by remember { mutableStateOf("") }
    var showBulkCleanIpDialog by remember { mutableStateOf(false) }
    var bulkCleanIpText by remember { mutableStateOf("") }

    LaunchedEffect(initialShowCleanIpTab) {
        if (initialShowCleanIpTab) {
            showCleanIpTab = true
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedProtocolFilter by remember { mutableStateOf("ALL") }
    var selectedCountryCode by remember { mutableStateOf("ALL") }
    var selectedStatusFilter by remember { mutableStateOf("ALL") }
    var bulkManageMode by remember { mutableStateOf(false) }
    var showBulkDeleteConfirm by remember { mutableStateOf<String?>(null) } // "SELECTED" | "ALL"
    var singleDeleteTarget by remember { mutableStateOf<VpnConfig?>(null) }
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
        selectedStatusFilter,
        activeLatencyTier,
        activeSortByLatency
    ) {
        val base = ServerLocationFilterEngine.filterAndSortLocations(
            configs = configs,
            servers = servers,
            liveLatencies = liveLatencies,
            searchQuery = searchQuery,
            protocolOrFavoriteFilter = selectedProtocolFilter,
            countryCodeFilter = selectedCountryCode,
            latencyTier = activeLatencyTier,
            sortByLatency = activeSortByLatency
        )
        when (selectedStatusFilter) {
            "ONLINE" -> base.filter { ServerLocationFilterEngine.effectiveStatus(it, liveLatencies).equals("online", ignoreCase = true) }
            "OFFLINE" -> base.filter { ServerLocationFilterEngine.effectiveStatus(it, liveLatencies).equals("offline", ignoreCase = true) }
            "UNTESTED" -> base.filter {
                val st = ServerLocationFilterEngine.effectiveStatus(it, liveLatencies)
                st.equals("untested", ignoreCase = true) || st.isBlank()
            }
            else -> base
        }
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
            // Segmented Tab Switch: VPN Nodes vs Clean IP Pool
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !showCleanIpTab,
                        onClick = { showCleanIpTab = false },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("user_servers_nodes_tab"),
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = {
                            Text(
                                text = if (isPersian) "نودهای VPN (${configs.size})" else "VPN Nodes (${configs.size})",
                                fontSize = 11.sp,
                                fontWeight = if (!showCleanIpTab) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                            selectedLabelColor = ElectricCyan,
                            selectedLeadingIconColor = ElectricCyan,
                            containerColor = SecondarySurface,
                            labelColor = MetallicSilver
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = !showCleanIpTab,
                            borderColor = GlassBorderSubtle,
                            selectedBorderColor = ElectricCyan
                        )
                    )

                    FilterChip(
                        selected = showCleanIpTab,
                        onClick = { showCleanIpTab = true },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("user_servers_clean_ip_tab"),
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.Dns, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = {
                            Text(
                                text = if (isPersian) "مخزن IP تمیز (${cleanIpPool.size})" else "Clean IPs (${cleanIpPool.size})",
                                fontSize = 11.sp,
                                fontWeight = if (showCleanIpTab) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PremiumTeal.copy(alpha = 0.2f),
                            selectedLabelColor = PremiumTeal,
                            selectedLeadingIconColor = PremiumTeal,
                            containerColor = SecondarySurface,
                            labelColor = MetallicSilver
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = showCleanIpTab,
                            borderColor = GlassBorderSubtle,
                            selectedBorderColor = PremiumTeal
                        )
                    )
                }
            }

            if (showCleanIpTab) {
                item {
                    val ipv4Count = remember(cleanIpPool) { cleanIpPool.count { it.addressType == CleanIpAddressType.IPV4.name } }
                    val ipv6Count = remember(cleanIpPool) { cleanIpPool.count { it.addressType == CleanIpAddressType.IPV6.name } }
                    val domainCount = remember(cleanIpPool) { cleanIpPool.count { it.addressType == CleanIpAddressType.DOMAIN.name } }
                    val healthyCount = remember(cleanIpPool) { cleanIpPool.count { it.healthStatus == CleanIpHealthStatus.HEALTHY.name } }
                    val fastestHealthyIp = remember(cleanIpPool) {
                        cleanIpPool
                            .filter { it.isAvailable && it.latencyMs > 0 && it.healthStatus == CleanIpHealthStatus.HEALTHY.name }
                            .minByOrNull { it.latencyMs }
                            ?: cleanIpPool.firstOrNull { it.isAvailable && it.addressType == CleanIpAddressType.IPV4.name }
                    }

                    CyberGlassCard(
                        modifier = Modifier.testTag("user_clean_ip_pool_section"),
                        accentColor = PremiumTeal.copy(alpha = 0.65f),
                        elevated = true,
                        contentPadding = 14.dp
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = if (isPersian) "مخزن آی‌پی‌های تمیز ضد فیلتر" else "Anti-Filter Clean IP Pool",
                                style = MaterialTheme.typography.titleMedium,
                                color = PremiumTeal
                            )
                            Text(
                                text = if (isPersian) {
                                    "کل: ${cleanIpPool.size} (IPv4: $ipv4Count • IPv6: $ipv6Count • دامنه: $domainCount) • سالم: $healthyCount"
                                } else {
                                    "Total: ${cleanIpPool.size} (v4: $ipv4Count • v6: $ipv6Count • Domain: $domainCount) • Healthy: $healthyCount"
                                },
                                fontSize = 11.sp,
                                color = TextCyberSecondary
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "ALL" to (if (isPersian) "همه (${cleanIpPool.size})" else "All"),
                                    "IPV4" to "IPv4 ($ipv4Count)",
                                    "IPV6" to "IPv6 ($ipv6Count)",
                                    "DOMAIN" to (if (isPersian) "دامنه ($domainCount)" else "Domain ($domainCount)"),
                                    "HEALTHY" to (if (isPersian) "سالم ($healthyCount)" else "Healthy ($healthyCount)")
                                ).forEach { (key, label) ->
                                    FilterChip(
                                        selected = cleanIpFilter == key,
                                        onClick = { cleanIpFilter = key },
                                        modifier = Modifier.minimumInteractiveComponentSize(),
                                        label = { Text(label, fontSize = 10.sp) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = cleanIpSearch,
                                onValueChange = { cleanIpSearch = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("user_clean_ip_search_input"),
                                leadingIcon = {
                                    Icon(imageVector = Icons.Filled.Search, contentDescription = null, tint = ElectricCyan)
                                },
                                placeholder = {
                                    Text(
                                        text = if (isPersian) "جستجوی IP یا دامنه..." else "Search IP or domain...",
                                        fontSize = 12.sp,
                                        color = TextCyberSecondary
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricCyan,
                                    unfocusedBorderColor = GlassBorderSubtle,
                                    focusedContainerColor = ElevatedSurface,
                                    unfocusedContainerColor = ElevatedSurface,
                                    focusedTextColor = TextCyberPrimary,
                                    unfocusedTextColor = TextCyberPrimary
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val probeType = when (cleanIpFilter) {
                                    "IPV4" -> CleanIpAddressType.IPV4
                                    "IPV6" -> CleanIpAddressType.IPV6
                                    "DOMAIN" -> CleanIpAddressType.DOMAIN
                                    else -> null
                                }
                                Button(
                                    onClick = { onProbeCleanIpPool(probeType) },
                                    enabled = !isProbingCleanIps && cleanIpPool.isNotEmpty(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("user_probe_clean_ips_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepGraphite),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (isProbingCleanIps) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = DeepGraphite)
                                    } else {
                                        Icon(imageVector = Icons.Filled.NetworkPing, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isProbingCleanIps) {
                                            if (isPersian) "در حال تست..." else "Probing..."
                                        } else {
                                            if (isPersian) "تست سلامت" else "Probe TCP"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                OutlinedButton(
                                    onClick = { showBulkCleanIpDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("user_add_clean_ip_btn"),
                                    border = BorderStroke(1.dp, PremiumTeal.copy(alpha = 0.7f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PremiumTeal),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isPersian) "افزودن IP تمیز" else "Add Clean IP",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (fastestHealthyIp != null) {
                                Button(
                                    onClick = { onApplyCleanIp(fastestHealthyIp.host, true) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("user_apply_fastest_clean_ip_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusConnected, contentColor = DeepGraphite),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isPersian) {
                                            "اتصال با سریع‌ترین IP (${fastestHealthyIp.host})"
                                        } else {
                                            "Connect with Fastest IP (${fastestHealthyIp.host})"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                val filteredCleanIps = cleanIpPool.filter { item ->
                    val typeMatch = when (cleanIpFilter) {
                        "IPV4" -> item.addressType == CleanIpAddressType.IPV4.name
                        "IPV6" -> item.addressType == CleanIpAddressType.IPV6.name
                        "DOMAIN" -> item.addressType == CleanIpAddressType.DOMAIN.name
                        "HEALTHY" -> item.healthStatus == CleanIpHealthStatus.HEALTHY.name
                        else -> true
                    }
                    val q = cleanIpSearch.trim().lowercase()
                    typeMatch && (q.isEmpty() || item.host.lowercase().contains(q) || item.notes.lowercase().contains(q) || item.sourceTag.lowercase().contains(q))
                }.take(100)

                if (filteredCleanIps.isEmpty()) {
                    item {
                        CyberGlassCard(contentPadding = 20.dp) {
                            Text(
                                text = if (isPersian) "هیچ IP تمیزی با این فیلتر یافت نشد." else "No clean IPs match the selected filter.",
                                color = TextCyberSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    items(filteredCleanIps, key = { "user_clean_${it.host}" }) { item ->
                        val statusEnum = item.healthStatusEnum()
                        val badgeColor = when (statusEnum) {
                            CleanIpHealthStatus.HEALTHY -> StatusConnected
                            CleanIpHealthStatus.DEGRADED -> StatusConnecting
                            CleanIpHealthStatus.UNHEALTHY, CleanIpHealthStatus.QUARANTINED -> StatusError
                            CleanIpHealthStatus.UNTESTED -> MetallicSilver
                        }
                        CyberGlassCard(
                            modifier = Modifier.testTag("clean_ip_item_${item.host}"),
                            accentColor = badgeColor.copy(alpha = 0.45f),
                            contentPadding = 12.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.host,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextCyberPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val pingLabel = if (item.latencyMs > 0) "${item.latencyMs}ms" else "--"
                                    Text(
                                        text = "${item.addressTypeEnum().labelEn} • ${if (isPersian) statusEnum.labelFa else statusEnum.labelEn} • Ping: $pingLabel",
                                        fontSize = 10.sp,
                                        color = badgeColor
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Clean IP", item.host))
                                            Toast.makeText(
                                                context,
                                                if (isPersian) "کپی شد: ${item.host}" else "Copied: ${item.host}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ContentCopy,
                                            contentDescription = if (isPersian) "کپی" else "Copy",
                                            tint = MetallicSilver,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    Button(
                                        onClick = { onApplyCleanIp(item.host, true) },
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("apply_clean_ip_${item.host}"),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepGraphite),
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) {
                                        Text(
                                            text = if (isPersian) "اعمال و اتصال" else "Use & Connect",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
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

            // Search Field, Bulk Manage Mode, Status & Protocol Filter Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .weight(1f)
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

                        OutlinedButton(
                            onClick = { bulkManageMode = !bulkManageMode },
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("user_bulk_manage_toggle"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, if (bulkManageMode) ElectricCyan else GlassBorderSubtle),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (bulkManageMode) ElectricCyan.copy(alpha = 0.16f) else SecondarySurface,
                                contentColor = if (bulkManageMode) ElectricCyan else MetallicSilver
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(imageVector = Icons.Filled.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPersian) "مدیریت" else "Manage",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Status filter chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "ALL" to (if (isPersian) "همه وضعیت‌ها" else "All Status"),
                            "ONLINE" to (if (isPersian) "آنلاین" else "Online"),
                            "OFFLINE" to (if (isPersian) "آفلاین" else "Offline"),
                            "UNTESTED" to (if (isPersian) "تست‌نشده" else "Untested")
                        ).forEach { (key, label) ->
                            val selected = selectedStatusFilter == key
                            FilterChip(
                                selected = selected,
                                onClick = { selectedStatusFilter = key },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("user_status_filter_chip_$key"),
                                label = { Text(text = label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = StatusConnected.copy(alpha = 0.18f),
                                    selectedLabelColor = StatusConnected,
                                    containerColor = SecondarySurface,
                                    labelColor = MetallicSilver
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = GlassBorderSubtle,
                                    selectedBorderColor = StatusConnected
                                )
                            )
                        }
                    }

                    AnimatedVisibility(visible = bulkManageMode) {
                        CyberGlassCard(
                            accentColor = ElectricCyan.copy(alpha = 0.5f),
                            contentPadding = 12.dp,
                            modifier = Modifier.testTag("user_bulk_action_bar")
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isPersian) {
                                            "انتخاب‌شده: ${selectedConfigIds.size} مورد • فقط کانفیگ‌های شخصی کاربر قابل حذف هستند"
                                        } else {
                                            "Selected: ${selectedConfigIds.size} • Only user-imported configs can be deleted"
                                        },
                                        fontSize = 11.sp,
                                        color = TextCyberPrimary
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onSelectAllConfigs(filteredConfigs.map { it.id }) },
                                        modifier = Modifier.height(48.dp).testTag("user_select_all_button"),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f))
                                    ) {
                                        Icon(imageVector = Icons.Filled.SelectAll, contentDescription = null, modifier = Modifier.size(15.dp), tint = ElectricCyan)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = if (isPersian) "انتخاب همه" else "Select All", fontSize = 11.sp, color = ElectricCyan)
                                    }

                                    OutlinedButton(
                                        onClick = onDeselectAllConfigs,
                                        enabled = selectedConfigIds.isNotEmpty(),
                                        modifier = Modifier.height(48.dp).testTag("user_deselect_all_button"),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, GlassBorderSubtle)
                                    ) {
                                        Icon(imageVector = Icons.Filled.CheckBoxOutlineBlank, contentDescription = null, modifier = Modifier.size(15.dp), tint = MetallicSilver)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = if (isPersian) "لغو انتخاب" else "Deselect All", fontSize = 11.sp, color = MetallicSilver)
                                    }

                                    Button(
                                        onClick = { showBulkDeleteConfirm = "SELECTED" },
                                        enabled = selectedConfigIds.isNotEmpty(),
                                        modifier = Modifier.height(48.dp).testTag("user_delete_selected_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = StatusError, contentColor = TextCyberPrimary),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(imageVector = Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isPersian) "حذف انتخابی (${selectedConfigIds.size})" else "Delete (${selectedConfigIds.size})",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { showBulkDeleteConfirm = "ALL" },
                                        modifier = Modifier.height(48.dp).testTag("user_delete_all_button"),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, StatusError.copy(alpha = 0.7f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError)
                                    ) {
                                        Icon(imageVector = Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = if (isPersian) "حذف شخصی‌ها" else "Delete Mine", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

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
                            val cfgOrigin = cfg.originEnum()
                            val isChecked = cfg.id in selectedConfigIds
                            val canUserDelete = cfgOrigin == ConfigOrigin.USER_IMPORTED

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (bulkManageMode) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { onToggleConfigSelection(cfg.id) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("user_config_checkbox_${cfg.id}"),
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = ElectricCyan,
                                            checkmarkColor = DeepGraphite,
                                            uncheckedColor = MetallicSilver
                                        )
                                    )
                                }
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
                                    if (cfgOrigin != ConfigOrigin.USER_IMPORTED) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (cfgOrigin == ConfigOrigin.SUBSCRIPTION_ASSIGNED) Icons.Filled.VerifiedUser else Icons.Filled.Lock,
                                                contentDescription = null,
                                                tint = if (cfgOrigin == ConfigOrigin.SUBSCRIPTION_ASSIGNED) ElectricCyan else StatusConnecting,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isPersian) cfgOrigin.titleFa else cfgOrigin.titleEn,
                                                fontSize = 9.sp,
                                                color = if (cfgOrigin == ConfigOrigin.SUBSCRIPTION_ASSIGNED) ElectricCyan else StatusConnecting
                                            )
                                        }
                                    }
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

                                if (bulkManageMode && canUserDelete) {
                                    IconButton(
                                        onClick = { singleDeleteTarget = cfg },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("user_delete_single_${cfg.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = if (isPersian) "حذف کانفیگ" else "Delete Config",
                                            tint = StatusError,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
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

    if (showBulkCleanIpDialog) {
        AlertDialog(
            onDismissRequest = { showBulkCleanIpDialog = false },
            containerColor = SecondarySurface,
            modifier = Modifier.testTag("user_bulk_clean_ip_dialog"),
            title = {
                Text(
                    text = if (isPersian) "افزودن IP تمیز یا رنج /24" else "Add Clean IPs or /24 Range",
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isPersian) {
                            "فقط آدرس IP تمیز (IPv4، IPv6، دامنه یا رنج /24) را وارد کنید — بدون نیاز به پورت:"
                        } else {
                            "Enter Clean IPs, IPv6, domains, or /24 CIDR ranges (no port needed):"
                        },
                        color = TextCyberSecondary,
                        fontSize = 11.sp
                    )
                    OutlinedTextField(
                        value = bulkCleanIpText,
                        onValueChange = { bulkCleanIpText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .testTag("user_bulk_clean_ip_input"),
                        placeholder = { Text("104.16.190.0/24\n172.67.127.156", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = GlassBorderSubtle,
                            focusedTextColor = TextCyberPrimary,
                            unfocusedTextColor = TextCyberPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (bulkCleanIpText.isNotBlank()) {
                            onImportBulkCleanIps(bulkCleanIpText)
                            bulkCleanIpText = ""
                            showBulkCleanIpDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepGraphite)
                ) {
                    Text(text = if (isPersian) "افزودن به مخزن" else "Import", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkCleanIpDialog = false }) {
                    Text(text = if (isPersian) "انصراف" else "Cancel", color = MetallicSilver)
                }
            }
        )
    }

    singleDeleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { singleDeleteTarget = null },
            containerColor = SecondarySurface,
            title = {
                Text(
                    text = if (isPersian) "حذف کانفیگ شخصی" else "Delete Personal Config",
                    color = StatusError,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isPersian) {
                        "آیا کانفیگ «${target.name}» برای همیشه حذف شود؟"
                    } else {
                        "Permanently delete '${target.name}'?"
                    },
                    color = TextCyberPrimary,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSingleConfig(target.id)
                        singleDeleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError, contentColor = TextCyberPrimary)
                ) {
                    Text(text = if (isPersian) "حذف" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { singleDeleteTarget = null }) {
                    Text(text = if (isPersian) "انصراف" else "Cancel", color = MetallicSilver)
                }
            }
        )
    }

    showBulkDeleteConfirm?.let { mode ->
        val userImportedVisibleCount = filteredConfigs.count { it.originEnum() == ConfigOrigin.USER_IMPORTED }
        val protectedCount = filteredConfigs.size - userImportedVisibleCount
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = null },
            containerColor = SecondarySurface,
            modifier = Modifier.testTag("user_confirm_bulk_delete_dialog"),
            title = {
                Text(
                    text = if (mode == "ALL") {
                        if (isPersian) "حذف همه کانفیگ‌های شخصی" else "Delete All Personal Configs"
                    } else {
                        if (isPersian) "حذف کانفیگ‌های انتخاب‌شده" else "Delete Selected Configs"
                    },
                    color = StatusError,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isPersian) {
                        "فقط کانفیگ‌های شخصی واردشده توسط شما حذف می‌شوند و $protectedCount مورد سیستمی یا اشتراکی محفوظ می‌مانند."
                    } else {
                        "Only your personal imported configs will be deleted; $protectedCount system/subscription configs stay preserved."
                    },
                    color = TextCyberPrimary,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (mode == "ALL") {
                            onDeleteAllPermittedConfigs(filteredConfigs)
                        } else {
                            onDeleteSelectedConfigs()
                        }
                        showBulkDeleteConfirm = null
                    },
                    modifier = Modifier.testTag("user_confirm_bulk_delete_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError, contentColor = TextCyberPrimary)
                ) {
                    Text(text = if (isPersian) "تایید حذف" else "Confirm Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = null }) {
                    Text(text = if (isPersian) "انصراف" else "Cancel", color = MetallicSilver)
                }
            }
        )
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
