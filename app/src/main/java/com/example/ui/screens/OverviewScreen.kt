package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OverviewMetrics
import com.example.data.model.VpnConfig
import com.example.data.model.VpnConnectionState
import com.example.data.model.VpnServer
import com.example.data.model.VpnSessionStats
import com.example.ui.theme.BorderGoldHighlight
import com.example.ui.theme.BorderGoldMuted
import com.example.ui.theme.CardSurfaceDark
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.StatusWarningAmber
import com.example.ui.theme.TextGoldMuted
import com.example.ui.theme.TextGraySecondary
import com.example.ui.theme.TextWhitePrimary

@Composable
fun OverviewScreen(
    metrics: OverviewMetrics,
    configs: List<VpnConfig>,
    servers: List<VpnServer>,
    vpnState: VpnConnectionState,
    vpnStats: VpnSessionStats,
    selectedConfig: VpnConfig?,
    isPersian: Boolean,
    onConnectVpn: () -> Unit,
    onDisconnectVpn: () -> Unit,
    onAutoSelectBest: () -> Unit
) {
    val isConnected = vpnState == VpnConnectionState.CONNECTED
    val isConnecting = vpnState == VpnConnectionState.CONNECTING
    val isDisconnecting = vpnState == VpnConnectionState.DISCONNECTING

    // Glow pulse animation for connection button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnected || isConnecting) 1.08f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header & Node Status
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, BorderGoldMuted), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isPersian) "داشبورد اتصال و وضعیت تونل" else "VPN Tunnel Controller",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MetallicGold
                            )
                            Text(
                                text = if (isPersian) "مسیریابی تمام ترافیک دستگاه از طریق VpnService بومی" else "Native Android VpnService tunnel routing",
                                fontSize = 11.sp,
                                color = TextGraySecondary
                            )
                        }

                        // Live Status Pill
                        val (pillColor, pillText) = when (vpnState) {
                            VpnConnectionState.CONNECTED -> Pair(EmeraldGreen, if (isPersian) "متصل و ایمن" else "CONNECTED")
                            VpnConnectionState.CONNECTING -> Pair(StatusWarningAmber, if (isPersian) "در حال اتصال..." else "CONNECTING")
                            VpnConnectionState.DISCONNECTING -> Pair(StatusWarningAmber, if (isPersian) "در حال قطع..." else "DISCONNECTING")
                            VpnConnectionState.ERROR -> Pair(StatusOfflineRed, if (isPersian) "خطای اتصال" else "ERROR")
                            VpnConnectionState.DISCONNECTED -> Pair(Color(0xFF888888), if (isPersian) "قطع / آماده" else "DISCONNECTED")
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(pillColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                .border(BorderStroke(1.dp, pillColor.copy(alpha = 0.6f)), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(modifier = Modifier.size(7.dp).background(pillColor, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = pillText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = pillColor)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Active Node Selector Card
                    val activeNode = selectedConfig ?: configs.firstOrNull()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0A0C12), RoundedCornerShape(12.dp))
                            .border(BorderStroke(0.8.dp, BorderGoldMuted), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(GoldContainer, CircleShape)
                                    .border(BorderStroke(1.dp, MetallicGold), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = MetallicGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = activeNode?.name ?: (if (isPersian) "هیچ کانفیگی انتخاب نشده" else "No config chosen"),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhitePrimary
                                )
                                Text(
                                    text = if (activeNode != null)
                                        "${activeNode.protocol.uppercase()} • ${activeNode.server}:${activeNode.port} ${if (activeNode.pingMs > 0) "• ${activeNode.pingMs}ms" else ""}"
                                    else
                                        (if (isPersian) "کانفیگ مورد نظر را انتخاب یا ثبت کنید" else "Select or import a configuration"),
                                    fontSize = 10.sp,
                                    color = TextGraySecondary
                                )
                            }
                        }

                        // Auto-Select Best Server Button
                        OutlinedButton(
                            onClick = onAutoSelectBest,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MetallicGold),
                            border = BorderStroke(0.8.dp, BorderGoldHighlight),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isPersian) "بهترین نود" else "Auto-Best", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // GIANT LUXURY CONNECTION BUTTON
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, BorderGoldMuted), RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val buttonBorderColor = when {
                        isConnected -> EmeraldGreen
                        isConnecting -> StatusWarningAmber
                        else -> MetallicGold
                    }
                    val buttonBg = when {
                        isConnected -> Color(0xFF042014)
                        isConnecting -> Color(0xFF261D09)
                        else -> Color(0xFF161511)
                    }

                    // Circular Connect / Disconnect Action Button
                    Box(
                        modifier = Modifier
                            .size((140 * pulseScale).dp)
                            .background(buttonBg, CircleShape)
                            .border(
                                BorderStroke(
                                    if (isConnected) 3.5.dp else 2.5.dp,
                                    buttonBorderColor
                                ),
                                CircleShape
                            )
                            .clickable {
                                if (isConnected || isConnecting) {
                                    onDisconnectVpn()
                                } else {
                                    onConnectVpn()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (isConnecting || isDisconnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    color = buttonBorderColor,
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (isConnected) Icons.Default.Shield else Icons.Default.PowerSettingsNew,
                                    contentDescription = "VPN Power Switch",
                                    tint = buttonBorderColor,
                                    modifier = Modifier.size(46.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = when (vpnState) {
                                    VpnConnectionState.CONNECTED -> if (isPersian) "قطع اتصال" else "DISCONNECT"
                                    VpnConnectionState.CONNECTING -> if (isPersian) "در حال اتصال" else "CONNECTING"
                                    VpnConnectionState.DISCONNECTING -> if (isPersian) "قطع تونل..." else "STOPPING..."
                                    else -> if (isPersian) "اتصال امن" else "CONNECT"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = buttonBorderColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = when (vpnState) {
                            VpnConnectionState.CONNECTED -> if (isPersian) "اینترنت شما از تونل رمزنگاری شده عبور می‌کند" else "Your internet traffic is securely routed through TUN"
                            VpnConnectionState.CONNECTING -> if (isPersian) "در حال برقراری اینترفیس TUN و اتصال سوکت..." else "Establishing TUN interface and handshake..."
                            VpnConnectionState.ERROR -> vpnStats.errorMessage ?: (if (isPersian) "خطا در اتصال، مجدداً تلاش نمایید" else "Connection error, tap to retry")
                            else -> if (isPersian) "برای فعال‌سازی تونل VPN لمس کنید" else "Tap button above to establish VPN tunnel"
                        },
                        fontSize = 12.sp,
                        color = if (vpnState == VpnConnectionState.ERROR) StatusOfflineRed else TextWhitePrimary,
                        textAlign = TextAlign.Center
                    )

                    if (isConnected) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Timer, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatDuration(vpnStats.connectedDurationSeconds),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen
                            )
                        }
                    }
                }
            }
        }

        // REAL-TIME TRAFFIC & TELEMETRY HUD (Real numbers, no fake statistics!)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, BorderGoldMuted), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPersian) "سنجش زنده ترافیک و سرعت تونل" else "Live Tunnel Telemetry & Throughput",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MetallicGold
                            )
                        }

                        Text(
                            text = if (isConnected) (if (isPersian) "سنجش بومی فعال" else "Active Native Meter") else (if (isPersian) "آماده‌به‌کار" else "Standby"),
                            fontSize = 10.sp,
                            color = if (isConnected) EmeraldGreen else TextGraySecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4-Quadrant Real Telemetry Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Download Speed
                        TelemetryCell(
                            title = if (isPersian) "سرعت دانلود" else "Download",
                            value = formatSpeed(vpnStats.downloadSpeedBytesPerSec),
                            icon = Icons.Default.Download,
                            tint = EmeraldGreen,
                            modifier = Modifier.weight(1f)
                        )

                        // Upload Speed
                        TelemetryCell(
                            title = if (isPersian) "سرعت آپلود" else "Upload",
                            value = formatSpeed(vpnStats.uploadSpeedBytesPerSec),
                            icon = Icons.Default.Upload,
                            tint = MetallicGold,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Total Data Transferred
                        TelemetryCell(
                            title = if (isPersian) "حجم کل تبادل" else "Total Data",
                            value = formatBytes(vpnStats.totalBytesReceived + vpnStats.totalBytesSent),
                            icon = Icons.Default.SwapVert,
                            tint = TextWhitePrimary,
                            modifier = Modifier.weight(1f)
                        )

                        // Stability
                        TelemetryCell(
                            title = if (isPersian) "امتیاز پایداری" else "Stability",
                            value = if (isConnected) "${vpnStats.stabilityScore}%" else "--",
                            icon = Icons.Default.CheckCircle,
                            tint = if (isConnected) EmeraldGreen else TextGraySecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // System Health & Cloud Database Status
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, BorderGoldMuted), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = if (isPersian) "خلاصه سلامت زیرساخت و سرورها" else "Infrastructure Overview & Health",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MetallicGold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF090A0F), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = if (isPersian) "کل کانفیگ‌ها" else "Total Configs", fontSize = 10.sp, color = TextGraySecondary)
                            Text(text = "${metrics.totalConfigs}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MetallicGold)
                        }

                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderGoldMuted))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = if (isPersian) "سرورهای آنلاین" else "Active Nodes", fontSize = 10.sp, color = TextGraySecondary)
                            Text(text = "${metrics.activeServers} / ${metrics.totalServers}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                        }

                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderGoldMuted))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = if (isPersian) "پایگاه داده" else "Firestore", fontSize = 10.sp, color = TextGraySecondary)
                            Text(text = "Online", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryCell(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF0B0D14), RoundedCornerShape(10.dp))
            .border(BorderStroke(0.6.dp, BorderGoldMuted), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(tint.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, fontSize = 10.sp, color = TextGraySecondary)
                Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = tint)
            }
        }
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1024 * 1024 -> String.format("%.2f MB/s", bytesPerSec / (1024.0 * 1024.0))
        bytesPerSec >= 1024 -> String.format("%.1f KB/s", bytesPerSec / 1024.0)
        else -> "$bytesPerSec B/s"
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format("%02d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}
