package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AdminPermission
import com.example.data.model.AdminRole
import com.example.data.model.VpnConfig
import com.example.ui.theme.BorderGoldHighlight
import com.example.ui.theme.BorderGoldMuted
import com.example.ui.theme.CardSurfaceDark
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.StatusWarningAmber
import com.example.ui.theme.TextGoldMuted
import com.example.ui.theme.TextGraySecondary
import com.example.ui.theme.TextWhitePrimary
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun ConfigManagementScreen(
    configs: List<VpnConfig>,
    currentRole: AdminRole,
    selectedConfigId: String? = null,
    isPersian: Boolean,
    isPinging: Boolean,
    onImportConfig: (String) -> Unit,
    onImportBatch: (String) -> Unit,
    onSelectForVpn: (VpnConfig) -> Unit,
    onPingConfig: (VpnConfig) -> Unit,
    onDeleteConfig: (String) -> Unit,
    onToggleFavorite: (VpnConfig) -> Unit,
    onQuickAudit: (VpnConfig) -> Unit
) {
    val context = LocalContext.current
    var showImportDialog by remember { mutableStateOf(false) }
    var rawInputText by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }

    val canManageConfigs = currentRole.hasPermission(AdminPermission.MANAGE_CONFIGS)

    // File picker launcher for importing VPN config files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val text = BufferedReader(InputStreamReader(stream)).readText()
                    rawInputText = text
                    showImportDialog = true
                }
            } catch (e: Exception) {
                // handle or ignore
            }
        }
    }

    // Filter configurations
    val filteredConfigs = remember(configs, selectedFilter) {
        when (selectedFilter) {
            "FAVORITES" -> configs.filter { it.isFavorite }
            "VLESS" -> configs.filter { it.protocol.equals("vless", ignoreCase = true) }
            "VMESS" -> configs.filter { it.protocol.equals("vmess", ignoreCase = true) }
            "TROJAN" -> configs.filter { it.protocol.equals("trojan", ignoreCase = true) }
            "SHADOWSOCKS" -> configs.filter { it.protocol.equals("shadowsocks", ignoreCase = true) }
            else -> configs
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isPersian) "مخزن کانفیگ‌های VPN" else "VPN Configurations",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MetallicGold
                        )
                        Text(
                            text = if (isPersian) "پشتیبانی از VLESS Reality, VMess, Trojan, Shadowsocks" else "Enterprise secure profile repository",
                            fontSize = 11.sp,
                            color = TextGraySecondary
                        )
                    }

                    if (canManageConfigs) {
                        Button(
                            onClick = { showImportDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MetallicGold,
                                contentColor = Color(0xFF1E1700)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isPersian) "واردسازی" else "Import", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Quick Import Shortcuts Bar (Clipboard & File)
            if (canManageConfigs) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val text = clip.getItemAt(0).text?.toString() ?: ""
                                    if (text.isNotBlank()) {
                                        rawInputText = text
                                        showImportDialog = true
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MetallicGold),
                            border = BorderStroke(1.dp, BorderGoldHighlight),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isPersian) "واردسازی از کلیپ‌بورد" else "From Clipboard", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("text/*") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen),
                            border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isPersian) "واردسازی از فایل (.txt)" else "From File", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Filter Chips Group
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filterOptions = listOf(
                        "ALL" to (if (isPersian) "همه (${configs.size})" else "All (${configs.size})"),
                        "FAVORITES" to (if (isPersian) "نشان‌شده" else "Starred"),
                        "VLESS" to "VLESS",
                        "VMESS" to "VMess",
                        "TROJAN" to "Trojan",
                        "SHADOWSOCKS" to "Shadowsocks"
                    )

                    filterOptions.forEach { (key, label) ->
                        val selected = selectedFilter == key
                        FilterChip(
                            selected = selected,
                            onClick = { selectedFilter = key },
                            label = { Text(text = label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldContainer,
                                selectedLabelColor = MetallicGold,
                                containerColor = CardSurfaceDark,
                                labelColor = TextGraySecondary
                            ),
                            border = BorderStroke(0.8.dp, if (selected) MetallicGold else BorderGoldMuted)
                        )
                    }
                }
            }

            // Zero State
            if (filteredConfigs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(1.dp, BorderGoldMuted), RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(GoldContainer, CircleShape)
                                    .border(BorderStroke(1.dp, MetallicGold), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = MetallicGold,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (isPersian) "هیچ کانفیگی یافت نشد" else "No Configurations Found",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhitePrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isPersian)
                                    "لینک‌های VLESS, VMess, Trojan یا Shadowsocks را کپی کرده یا فایل متنی حاوی چند کانفیگ را بارگذاری نمایید."
                                else
                                    "Import single or batch VPN configurations from clipboard, text, or file.",
                                fontSize = 12.sp,
                                color = TextGraySecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredConfigs.size) { index ->
                    val config = filteredConfigs[index]
                    val isSelectedForTunnel = config.id == selectedConfigId || (selectedConfigId == null && index == 0)

                    ConfigCard(
                        config = config,
                        canManage = canManageConfigs,
                        isSelected = isSelectedForTunnel,
                        isPersian = isPersian,
                        isPinging = isPinging,
                        onSelectForVpn = { onSelectForVpn(config) },
                        onPing = { onPingConfig(config) },
                        onDelete = { onDeleteConfig(config.id) },
                        onToggleFavorite = { onToggleFavorite(config) },
                        onAudit = { onQuickAudit(config) },
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("VPN Config", config.rawConfig))
                        }
                    )
                }
            }
        }

        // Floating Action Button
        if (canManageConfigs) {
            FloatingActionButton(
                onClick = { showImportDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                containerColor = MetallicGold,
                contentColor = Color(0xFF1E1700)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Config")
            }
        }
    }

    // Import Modal Dialog (Single or Batch Multi-Line)
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            containerColor = CardSurfaceDark,
            title = {
                Text(
                    text = if (isPersian) "واردسازی کانفیگ VPN (تکی یا گروهی)" else "Import VPN Configurations",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MetallicGold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isPersian)
                            "یک یا چند لینک (VLESS, VMess, Trojan, Shadowsocks) را در کادر زیر قرار دهید:"
                        else
                            "Paste one or multiple VPN configuration URIs (separated by line breaks):",
                        fontSize = 12.sp,
                        color = TextWhitePrimary
                    )

                    OutlinedTextField(
                        value = rawInputText,
                        onValueChange = { rawInputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        placeholder = {
                            Text(
                                text = "vless://uuid@server:443?security=reality&type=tcp#Node-1\nvmess://...\ntrojan://...",
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0D0F15), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPersian) "اعتبارسنجی خودکار، شناسایی تکراری‌ها و ذخیره‌سازی ایزوله اختصاصی شما" else "Auto-validation, duplicate detection, and private user storage",
                            fontSize = 10.sp,
                            color = TextGraySecondary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (rawInputText.isNotBlank()) {
                            if (rawInputText.contains("\n")) {
                                onImportBatch(rawInputText)
                            } else {
                                onImportConfig(rawInputText)
                            }
                            rawInputText = ""
                            showImportDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = Color(0xFF1E1700)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = if (isPersian) "تجزیه و ثبت" else "Parse & Import", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(text = if (isPersian) "انصراف" else "Cancel", color = TextGraySecondary)
                }
            }
        )
    }
}

