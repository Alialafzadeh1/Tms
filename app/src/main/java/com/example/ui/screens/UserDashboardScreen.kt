package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.vpn.VpnController
import com.example.vpn.engine.NationalIntranetPiercer
import com.example.vpn.engine.ConfigAutoHealer
import com.example.vpn.engine.DynamicIpDnsRotator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VpnConfig
import com.example.data.model.VpnConnectionState
import com.example.data.model.VpnSessionStats
import com.example.ui.theme.BorderGoldHighlight
import com.example.ui.theme.BorderGoldMuted
import com.example.ui.theme.CardSurfaceDark
import com.example.ui.theme.DeepBlackBackground
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.StatusWarningAmber
import com.example.ui.theme.TextGraySecondary
import com.example.ui.theme.TextWhitePrimary

@Composable
fun UserDashboardScreen(
    vpnState: VpnConnectionState,
    sessionStats: VpnSessionStats,
    configs: List<VpnConfig>,
    selectedConfig: VpnConfig?,
    isPersian: Boolean,
    onConnectVpn: () -> Unit,
    onDisconnectVpn: () -> Unit,
    onSelectConfig: (VpnConfig) -> Unit,
    onImportConfig: (String) -> Unit,
    onImportBatch: (String) -> Unit,
    onAddManual: (String, String, String, Int, String) -> Unit,
    onOpenRadar: () -> Unit,
    onRequestAdminSpace: () -> Unit,
    onToggleLanguage: () -> Unit
) {
    val context = LocalContext.current
    var showServerSelector by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var rawInputText by remember { mutableStateOf("") }
    val nationalIntranetMode by VpnController.nationalIntranetMode.collectAsStateWithLifecycle()
    val iranRelayServer by VpnController.iranRelayServer.collectAsStateWithLifecycle()
    var showRelaySettingsDialog by remember { mutableStateOf(false) }
    var customRelayInput by remember { mutableStateOf(iranRelayServer) }
    val isAutoRotateEnabled by DynamicIpDnsRotator.isAutoRotateEnabled.collectAsStateWithLifecycle()
    val countdownSeconds by DynamicIpDnsRotator.countdownSeconds.collectAsStateWithLifecycle()
    val currentCleanIp by DynamicIpDnsRotator.currentCleanIp.collectAsStateWithLifecycle()
    val currentDns by DynamicIpDnsRotator.currentDns.collectAsStateWithLifecycle()
    val rotationCount by DynamicIpDnsRotator.rotationCount.collectAsStateWithLifecycle()
    var showHealDialog by remember { mutableStateOf(false) }
    var healedInfoText by remember { mutableStateOf("") }

    // Pulse animation for 3D connection ring
    val infiniteTransition = rememberInfiniteTransition(label = "UserVpnPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val isConnected = vpnState == VpnConnectionState.CONNECTED
    val isConnecting = vpnState == VpnConnectionState.CONNECTING

    // Neon Glow Colors
    val primaryNeonColor = if (isConnected) EmeraldGreen else MetallicGold
    val secondaryNeonColor = if (isConnected) Color(0xFF00B0FF) else Color(0xFFFF9100)

    val glassBrush = Brush.linearGradient(
        listOf(
            Color(0x33252E3E),
            Color(0x1A141A26),
            Color(0x28202A3A)
        )
    )
    val borderNeonBrush = Brush.linearGradient(
        listOf(
            primaryNeonColor.copy(alpha = 0.8f),
            Color(0x22FFFFFF),
            primaryNeonColor.copy(alpha = 0.4f)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        DeepBlackBackground,
                        Color(0xFF080B12),
                        Color(0xFF0D121B),
                        DeepBlackBackground
                    )
                )
            )
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar: Brand, Admin Portal Gate, Language
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF191D26))
                        .border(BorderStroke(1.5.dp, MetallicGold), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "TMS Logo",
                        tint = MetallicGold,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "TMS VPN",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MetallicGold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (isPersian) "فضای کاربری امن" else "Secure User Portal",
                        fontSize = 10.sp,
                        color = TextGraySecondary
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Admin Space Gate Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33282215))
                        .border(BorderStroke(1.dp, BorderGoldHighlight), RoundedCornerShape(12.dp))
                        .clickable { onRequestAdminSpace() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = "Admin Portal",
                            tint = MetallicGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPersian) "پنل مدیریت" else "Admin",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MetallicGold
                        )
                    }
                }

                // Language switcher
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF141822))
                        .border(BorderStroke(0.8.dp, BorderGoldMuted), RoundedCornerShape(10.dp))
                        .clickable { onToggleLanguage() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isPersian) "EN" else "فا",
                        color = MetallicGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3D Glass Server Selector Capsule
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(BorderStroke(1.dp, borderNeonBrush), RoundedCornerShape(18.dp))
                .clickable { showServerSelector = true },
            colors = CardDefaults.cardColors(containerColor = Color(0x2B151C28)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF161C26))
                            .border(BorderStroke(1.dp, primaryNeonColor), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (selectedConfig?.protocol?.lowercase()) {
                                "vless" -> "VL"
                                "vmess" -> "VM"
                                "trojan" -> "TR"
                                "shadowsocks" -> "SS"
                                else -> "VPN"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = primaryNeonColor
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = selectedConfig?.name?.ifBlank { "انتخاب سرور وی‌پی‌ان" } ?: "انتخاب سرور وی‌پی‌ان",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhitePrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = selectedConfig?.server?.ifBlank { "لمس برای تغییر نود" } ?: "لمس برای انتخاب سرور",
                                fontSize = 11.sp,
                                color = TextGraySecondary
                            )
                            if (selectedConfig != null && selectedConfig.pingMs > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "• ${selectedConfig.pingMs}ms",
                                    fontSize = 11.sp,
                                    color = EmeraldGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Select",
                    tint = MetallicGold,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        // 3D HOLOGRAPHIC CONNECTION HUB (Big Glowing Glass Orb)
        Box(
            modifier = Modifier
                .size(240.dp)
                .scale(if (isConnected || isConnecting) pulseScale else 1f),
            contentAlignment = Alignment.Center
        ) {
            // Concentric Neon Halo Rings
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                primaryNeonColor.copy(alpha = if (isConnected) 0.25f else 0.12f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .border(
                        BorderStroke(
                            2.dp,
                            Brush.sweepGradient(
                                listOf(
                                    primaryNeonColor,
                                    Color(0x33FFFFFF),
                                    secondaryNeonColor,
                                    primaryNeonColor
                                )
                            )
                        ),
                        CircleShape
                    )
            )

            // Inner 3D Glass Sphere
            Box(
                modifier = Modifier
                    .size(165.dp)
                    .shadow(16.dp, CircleShape, spotColor = primaryNeonColor)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF222C3D),
                                Color(0xFF131822),
                                Color(0xFF090C12)
                            )
                        )
                    )
                    .border(BorderStroke(2.dp, borderNeonBrush), CircleShape)
                    .clickable {
                        if (isConnected) onDisconnectVpn() else onConnectVpn()
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(46.dp),
                            color = MetallicGold,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Toggle VPN",
                            tint = if (isConnected) EmeraldGreen else MetallicGold,
                            modifier = Modifier.size(52.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = when (vpnState) {
                            VpnConnectionState.CONNECTED -> if (isPersian) "متصل" else "CONNECTED"
                            VpnConnectionState.CONNECTING -> if (isPersian) "در حال اتصال" else "CONNECTING"
                            VpnConnectionState.DISCONNECTING -> if (isPersian) "قطع اتصال" else "DISCONNECTING"
                            else -> if (isPersian) "اتصال" else "CONNECT"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) EmeraldGreen else MetallicGold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Connection duration counter
        AnimatedVisibility(visible = isConnected) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x33101C1A))
                    .border(BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.5f)), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "زمان اتصال: ${formatDuration(sessionStats.connectedDurationSeconds)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldGreen
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3D DUAL NEON SPEEDOMETER GAUGES
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Download Speed Neon Glass Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(
                        BorderStroke(0.8.dp, Brush.linearGradient(listOf(Color(0xFF00E676), Color(0x22FFFFFF)))),
                        RoundedCornerShape(18.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = Color(0x1F15222E)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Download",
                            tint = EmeraldGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPersian) "سرعت دانلود" else "Download",
                            fontSize = 11.sp,
                            color = TextGraySecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = formatSpeed(sessionStats.downloadSpeedBytesPerSec),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreen
                    )
                }
            }

            // Upload Speed Neon Glass Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(
                        BorderStroke(0.8.dp, Brush.linearGradient(listOf(MetallicGold, Color(0x22FFFFFF)))),
                        RoundedCornerShape(18.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = Color(0x1F221E15)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Upload",
                            tint = MetallicGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPersian) "سرعت آپلود" else "Upload",
                            fontSize = 11.sp,
                            color = TextGraySecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = formatSpeed(sessionStats.uploadSpeedBytesPerSec),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MetallicGold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ANTI-FILTER RADAR CAPSULE
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, BorderGoldHighlight), RoundedCornerShape(18.dp))
                .clickable { onOpenRadar() },
            colors = CardDefaults.cardColors(containerColor = Color(0x2E181D2A)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF131720))
                            .border(BorderStroke(1.dp, EmeraldGreen), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = "Radar",
                            tint = EmeraldGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = if (isPersian) "رادار عبور از فیلترینگ تلگرام و اینستاگرام" else "Anti-Filter Radar for Telegram & Insta",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhitePrimary
                        )
                        Text(
                            text = if (isConnected) "آماده بازگشایی بدون اختلال و قطعی" else "تست زنده دسترسی به شبکه‌ها",
                            fontSize = 11.sp,
                            color = if (isConnected) EmeraldGreen else TextGraySecondary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF19202E))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = "بررسی ➔", fontSize = 11.sp, color = MetallicGold, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 🛡️ NATIONAL INTRANET PIERCER (سپر نفوذ و عبور از اینترنت ملی)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    BorderStroke(
                        1.2.dp,
                        if (nationalIntranetMode) EmeraldGreen else BorderGoldHighlight
                    ),
                    RoundedCornerShape(18.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (nationalIntranetMode) Color(0x3D0D2E1F) else Color(0x2E181D2A)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
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
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF131720))
                                .border(
                                    BorderStroke(1.dp, if (nationalIntranetMode) EmeraldGreen else MetallicGold),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = "National Intranet Piercer",
                                tint = if (nationalIntranetMode) EmeraldGreen else MetallicGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isPersian) "سپر نفوذ از اینترنت ملی" else "National Intranet Piercer",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (nationalIntranetMode) EmeraldGreen else MetallicGold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (nationalIntranetMode) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFFFFD700).copy(alpha = 0.15f))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (nationalIntranetMode) "فعال" else "آماده‌به‌کار",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (nationalIntranetMode) EmeraldGreen else MetallicGold
                                    )
                                }
                            }
                            Text(
                                text = if (nationalIntranetMode)
                                    "اتصال اضطراری به سرور خارج از طریق پل داخلی و استتار ترافیک مجاز"
                                else
                                    "اتصال به سرور اختصاصی حتی با قطعی کامل اینترنت بین‌الملل",
                                fontSize = 11.sp,
                                color = if (nationalIntranetMode) TextWhitePrimary else TextGraySecondary
                            )
                        }
                    }

                    Switch(
                        checked = nationalIntranetMode,
                        onCheckedChange = {
                            VpnController.toggleNationalIntranetMode()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF12141A),
                            checkedTrackColor = EmeraldGreen,
                            uncheckedThumbColor = Color(0xFF888888),
                            uncheckedTrackColor = Color(0xFF222632)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF141924))
                        .clickable {
                            customRelayInput = iranRelayServer
                            showRelaySettingsDialog = true
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "پل سرور واسط ایران (Relay): ",
                            fontSize = 11.sp,
                            color = TextGraySecondary
                        )
                        Text(
                            text = iranRelayServer.ifBlank { "خودکار (دیتاسنتر آسیاتک/مبین‌نت/افرانت)" },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MetallicGold
                        )
                    }
                    Text(
                        text = "تنظیم ⚙",
                        fontSize = 11.sp,
                        color = MetallicGold,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 🔄 REVERSE-ENGINEERING & 10s AUTO-ROTATION ENGINE (موتور مهندسی معکوس و چرخش خودکار ۱۰ ثانیه‌ای)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    BorderStroke(
                        1.2.dp,
                        if (isAutoRotateEnabled) EmeraldGreen else BorderGoldHighlight
                    ),
                    RoundedCornerShape(18.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isAutoRotateEnabled) Color(0x350A241C) else Color(0x2E181D2A)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
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
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF131720))
                                .border(
                                    BorderStroke(1.dp, if (isAutoRotateEnabled) EmeraldGreen else MetallicGold),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Autorenew,
                                contentDescription = "Auto Rotate Engine",
                                tint = if (isAutoRotateEnabled) EmeraldGreen else MetallicGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isPersian) "چرخش خودکار ۱۰ ثانیه‌ای IP و DNS" else "10s IP & DNS Auto-Hop",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAutoRotateEnabled) EmeraldGreen else MetallicGold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isAutoRotateEnabled) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFFFFD700).copy(alpha = 0.15f))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isAutoRotateEnabled) "فعال" else "آماده",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAutoRotateEnabled) EmeraldGreen else MetallicGold
                                    )
                                }
                            }
                            Text(
                                text = if (isAutoRotateEnabled)
                                    "تغییر پیوسته آی‌پی تمیز و دی‌ان‌اس هر ۱۰ ثانیه برای دور زدن فیلترینگ و پایداری"
                                else
                                    "تطبیق هوشمند هر ۱۰ ثانیه برای جلوگیری از شناسایی توسط فایروال",
                                fontSize = 11.sp,
                                color = if (isAutoRotateEnabled) TextWhitePrimary else TextGraySecondary
                            )
                        }
                    }

                    Switch(
                        checked = isAutoRotateEnabled,
                        onCheckedChange = {
                            DynamicIpDnsRotator.toggleAutoRotation()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF12141A),
                            checkedTrackColor = EmeraldGreen,
                            uncheckedThumbColor = Color(0xFF888888),
                            uncheckedTrackColor = Color(0xFF222632)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Real-time Rotator Dashboard Status Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF141924))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⏱️ چرخش بعدی: ",
                                fontSize = 11.sp,
                                color = TextGraySecondary
                            )
                            Text(
                                text = "$countdownSeconds ثانیه (جهش #$rotationCount)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAutoRotateEnabled) EmeraldGreen else MetallicGold
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🌐 آی‌پی تمیز: ",
                                fontSize = 10.sp,
                                color = TextGraySecondary
                            )
                            Text(
                                text = currentCleanIp,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextWhitePrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "🛡️ DNS: ",
                                fontSize = 10.sp,
                                color = TextGraySecondary
                            )
                            Text(
                                text = currentDns,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = EmeraldGreen
                            )
                        }
                    }

                    Button(
                        onClick = { DynamicIpDnsRotator.forceHopNow() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E283A),
                            contentColor = MetallicGold
                        ),
                        border = BorderStroke(0.8.dp, BorderGoldMuted),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "جهش فوری", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 🛠️ REVERSE ENGINEERING & AUTO-HEAL BUTTON
                Button(
                    onClick = {
                        val target = selectedConfig ?: configs.firstOrNull()
                        if (target != null) {
                            val healed = ConfigAutoHealer.heal(target)
                            onImportConfig(healed.rawConfig)
                            onSelectConfig(healed)
                            healedInfoText = "✅ کانفیگ «${healed.name}» با موفقیت مهندسی معکوس شد!\n\n" +
                                    "🌐 آی‌پی تمیز کلودفلر: ${healed.server}:${healed.port}\n" +
                                    "🔒 استتار SNI بدون اختلال: speedtest.net\n" +
                                    "⚡ ریزقطعه‌بندی Anti-DPI سه‌مرحله‌ای فعال شد."
                            showHealDialog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1F281E),
                        contentColor = EmeraldGreen
                    ),
                    border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPersian) "🛠️ مهندسی معکوس و احیای کانفیگ انتخابی (Auto-Heal)" else "Reverse-Engineer & Auto-Heal Config",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 📢 OFFICIAL APP CHANNELS (کانال‌های رسمی برنامه TMS Time)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    BorderStroke(1.2.dp, Brush.horizontalGradient(listOf(MetallicGold, Color(0xFF229ED9), EmeraldGreen))),
                    RoundedCornerShape(18.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = Color(0x35141D29)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF131720))
                                .border(BorderStroke(1.dp, MetallicGold), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Campaign,
                                contentDescription = "Official Channels",
                                tint = MetallicGold,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = if (isPersian) "کانال‌های رسمی برنامه (TMS Time)" else "Official Channels (TMS Time)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MetallicGold
                            )
                            Text(
                                text = if (isPersian) "ارائه کانفیگ‌های فعال حتی در زمان قطعی کامل اینترنت" else "Active Configs During Outages",
                                fontSize = 10.sp,
                                color = EmeraldGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E283A))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "ضد قطعی ⚡",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MetallicGold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Explanatory Note Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF101622))
                        .border(BorderStroke(0.6.dp, Color(0x33FFFFFF)), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = if (isPersian)
                            "💡 داخل این کانال‌ها حتی در زمان قطعی اینترنت و ملی شدن شبکه، کانفیگ‌های کاملاً فعال، تست‌شده و پرسرعت اختصاصی به شما ارائه می‌شود تا بتوانید به راحتی، پایدار و بدون فیلتر متصل شوید."
                        else
                            "💡 Get guaranteed active, high-speed and tested configs directly through our official channels even during total network disruptions or national intranet events.",
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = TextWhitePrimary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Telegram Channel Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(0.8.dp, Color(0xFF229ED9).copy(alpha = 0.6f)), RoundedCornerShape(12.dp))
                        .clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/TMS_TIME")).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "خطا در باز کردن تلگرام: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111D2B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF229ED9).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "✈️", fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isPersian) "کانال تلگرام TMS Time" else "Telegram Channel",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF29B6F6)
                                )
                                Text(
                                    text = "https://t.me/TMS_TIME",
                                    fontSize = 10.sp,
                                    color = TextGraySecondary
                                )
                            }
                        }

                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/TMS_TIME")).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "خطا در باز کردن تلگرام: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF229ED9),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "عضویت", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Rubika Channel Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(0.8.dp, Color(0xFF9C27B0).copy(alpha = 0.6f)), RoundedCornerShape(12.dp))
                        .clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://rubika.ir/TMS_Time")).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "خطا در باز کردن روبیکا: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1128)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF9C27B0).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "🟣", fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isPersian) "کانال روبیکا TMS Time" else "Rubika Channel",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFCE93D8)
                                )
                                Text(
                                    text = "https://rubika.ir/TMS_Time",
                                    fontSize = 10.sp,
                                    color = TextGraySecondary
                                )
                            }
                        }

                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://rubika.ir/TMS_Time")).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "خطا در باز کردن روبیکا: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF8E24AA),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "عضویت", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Quick Action Buttons: Copy Links & Share
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clipText = "📢 کانال‌های رسمی TMS VPN (دریافت کانفیگ فعال حتی در زمان قطعی اینترنت):\n\n" +
                                    "🔹 تلگرام:\nhttps://t.me/TMS_TIME\n\n" +
                                    "🔸 روبیکا:\nhttps://rubika.ir/TMS_Time"
                            clipboard?.setPrimaryClip(ClipData.newPlainText("TMS Channels", clipText))
                            Toast.makeText(context, "📋 لینک هر دو کانال تلگرام و روبیکا کپی شد!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF17202C),
                            contentColor = MetallicGold
                        ),
                        border = BorderStroke(0.8.dp, BorderGoldMuted),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (isPersian) "کپی لینک‌ها" else "Copy Links", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            try {
                                val shareText = "📢 کانال‌های رسمی TMS VPN برای دریافت کانفیگ‌های فعال ضد فیلتر:\n\n" +
                                        "🔹 تلگرام: https://t.me/TMS_TIME\n" +
                                        "🔸 روبیکا: https://rubika.ir/TMS_Time"
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "اشتراک‌گذاری کانال‌ها"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "خطا در اشتراک‌گذاری", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF14241B),
                            contentColor = EmeraldGreen
                        ),
                        border = BorderStroke(0.8.dp, EmeraldGreen.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (isPersian) "اشتراک‌گذاری" else "Share", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Import Button
        Button(
            onClick = { showImportDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF181C26),
                contentColor = MetallicGold
            ),
            border = BorderStroke(1.dp, BorderGoldMuted),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isPersian) "افزودن کانفیگ جدید (لینک VLESS, VMess, Trojan, SS)" else "Add New VPN Config",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    // SERVER SELECTOR BOTTOM SHEET / DIALOG
    if (showServerSelector) {
        AlertDialog(
            onDismissRequest = { showServerSelector = false },
            confirmButton = {
                TextButton(onClick = { showServerSelector = false }) {
                    Text("بستن / Close", color = MetallicGold)
                }
            },
            title = {
                Text(
                    text = "انتخاب سرور وی‌پی‌ان / Select Server",
                    color = MetallicGold,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(configs, key = { it.id }) { cfg ->
                        val isSelected = selectedConfig?.id == cfg.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (isSelected) EmeraldGreen else BorderGoldMuted
                                    ),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    onSelectConfig(cfg)
                                    showServerSelector = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF14241E) else Color(0xFF12151D)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cfg.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = TextWhitePrimary
                                    )
                                    Text(
                                        text = "${cfg.protocol.uppercase()} • ${cfg.server}:${cfg.port}",
                                        fontSize = 11.sp,
                                        color = TextGraySecondary
                                    )
                                }
                                if (cfg.pingMs > 0) {
                                    Text(
                                        text = "${cfg.pingMs}ms",
                                        fontSize = 11.sp,
                                        color = EmeraldGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            containerColor = CardSurfaceDark,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // QUICK IMPORT DIALOG
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val text = rawInputText.trim()
                        if (text.isNotBlank()) {
                            if (text.lines().size > 1) {
                                onImportBatch(text)
                            } else {
                                onImportConfig(text)
                            }
                            showImportDialog = false
                            rawInputText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = Color(0xFF12141A)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("ثبت و ذخیره", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("انصراف", color = TextGraySecondary)
                }
            },
            title = {
                Text(
                    text = "افزودن کانفیگ VPN",
                    color = MetallicGold,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "لینک کانفیگ (vless://, vmess://, trojan://, ss://) را در کادر زیر جای‌گذاری کنید:",
                        fontSize = 12.sp,
                        color = TextWhitePrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = rawInputText,
                        onValueChange = { rawInputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        placeholder = { Text("vless://... یا vmess://...", fontSize = 11.sp, color = TextGraySecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = BorderGoldMuted,
                            focusedTextColor = TextWhitePrimary,
                            unfocusedTextColor = TextWhitePrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Paste from clipboard button
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = clipboard.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                val pasteText = clip.getItemAt(0).text?.toString() ?: ""
                                if (pasteText.isNotBlank()) {
                                    rawInputText = pasteText
                                }
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp), tint = MetallicGold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("جای‌گذاری از کلیپ‌بورد (Paste)", color = MetallicGold, fontSize = 12.sp)
                    }
                }
            },
            containerColor = CardSurfaceDark,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // RELAY SERVER SETTINGS MODAL
    if (showRelaySettingsDialog) {
        AlertDialog(
            onDismissRequest = { showRelaySettingsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPersian) "تنظیم پل سرور واسط ایران (Relay Bridge)" else "Iran Relay Bridge Settings",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MetallicGold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isPersian)
                            "در زمان ملی شدن اینترنت، ترافیک شما ابتدا به این سرور داخلی ارسال شده و از طریق تونل اختصاصی نامتقارن به سرور خارج منتقل می‌شود:"
                        else
                            "During national internet blackout, traffic is routed through this domestic node to reach foreign servers:",
                        fontSize = 11.sp,
                        color = TextWhitePrimary
                    )

                    OutlinedTextField(
                        value = customRelayInput,
                        onValueChange = { customRelayInput = it },
                        label = { Text("آدرس IP یا دامنه پل ایران") },
                        placeholder = { Text("مثال: 185.143.232.10 یا relay.domain.ir") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = BorderGoldMuted,
                            focusedTextColor = TextWhitePrimary,
                            unfocusedTextColor = TextWhitePrimary
                        ),
                        singleLine = true
                    )

                    Text(
                        text = "پل‌های پیش‌فرض دیتاسنترهای داخلی ایران:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MetallicGold
                    )

                    NationalIntranetPiercer.DEFAULT_IRAN_RELAYS.forEach { relay ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { customRelayInput = relay }
                                .border(BorderStroke(0.6.dp, BorderGoldMuted), RoundedCornerShape(8.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF141924))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = relay, fontSize = 11.sp, color = TextWhitePrimary)
                                Text(text = "انتخاب", fontSize = 10.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        VpnController.setIranRelayServer(customRelayInput.trim())
                        showRelaySettingsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color(0xFF0C1610)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = "ذخیره و اعمال", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRelaySettingsDialog = false }) {
                    Text(text = "انصراف", color = TextGraySecondary)
                }
            },
            containerColor = CardSurfaceDark,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showHealDialog) {
        AlertDialog(
            onDismissRequest = { showHealDialog = false },
            confirmButton = {
                Button(
                    onClick = { showHealDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color(0xFF0C1610)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = "متوجه شدم و اعمال شد", fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "موتور مهندسی معکوس فعال شد", color = EmeraldGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = healedInfoText,
                    color = TextWhitePrimary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            containerColor = CardSurfaceDark,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
        bytesPerSec >= 1024 -> String.format("%.0f KB/s", bytesPerSec / 1024.0)
        else -> "$bytesPerSec B/s"
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}
