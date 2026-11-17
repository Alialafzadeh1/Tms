package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import com.example.ui.theme.GlassBorderCyan
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
    onRacingConnectAll: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filters = listOf(
        "ALL" to (if (isPersian) "همه" else "All"),
        "FAV" to (if (isPersian) "علاقه‌مندی‌ها" else "Favorites"),
        "VLESS" to "VLESS",
        "VMESS" to "VMess",
        "TROJAN" to "Trojan",
        "SS" to "Shadowsocks"
    )

    val filteredConfigs = remember(configs, searchQuery, selectedFilter) {
        configs.filter { cfg ->
            val matchesQuery = searchQuery.isBlank() ||
                cfg.name.contains(searchQuery, ignoreCase = true) ||
                cfg.server.contains(searchQuery, ignoreCase = true) ||
                cfg.protocol.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                "ALL" -> true
                "FAV" -> cfg.isFavorite
                "VLESS" -> cfg.protocol.equals("vless", ignoreCase = true)
                "VMESS" -> cfg.protocol.equals("vmess", ignoreCase = true)
                "TROJAN" -> cfg.protocol.equals("trojan", ignoreCase = true)
                "SS" -> cfg.protocol.contains("shadow", ignoreCase = true) || cfg.protocol.equals("ss", ignoreCase = true)
                else -> true
            }
            matchesQuery && matchesFilter
        }
    }

    val recommendedConfig = remember(configs) {
        configs.filter { it.status == "online" && it.pingMs > 0 }
            .minByOrNull { it.pingMs }
            ?: configs.firstOrNull { it.isFavorite }
    }

    val mapNodes = remember(configs, servers, selectedConfig) {
        configs.take(12).map { cfg ->
            val (_, code, _) = inferCountryMeta(cfg, servers)
            val (x, y) = mapCoordinatesForCode(code)
            MapRegionNode(
                code = code,
                xFraction = x,
                yFraction = y,
                isOnline = cfg.status == "online" && cfg.pingMs > 0,
                isSelected = selectedConfig?.id == cfg.id
            )
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp),
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
                        Column {
                            Text(
                                text = if (isPersian) "نقشه نودهای فعال شبکه" else "Global Node Topology",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextCyberPrimary
                            )
                            Text(
                                text = if (isPersian) "${configs.size} کانفیگ ثبت‌شده • ${configs.count { it.status == "online" && it.pingMs > 0 }} پینگ تاییدشده"
                                else "${configs.size} configs • ${configs.count { it.status == "online" && it.pingMs > 0 }} verified online",
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
                            .height(145.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(DeepGraphite.copy(alpha = 0.75f))
                            .border(BorderStroke(0.8.dp, GlassBorderSubtle), RoundedCornerShape(14.dp))
                            .padding(8.dp)
                    ) {
                        CyberWorldNodeMap(nodes = mapNodes)
                    }
                }
            }

            // Recommended Server Card
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
                                    text = if (isPersian) "نود پیشنهادی هوشمند" else "Recommended Fastest Node",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = PremiumTeal
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = recommendedConfig?.name ?: if (isPersian) "برای یافتن بهترین نود پینگ بگیرید" else "Run ping test to discover fastest node",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextCyberPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (recommendedConfig != null && recommendedConfig.pingMs > 0) {
                                Text(
                                    text = "${recommendedConfig.protocol.uppercase()} • ${recommendedConfig.pingMs}ms",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = StatusConnected
                                )
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

                    Button(
                        onClick = onRacingConnectAll,
                        enabled = !isRacingConnect && configs.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("servers_racing_all_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = DeepGraphite
                        )
                    ) {
                        Icon(imageVector = Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRacingConnect) {
                                if (isPersian) "در حال تست مسابقه‌ای نودها..." else "Racing all nodes..."
                            } else {
                                if (isPersian) "تست هم‌زمان (Real-Delay) و اتصال مسابقه‌ای" else "Parallel Real-Delay Race & Connect"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Search Field & Filter Chips
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
                                text = if (isPersian) "جستجوی سرور، کشور یا پروتکل..." else "Search server, host, or protocol...",
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
                        filters.forEach { (key, label) ->
                            val selected = selectedFilter == key
                            FilterChip(
                                selected = selected,
                                onClick = { selectedFilter = key },
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
                    CyberGlassCard(contentPadding = 24.dp) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (isPersian) "هیچ سروری با این فیلتر پیدا نشد" else "No matching servers found",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextCyberPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isPersian) "از صفحه خانه کانفیگ VLESS یا VMess وارد کنید." else "Import a VLESS, VMess, Trojan, or SS config from Home.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextCyberSecondary
                            )
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
                    val pingColor = when {
                        cfg.pingMs in 1..140 -> StatusConnected
                        cfg.pingMs in 141..300 -> StatusConnecting
                        cfg.pingMs > 300 || cfg.status == "offline" -> StatusError
                        else -> StatusDisconnected
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
                                // Real Ping Badge
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(pingColor.copy(alpha = 0.14f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = when {
                                            cfg.pingMs > 0 -> "${cfg.pingMs}ms"
                                            cfg.status == "offline" -> if (isPersian) "قطع" else "Down"
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
                                    enabled = !isPinging,
                                    modifier = Modifier.size(48.dp)
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
                                    modifier = Modifier.size(48.dp)
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
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepGraphite)
                            ) {
                                Text(
                                    text = if (isPersian) "اتصال با این نود" else "Connect with this Node",
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
