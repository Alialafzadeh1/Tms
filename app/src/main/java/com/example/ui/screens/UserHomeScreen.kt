package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.VpnConfig
import com.example.data.model.VpnConnectionState
import com.example.data.model.VpnServer
import com.example.data.model.VpnSessionStats
import com.example.ui.components.CyberGlassCard
import com.example.ui.components.CyberStatusPill
import com.example.ui.components.HolographicVpnOrb
import com.example.ui.components.TelemetryMetricTile
import com.example.ui.components.formatCyberBytes
import com.example.ui.components.formatCyberDuration
import com.example.ui.components.formatCyberSpeed
import com.example.ui.components.inferCountryMeta
import com.example.ui.components.statusColorFor
import com.example.ui.theme.DeepGraphite
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.GlassBorderCyan
import com.example.ui.theme.GlassBorderMetallic
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
import com.example.vpn.VpnController
import com.example.vpn.engine.ConfigAutoHealer
import com.example.vpn.engine.DynamicIpDnsRotator
import com.example.vpn.engine.IspTuningReport
import com.example.vpn.engine.LeakCheckReport
import com.example.vpn.engine.NationalIntranetPiercer
import com.example.vpn.engine.RacingSummary
import com.example.vpn.engine.TunnelHealthReport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserHomeScreen(
    vpnState: VpnConnectionState,
    sessionStats: VpnSessionStats,
    configs: List<VpnConfig>,
    servers: List<VpnServer>,
    selectedConfig: VpnConfig?,
    isPersian: Boolean,
    onConnectVpn: () -> Unit,
    onDisconnectVpn: () -> Unit,
    onSelectConfig: (VpnConfig) -> Unit,
    onImportConfig: (String) -> Unit,
    onImportBatch: (String) -> Unit,
    onOpenServersTab: () -> Unit,
    onOpenRadar: () -> Unit,
    modifier: Modifier = Modifier,
    healthReport: TunnelHealthReport? = null,
    healthCheckInProgress: Boolean = false,
    isSubscriptionImporting: Boolean = false,
    onRunHealthCheck: () -> Unit = {},
    onFailoverNextNode: () -> Unit = {},
    onImportSubscription: (String) -> Unit = {},
    isRacingConnect: Boolean = false,
    racingSummary: RacingSummary? = null,
    onRacingConnect: () -> Unit = {},
    ispTuningReport: IspTuningReport? = null,
    isTuningIsp: Boolean = false,
    onRunIspAutoTune: () -> Unit = {},
    leakCheckReport: LeakCheckReport? = null,
    leakCheckInProgress: Boolean = false,
    onRunLeakCheck: () -> Unit = {}
) {
    val context = LocalContext.current
    val nationalIntranetMode by VpnController.nationalIntranetMode.collectAsStateWithLifecycle()
    val iranRelayServer by VpnController.iranRelayServer.collectAsStateWithLifecycle()
    val isAutoRotateEnabled by DynamicIpDnsRotator.isAutoRotateEnabled.collectAsStateWithLifecycle()
    val countdownSeconds by DynamicIpDnsRotator.countdownSeconds.collectAsStateWithLifecycle()
    val currentCleanIp by DynamicIpDnsRotator.currentCleanIp.collectAsStateWithLifecycle()
    val currentDns by DynamicIpDnsRotator.currentDns.collectAsStateWithLifecycle()
    val rotationCount by DynamicIpDnsRotator.rotationCount.collectAsStateWithLifecycle()

    var showCyberToolsSheet by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importSubscriptionMode by remember { mutableStateOf(false) }
    var subscriptionUrlInput by remember { mutableStateOf("") }
    var showRelayDialog by remember { mutableStateOf(false) }
    var rawInputText by remember { mutableStateOf("") }
    var customRelayInput by remember { mutableStateOf(iranRelayServer) }
    var healedInfoText by remember { mutableStateOf<String?>(null) }

    val isConnected = vpnState == VpnConnectionState.CONNECTED
    val isConnecting = vpnState == VpnConnectionState.CONNECTING
    val isDisconnecting = vpnState == VpnConnectionState.DISCONNECTING
    val stateAccent = statusColorFor(vpnState)
    val (flag, countryCode, countryName) = inferCountryMeta(selectedConfig, servers)

    val matchingServer = remember(selectedConfig, servers) {
        servers.firstOrNull {
            selectedConfig != null &&
                (it.host.equals(selectedConfig.server, ignoreCase = true) ||
                    it.name.equals(selectedConfig.name, ignoreCase = true))
        }
    }

    val displayedPingMs = when {
        sessionStats.realTunnelLatencyMs > 0 -> sessionStats.realTunnelLatencyMs
        selectedConfig != null && selectedConfig.pingMs > 0 -> selectedConfig.pingMs
        matchingServer != null && matchingServer.latencyMs > 0 -> matchingServer.latencyMs
        else -> -1
    }

    val totalSessionBytes = sessionStats.totalBytesReceived + sessionStats.totalBytesSent

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // B. MAIN CONNECTION AREA
            CyberStatusPill(state = vpnState, isPersian = isPersian)

            Spacer(modifier = Modifier.height(10.dp))

            HolographicVpnOrb(
                vpnState = vpnState,
                isPersian = isPersian,
                onToggleConnection = {
                    if (isConnected || isConnecting) onDisconnectVpn() else onConnectVpn()
                },
                size = 248.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedVisibility(
                visible = isConnected,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(StatusConnected.copy(alpha = 0.14f))
                        .border(BorderStroke(1.dp, StatusConnected.copy(alpha = 0.55f)), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("home_connected_banner"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = StatusConnected,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPersian) {
                                "متصل شد • ${selectedConfig?.name ?: "تونل امن"}"
                            } else {
                                "VPN Connected • ${selectedConfig?.name ?: "Secure Tunnel"}"
                            },
                            color = StatusConnected,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = formatCyberDuration(sessionStats.connectedDurationSeconds),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = StatusConnected
                    )
                }
            }

            if (isConnected) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(50.dp))
                        .background(ElevatedSurface)
                        .border(BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)), RoundedCornerShape(50.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                        .testTag("home_engine_mode_badge"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val engineText = buildString {
                        append(sessionStats.engineMode)
                        if (sessionStats.xrayVersion.isNotBlank()) {
                            append(" • v")
                            append(sessionStats.xrayVersion)
                        }
                    }
                    Text(
                        text = engineText,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ElectricCyan
                    )
                }
            }

            // Primary CONNECT / DISCONNECT Action Button
            Button(
                onClick = {
                    if (isConnected || isConnecting) onDisconnectVpn() else onConnectVpn()
                },
                enabled = !isDisconnecting,
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .height(50.dp)
                    .testTag("home_connect_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) ElevatedSurface else ElectricCyan,
                    contentColor = if (isConnected) StatusConnected else Color(0xFF001A22)
                ),
                border = if (isConnected) BorderStroke(1.2.dp, StatusConnected.copy(alpha = 0.7f)) else null
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Filled.PowerOff else Icons.Filled.PowerSettingsNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (vpnState) {
                        VpnConnectionState.CONNECTED -> if (isPersian) "قطع اتصال" else "DISCONNECT"
                        VpnConnectionState.CONNECTING -> if (isPersian) "در حال اتصال..." else "CONNECTING..."
                        VpnConnectionState.DISCONNECTING -> if (isPersian) "در حال قطع اتصال..." else "DISCONNECTING..."
                        VpnConnectionState.ERROR -> if (isPersian) "اتصال امن" else "RETRY CONNECTION"
                        VpnConnectionState.DISCONNECTED -> if (isPersian) "اتصال امن" else "CONNECT"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Actions: Happy Eyeballs Racing Connect + ISP Clean IP & Fragment Tuner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onRacingConnect,
                    enabled = !isRacingConnect && !isDisconnecting,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("home_racing_connect_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, PremiumTeal.copy(alpha = 0.65f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    if (isRacingConnect) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp,
                            color = PremiumTeal
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = PremiumTeal,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPersian) "اتصال مسابقه‌ای" else "Racing Connect",
                        color = PremiumTeal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onRunIspAutoTune,
                    enabled = !isTuningIsp,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("home_isp_tune_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.55f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    if (isTuningIsp) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp,
                            color = ElectricCyan
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPersian) "تیونر اپراتور / IP" else "ISP Auto-Tune",
                        color = ElectricCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (ispTuningReport != null) {
                Spacer(modifier = Modifier.height(10.dp))
                CyberGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_isp_report_badge"),
                    accentColor = ElectricCyan.copy(alpha = 0.45f),
                    contentPadding = 12.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isPersian) {
                                    "پروفایل شبکه: ${ispTuningReport.transportLabel} (${ispTuningReport.operatorLabel})"
                                } else {
                                    "Network: ${ispTuningReport.networkType.labelEn} (${ispTuningReport.operatorLabel})"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = TextCyberPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    if (ispTuningReport.bestCleanIp.isNotBlank() && ispTuningReport.bestCleanIpLatencyMs > 0) {
                                        append("Clean IP: ${ispTuningReport.bestCleanIp} (${ispTuningReport.bestCleanIpLatencyMs}ms)")
                                    } else {
                                        append(if (isPersian) "Clean IP در دسترس یافت نشد" else "No Clean IP reachable")
                                    }
                                    append(" • Frag ${ispTuningReport.fragmentProfile.length}/${ispTuningReport.fragmentProfile.interval}")
                                },
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = if (ispTuningReport.bestCleanIpLatencyMs > 0) StatusConnected else StatusConnecting
                            )
                        }
                    }
                }
            }

            if (racingSummary != null && racingSummary.winner != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isPersian) {
                        "برنده تست مسابقه‌ای (${racingSummary.testedCount} سرور): ${racingSummary.winner.name} • ${racingSummary.winnerDelayMs}ms"
                    } else {
                        "Racing winner (${racingSummary.testedCount} tested): ${racingSummary.winner.name} • ${racingSummary.winnerDelayMs}ms"
                    },
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = PremiumTeal,
                    modifier = Modifier.testTag("home_racing_winner_text")
                )
            }

            AnimatedVisibility(
                visible = vpnState == VpnConnectionState.ERROR || !sessionStats.errorMessage.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = sessionStats.errorMessage.orEmpty().ifBlank {
                        if (isPersian) "خطا در برقراری اتصال VPN" else "VPN connection failed"
                    },
                    color = StatusError,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag("home_error_banner")
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // D. SERVER SUMMARY CARD
            CyberGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_server_summary_card"),
                accentColor = if (isConnected) StatusConnected else ElectricCyan.copy(alpha = 0.55f),
                elevated = true,
                onClick = onOpenServersTab,
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
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ElevatedSurface)
                                .border(BorderStroke(1.dp, GlassBorderMetallic), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = flag, fontSize = 22.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedConfig?.name?.ifBlank {
                                    if (isPersian) "انتخاب سرور" else "Select Server"
                                } ?: if (isPersian) "هیچ سروری انتخاب نشده" else "No Server Selected",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextCyberPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "$countryName • ${selectedConfig?.protocol?.uppercase() ?: "--"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextCyberSecondary
                                )
                                if (selectedConfig != null) {
                                    Text(
                                        text = if (displayedPingMs > 0) "${displayedPingMs}ms"
                                        else if (selectedConfig.status == "offline") (if (isPersian) "آفلاین" else "Offline")
                                        else (if (isPersian) "تست‌نشده" else "Untested"),
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            displayedPingMs in 1..140 -> StatusConnected
                                            displayedPingMs > 140 -> StatusConnecting
                                            selectedConfig.status == "offline" -> StatusError
                                            else -> StatusDisconnected
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Server Load badge (only real value when matchingServer exists) + Change button
                    Column(horizontalAlignment = Alignment.End) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(ElectricCyan.copy(alpha = 0.14f))
                                .border(BorderStroke(0.8.dp, ElectricCyan.copy(alpha = 0.45f)), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SwapHoriz,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPersian) "تغییر سرور" else "Change",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan
                            )
                        }
                        if (matchingServer != null) {
                            val loadPct = if (matchingServer.bandwidthLimitGb > 0) {
                                ((matchingServer.bandwidthUsageGb / matchingServer.bandwidthLimitGb) * 100).toInt().coerceIn(0, 100)
                            } else {
                                (100 - matchingServer.stabilityScore).coerceIn(0, 100)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isPersian) "بار سرور: $loadPct%" else "Load: $loadPct%",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                color = MetallicSilver
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // C. NETWORK TELEMETRY GRID (Download, Upload, Ping, Duration, Data Usage)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryMetricTile(
                    label = if (isPersian) "دانلود" else "Download",
                    value = formatCyberSpeed(sessionStats.downloadSpeedBytesPerSec),
                    icon = Icons.Filled.ArrowDownward,
                    accentColor = StatusConnected,
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricTile(
                    label = if (isPersian) "آپلود" else "Upload",
                    value = formatCyberSpeed(sessionStats.uploadSpeedBytesPerSec),
                    icon = Icons.Filled.ArrowUpward,
                    accentColor = ElectricCyan,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryMetricTile(
                    label = if (isPersian) "پینگ" else "Ping",
                    value = if (displayedPingMs > 0) "${displayedPingMs} ms" else "-- ms",
                    icon = Icons.Filled.Speed,
                    accentColor = if (displayedPingMs in 1..150) StatusConnected else MetallicSilver,
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricTile(
                    label = if (isPersian) "مدت اتصال" else "Duration",
                    value = formatCyberDuration(sessionStats.connectedDurationSeconds),
                    icon = Icons.Filled.Timer,
                    accentColor = stateAccent,
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricTile(
                    label = if (isPersian) "مصرف سشن" else "Session",
                    value = formatCyberBytes(totalSessionBytes),
                    icon = Icons.Filled.DataUsage,
                    accentColor = PremiumTeal,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // CYBER DEFENSE & QUICK ACTIONS BAR (Radar, Anti-Censorship Engine, Quick Import)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CyberGlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_radar_button"),
                    accentColor = PremiumTeal.copy(alpha = 0.55f),
                    onClick = onOpenRadar,
                    contentPadding = 12.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Radar,
                            contentDescription = null,
                            tint = PremiumTeal,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isPersian) "رادار ضد فیلتر" else "Anti-Filter Radar",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextCyberPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isPersian) "تست زنده دسترسی" else "Live Network Probe",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextCyberSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                CyberGlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_cyber_shield_sheet_button"),
                    accentColor = if (nationalIntranetMode || isAutoRotateEnabled) ElectricCyan else GlassBorderMetallic,
                    onClick = { showCyberToolsSheet = true },
                    contentPadding = 12.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Hub,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isPersian) "موتور عبور و احیا" else "Bypass & Heal",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextCyberPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isAutoRotateEnabled) "${countdownSeconds}s • $currentCleanIp"
                                else if (isPersian) "اینترنت ملی / چرخش IP" else "Intranet / IP Hop",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                color = ElectricCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // REAL IN-TUNNEL HEALTH PROBE, IP/DNS LEAK GUARD & AUTO-FAILOVER CARD
            if (isConnected || healthReport != null || leakCheckReport != null) {
                CyberGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_tunnel_health_card"),
                    accentColor = when {
                        healthReport == null -> ElectricCyan.copy(alpha = 0.45f)
                        healthReport.isHealthy -> StatusConnected.copy(alpha = 0.65f)
                        else -> StatusError.copy(alpha = 0.7f)
                    },
                    contentPadding = 14.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (isPersian) "سلامت واقعی تونل و تست نشت IP/DNS" else "Live Tunnel Health & IP/DNS Leak Guard",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextCyberPrimary
                        )
                        Text(
                            text = if (healthReport?.checkedThroughProxy == true) {
                                if (isPersian) "تست واقعی از طریق پروکسی SOCKS5 Xray" else "Verified via local Xray SOCKS5"
                            } else {
                                if (isPersian) "بررسی Global 204، تلگرام، اینستاگرام و IP خروجی" else "Checks Global 204, Telegram, Instagram & Exit IP"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextCyberSecondary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onRunHealthCheck,
                                enabled = !healthCheckInProgress,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("home_health_probe_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f))
                            ) {
                                if (healthCheckInProgress) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = ElectricCyan
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Speed,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isPersian) "تست سلامت" else "Probe",
                                    color = ElectricCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = onRunLeakCheck,
                                enabled = !leakCheckInProgress,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("home_leak_check_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, PremiumTeal.copy(alpha = 0.65f))
                            ) {
                                if (leakCheckInProgress) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = PremiumTeal
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Security,
                                        contentDescription = null,
                                        tint = PremiumTeal,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isPersian) "تست نشت IP/DNS" else "Leak Check",
                                    color = PremiumTeal,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (healthReport != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HealthEndpointChip(
                                title = "Global 204",
                                reachable = healthReport.global204Reachable,
                                latencyMs = healthReport.global204LatencyMs,
                                modifier = Modifier.weight(1f)
                            )
                            HealthEndpointChip(
                                title = "Telegram",
                                reachable = healthReport.telegramReachable,
                                latencyMs = healthReport.telegramLatencyMs,
                                modifier = Modifier.weight(1f)
                            )
                            HealthEndpointChip(
                                title = "Instagram",
                                reachable = healthReport.instagramReachable,
                                latencyMs = healthReport.instagramLatencyMs,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (!healthReport.isHealthy && configs.size > 1) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onFailoverNextNode,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("home_failover_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StatusConnecting,
                                    contentColor = DeepGraphite
                                )
                            ) {
                                Icon(imageVector = Icons.Filled.Autorenew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isPersian) "تغییر خودکار به نود سالم بعدی" else "Auto-Failover to Next Node",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (leakCheckReport != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val leakAccent = if (leakCheckReport.isLeakProtected && leakCheckReport.errorMessage == null) {
                            StatusConnected
                        } else if (!leakCheckReport.errorMessage.isNullOrBlank()) {
                            StatusError
                        } else {
                            StatusConnecting
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(leakAccent.copy(alpha = 0.12f))
                                .border(BorderStroke(0.8.dp, leakAccent.copy(alpha = 0.45f)), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                                .testTag("home_leak_report_panel")
                        ) {
                            if (!leakCheckReport.errorMessage.isNullOrBlank()) {
                                Text(
                                    text = leakCheckReport.errorMessage,
                                    color = StatusError,
                                    fontSize = 12.sp
                                )
                            } else {
                                Text(
                                    text = if (isPersian) {
                                        "IP خروجی: ${leakCheckReport.exitIp} • ${leakCheckReport.countryName.ifBlank { leakCheckReport.countryCode }}"
                                    } else {
                                        "Exit IP: ${leakCheckReport.exitIp} • ${leakCheckReport.countryName.ifBlank { leakCheckReport.countryCode }}"
                                    },
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextCyberPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${leakCheckReport.ispOrOrg.ifBlank { "--" }} • ${leakCheckReport.dnsStatusLabel}",
                                    fontSize = 11.sp,
                                    color = leakAccent
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            OutlinedButton(
                onClick = { showImportDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("home_import_config_button"),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, GlassBorderMetallic),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MetallicSilver)
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPersian) "افزودن کانفیگ یا لینک سابسکریپشن" else "Import Config / Subscription URL",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // کانال برنامه / Emergency Channels Card (Working configs even during blackouts)
            CyberGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_emergency_channels_card"),
                accentColor = ElectricCyan.copy(alpha = 0.55f),
                contentPadding = 14.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.RssFeed,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPersian) "کانال برنامه (دریافت کانفیگ‌های اضطراری)" else "Official Channels (Emergency Configs)",
                        style = MaterialTheme.typography.titleSmall,
                        color = ElectricCyan
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isPersian)
                        "حتی در زمان قطعی شدید اینترنت و محدودیت‌ها، جدیدترین کانفیگ‌ها و نودهای فعال در کانال‌های رسمی برنامه منتشر می‌شوند:"
                    else
                        "Working configs & updates are published here even during severe internet restrictions:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextCyberSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://rubika.ir/TMS_Time")).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("home_channel_rubika_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PremiumTeal,
                            contentColor = DeepGraphite
                        )
                    ) {
                        Icon(imageVector = Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPersian) "روبیکا (داخلی)" else "Rubika",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/TMS_TIME")).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("home_channel_telegram_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.7f))
                    ) {
                        Icon(imageVector = Icons.Filled.Send, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPersian) "تلگرام" else "Telegram",
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // CYBER BYPASS & AUTO-HEAL SHEET
    if (showCyberToolsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCyberToolsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SecondarySurface,
            contentColor = TextCyberPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isPersian) "ابزارهای پیشرفته عبور از محدودیت" else "Advanced Anti-Censorship Controls",
                    style = MaterialTheme.typography.titleLarge,
                    color = ElectricCyan
                )

                // National Intranet Piercer Toggle
                CyberGlassCard(accentColor = if (nationalIntranetMode) StatusConnected else null, contentPadding = 14.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isPersian) "سپر نفوذ اینترنت ملی" else "National Intranet Piercer",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextCyberPrimary
                            )
                            Text(
                                text = if (isPersian) "مسیریابی اضطراری از طریق پل داخلی ایران" else "Emergency routing via domestic relay bridge",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextCyberSecondary
                            )
                        }
                        Switch(
                            checked = nationalIntranetMode,
                            onCheckedChange = { VpnController.toggleNationalIntranetMode() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepGraphite,
                                checkedTrackColor = StatusConnected
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            customRelayInput = iranRelayServer
                            showRelayDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) {
                        Text(
                            text = if (isPersian) "تنظیم آدرس پل ایران: ${iranRelayServer.ifBlank { "خودکار" }}"
                            else "Configure Iran Relay: ${iranRelayServer.ifBlank { "Auto" }}",
                            color = ElectricCyan,
                            fontSize = 12.sp
                        )
                    }
                }

                // 10s Auto-Hop IP & DNS
                CyberGlassCard(accentColor = if (isAutoRotateEnabled) ElectricCyan else null, contentPadding = 14.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isPersian) "چرخش خودکار ۱۰ ثانیه‌ای IP و DNS" else "10s Dynamic IP & DNS Hop",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextCyberPrimary
                            )
                            Text(
                                text = "IP: $currentCleanIp • DNS: $currentDns (#$rotationCount)",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = ElectricCyan
                            )
                        }
                        Switch(
                            checked = isAutoRotateEnabled,
                            onCheckedChange = { DynamicIpDnsRotator.toggleAutoRotation() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepGraphite,
                                checkedTrackColor = ElectricCyan
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { DynamicIpDnsRotator.forceHopNow() },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GlassBorderCyan)
                    ) {
                        Icon(imageVector = Icons.Filled.Sync, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (isPersian) "جهش فوری IP ($countdownSeconds ثانیه)" else "Hop Now (${countdownSeconds}s)", color = ElectricCyan)
                    }
                }

                // Auto-Heal Selected Config
                Button(
                    onClick = {
                        val target = selectedConfig ?: configs.firstOrNull()
                        if (target != null) {
                            val healed = ConfigAutoHealer.heal(target)
                            onImportConfig(healed.rawConfig)
                            onSelectConfig(healed)
                            healedInfoText = if (isPersian) {
                                "کانفیگ «${healed.name}» با آی‌پی ${healed.server}:${healed.port} و استتار TLS بازسازی شد."
                            } else {
                                "Healed '${healed.name}' -> ${healed.server}:${healed.port}"
                            }
                            showCyberToolsSheet = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PremiumTeal, contentColor = DeepGraphite)
                ) {
                    Icon(imageVector = Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPersian) "احیا و بهینه‌سازی کانفیگ انتخابی (Auto-Heal)" else "Auto-Heal Selected Config",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // IMPORT CONFIG / SUBSCRIPTION DIALOG
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            containerColor = SecondarySurface,
            title = {
                Text(
                    text = if (isPersian) "افزودن کانفیگ یا سابسکریپشن" else "Import Config or Subscription",
                    color = ElectricCyan,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { importSubscriptionMode = false },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!importSubscriptionMode) ElectricCyan else ElevatedSurface,
                                contentColor = if (!importSubscriptionMode) DeepGraphite else TextCyberSecondary
                            )
                        ) {
                            Text(if (isPersian) "لینک کانفیگ" else "URI / Batch", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { importSubscriptionMode = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("import_mode_subscription_tab"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (importSubscriptionMode) ElectricCyan else ElevatedSurface,
                                contentColor = if (importSubscriptionMode) DeepGraphite else TextCyberSecondary
                            )
                        ) {
                            Text(if (isPersian) "سابسکریپشن (URL)" else "Subscription", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (!importSubscriptionMode) {
                        Text(
                            text = if (isPersian) "لینک‌های vless://، vmess://، trojan:// یا ss:// را وارد کنید:"
                            else "Paste vless://, vmess://, trojan://, or ss:// URI(s):",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextCyberSecondary
                        )
                        OutlinedTextField(
                            value = rawInputText,
                            onValueChange = { rawInputText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            placeholder = { Text("vless://...", fontFamily = JetBrainsMonoFontFamily, fontSize = 11.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = GlassBorderSubtle,
                                focusedTextColor = TextCyberPrimary,
                                unfocusedTextColor = TextCyberPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                if (clipText.isNotBlank()) rawInputText = clipText
                            }
                        ) {
                            Icon(imageVector = Icons.Filled.ContentPaste, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isPersian) "جای‌گذاری از کلیپ‌بورد" else "Paste from Clipboard", color = ElectricCyan)
                        }
                    } else {
                        Text(
                            text = if (isPersian) "آدرس سابسکریپشن http(s):// حاوی لیست یا Base64 را وارد کنید:"
                            else "Enter HTTP(S) subscription URL (plain or Base64):",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextCyberSecondary
                        )
                        OutlinedTextField(
                            value = subscriptionUrlInput,
                            onValueChange = { subscriptionUrlInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("subscription_url_input"),
                            singleLine = true,
                            placeholder = { Text("https://panel.example.com/sub/...", fontFamily = JetBrainsMonoFontFamily, fontSize = 11.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = GlassBorderSubtle,
                                focusedTextColor = TextCyberPrimary,
                                unfocusedTextColor = TextCyberPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importSubscriptionMode) {
                            val sub = subscriptionUrlInput.trim()
                            if (sub.isNotBlank()) {
                                onImportSubscription(sub)
                                subscriptionUrlInput = ""
                                showImportDialog = false
                            }
                        } else {
                            val txt = rawInputText.trim()
                            if (txt.isNotBlank()) {
                                if (txt.lines().size > 1) onImportBatch(txt) else onImportConfig(txt)
                                rawInputText = ""
                                showImportDialog = false
                            }
                        }
                    },
                    enabled = !isSubscriptionImporting,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepGraphite)
                ) {
                    Text(
                        text = if (importSubscriptionMode) (if (isPersian) "دریافت ساب" else "Fetch")
                        else (if (isPersian) "ذخیره" else "Save"),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(if (isPersian) "انصراف" else "Cancel", color = TextCyberSecondary)
                }
            }
        )
    }

    // IRAN RELAY MODAL
    if (showRelayDialog) {
        AlertDialog(
            onDismissRequest = { showRelayDialog = false },
            containerColor = SecondarySurface,
            title = {
                Text(
                    text = if (isPersian) "تنظیم پل واسط ایران" else "Iran Relay Bridge",
                    color = ElectricCyan,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customRelayInput,
                        onValueChange = { customRelayInput = it },
                        label = { Text(if (isPersian) "IP یا دامنه پل" else "Relay IP or Domain") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = GlassBorderSubtle,
                            focusedTextColor = TextCyberPrimary,
                            unfocusedTextColor = TextCyberPrimary
                        )
                    )
                    NationalIntranetPiercer.DEFAULT_IRAN_RELAYS.forEach { relay ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ElevatedSurface)
                                .clickable { customRelayInput = relay }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = relay, fontFamily = JetBrainsMonoFontFamily, fontSize = 11.sp, color = TextCyberPrimary)
                            Text(text = if (isPersian) "انتخاب" else "Use", fontSize = 11.sp, color = ElectricCyan)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        VpnController.setIranRelayServer(customRelayInput.trim())
                        showRelayDialog = false
                        Toast.makeText(context, if (isPersian) "پل اعمال شد" else "Relay updated", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepGraphite)
                ) {
                    Text(if (isPersian) "اعمال" else "Apply", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRelayDialog = false }) {
                    Text(if (isPersian) "انصراف" else "Cancel", color = TextCyberSecondary)
                }
            }
        )
    }

    if (healedInfoText != null) {
        AlertDialog(
            onDismissRequest = { healedInfoText = null },
            containerColor = SecondarySurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = StatusConnected)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (isPersian) "کانفیگ احیا شد" else "Config Auto-Healed", color = StatusConnected)
                }
            },
            text = { Text(text = healedInfoText.orEmpty(), color = TextCyberPrimary) },
            confirmButton = {
                Button(
                    onClick = { healedInfoText = null },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusConnected, contentColor = DeepGraphite)
                ) {
                    Text(if (isPersian) "تایید" else "OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun HealthEndpointChip(
    title: String,
    reachable: Boolean,
    latencyMs: Long,
    modifier: Modifier = Modifier
) {
    val accent = if (reachable) StatusConnected else StatusError
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(BorderStroke(0.8.dp, accent.copy(alpha = 0.45f)), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextCyberPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (reachable && latencyMs > 0) "${latencyMs}ms" else "FAIL",
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = accent
        )
    }
}
