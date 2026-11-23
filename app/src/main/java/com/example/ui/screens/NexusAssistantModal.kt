package com.example.ui.screens

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ai.NexusChatMessage
import com.example.data.ai.NexusDiagnosticReport
import com.example.data.ai.NexusKnowledgeBase
import com.example.data.model.SupportTicket
import com.example.data.model.SupportTicketCategory
import com.example.data.model.SupportTicketPriority
import com.example.data.model.SupportTicketReply
import com.example.data.model.SupportTicketStatus
import com.example.ui.components.CyberGlassCard
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
import com.example.ui.theme.StatusError
import com.example.ui.theme.TextCyberPrimary
import com.example.ui.theme.TextCyberSecondary
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class NexusModalTab {
    CHAT,
    DIAGNOSTICS,
    SUPPORT_KB
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexusAssistantModal(
    isPersian: Boolean,
    messages: List<NexusChatMessage>,
    isStreaming: Boolean,
    diagnosticReport: NexusDiagnosticReport?,
    diagnosticLoading: Boolean,
    diagnosticError: String?,
    tickets: List<SupportTicket>,
    ticketBusy: Boolean,
    observeTicketReplies: (String) -> Flow<List<SupportTicketReply>>,
    onSendChatMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onRunDiagnostics: (String) -> Unit,
    onTriggerIspAutoTune: () -> Unit,
    onTriggerFailover: () -> Unit,
    onSubmitTicket: (subject: String, description: String, category: SupportTicketCategory, priority: SupportTicketPriority) -> Unit,
    onReplyTicket: (ticket: SupportTicket, message: String) -> Unit,
    onDismiss: () -> Unit
) {
    var activeTab by remember { mutableStateOf(NexusModalTab.CHAT) }
    var inspectedTicket by remember { mutableStateOf<SupportTicket?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("nexus_assistant_modal"),
            containerColor = DeepGraphite,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(ElectricCyan.copy(alpha = 0.16f), CircleShape)
                                    .border(BorderStroke(1.dp, ElectricCyan), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Psychology,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "TMS NEXUS AI",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricCyan
                                )
                                Text(
                                    text = if (isPersian) "دستیار هوشمند و عیب‌یابی اختصاصی شما" else "Personal AI Diagnostics & Support",
                                    fontSize = 10.sp,
                                    color = TextCyberSecondary
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("nexus_modal_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = if (isPersian) "بستن" else "Close",
                                tint = TextCyberPrimary
                            )
                        }
                    },
                    actions = {
                        if (activeTab == NexusModalTab.CHAT && messages.isNotEmpty()) {
                            IconButton(
                                onClick = onClearChat,
                                enabled = !isStreaming,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("nexus_clear_chat_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteSweep,
                                    contentDescription = if (isPersian) "پاک کردن گفتگو" else "Clear Chat",
                                    tint = TextCyberSecondary
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = SecondarySurface
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Top 3 Sub-Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NexusTabChip(
                        selected = activeTab == NexusModalTab.CHAT,
                        label = if (isPersian) "دستیار هوشمند" else "AI Chat",
                        tag = "nexus_tab_chat",
                        modifier = Modifier.weight(1f),
                        onClick = { activeTab = NexusModalTab.CHAT }
                    )
                    NexusTabChip(
                        selected = activeTab == NexusModalTab.DIAGNOSTICS,
                        label = if (isPersian) "عیب‌یابی خودکار" else "Diagnostics",
                        tag = "nexus_tab_diagnostics",
                        modifier = Modifier.weight(1f),
                        onClick = { activeTab = NexusModalTab.DIAGNOSTICS }
                    )
                    NexusTabChip(
                        selected = activeTab == NexusModalTab.SUPPORT_KB,
                        label = if (isPersian) "پشتیبانی و راهنما" else "Tickets & KB",
                        tag = "nexus_tab_support",
                        modifier = Modifier.weight(1f),
                        onClick = { activeTab = NexusModalTab.SUPPORT_KB }
                    )
                }

                when (activeTab) {
                    NexusModalTab.CHAT -> {
                        NexusChatPane(
                            isPersian = isPersian,
                            messages = messages,
                            isStreaming = isStreaming,
                            onSendMessage = onSendChatMessage
                        )
                    }
                    NexusModalTab.DIAGNOSTICS -> {
                        NexusDiagnosticsPane(
                            isPersian = isPersian,
                            report = diagnosticReport,
                            isLoading = diagnosticLoading,
                            errorMessage = diagnosticError,
                            onRunDiagnostics = onRunDiagnostics,
                            onTriggerIspAutoTune = onTriggerIspAutoTune,
                            onTriggerFailover = onTriggerFailover
                        )
                    }
                    NexusModalTab.SUPPORT_KB -> {
                        NexusSupportAndKbPane(
                            isPersian = isPersian,
                            tickets = tickets,
                            ticketBusy = ticketBusy,
                            onSubmitTicket = onSubmitTicket,
                            onInspectTicket = { inspectedTicket = it }
                        )
                    }
                }
            }
        }
    }

    inspectedTicket?.let { activeTicket ->
        val syncedTicket = tickets.firstOrNull { it.ticketId == activeTicket.ticketId } ?: activeTicket
        val replies by observeTicketReplies(syncedTicket.ticketId)
            .collectAsStateWithLifecycle(initialValue = emptyList())
        TicketConversationDialog(
            ticket = syncedTicket,
            replies = replies,
            isAdmin = false,
            isBusy = ticketBusy,
            isPersian = isPersian,
            onSendReply = { msg -> onReplyTicket(syncedTicket, msg) },
            onUpdateStatus = null,
            onDismiss = { inspectedTicket = null }
        )
    }
}

@Composable
private fun NexusTabChip(
    selected: Boolean,
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .testTag(tag),
        label = {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = ElectricCyan.copy(alpha = 0.18f),
            selectedLabelColor = ElectricCyan,
            containerColor = SecondarySurface,
            labelColor = TextCyberSecondary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = GlassBorderSubtle,
            selectedBorderColor = ElectricCyan
        )
    )
}

