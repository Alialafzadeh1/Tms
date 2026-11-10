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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.model.AuthAuditLog
import com.example.data.model.UserProfile
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
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UserManagementScreen(
    profile: UserProfile?,
    currentRole: AdminRole,
    auditLogs: List<AuthAuditLog>,
    isPersian: Boolean,
    onSwitchRole: (AdminRole) -> Unit,
    onUpdateQuota: (deviceCount: Int, maxDevices: Int, dataLimitBytes: Double) -> Unit
) {
    val currentUser = Firebase.auth.currentUser
    var showRoleModal by remember { mutableStateOf(false) }
    var showQuotaModal by remember { mutableStateOf(false) }

    var quotaMaxDevices by remember { mutableIntStateOf(profile?.maxDevices ?: 5) }
    var quotaLimitGb by remember { mutableStateOf("100") }

    val canManageUsers = currentRole.hasPermission(AdminPermission.MANAGE_USERS)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        item {
            Column {
                Text(
                    text = if (isPersian) "مدیریت کاربران، نقش‌ها و امنیت" else "Users, RBAC & Audit Logs",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MetallicGold
                )
                Text(
                    text = if (isPersian) "کنترل دسترسی نقش‌محور (RBAC)، سهمیه مصرفی و ثبت لاگ‌های امنیتی" else "Role-Based Access Control, Quota & Tamper-Resistant Security Logs",
                    fontSize = 12.sp,
                    color = TextGraySecondary
                )
            }
        }

        // Current User & Active Role Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.2.dp, MetallicGold), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(GoldContainer, CircleShape)
                                    .border(BorderStroke(1.5.dp, MetallicGold), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MetallicGold,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = profile?.displayName ?: currentUser?.displayName ?: "مدیر سامانه / Administrator",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhitePrimary
                                )
                                Text(
                                    text = profile?.email ?: currentUser?.email ?: "admin@tmsvpn.local",
                                    fontSize = 12.sp,
                                    color = TextGraySecondary
                                )
                            }
                        }

                        // Switch Role Button
                        OutlinedButton(
                            onClick = { showRoleModal = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MetallicGold),
                            border = BorderStroke(1.dp, BorderGoldHighlight),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isPersian) "تغییر نقش RBAC" else "Switch Role", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Active Role Banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F121B), RoundedCornerShape(10.dp))
                            .border(BorderStroke(0.8.dp, BorderGoldMuted), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = MetallicGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isPersian) currentRole.titleFa else currentRole.titleEn,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MetallicGold
                                )
                                Text(
                                    text = if (isPersian) "سطح دسترسی: ${currentRole.permissions.size} مجوز فعال" else "Active permissions: ${currentRole.permissions.size}",
                                    fontSize = 10.sp,
                                    color = TextGraySecondary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .background(EmeraldContainer, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(text = currentRole.id, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Status and Subscription Pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF090A0F), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = if (isPersian) "وضعیت حساب" else "Account Status", fontSize = 10.sp, color = TextGraySecondary)
                            Text(
                                text = if (isPersian) "سازمانی فعال" else "Active Enterprise",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen
                            )
                        }

                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderGoldMuted))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = if (isPersian) "سقف دستگاه‌ها" else "Device Limit", fontSize = 10.sp, color = TextGraySecondary)
                            Text(
                                text = "${profile?.deviceCount ?: 3} / ${profile?.maxDevices ?: 5}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MetallicGold
                            )
                        }

                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderGoldMuted))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = if (isPersian) "سطح امنیت" else "Security Tier", fontSize = 10.sp, color = TextGraySecondary)
                            Text(
                                text = "Zero-Trust PBKDF2",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhitePrimary
                            )
                        }
                    }
                }
            }
        }

        // RBAC Permissions Matrix Card
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
                            text = if (isPersian) "ماتریس مجوزهای دسترسی نقش (${currentRole.name})" else "Role Permissions Matrix (${currentRole.name})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MetallicGold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    val allPerms = AdminPermission.entries
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        allPerms.forEach { perm ->
                            val granted = currentRole.hasPermission(perm)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0C0E14), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isPersian) perm.titleFa else perm.titleEn,
                                    fontSize = 11.sp,
                                    color = if (granted) TextWhitePrimary else TextGraySecondary
                                )
                                Box(
                                    modifier = Modifier
                                        .background(if (granted) EmeraldContainer else Color(0xFF221114), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (granted) (if (isPersian) "مجاز" else "GRANTED") else (if (isPersian) "غیرمجاز" else "DENIED"),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (granted) EmeraldGreen else StatusOfflineRed
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quota & Bandwidth with Edit Button
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
                            text = if (isPersian) "سهمیه ترافیک و دستگاه‌های کاربری" else "Bandwidth Allocation & Device Limits",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MetallicGold
                        )
                        if (canManageUsers) {
                            IconButton(onClick = { showQuotaModal = true }, modifier = Modifier.size(28.dp)) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Quotas", tint = MetallicGold, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "14.2 GB / ${(profile?.dataLimitBytes ?: (100.0 * 1024 * 1024 * 1024)) / (1024 * 1024 * 1024)} GB مصرف شده",
                        fontSize = 12.sp,
                        color = TextWhitePrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { 0.142f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = EmeraldGreen,
                        trackColor = Color(0xFF1B1E28)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (canManageUsers)
                            (if (isPersian) "شما دسترسی ویرایش سهمیه‌ها را دارید (Super Admin / User Admin)" else "Quota modification enabled for current role")
                        else
                            (if (isPersian) "حالت فقط خواندنی: برای تغییر سهمیه نیاز به نقش Super Admin یا User Admin است" else "Read-only: Super Admin or User Admin required to edit"),
                        fontSize = 10.sp,
                        color = if (canManageUsers) EmeraldGreen else StatusWarningAmber
                    )
                }
            }
        }

        // Security Audit Logs Section (Audit & Compliance)
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
                            Icon(imageVector = Icons.Default.History, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPersian) "لاگ‌های امنیتی و رخدادها (Audit Logs)" else "Security & Audit Event Trail",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MetallicGold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(GoldContainer, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${auditLogs.size} ${if (isPersian) "ثبت شده" else "events"}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MetallicGold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isPersian) "لاگ‌های غیرقابل دستکاری (Append-Only) جهت ردگیری تمام تلاش‌های ورود، تغییر نقش و ورودی‌ها" else "Tamper-resistant append-only security logs for authentication and validation events",
                        fontSize = 10.sp,
                        color = TextGraySecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (auditLogs.isEmpty()) {
                        Text(
                            text = if (isPersian) "هیچ رخدادی ثبت نشده است" else "No security events logged yet.",
                            fontSize = 11.sp,
                            color = TextGraySecondary
                        )
                    } else {
                        val dateFormat = SimpleDateFormat("HH:mm:ss - yyyy/MM/dd", Locale.getDefault())
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            auditLogs.take(8).forEach { log ->
                                val isSuccess = log.status == "SUCCESS"
                                val isLocked = log.status == "LOCKED_OUT"
                                val statusColor = when {
                                    isSuccess -> EmeraldGreen
                                    isLocked -> StatusWarningAmber
                                    else -> StatusOfflineRed
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF090A0E), RoundedCornerShape(8.dp))
                                        .border(BorderStroke(0.6.dp, statusColor.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(6.dp).background(statusColor, CircleShape))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(text = log.action, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextWhitePrimary)
                                        }

                                        Text(text = log.status, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = statusColor)
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = log.details, fontSize = 10.sp, color = TextGraySecondary)

                                    if (log.createdAt != null) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${dateFormat.format(Date(log.createdAt.toDate().time))} • نقش: ${log.role}",
                                            fontSize = 9.sp,
                                            color = TextGoldMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Role Switcher Modal
    if (showRoleModal) {
        AlertDialog(
            onDismissRequest = { showRoleModal = false },
            containerColor = CardSurfaceDark,
            title = {
                Text(
                    text = if (isPersian) "تغییر نقش فعال مدیر (RBAC)" else "Switch Active Administrator Role",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MetallicGold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isPersian)
                            "نقش‌های مختلف را برای آزمایش و ارزیابی سطوح دسترسی ماژول‌های مختلف انتخاب کنید:"
                        else
                            "Select a role to test granular access enforcement across dashboard modules:",
                        fontSize = 12.sp,
                        color = TextWhitePrimary
                    )

                    AdminRole.entries.forEach { role ->
                        val isSelected = currentRole == role
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    BorderStroke(if (isSelected) 1.5.dp else 0.8.dp, if (isSelected) MetallicGold else BorderGoldMuted),
                                    RoundedCornerShape(10.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF1E1700) else Color(0xFF0C0E14)
                            ),
                            onClick = {
                                onSwitchRole(role)
                                showRoleModal = false
                            }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isPersian) role.titleFa else role.titleEn,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MetallicGold else TextWhitePrimary
                                    )
                                    if (isSelected) {
                                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${role.permissions.size} مجوز فعال / ${role.id}",
                                    fontSize = 10.sp,
                                    color = TextGraySecondary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRoleModal = false }) {
                    Text(text = if (isPersian) "بستن" else "Close", color = TextGraySecondary)
                }
            }
        )
    }

    // Quota Edit Modal
    if (showQuotaModal) {
        AlertDialog(
            onDismissRequest = { showQuotaModal = false },
            containerColor = CardSurfaceDark,
            title = {
                Text(
                    text = if (isPersian) "تنظیم سهمیه حساب کاربری" else "Modify User Quotas",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MetallicGold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = quotaMaxDevices.toString(),
                        onValueChange = { quotaMaxDevices = it.toIntOrNull() ?: 5 },
                        label = { Text("سقف دستگاه‌های مجاز (Max Devices)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = BorderGoldMuted,
                            focusedTextColor = TextWhitePrimary,
                            unfocusedTextColor = TextWhitePrimary
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = quotaLimitGb,
                        onValueChange = { quotaLimitGb = it },
                        label = { Text("سقف حجم داده (گیگابایت)") },
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
                        val gb = quotaLimitGb.toDoubleOrNull() ?: 100.0
                        onUpdateQuota(profile?.deviceCount ?: 1, quotaMaxDevices, gb * 1024 * 1024 * 1024)
                        showQuotaModal = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = Color(0xFF1E1700)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = if (isPersian) "ذخیره سهمیه" else "Save Quota", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuotaModal = false }) {
                    Text(text = if (isPersian) "انصراف" else "Cancel", color = TextGraySecondary)
                }
            }
        )
    }
}
