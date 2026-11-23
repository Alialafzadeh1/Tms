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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FailoverPolicy
import com.example.data.model.VpnConfig
import com.example.data.model.VpnServer
import com.example.ui.theme.BorderGoldHighlight
import com.example.ui.theme.BorderGoldMuted
import com.example.ui.theme.CardSurfaceDark
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.TextGoldMuted
import com.example.ui.theme.TextGraySecondary
import com.example.ui.theme.TextWhitePrimary

import com.example.data.model.AdminPermission
import com.example.data.model.AdminRole
import com.example.data.model.SupportTicket
import com.example.data.model.SupportTicketReply
import com.example.data.model.SupportTicketStatus
import com.example.data.ai.NexusChatMessage
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusWarningAmber
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntelligentEngineScreen(
    configs: List<VpnConfig>,
    servers: List<VpnServer>,
    policies: List<FailoverPolicy>,
    currentRole: AdminRole,
    isPersian: Boolean,
    aiLoading: Boolean,
    aiResponse: String?,
    onAddPolicy: (name: String, primaryId: String, backupId: String, threshold: Int) -> Unit,
    onDeletePolicy: (String) -> Unit,
    onOptimizeTopology: (String) -> Unit,
    onClearAiResponse: () -> Unit,
    adminNexusMessages: List<NexusChatMessage> = emptyList(),
    adminNexusStreaming: Boolean = false,
    onSendAdminNexusMessage: (String) -> Unit = {},
    onClearAdminNexusChat: () -> Unit = {},
    adminTickets: List<SupportTicket> = emptyList(),
    ticketBusy: Boolean = false,
    observeTicketReplies: (String) -> Flow<List<SupportTicketReply>> = { flowOf(emptyList()) },
    onReplyToTicket: (SupportTicket, String) -> Unit = { _, _ -> },
    onUpdateTicketStatus: (String, SupportTicketStatus) -> Unit = { _, _ -> }
) {
    val canRunAi = currentRole.hasPermission(AdminPermission.RUN_INTELLIGENCE)
    val canManageFailover = currentRole.hasPermission(AdminPermission.MANAGE_FAILOVER)
    val canManageUsers = currentRole.hasPermission(AdminPermission.MANAGE_USERS)

    var showAddPolicyDialog by remember { mutableStateOf(false) }
    var policyName by remember { mutableStateOf("") }
    var selectedPrimaryId by remember { mutableStateOf(servers.firstOrNull()?.id ?: "") }
    var selectedBackupId by remember { mutableStateOf(servers.getOrNull(1)?.id ?: "") }
    var thresholdMs by remember { mutableFloatStateOf(250f) }

    var userCustomAiPrompt by remember { mutableStateOf("") }
    var copilotPrompt by remember { mutableStateOf("") }
    var inspectedAdminTicket by remember { mutableStateOf<SupportTicket?>(null) }

    // Rank servers by lowest latency (ignoring offline -1)
    val sortedByLatency = remember(servers) {
        servers.filter { it.status == "online" && it.latencyMs > 0 }
            .sortedBy { it.latencyMs }
    }

    val bestRecommendedServer = sortedByLatency.firstOrNull()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title Header
        item {
            Column {
                Text(
                    text = if (isPersian) "موتور هوشمند مسیریابی و Failover" else "Intelligent Routing & Failover Engine",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MetallicGold
                )
                Text(
                    text = if (isPersian) "رتبه‌بندی تاخیر، انتخاب خودکار بهترین سرور و سیاست‌های جابجایی ترافیک" else "Real latency ranking, node stability scoring and automatic failover",
                    fontSize = 12.sp,
                    color = TextGraySecondary
                )
            }
        }

        // Automatic Server Recommendation Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.2.dp, MetallicGold), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF13151D)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(GoldContainer, CircleShape)
                                .border(BorderStroke(1.dp, MetallicGold), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = MetallicGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isPersian) "پیشنهاد هوشمند بهترین سرور فعلی" else "Intelligent Node Recommendation",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MetallicGold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (bestRecommendedServer != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF090B10), RoundedCornerShape(10.dp))
                                .border(BorderStroke(0.6.dp, EmeraldGreen.copy(alpha = 0.5f)), RoundedCornerShape(10.dp))
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${bestRecommendedServer.name} (${bestRecommendedServer.country})",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhitePrimary
                                )
                                Text(
                                    text = "${bestRecommendedServer.host}:${bestRecommendedServer.port} • پایداری: ${bestRecommendedServer.stabilityScore}%",
                                    fontSize = 11.sp,
                                    color = TextGraySecondary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(EmeraldContainer, RoundedCornerShape(6.dp))
                                    .border(BorderStroke(0.8.dp, EmeraldGreen), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${bestRecommendedServer.latencyMs} ms",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldGreen
                                )
                            }
                        }
                    } else {
                        Text(
                            text = if (isPersian)
                                "هیچ سرور آنلاینی با پینگ معتبر شناسایی نشده است. لطفاً سرورها را تست پینگ سوکت کنید."
                            else
                                "No online nodes with verified ping. Please perform socket ping in Server Management.",
                            fontSize = 12.sp,
                            color = TextGraySecondary
                        )
                    }
                }
            }
        }

        // Latency Ranking Section
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
                        Text(
                            text = if (isPersian) "رتبه‌بندی تاخیر سرورها (پایین‌ترین تاخیر)" else "Latency Ranking (Fastest to Slowest)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MetallicGold
                        )
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (sortedByLatency.isEmpty()) {
                        Text(
                            text = if (isPersian) "اطلاعات پینگ ثبت نشده است." else "No ping metrics recorded yet.",
                            fontSize = 12.sp,
                            color = TextGraySecondary
                        )
                    } else {
                        sortedByLatency.forEachIndexed { rank, srv ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .background(Color(0xFF0C0E14), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "#${rank + 1}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (rank == 0) MetallicGold else TextGraySecondary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "${srv.name} (${srv.countryCode})",
                                        fontSize = 13.sp,
                                        color = TextWhitePrimary
                                    )
                                }
                                Text(
                                    text = "${srv.latencyMs} ms",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (srv.latencyMs < 120) EmeraldGreen else MetallicGold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Failover Policies Section
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
                                text = if (isPersian) "سیاست‌های سوییچ خودکار (Failover)" else "Failover & Redundancy Policies",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MetallicGold
                            )
                            Text(
                                text = if (isPersian) "انتقال خودکار ترافیک در صورت اختلال سرور اصلی" else "Auto-reroute traffic when primary server degrades",
                                fontSize = 11.sp,
                                color = TextGraySecondary
                            )
                        }

                        if (canManageFailover) {
                            Button(
                                onClick = { showAddPolicyDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MetallicGold,
                                    contentColor = Color(0xFF1E1700)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(text = if (isPersian) "سیاست جدید" else "New Policy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF1D170A), RoundedCornerShape(6.dp))
                                    .border(BorderStroke(0.6.dp, StatusWarningAmber), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(text = if (isPersian) "فقط خواندنی" else "Read-Only", fontSize = 10.sp, color = StatusWarningAmber)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (policies.isEmpty()) {
                        Text(
                            text = if (isPersian) "هیچ سیاست جایگزینی تعریف نشده است." else "No failover policies configured.",
                            fontSize = 12.sp,
                            color = TextGraySecondary
                        )
                    } else {
                        policies.forEach { pol ->
                            val primaryName = servers.find { it.id == pol.primaryServerId }?.name ?: pol.primaryServerId
                            val backupName = servers.find { it.id == pol.backupServerId }?.name ?: pol.backupServerId

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .border(BorderStroke(0.6.dp, BorderGoldMuted), RoundedCornerShape(10.dp)),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0E14))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pol.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextWhitePrimary
                                        )
                                        Text(
                                            text = "$primaryName  ➔  $backupName",
                                            fontSize = 12.sp,
                                            color = MetallicGold
                                        )
                                        Text(
                                            text = "آستانه جابجایی: ${pol.latencyThresholdMs}ms | سوییچ خودکار: فعال",
                                            fontSize = 10.sp,
                                            color = TextGraySecondary
                                        )
                                    }

                                    if (canManageFailover) {
                                        IconButton(
                                            onClick = { onDeletePolicy(pol.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = StatusOfflineRed, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // AI Reasoning Console (gemini-4-argon with High Thinking)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, BorderGoldHighlight), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF11141E)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color(0xFF262013), CircleShape)
                                .border(BorderStroke(1.dp, MetallicGold), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Psychology, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isPersian) "هوش مصنوعی استدلال عمیق (Gemini 4 Argon High Thinking)" else "Deep Reasoning AI Engine (Gemini 4 Argon)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MetallicGold
                            )
                            Text(
                                text = if (isPersian) "تحلیل توپولوژی پیشرفته و استراتژی عبور از اختلالات شدید" else "High thinking mode for complex routing & censorship bypass",
                                fontSize = 11.sp,
                                color = TextGraySecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = userCustomAiPrompt,
                        onValueChange = { userCustomAiPrompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canRunAi,
                        placeholder = {
                            Text(
                                text = if (isPersian) "مثال: استراتژی بهینه‌سازی نودها برای اختلالات پورت 443 و کاهش پینگ گیمینگ..." else "Describe network condition or optimization request...",
                                fontSize = 11.sp,
                                color = TextGraySecondary
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = BorderGoldMuted,
                            focusedTextColor = TextWhitePrimary,
                            unfocusedTextColor = TextWhitePrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val prompt = userCustomAiPrompt.ifBlank {
                                "تحلیل پایداری سرورها، کاهش تاخیر و ارائه استراتژی جامع Failover بر اساس نودهای فعال"
                            }
                            onOptimizeTopology(prompt)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canRunAi) MetallicGold else Color(0xFF2E2A1E),
                            contentColor = if (canRunAi) Color(0xFF1E1700) else TextGraySecondary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !aiLoading && canRunAi
                    ) {
                        if (aiLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF1E1700), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = if (isPersian) "در حال پردازش استدلال عمیق..." else "Reasoning with High Thinking...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = if (canRunAi) Icons.Default.AutoAwesome else Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (canRunAi)
                                        (if (isPersian) "شروع استدلال و تحلیل هوشمند" else "Run Deep Reasoning")
                                    else
                                        (if (isPersian) "اجرای هوش مصنوعی برای نقش فعلی غیرمجاز است" else "AI Execution Restricted for Role"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (aiResponse != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF090A0E), RoundedCornerShape(10.dp))
                                .border(BorderStroke(0.8.dp, EmeraldGreen.copy(alpha = 0.6f)), RoundedCornerShape(10.dp))
                                .padding(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF090A0E))
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isPersian) "پاسخ استدلال مهندسی TMS AI" else "TMS AI Reasoning Report",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldGreen
                                    )
                                    TextButton(onClick = onClearAiResponse) {
                                        Text(text = if (isPersian) "بستن" else "Dismiss", color = TextGraySecondary, fontSize = 11.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = aiResponse,
                                    fontSize = 12.sp,
                                    color = TextWhitePrimary,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // TMS NEXUS AI Operations Copilot (Streaming Live Operations Console)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_nexus_copilot_card")
                    .border(BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.65f)), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1520)),
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
                                text = if (isPersian) "دستیار عملیات TMS NEXUS AI (زنده)" else "TMS NEXUS AI Operations Copilot",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan
                            )
                            Text(
                                text = if (isPersian) "تحلیل لحظه‌ای مخزن ۱۵۰ IP تمیز، کمبود ظرفیت و چرخش اشتراک‌ها" else "Live streaming audit for clean IP pool, shortages & rotation",
                                fontSize = 11.sp,
                                color = TextGraySecondary
                            )
                        }
                        if (adminNexusMessages.isNotEmpty()) {
                            IconButton(
                                onClick = onClearAdminNexusChat,
                                enabled = !adminNexusStreaming
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteSweep,
                                    contentDescription = if (isPersian) "پاک کردن" else "Clear",
                                    tint = TextGraySecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val adminQuickPrompts = if (isPersian) {
                        listOf(
                            "وضعیت سلامت مخزن ۱۵۰ IP تمیز و موارد قرنطینه را تحلیل کن",
                            "آیا هشدار کمبود ظرفیت برای پلن‌های اشتراک داریم؟",
                            "پیشنهاد چرخش روزانه و Failover بدون اختلال بده"
                        )
                    } else {
                        listOf(
                            "Audit 150 clean IP pool health & quarantined hosts",
                            "Analyze unresolved subscription capacity shortages",
                            "Recommend safe daily rotation & failover strategy"
                        )
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(adminQuickPrompts) { quick ->
                            OutlinedButton(
                                onClick = { if (canRunAi && !adminNexusStreaming) onSendAdminNexusMessage(quick) },
                                enabled = canRunAi && !adminNexusStreaming,
                                modifier = Modifier.height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.8.dp, ElectricCyan.copy(alpha = 0.55f))
                            ) {
                                Text(
                                    text = quick,
                                    fontSize = 10.sp,
                                    color = ElectricCyan,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (adminNexusMessages.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            adminNexusMessages.takeLast(4).forEach { msg ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when {
                                                msg.isError -> StatusOfflineRed.copy(alpha = 0.14f)
                                                msg.isFromUser -> ElectricCyan.copy(alpha = 0.14f)
                                                else -> ElevatedSurface
                                            }
                                        )
                                        .border(
                                            BorderStroke(
                                                0.7.dp,
                                                when {
                                                    msg.isError -> StatusOfflineRed.copy(alpha = 0.7f)
                                                    msg.isFromUser -> ElectricCyan.copy(alpha = 0.5f)
                                                    else -> BorderGoldMuted
                                                }
                                            ),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = when {
                                                msg.isFromUser -> if (isPersian) "مدیر" else "Admin"
                                                msg.isError -> if (isPersian) "خطای سرویس AI" else "AI Error"
                                                else -> "NEXUS Copilot"
                                            },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (msg.isError) StatusOfflineRed else ElectricCyan
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = if (msg.isStreaming && msg.text.isBlank()) {
                                                if (isPersian) "در حال پردازش..." else "Streaming..."
                                            } else {
                                                msg.text
                                            },
                                            fontSize = 12.sp,
                                            lineHeight = 19.sp,
                                            color = if (msg.isError) StatusOfflineRed else TextWhitePrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = copilotPrompt,
                            onValueChange = { copilotPrompt = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_nexus_prompt_input"),
                            enabled = canRunAi,
                            placeholder = {
                                Text(
                                    text = if (isPersian) "دستور یا سوال عملیاتی مدیر..." else "Ask NEXUS Operations Copilot...",
                                    fontSize = 11.sp,
                                    color = TextGraySecondary
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = BorderGoldMuted,
                                focusedTextColor = TextWhitePrimary,
                                unfocusedTextColor = TextWhitePrimary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Button(
                            onClick = {
                                val clean = copilotPrompt.trim()
                                if (clean.isNotBlank()) {
                                    copilotPrompt = ""
                                    onSendAdminNexusMessage(clean)
                                }
                            },
                            enabled = canRunAi && !adminNexusStreaming && copilotPrompt.isNotBlank(),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("admin_nexus_send_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = Color(0xFF0A1018)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Support Tickets Triage Queue
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_support_tickets_card")
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
                            Icon(
                                imageVector = Icons.Filled.SupportAgent,
                                contentDescription = null,
                                tint = MetallicGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPersian) "تیکت‌های پشتیبانی کاربران (${adminTickets.size})" else "User Support Tickets (${adminTickets.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MetallicGold
                            )
                        }
                        if (!canManageUsers) {
                            Text(
                                text = if (isPersian) "فقط خواندنی" else "Read-Only",
                                fontSize = 10.sp,
                                color = StatusWarningAmber
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    if (adminTickets.isEmpty()) {
                        Text(
                            text = if (isPersian) "تیکت باز یا ثبت‌شده‌ای وجود ندارد." else "No support tickets in queue.",
                            fontSize = 12.sp,
                            color = TextGraySecondary
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            adminTickets.take(15).forEach { ticket ->
                                SupportTicketCard(
                                    ticket = ticket,
                                    isPersian = isPersian,
                                    onClick = { inspectedAdminTicket = ticket }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    inspectedAdminTicket?.let { activeTicket ->
        val syncedTicket = adminTickets.firstOrNull { it.ticketId == activeTicket.ticketId } ?: activeTicket
        val replies by observeTicketReplies(syncedTicket.ticketId)
            .collectAsStateWithLifecycle(initialValue = emptyList())
        TicketConversationDialog(
            ticket = syncedTicket,
            replies = replies,
            isAdmin = canManageUsers,
            isBusy = ticketBusy,
            isPersian = isPersian,
            onSendReply = { msg -> onReplyToTicket(syncedTicket, msg) },
            onUpdateStatus = if (canManageUsers) {
                { newStatus -> onUpdateTicketStatus(syncedTicket.ticketId, newStatus) }
            } else null,
            onDismiss = { inspectedAdminTicket = null }
        )
    }

    // Add Policy Dialog
    if (showAddPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showAddPolicyDialog = false },
            containerColor = CardSurfaceDark,
            title = {
                Text(
                    text = if (isPersian) "تعریف سیاست جایگزینی (Failover)" else "New Failover Policy",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MetallicGold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = policyName,
                        onValueChange = { policyName = it },
                        label = { Text("نام سیاست (مثال: سوییچ اضطراری فرانکفورت)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = BorderGoldMuted,
                            focusedTextColor = TextWhitePrimary,
                            unfocusedTextColor = TextWhitePrimary
                        ),
                        singleLine = true
                    )

                    Text(
                        text = "آستانه تاخیر مجاز: ${thresholdMs.toInt()} ms",
                        fontSize = 12.sp,
                        color = TextWhitePrimary
                    )

                    Slider(
                        value = thresholdMs,
                        onValueChange = { thresholdMs = it },
                        valueRange = 100f..1000f,
                        steps = 9,
                        colors = SliderDefaults.colors(
                            thumbColor = MetallicGold,
                            activeTrackColor = MetallicGold,
                            inactiveTrackColor = Color(0xFF282C37)
                        )
                    )

                    Text(
                        text = if (isPersian) "در صورت فراتر رفتن تاخیر یا قطعی سرور اول، ترافیک بلافاصله به سرور دوم هدایت می‌شود." else "Traffic redirects to backup when primary node exceeds threshold or times out.",
                        fontSize = 11.sp,
                        color = TextGraySecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pId = servers.firstOrNull()?.id ?: "srv1"
                        val bId = servers.getOrNull(1)?.id ?: "srv2"
                        if (policyName.isNotBlank()) {
                            onAddPolicy(policyName, pId, bId, thresholdMs.toInt())
                            policyName = ""
                            showAddPolicyDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MetallicGold,
                        contentColor = Color(0xFF1E1700)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = if (isPersian) "ثبت سیاست" else "Save Policy", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPolicyDialog = false }) {
                    Text(text = if (isPersian) "انصراف" else "Cancel", color = TextGraySecondary)
                }
            }
        )
    }
}
