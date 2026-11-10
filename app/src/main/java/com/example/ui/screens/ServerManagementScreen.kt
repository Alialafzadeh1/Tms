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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AdminPermission
import com.example.data.model.AdminRole
import com.example.data.model.VpnServer
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
fun ServerManagementScreen(
    servers: List<VpnServer>,
    currentRole: AdminRole,
    isPersian: Boolean,
    isPinging: Boolean,
    onAddServer: (name: String, country: String, code: String, host: String, port: Int, protocol: String) -> Unit,
    onPingServer: (VpnServer) -> Unit,
    onDeleteServer: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    var serverName by remember { mutableStateOf("") }
    var serverCountry by remember { mutableStateOf("") }
    var serverCountryCode by remember { mutableStateOf("") }
    var serverHost by remember { mutableStateOf("") }
    var serverPort by remember { mutableStateOf("443") }
    var serverProtocol by remember { mutableStateOf("VLESS Reality") }

    val canManageServers = currentRole.hasPermission(AdminPermission.MANAGE_SERVERS)

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
                            text = if (isPersian) "مدیریت زیرساخت و سرورها" else "Server Infrastructure",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MetallicGold
                        )
                        Text(
                            text = if (isPersian) "پایش سلامت، پینگ زنده و پایداری نودها" else "Node health, actual latency and bandwidth monitoring",
                            fontSize = 12.sp,
                            color = TextGraySecondary
                        )
                    }

                    if (canManageServers) {
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MetallicGold,
                                contentColor = Color(0xFF1E1700)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isPersian) "افزودن سرور" else "Add Node", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Read-Only Warning if role lacks MANAGE_SERVERS
            if (!canManageServers) {
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
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = StatusWarningAmber, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isPersian) "دسترسی فقط خواندنی (Read-Only) - نقش: ${currentRole.titleFa}" else "Read-Only Access - Role: ${currentRole.titleEn}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusWarningAmber
                                )
                                Text(
                                    text = if (isPersian) "افزودن یا حذف سرورهای VPN برای این نقش مسدود است. فقط مشاهده و پایش امکان‌پذیر است." else "Adding or deleting VPN servers is restricted. Monitoring only.",
                                    fontSize = 10.sp,
                                    color = TextGraySecondary
                                )
                            }
                        }
                    }
                }
            }

            if (servers.isEmpty()) {
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
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = null,
                                    tint = MetallicGold,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (isPersian) "هیچ سروری ثبت نشده است" else "No VPN Servers Configured",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhitePrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isPersian)
                                    "با فشردن دکمه «افزودن سرور»، نودهای اختصاصی، سرورهای لبه و موقعیت‌های جغرافیایی را اضافه کنید."
                                else
                                    "Add dedicated edge nodes and server locations to monitor health and latency.",
                                fontSize = 12.sp,
                                color = TextGraySecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(servers.size) { index ->
                    val server = servers[index]
                    ServerCard(
                        server = server,
                        canManage = canManageServers,
                        isPersian = isPersian,
                        isPinging = isPinging,
                        onPing = { onPingServer(server) },
                        onDelete = { onDeleteServer(server.id) }
                    )
                }
            }
        }

        if (canManageServers) {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                containerColor = MetallicGold,
                contentColor = Color(0xFF1E1700)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Server")
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = CardSurfaceDark,
            title = {
                Text(
                    text = if (isPersian) "افزودن نود سرور جدید" else "Add New VPN Server Node",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MetallicGold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = serverName,
                        onValueChange = { serverName = it },
                        label = { Text("نام سرور / Server Name (حداقل ۳ کاراکتر)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = BorderGoldMuted,
                            focusedTextColor = TextWhitePrimary,
                            unfocusedTextColor = TextWhitePrimary
                        ),
                        singleLine = true
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = serverCountry,
                            onValueChange = { serverCountry = it },
                            label = { Text("کشور / Country") },
                            modifier = Modifier.weight(1.5f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MetallicGold,
                                unfocusedBorderColor = BorderGoldMuted,
                                focusedTextColor = TextWhitePrimary,
                                unfocusedTextColor = TextWhitePrimary
                            ),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = serverCountryCode,
                            onValueChange = { serverCountryCode = it },
                            label = { Text("کد (DE, FI, NL)") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MetallicGold,
                                unfocusedBorderColor = BorderGoldMuted,
                                focusedTextColor = TextWhitePrimary,
                                unfocusedTextColor = TextWhitePrimary
                            ),
                            singleLine = true
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = serverHost,
                            onValueChange = { serverHost = it },
                            label = { Text("IP معتبر یا دامنه FQDN") },
                            modifier = Modifier.weight(2f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MetallicGold,
                                unfocusedBorderColor = BorderGoldMuted,
                                focusedTextColor = TextWhitePrimary,
                                unfocusedTextColor = TextWhitePrimary
                            ),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = serverPort,
                            onValueChange = { serverPort = it },
                            label = { Text("پورت (۱-۶۵۵۳۵)") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MetallicGold,
                                unfocusedBorderColor = BorderGoldMuted,
                                focusedTextColor = TextWhitePrimary,
                                unfocusedTextColor = TextWhitePrimary
                            ),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = serverProtocol,
                        onValueChange = { serverProtocol = it },
                        label = { Text("پروتکل (VLESS Reality, Trojan, WS)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = BorderGoldMuted,
                            focusedTextColor = TextWhitePrimary,
                            unfocusedTextColor = TextWhitePrimary
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = serverPort.toIntOrNull() ?: 443
                        if (serverName.isNotBlank() && serverHost.isNotBlank()) {
                            onAddServer(
                                serverName,
                                serverCountry.ifBlank { "Germany" },
                                serverCountryCode.ifBlank { "DE" },
                                serverHost,
                                p,
                                serverProtocol
                            )
                            serverName = ""
                            serverHost = ""
                            showAddDialog = false
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
                TextButton(onClick = { showAddDialog = false }) {
                    Text(text = if (isPersian) "انصراف" else "Cancel", color = TextGraySecondary)
                }
            }
        )
    }
}

@Composable
private fun ServerCard(
    server: VpnServer,
    canManage: Boolean,
    isPersian: Boolean,
    isPinging: Boolean,
    onPing: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, BorderGoldMuted), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Country Badge
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(GoldContainer, RoundedCornerShape(8.dp))
                            .border(BorderStroke(1.dp, BorderGoldHighlight), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = server.countryCode,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MetallicGold
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = server.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhitePrimary
                        )
                        Text(
                            text = "${server.country} • ${server.protocol}",
                            fontSize = 11.sp,
                            color = TextGraySecondary
                        )
                    }
                }

                // Online/Offline Pill
                val isOnline = server.status == "online" && server.latencyMs > 0
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
                            text = if (isOnline) "آنلاین / ONLINE" else (if (server.latencyMs == -1) "آفلاین / OFFLINE" else "UNTESTED"),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOnline) EmeraldGreen else StatusOfflineRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metrics row: Host, Latency, Stability, Uptime
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0E14), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = if (isPersian) "تاخیر زنده" else "Latency", fontSize = 10.sp, color = TextGraySecondary)
                    Text(
                        text = if (server.latencyMs > 0) "${server.latencyMs} ms" else "--",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (server.latencyMs in 1..200) EmeraldGreen else MetallicGold
                    )
                }

                Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderGoldMuted))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = if (isPersian) "امتیاز ثبات" else "Stability", fontSize = 10.sp, color = TextGraySecondary)
                    Text(
                        text = "${server.stabilityScore}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (server.stabilityScore >= 80) EmeraldGreen else StatusWarningAmber
                    )
                }

                Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderGoldMuted))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = if (isPersian) "آپ‌تایم" else "Uptime", fontSize = 10.sp, color = TextGraySecondary)
                    Text(
                        text = "${server.uptimePercentage}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhitePrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bandwidth usage meter
            val progress = ((server.bandwidthUsageGb / server.bandwidthLimitGb).toFloat()).coerceIn(0f, 1f)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isPersian) "ترافیک مصرفی:" else "Bandwidth Usage:",
                        fontSize = 11.sp,
                        color = TextGraySecondary
                    )
                    Text(
                        text = "${String.format("%.1f", server.bandwidthUsageGb)} GB / ${server.bandwidthLimitGb.toInt()} GB",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextGoldMuted
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = MetallicGold,
                    trackColor = Color(0xFF1E212B)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Actions: Real Ping & Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen),
                    border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isPinging
                ) {
                    Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (isPersian) "پایش زنده و تست سوکت" else "Test Socket Ping", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = onDelete,
                    enabled = canManage,
                    modifier = Modifier
                        .size(40.dp)
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