@Composable
private fun ConfigCard(
    config: VpnConfig,
    canManage: Boolean,
    isSelected: Boolean,
    isPersian: Boolean,
    isPinging: Boolean,
    onSelectForVpn: () -> Unit,
    onPing: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAudit: () -> Unit,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                BorderStroke(
                    if (isSelected) 1.8.dp else (if (config.isFavorite) 1.2.dp else 0.8.dp),
                    if (isSelected) EmeraldGreen else (if (config.isFavorite) MetallicGold else BorderGoldMuted)
                ),
                RoundedCornerShape(14.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top row: Protocol tag, Name, Active Selection Indicator, Favorite
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(GoldContainer, RoundedCornerShape(6.dp))
                            .border(BorderStroke(1.dp, BorderGoldHighlight), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = config.protocol.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MetallicGold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = config.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhitePrimary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .background(EmeraldContainer, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isPersian) "نود فعال تونل" else "ACTIVE TUNNEL",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (config.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (config.isFavorite) MetallicGold else TextGraySecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Server & Port detail
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0E14), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${config.server}:${config.port}",
                        fontSize = 12.sp,
                        color = TextWhitePrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Transport: ${config.transport.uppercase()} • Security: ${config.security.uppercase()} ${if (config.sni.isNotBlank()) "• SNI: ${config.sni}" else ""}",
                        fontSize = 10.sp,
                        color = TextGraySecondary
                    )
                }

                // Latency Badge
                val isOnline = config.status == "online" && config.pingMs > 0
                Box(
                    modifier = Modifier
                        .background(
                            if (isOnline) EmeraldContainer else Color(0xFF281114),
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            BorderStroke(0.8.dp, if (isOnline) EmeraldGreen else StatusOfflineRed),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).background(if (isOnline) EmeraldGreen else StatusOfflineRed, CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (config.pingMs > 0) "${config.pingMs} ms" else (if (config.status == "offline") "آفلاین / Offline" else "تست نشده / Untested"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOnline) EmeraldGreen else StatusOfflineRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons: Select For Tunnel, Real Ping, AI Audit, Copy, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Select for Tunnel Button
                Button(
                    onClick = onSelectForVpn,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) EmeraldGreen else Color(0xFF191B24),
                        contentColor = if (isSelected) Color(0xFF021B10) else MetallicGold
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (isSelected) (if (isPersian) "انتخاب شده" else "Selected") else (if (isPersian) "انتخاب تونل" else "Set Active"),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Real Ping Button
                OutlinedButton(
                    onClick = onPing,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen),
                    border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    enabled = !isPinging
                ) {
                    Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(text = if (isPersian) "پینگ سوکت" else "Ping", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                // AI Audit
                OutlinedButton(
                    onClick = onAudit,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MetallicGold),
                    border = BorderStroke(1.dp, MetallicGold.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(text = if (isPersian) "بررسی AI" else "Audit", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.weight(1f))

                // Copy URI
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(34.dp).background(Color(0xFF141720), RoundedCornerShape(8.dp))
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy URI", tint = TextGoldMuted, modifier = Modifier.size(16.dp))
                }

                // Delete
                IconButton(
                    onClick = onDelete,
                    enabled = canManage,
                    modifier = Modifier.size(34.dp).background(if (canManage) Color(0xFF221114) else Color(0xFF15161A), RoundedCornerShape(8.dp))
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = if (canManage) StatusOfflineRed else Color.Gray, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