@Composable
private fun NexusChatPane(
    isPersian: Boolean,
    messages: List<NexusChatMessage>,
    isStreaming: Boolean,
    onSendMessage: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val quickPrompts = remember(isPersian) {
        if (isPersian) {
            listOf(
                "چرا اتصال VPN کند یا قطع می‌شود؟",
                "بهترین تنظیم برای همراه اول و ایرانسل چیست؟",
                "وضعیت فعلی اشتراک و نود من را بررسی کن",
                "چطور همراه‌بانک‌ها را از VPN جدا کنم؟"
            )
        } else {
            listOf(
                "Why is my VPN connection slow or dropping?",
                "Best anti-DPI transport for mobile ISP?",
                "Audit my current tunnel & subscription status",
                "How do I bypass banking apps?"
            )
        }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    CyberGlassCard(
                        accentColor = ElectricCyan.copy(alpha = 0.5f),
                        contentPadding = 16.dp
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPersian) "دستیار زنده TMS NEXUS AI" else "Live TMS NEXUS AI Assistant",
                                style = MaterialTheme.typography.titleSmall,
                                color = ElectricCyan
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isPersian)
                                "این دستیار فقط وضعیت اتصال، کانفیگ فعال، پروفایل اپراتور و اشتراک خود شما را تحلیل می‌کند و به داده‌های دیگر کاربران یا توکن‌های خام دسترسی ندارد."
                            else
                                "Context is strictly isolated to your own tunnel telemetry and subscription. Raw tokens and other users' data are never shared.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextCyberSecondary
                        )
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                val bubbleBg = when {
                    msg.isError -> StatusError.copy(alpha = 0.14f)
                    msg.isFromUser -> ElectricCyan.copy(alpha = 0.16f)
                    else -> ElevatedSurface
                }
                val bubbleBorder = when {
                    msg.isError -> StatusError.copy(alpha = 0.7f)
                    msg.isFromUser -> ElectricCyan.copy(alpha = 0.65f)
                    else -> GlassBorderSubtle
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isFromUser) Arrangement.End else Arrangement.Start
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 320.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(bubbleBg)
                            .border(BorderStroke(0.9.dp, bubbleBorder), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = when {
                                msg.isFromUser -> if (isPersian) "شما" else "You"
                                msg.isError -> if (isPersian) "خطای سرویس AI" else "AI Error"
                                else -> "TMS NEXUS AI"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                msg.isError -> StatusError
                                msg.isFromUser -> ElectricCyan
                                else -> PremiumTeal
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (msg.isStreaming && msg.text.isBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = ElectricCyan
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isPersian) "در حال دریافت پاسخ..." else "Streaming...",
                                    fontSize = 12.sp,
                                    color = TextCyberSecondary
                                )
                            }
                        } else {
                            Text(
                                text = msg.text,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = if (msg.isError) StatusError else TextCyberPrimary
                            )
                        }
                    }
                }
            }
        }

        // Quick suggestions
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickPrompts) { suggestion ->
                OutlinedButton(
                    onClick = { if (!isStreaming) onSendMessage(suggestion) },
                    enabled = !isStreaming,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.8.dp, GlassBorderCyan)
                ) {
                    Text(
                        text = suggestion,
                        fontSize = 11.sp,
                        color = ElectricCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("nexus_chat_input"),
                placeholder = {
                    Text(
                        text = if (isPersian) "سوال خود درباره اتصال یا تنظیمات را بنویسید..." else "Ask NEXUS AI about your connection...",
                        fontSize = 12.sp,
                        color = TextCyberSecondary
                    )
                },
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = GlassBorderSubtle,
                    focusedTextColor = TextCyberPrimary,
                    unfocusedTextColor = TextCyberPrimary
                ),
                shape = RoundedCornerShape(14.dp)
            )

            Button(
                onClick = {
                    val toSend = inputText.trim()
                    if (toSend.isNotBlank()) {
                        inputText = ""
                        onSendMessage(toSend)
                    }
                },
                enabled = inputText.isNotBlank() && !isStreaming,
                modifier = Modifier
                    .height(52.dp)
                    .testTag("nexus_chat_send_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = DeepGraphite
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (isPersian) "ارسال" else "Send",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun NexusDiagnosticsPane(
    isPersian: Boolean,
    report: NexusDiagnosticReport?,
    isLoading: Boolean,
    errorMessage: String?,
    onRunDiagnostics: (String) -> Unit,
    onTriggerIspAutoTune: () -> Unit,
    onTriggerFailover: () -> Unit
) {
    var symptomInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CyberGlassCard(accentColor = ElectricCyan.copy(alpha = 0.55f), contentPadding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.Build, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPersian) "عیب‌یابی ساختاریافته NEXUS AI" else "Structured AI Connection Diagnostics",
                        style = MaterialTheme.typography.titleSmall,
                        color = ElectricCyan
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isPersian)
                        "وضعیت تونل، نشت DNS، پروفایل اپراتور و کانفیگ فعال را به‌صورت JSON ساختاریافته تحلیل می‌کند."
                    else
                        "Runs structured schema analysis on your active config, leak report, and ISP telemetry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextCyberSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = symptomInput,
                    onValueChange = { symptomInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = if (isPersian) "توضیح اختیاری (مثلاً تلگرام روی ایرانسل باز نمی‌شود)" else "Optional symptom note...",
                            fontSize = 11.sp
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = GlassBorderSubtle,
                        focusedTextColor = TextCyberPrimary,
                        unfocusedTextColor = TextCyberPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { onRunDiagnostics(symptomInput) },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("nexus_run_diagnostics_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = DeepGraphite
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = DeepGraphite)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (isPersian) "در حال عیب‌یابی..." else "Diagnosing...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(imageVector = Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (isPersian) "اجرای عیب‌یابی هوشمند" else "Run Smart Diagnostics", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            item {
                CyberGlassCard(
                    modifier = Modifier.testTag("nexus_diagnostic_error_card"),
                    accentColor = StatusError,
                    contentPadding = 14.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.ErrorOutline, contentDescription = null, tint = StatusError)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage,
                            color = StatusError,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        if (report != null) {
            val sevColor = when (report.severity.uppercase()) {
                "CRITICAL", "HIGH" -> StatusError
                "MEDIUM" -> StatusConnecting
                else -> StatusConnected
            }
            item {
                CyberGlassCard(
                    modifier = Modifier.testTag("nexus_diagnostic_report_card"),
                    accentColor = sevColor,
                    contentPadding = 16.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPersian) "شدت: ${report.severity}" else "Severity: ${report.severity}",
                            color = sevColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "${report.confidencePercent}% • ${report.suggestedTransport}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = MetallicSilver
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (report.confidencePercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                        color = sevColor,
                        trackColor = ElevatedSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = report.summaryFa,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextCyberPrimary
                    )
                    if (report.rootCause.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isPersian) "علت فنی: ${report.rootCause}" else "Root Cause: ${report.rootCause}",
                            fontSize = 12.sp,
                            color = TextCyberSecondary
                        )
                    }
                    if (report.recommendedActions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        report.recommendedActions.forEachIndexed { idx, step ->
                            Text(
                                text = "${idx + 1}. $step",
                                fontSize = 12.sp,
                                color = PremiumTeal,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onTriggerIspAutoTune,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("nexus_diag_autotune_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ElectricCyan)
                        ) {
                            Icon(imageVector = Icons.Filled.Tune, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isPersian) "Auto-Tune اپراتور" else "ISP Auto-Tune", color = ElectricCyan, fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = onTriggerFailover,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("nexus_diag_failover_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PremiumTeal)
                        ) {
                            Icon(imageVector = Icons.Filled.SwapHoriz, contentDescription = null, tint = PremiumTeal, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isPersian) "سوییچ نود بعدی" else "Next Node", color = PremiumTeal, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NexusSupportAndKbPane(
    isPersian: Boolean,
    tickets: List<SupportTicket>,
    ticketBusy: Boolean,
    onSubmitTicket: (subject: String, description: String, category: SupportTicketCategory, priority: SupportTicketPriority) -> Unit,
    onInspectTicket: (SupportTicket) -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(SupportTicketCategory.CONNECTION) }
    var selectedPriority by remember { mutableStateOf(SupportTicketPriority.MEDIUM) }
    var kbSearch by remember { mutableStateOf("") }

    val filteredArticles = remember(kbSearch) {
        val q = kbSearch.trim().lowercase()
        if (q.isEmpty()) NexusKnowledgeBase.articles
        else NexusKnowledgeBase.articles.filter {
            it.titleFa.lowercase().contains(q) ||
                it.titleEn.lowercase().contains(q) ||
                it.summaryFa.lowercase().contains(q) ||
                it.summaryEn.lowercase().contains(q)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Submit Support Ticket Card
        item {
            CyberGlassCard(accentColor = ElectricCyan.copy(alpha = 0.55f), contentPadding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.SupportAgent, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPersian) "ثبت تیکت پشتیبانی با دسته‌بندی هوشمند" else "Submit AI-Triaged Support Ticket",
                        style = MaterialTheme.typography.titleSmall,
                        color = ElectricCyan
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ticket_subject_input"),
                    label = { Text(if (isPersian) "عنوان مشکل یا درخواست" else "Ticket Subject") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = GlassBorderSubtle,
                        focusedTextColor = TextCyberPrimary,
                        unfocusedTextColor = TextCyberPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ticket_description_input"),
                    label = { Text(if (isPersian) "شرح کامل (نام اپراتور، نوع خطا و...)" else "Details (ISP, error, config type)") },
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = GlassBorderSubtle,
                        focusedTextColor = TextCyberPrimary,
                        unfocusedTextColor = TextCyberPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(SupportTicketCategory.entries) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            modifier = Modifier.height(48.dp),
                            label = {
                                Text(text = if (isPersian) cat.titleFa else cat.titleEn, fontSize = 11.sp)
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onSubmitTicket(subject, description, selectedCategory, selectedPriority)
                        if (subject.isNotBlank() && description.isNotBlank()) {
                            subject = ""
                            description = ""
                        }
                    },
                    enabled = !ticketBusy && subject.isNotBlank() && description.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("ticket_submit_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PremiumTeal,
                        contentColor = DeepGraphite
                    )
                ) {
                    if (ticketBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = DeepGraphite)
                    } else {
                        Text(
                            text = if (isPersian) "ثبت و ارسال تیکت به تیم پشتیبانی" else "Submit Support Ticket",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // User's Tickets List
        item {
            Text(
                text = if (isPersian) "تیکت‌های من (${tickets.size})" else "My Support Tickets (${tickets.size})",
                style = MaterialTheme.typography.titleSmall,
                color = TextCyberPrimary
            )
        }

        if (tickets.isEmpty()) {
            item {
                CyberGlassCard(contentPadding = 14.dp) {
                    Text(
                        text = if (isPersian) "هنوز تیکتی ثبت نکرده‌اید." else "No support tickets submitted yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextCyberSecondary
                    )
                }
            }
        } else {
            items(tickets, key = { it.ticketId }) { ticket ->
                SupportTicketCard(
                    ticket = ticket,
                    isPersian = isPersian,
                    onClick = { onInspectTicket(ticket) }
                )
            }
        }

        // Knowledge Base Section
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Filled.MenuBook, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isPersian) "پایگاه دانش و راهنمای سریع" else "Verified Knowledge Base",
                    style = MaterialTheme.typography.titleSmall,
                    color = ElectricCyan
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = kbSearch,
                onValueChange = { kbSearch = it },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null, tint = ElectricCyan) },
                placeholder = { Text(if (isPersian) "جستجو در راهنما (همراه اول، بانکی، IP اختصاصی)..." else "Search guides...") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = GlassBorderSubtle,
                    focusedTextColor = TextCyberPrimary,
                    unfocusedTextColor = TextCyberPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        items(filteredArticles, key = { it.id }) { article ->
            CyberGlassCard(contentPadding = 14.dp) {
                Text(
                    text = if (isPersian) article.titleFa else article.titleEn,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextCyberPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isPersian) article.summaryFa else article.summaryEn,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextCyberSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                val steps = if (isPersian) article.stepsFa else article.stepsEn
                steps.forEachIndexed { idx, step ->
                    Text(
                        text = "${idx + 1}. $step",
                        fontSize = 11.sp,
                        color = PremiumTeal,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SupportTicketCard(
    ticket: SupportTicket,
    isPersian: Boolean,
    onClick: () -> Unit
) {
    val statusEnum = ticket.statusEnum()
    val statusColor = when (statusEnum) {
        SupportTicketStatus.ADMIN_REPLIED, SupportTicketStatus.RESOLVED -> StatusConnected
        SupportTicketStatus.AI_TRIAGED -> ElectricCyan
        SupportTicketStatus.CLOSED -> MetallicSilver
        SupportTicketStatus.OPEN -> StatusConnecting
    }
    CyberGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("support_ticket_card_${ticket.ticketId}"),
        accentColor = statusColor.copy(alpha = 0.55f),
        contentPadding = 14.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = ticket.subject,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TextCyberPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                    .border(BorderStroke(0.6.dp, statusColor), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isPersian) statusEnum.titleFa else statusEnum.titleEn,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${if (isPersian) ticket.categoryEnum().titleFa else ticket.categoryEnum().titleEn} • ${if (isPersian) ticket.priorityEnum().titleFa else ticket.priorityEnum().titleEn}",
            fontSize = 11.sp,
            color = MetallicSilver
        )
        if (ticket.aiTriageSummary.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = ticket.aiTriageSummary,
                fontSize = 11.sp,
                color = ElectricCyan,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (ticket.lastReplyText.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isPersian) "آخرین پاسخ (${ticket.lastReplyByRole}): ${ticket.lastReplyText}" else "Last reply (${ticket.lastReplyByRole}): ${ticket.lastReplyText}",
                fontSize = 11.sp,
                color = StatusConnected,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TicketConversationDialog(
    ticket: SupportTicket,
    replies: List<SupportTicketReply>,
    isAdmin: Boolean,
    isBusy: Boolean,
    isPersian: Boolean,
    onSendReply: (String) -> Unit,
    onUpdateStatus: ((SupportTicketStatus) -> Unit)?,
    onDismiss: () -> Unit
) {
    var replyText by remember { mutableStateOf("") }
    val fmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.US) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SecondarySurface,
        title = {
            Column {
                Text(
                    text = ticket.subject,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan
                )
                Text(
                    text = if (isPersian) ticket.statusEnum().titleFa else ticket.statusEnum().titleEn,
                    fontSize = 11.sp,
                    color = MetallicSilver
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = ticket.description,
                    fontSize = 12.sp,
                    color = TextCyberPrimary
                )
                if (ticket.aiTriageSummary.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ElectricCyan.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .border(BorderStroke(0.6.dp, ElectricCyan.copy(alpha = 0.45f)), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = ticket.aiTriageSummary,
                            fontSize = 11.sp,
                            color = ElectricCyan
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(replies, key = { it.replyId.ifBlank { "${it.senderUid}_${it.message.hashCode()}" } }) { rep ->
                        val fromAdmin = rep.senderRole == "ADMIN"
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (fromAdmin) StatusConnected.copy(alpha = 0.12f) else ElevatedSurface,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (fromAdmin) (if (isPersian) "پشتیبانی TMS" else "TMS Support") else (if (isPersian) "کاربر" else "User"),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (fromAdmin) StatusConnected else ElectricCyan
                                )
                                rep.createdAt?.toDate()?.let { d ->
                                    Text(text = fmt.format(d), fontSize = 9.sp, color = MetallicSilver)
                                }
                            }
                            Text(text = rep.message, fontSize = 12.sp, color = TextCyberPrimary)
                        }
                    }
                }

                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (isPersian) "پاسخ خود را بنویسید..." else "Write a reply...") },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = GlassBorderSubtle,
                        focusedTextColor = TextCyberPrimary,
                        unfocusedTextColor = TextCyberPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                if (isAdmin && onUpdateStatus != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onUpdateStatus(SupportTicketStatus.RESOLVED) },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = StatusConnected, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isPersian) "حل شد" else "Resolve", fontSize = 11.sp, color = StatusConnected)
                        }
                        OutlinedButton(
                            onClick = { onUpdateStatus(SupportTicketStatus.CLOSED) },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text(text = if (isPersian) "بستن تیکت" else "Close", fontSize = 11.sp, color = MetallicSilver)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = replyText.trim()
                    if (clean.isNotBlank()) {
                        replyText = ""
                        onSendReply(clean)
                    }
                },
                enabled = replyText.isNotBlank() && !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepGraphite)
            ) {
                Text(text = if (isPersian) "ارسال پاسخ" else "Send Reply", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = if (isPersian) "بستن" else "Close", color = TextCyberSecondary)
            }
        }
    )
}
