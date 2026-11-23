package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VpnConfig
import com.example.data.model.VpnConnectionState
import com.example.data.model.VpnSessionStats
import com.example.data.network.AntiFilterRadarEngine
import com.example.data.network.ServiceTarget
import com.example.data.ai.VpnAiEngine
import com.example.vpn.engine.DynamicIpDnsRotator
import com.example.ui.theme.BorderGoldHighlight
import com.example.ui.theme.BorderGoldMuted
import com.example.ui.theme.CardSurfaceDark
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.StatusWarningAmber
import com.example.ui.theme.TextGraySecondary
import com.example.ui.theme.TextWhitePrimary
import kotlinx.coroutines.launch

@Composable
fun AntiFilterRadarScreen(
    vpnState: VpnConnectionState,
    sessionStats: VpnSessionStats,
    configs: List<VpnConfig>,
    isPersian: Boolean,
    onConnectVpn: (VpnConfig, Boolean) -> Unit,
    onDisconnectVpn: () -> Unit,
    isAdminMode: Boolean = false
) {
    val coroutineScope = rememberCoroutineScope()
    var targets by remember { mutableStateOf(AntiFilterRadarEngine.getInitialTargets()) }
    var isTestingAll by remember { mutableStateOf(false) }
    var antiDpiFragmentation by remember { mutableStateOf(true) }
    var bypassIranianBanking by remember { mutableStateOf(true) }
    val aiEngine = remember { VpnAiEngine() }
    var isAiAnalyzing by remember { mutableStateOf(false) }
    var aiAnalysisText by remember { mutableStateOf<String?>(null) }
    var aiErrorText by remember { mutableStateOf<String?>(null) }

    fun runRadarScan() {
        coroutineScope.launch {
            isTestingAll = true
            val updated = targets.map { target ->
                AntiFilterRadarEngine.testTarget(target)
            }
            targets = updated
            isTestingAll = false
        }
    }

    LaunchedEffect(vpnState) {
        if (vpnState == VpnConnectionState.CONNECTED) {
            runRadarScan()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Radar Hero Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.2.dp, BorderGoldHighlight), RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .scale(if (vpnState == VpnConnectionState.CONNECTED) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(Color(0xFF151922))
                            .border(
                                BorderStroke(
                                    2.dp,
                                    if (vpnState == VpnConnectionState.CONNECTED) EmeraldGreen else MetallicGold
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = "Radar",
                            tint = if (vpnState == VpnConnectionState.CONNECTED) EmeraldGreen else MetallicGold,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (isPersian) "رادار هوشمند دورزدن فیلترینگ و شتاب‌دهنده تونل" else "Anti-Censorship Radar & Tunnel Booster",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MetallicGold
                    )

                    Text(
                        text = when {
                            isAdminMode -> if (isPersian) {
                                "پایش وضعیت فیلترینگ زیرساخت و سلامت پلتفرم‌ها (فقط مدیریت)"
                            } else {
                                "Infrastructure Censorship & Platform Health Diagnostics (Management Only)"
                            }
                            vpnState == VpnConnectionState.CONNECTED ->
                                "تونل فعال است • ترافیک اینستاگرام، تلگرام و وب از فیلترینگ عبور می‌کند"
                            else ->
                                "برای باز شدن تلگرام و اینستاگرام، اتصال به کانفیگ را فعال کنید"
                        },
                        fontSize = 12.sp,
                        color = if (!isAdminMode && vpnState == VpnConnectionState.CONNECTED) EmeraldGreen else TextGraySecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { runRadarScan() },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E222D),
                                contentColor = MetallicGold
                            ),
                            border = BorderStroke(1.dp, BorderGoldMuted),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isTestingAll
                        ) {
                            if (isTestingAll) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MetallicGold,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.NetworkCheck,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تست اتصال پلتفرم‌ها", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (!isAdminMode) {
                            if (vpnState == VpnConnectionState.CONNECTED || vpnState == VpnConnectionState.CONNECTING) {
                                Button(
                                    onClick = onDisconnectVpn,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusOfflineRed),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (vpnState == VpnConnectionState.CONNECTING) "در حال اتصال..." else "قطع اتصال",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                val activeCandidate = configs.firstOrNull { it.status == "online" } ?: configs.firstOrNull()
                                Button(
                                    onClick = {
                                        if (activeCandidate != null) {
                                            onConnectVpn(activeCandidate, antiDpiFragmentation)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MetallicGold,
                                        contentColor = Color(0xFF12141A)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = activeCandidate != null && vpnState != VpnConnectionState.DISCONNECTING
                                ) {
                                    Icon(imageVector = Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (vpnState == VpnConnectionState.DISCONNECTING) "در حال قطع اتصال..." else "اتصال امن سریع",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Killer Anti-DPI Features Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(0.8.dp, BorderGoldMuted), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "تقسیم بسته‌های TLS (Anti-DPI Fragmentation)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhitePrimary
                            )
                            Text(
                                text = "شکستن هندشیک کلاینت جهت دورزدن فیلترینگ شدید همراه اول و ایرانسل",
                                fontSize = 11.sp,
                                color = TextGraySecondary
                            )
                        }
                        Switch(
                            checked = antiDpiFragmentation,
                            onCheckedChange = { antiDpiFragmentation = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF12141A),
                                checkedTrackColor = MetallicGold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "عدم عبور سایت‌های بانکی و ایرانی (Split-Tunneling)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhitePrimary
                            )
                            Text(
                                text = "بانک‌ها و سامانه‌های دولتی بدون فیلترشکن و با اینترنت مستقیم باز می‌شوند",
                                fontSize = 11.sp,
                                color = TextGraySecondary
                            )
                        }
                        Switch(
                            checked = bypassIranianBanking,
                            onCheckedChange = { bypassIranianBanking = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF12141A),
                                checkedTrackColor = EmeraldGreen
                            )
                        )
                    }
                }
            }
        }

        // 🤖 AI SMART NETWORK ADAPTATION CARD (هوش مصنوعی تطبیق شبکه و دورزدن فیلترینگ)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        BorderStroke(
                            1.2.dp,
                            when {
                                aiErrorText != null -> StatusOfflineRed
                                aiAnalysisText != null -> EmeraldGreen
                                else -> BorderGoldHighlight
                            }
                        ),
                        RoundedCornerShape(18.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141924)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1B2332))
                                    .border(BorderStroke(1.dp, EmeraldGreen), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Adaptation",
                                    tint = EmeraldGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isPersian) "هوش مصنوعی تطبیق شبکه و رفع فیلتر" else "AI Anti-Censorship Adaptation",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldGreen
                                )
                                Text(
                                    text = "تحلیل پایش زنده تلگرام و اینستاگرام و بهینه‌سازی پارامترها",
                                    fontSize = 11.sp,
                                    color = TextGraySecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isAiAnalyzing = true
                                aiErrorText = null
                                val report = AntiFilterRadarEngine.buildRadarReport(targets)
                                val currentConfig = configs.firstOrNull { it.status == "online" }?.name ?: "VLESS Ultra Reality"
                                val result = aiEngine.analyzeRadarAndAdaptNetwork(
                                    radarResultsSummary = report,
                                    activeConfig = currentConfig
                                )
                                when (result) {
                                    is com.example.data.ai.AiResult.Success -> {
                                        aiAnalysisText = result.responseText
                                        aiErrorText = null
                                    }
                                    is com.example.data.ai.AiResult.Error -> {
                                        aiAnalysisText = null
                                        aiErrorText = result.message
                                    }
                                }
                                isAiAnalyzing = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E2838),
                            contentColor = MetallicGold
                        ),
                        border = BorderStroke(1.dp, MetallicGold.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isAiAnalyzing
                    ) {
                        if (isAiAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MetallicGold,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "هوش مصنوعی در حال تحلیل شبکه...", fontSize = 11.sp)
                        } else {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "⚡ تحلیل هوشمند وضعیت و تطبیق با هوش مصنوعی", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (aiErrorText != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(StatusOfflineRed.copy(alpha = 0.14f))
                                .border(BorderStroke(0.9.dp, StatusOfflineRed.copy(alpha = 0.7f)), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = aiErrorText ?: "",
                                color = StatusOfflineRed,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    if (aiAnalysisText != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F141E))
                                .border(BorderStroke(0.8.dp, EmeraldGreen.copy(alpha = 0.4f)), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = aiAnalysisText ?: "",
                                    color = TextWhitePrimary,
                                    fontSize = 12.sp,
                                    lineHeight = 19.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        DynamicIpDnsRotator.forceHopNow()
                                        antiDpiFragmentation = true
                                        runRadarScan()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldGreen,
                                        contentColor = Color(0xFF0C140E)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "اعمال خودکار بهینه‌سازی هوش مصنوعی و جهش IP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Live Platforms Censorship Status List
        item {
            Text(
                text = "وضعیت زنده دسترسی به شبکه‌ها و پلتفرم‌ها",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MetallicGold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        items(targets, key = { it.id }) { target ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        BorderStroke(
                            0.8.dp,
                            if (target.isAccessible) EmeraldGreen.copy(alpha = 0.5f) else BorderGoldMuted
                        ),
                        RoundedCornerShape(14.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF11141C)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                if (target.isAccessible) EmeraldGreen else StatusOfflineRed,
                                CircleShape
                            )
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isPersian) target.nameFa else target.nameEn,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhitePrimary
                        )
                        Text(
                            text = target.statusText,
                            fontSize = 12.sp,
                            color = if (target.isAccessible) EmeraldGreen else TextGraySecondary
                        )
                    }

                    if (target.latencyMs > 0) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A212B)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${target.latencyMs} ms",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
