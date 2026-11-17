package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AuthAuditLog
import com.example.data.model.UserProfile
import com.example.data.model.VpnConfig
import com.example.data.model.VpnConnectionState
import com.example.data.model.VpnSessionStats
import com.example.ui.components.CyberGlassCard
import com.example.ui.components.LatencyDistributionBarChart
import com.example.ui.components.TelemetryMetricTile
import com.example.ui.components.ThroughputWaveformChart
import com.example.ui.components.formatCyberBytes
import com.example.ui.components.formatCyberDuration
import com.example.ui.components.formatCyberSpeed
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.GlassBorderSubtle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MetallicSilver
import com.example.ui.theme.PremiumTeal
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusConnecting
import com.example.ui.theme.StatusError
import com.example.ui.theme.TextCyberPrimary
import com.example.ui.theme.TextCyberSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UserStatisticsScreen(
    vpnState: VpnConnectionState,
    sessionStats: VpnSessionStats,
    userProfile: UserProfile?,
    configs: List<VpnConfig>,
    auditLogs: List<AuthAuditLog>,
    isPersian: Boolean,
    modifier: Modifier = Modifier
) {
    // Live rolling sample buffer populated exclusively from real VpnSessionStats
    val downloadHistory = remember { mutableStateListOf<Long>() }
    val uploadHistory = remember { mutableStateListOf<Long>() }

    LaunchedEffect(
        sessionStats.connectedDurationSeconds,
        sessionStats.downloadSpeedBytesPerSec,
        sessionStats.uploadSpeedBytesPerSec
    ) {
        if (downloadHistory.size >= 24) downloadHistory.removeAt(0)
        if (uploadHistory.size >= 24) uploadHistory.removeAt(0)
        downloadHistory.add(sessionStats.downloadSpeedBytesPerSec)
        uploadHistory.add(sessionStats.uploadSpeedBytesPerSec)
    }

    val testedConfigs = remember(configs) {
        configs.filter { it.pingMs > 0 }.take(8)
    }

    val totalSessionBytes = sessionStats.totalBytesReceived + sessionStats.totalBytesSent
    val profileUsageBytes = userProfile?.dataUsageBytes?.toLong()?.coerceAtLeast(0L) ?: 0L
    val totalConsumed = maxOf(totalSessionBytes, profileUsageBytes)
    val peakDownload = downloadHistory.maxOrNull() ?: sessionStats.downloadSpeedBytesPerSec
    val peakUpload = uploadHistory.maxOrNull() ?: sessionStats.uploadSpeedBytesPerSec

    val connectionHistoryLogs = remember(auditLogs) {
        auditLogs.filter {
            it.action.contains("VPN", ignoreCase = true) ||
                it.module.equals("VpnService", ignoreCase = true) ||
                it.action.contains("CONFIG", ignoreCase = true)
        }.take(20)
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live Download / Upload Chart
            item {
                CyberGlassCard(
                    accentColor = ElectricCyan.copy(alpha = 0.5f),
                    elevated = true,
                    contentPadding = 16.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isPersian) "نمودار ترافیک زنده (دانلود و آپلود)" else "Live Throughput Analytics",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextCyberPrimary
                            )
                            Text(
                                text = if (vpnState == VpnConnectionState.CONNECTED) {
                                    if (isPersian) "نمونه‌برداری لحظه‌ای از تونل فعال" else "Real-time active tunnel sampling"
                                } else {
                                    if (isPersian) "برای ثبت ترافیک زنده، VPN را متصل کنید" else "Connect VPN to record live tunnel telemetry"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextCyberSecondary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "▼ ${formatCyberSpeed(sessionStats.downloadSpeedBytesPerSec)}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusConnected
                            )
                            Text(
                                text = "▲ ${formatCyberSpeed(sessionStats.uploadSpeedBytesPerSec)}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = MetallicSilver
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    ThroughputWaveformChart(
                        downloadSamples = downloadHistory.toList(),
                        uploadSamples = uploadHistory.toList()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isPersian) "اوج دانلود: ${formatCyberSpeed(peakDownload)}" else "Peak Down: ${formatCyberSpeed(peakDownload)}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            color = ElectricCyan
                        )
                        Text(
                            text = if (isPersian) "اوج آپلود: ${formatCyberSpeed(peakUpload)}" else "Peak Up: ${formatCyberSpeed(peakUpload)}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            color = MetallicSilver
                        )
                    }
                }
            }

            // Summary KPI Tiles
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TelemetryMetricTile(
                        label = if (isPersian) "کل مصرف داده" else "Total Data",
                        value = formatCyberBytes(totalConsumed),
                        icon = Icons.Filled.DataUsage,
                        accentColor = ElectricCyan,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryMetricTile(
                        label = if (isPersian) "مدت سشن فعال" else "Session Time",
                        value = formatCyberDuration(sessionStats.connectedDurationSeconds),
                        icon = Icons.Filled.Timer,
                        accentColor = PremiumTeal,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TelemetryMetricTile(
                        label = if (isPersian) "دریافت سشن" else "Session RX",
                        value = formatCyberBytes(sessionStats.totalBytesReceived),
                        icon = Icons.Filled.ArrowDownward,
                        accentColor = StatusConnected,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryMetricTile(
                        label = if (isPersian) "ارسال سشن" else "Session TX",
                        value = formatCyberBytes(sessionStats.totalBytesSent),
                        icon = Icons.Filled.ArrowUpward,
                        accentColor = MetallicSilver,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TelemetryMetricTile(
                        label = if (isPersian) "پینگ تونل" else "Tunnel Ping",
                        value = if (sessionStats.realTunnelLatencyMs > 0) "${sessionStats.realTunnelLatencyMs} ms" else "-- ms",
                        icon = Icons.Filled.Speed,
                        accentColor = if (sessionStats.realTunnelLatencyMs in 1..150) StatusConnected else StatusConnecting,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryMetricTile(
                        label = if (isPersian) "امتیاز پایداری" else "Stability",
                        value = if (vpnState == VpnConnectionState.CONNECTED) "${sessionStats.stabilityScore}%" else "--%",
                        icon = Icons.Filled.NetworkCheck,
                        accentColor = PremiumTeal,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Real Node Latency Distribution Chart
            item {
                CyberGlassCard(contentPadding = 16.dp) {
                    Text(
                        text = if (isPersian) "توزیع پینگ واقعی نودها" else "Measured Node Latency Comparison",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextCyberPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (testedConfigs.isEmpty()) {
                        Text(
                            text = if (isPersian) "هنوز پینگ واقعی برای نودها ثبت نشده است. از تب سرورها تست پینگ بگیرید."
                            else "No verified node pings yet. Run a real ping test from the Servers tab.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextCyberSecondary
                        )
                    } else {
                        LatencyDistributionBarChart(
                            bars = testedConfigs.map { it.name.take(8) to it.pingMs }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            testedConfigs.take(4).forEach { cfg ->
                                Text(
                                    text = "${cfg.name.take(6)}: ${cfg.pingMs}ms",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 9.sp,
                                    color = MetallicSilver
                                )
                            }
                        }
                    }
                }
            }

            // Connection History Header
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.History, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPersian) "تاریخچه اتصالات و رویدادها" else "Connection History",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextCyberPrimary
                    )
                }
            }

            if (connectionHistoryLogs.isEmpty()) {
                item {
                    CyberGlassCard(contentPadding = 16.dp) {
                        Text(
                            text = if (isPersian) "هنوز سابقه اتصالی ثبت نشده است." else "No recorded connection events yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextCyberSecondary
                        )
                    }
                }
            } else {
                items(connectionHistoryLogs, key = { it.id.ifBlank { "${it.action}_${it.details.hashCode()}" } }) { log ->
                    val statusTint = if (log.status == "SUCCESS") StatusConnected else StatusError
                    val formattedTime = remember(log.createdAt) {
                        val date = log.createdAt?.toDate() ?: Date()
                        SimpleDateFormat("MM-dd HH:mm", Locale.US).format(date)
                    }
                    CyberGlassCard(contentPadding = 12.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(statusTint)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = log.action,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextCyberPrimary
                                    )
                                    Text(
                                        text = log.details,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextCyberSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                text = formattedTime,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                color = MetallicSilver
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}
