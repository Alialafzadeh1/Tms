package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
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

@Composable
fun ConfigManagementScreen(
    configs: List<VpnConfig>,
    currentRole: AdminRole,
    isPersian: Boolean,
    isPinging: Boolean,
    onImportConfig: (String) -> Unit,
    onPingConfig: (VpnConfig) -> Unit,
    onDeleteConfig: (String) -> Unit,
    onToggleFavorite: (VpnConfig) -> Unit,
    onQuickAudit: (VpnConfig) -> Unit
) {
    val context = LocalContext.current
    var showImportDialog by remember { mutableStateOf(false) }
    var rawInputText by remember { mutableStateOf("") }

    val canManageConfigs = currentRole.hasPermission(AdminPermission.MANAGE_CONFIGS)

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
                            text = if (isPersian) "مدیریت کانفیگ‌های VPN" else "VPN Configurations",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MetallicGold
                        )
                        Text(
                            text = if (isPersian) "پشتیبانی از VLESS, VMess, Trojan, Shadowsocks" else "Enterprise secure profile repository",
                            fontSize = 12.sp,
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
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPersian) "افزودن کانفیگ" else "Import",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Read-Only RBAC Notice if role cannot manage configs
            if (!canManageConfigs) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(1.dp, StatusWarningAmber.copy(alpha = 0.6f)), RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1D170A))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = StatusWarningAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isPersian) "دسترسی فقط خواندنی (Read-Only) - نقش: ${currentRole.titleFa}" else "Read-Only Access - Role: ${currentRole.titleEn}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusWarningAmber
                                )
                                Text(
                                    text = if (isPersian) "افزودن یا حذف کانفیگ برای این نقش مسدود است. برای دسترسی کامل به Super Admin یا Server Manager تغییر نقش دهید." else "Modifying configurations is restricted. Switch role to manage.",
                                    fontSize = 10.sp,
                                    color = TextGraySecondary
                                )
                            }
                        }
                    }
                }
            }

            // Zero State
            if (configs.isEmpty()) {
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
                                text = if (isPersian) "هیچ کانفیگی وارد نشده است" else "No VPN Configurations",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhitePrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isPersian)
                                    "لینک‌های VLESS, VMess, Trojan یا Shadowsocks را کپی کرده و با دکمه «افزودن کانفیگ» ثبت کنید."
                                else
                                    "Import VLESS, VMess, Trojan, or Shadowsocks links using the button above.",
                                fontSize = 12.sp,
                                color = TextGraySecondary,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(configs.size) { index ->
                    val config = configs[index]
                    ConfigCard(
                        config = config,
                        canManage = canManageConfigs,
                        isPersian = isPersian,
                        isPinging = isPinging,
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

        // Floating Action Button (Only if allowed by RBAC)
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

    // Import Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            containerColor = CardSurfaceDark,
            title = {
                Text(
                    text = if (isPersian) "واردسازی کانفیگ VPN" else "Import VPN Configuration",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MetallicGold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isPersian)
                            "آدرس کانفیگ (VLESS, VMess, Trojan یا Shadowsocks) را در کادر زیر قرار دهید:"
                        else
                            "Paste VLESS, VMess, Trojan, or Shadowsocks URI:",
                        fontSize = 12.sp,
                        color = TextWhitePrimary
                    )

                    OutlinedTextField(
                        value = rawInputText,
                        onValueChange = { rawInputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        placeholder = {
                            Text(
                                text = "vless://uuid@server:443?security=reality&type=tcp#Name",
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
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPersian) "اعتبارسنجی دقیق RFC، پورت ۱-۶۵۵۳۵، فرمت UUID و رمزنگاری داده" else "RFC Validation: Port 1-65535, UUID format, and secure encryption",
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
                            onImportConfig(rawInputText)
                            rawInputText = ""
                            showImportDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MetallicGold,
                        contentColor = Color(0xFF1E1700)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = if (isPersian) "اعتبارسنجی و ثبت" else "Validate & Save", fontWeight = FontWeight.Bold)
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
    isPersian: Boolean,
    isPinging: Boolean,
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
                    if (config.isFavorite) 1.5.dp else 0.8.dp,
                    if (config.isFavorite) MetallicGold else BorderGoldMuted
                ),
                RoundedCornerShape(14.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top row: Protocol tag, Name, Favorite
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Protocol badge
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

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (config.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (config.isFavorite) MetallicGold else TextGraySecondary
                    )
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
                            BorderStroke(
                                0.8.dp,
                                if (isOnline) EmeraldGreen else StatusOfflineRed
                            ),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(if (isOnline) EmeraldGreen else StatusOfflineRed, CircleShape)
                        )
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

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Real Ping Button
                OutlinedButton(
                    onClick = onPing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = EmeraldGreen
                    ),
                    border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    enabled = !isPinging
                ) {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPersian) "پینگ واقعی TCP" else "Real Ping",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Fast AI Audit
                OutlinedButton(
                    onClick = onAudit,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MetallicGold
                    ),
                    border = BorderStroke(1.dp, MetallicGold.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPersian) "بررسی AI" else "Fast AI Audit",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Copy URI
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF141720), RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy URI",
                        tint = TextGoldMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Delete (only active if allowed by RBAC)
                IconButton(
                    onClick = onDelete,
                    enabled = canManage,
                    modifier = Modifier
                        .size(36.dp)
                        .background(if (canManage) Color(0xFF221114) else Color(0xFF15161A), RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = if (canManage) StatusOfflineRed else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
