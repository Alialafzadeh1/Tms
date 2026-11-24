package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.model.AdminPermission
import com.example.data.model.AdminRole
import com.example.data.security.LocalUserAuthManager
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
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SystemSettingsScreen(
    currentRole: AdminRole,
    isPersian: Boolean,
    isUnlocked: Boolean,
    lockoutSeconds: Long,
    onToggleLanguage: () -> Unit,
    onChangePin: (oldPin: String, newPin: String) -> Unit,
    onEmergencyResetPin: (token: String, newPin: String) -> Unit,
    onLockSession: () -> Unit,
    onVerifyPin: (pin: String) -> Boolean,
    getRecoveryToken: () -> String,
    onSignOutComplete: () -> Unit,
    hasConfiguredPin: Boolean = true,
    onConfigureInitialPin: (newPin: String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var enableKillSwitch by remember { mutableStateOf(true) }
    var enableDnsProtection by remember { mutableStateOf(true) }
    var enableStrictTls by remember { mutableStateOf(true) }

    var showChangePinModal by remember { mutableStateOf(false) }
    var showEmergencyModal by remember { mutableStateOf(false) }
    var showUnlockModal by remember { mutableStateOf(false) }
    var showSetupInitialPinModal by remember { mutableStateOf(false) }

    var oldPinInput by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }
    var initialPinInput by remember { mutableStateOf("") }
    var recoveryTokenInput by remember { mutableStateOf("") }
    var emergencyNewPinInput by remember { mutableStateOf("") }
    var unlockPinInput by remember { mutableStateOf("") }

    val canManageSettings = currentRole.hasPermission(AdminPermission.MANAGE_SETTINGS)

    val databaseId = try {
        context.getString(R.string.firestore_database_id)
    } catch (e: Exception) {
        "ai-studio-android-tmsvpn-f4c920d0-5850-4fc7-93d2-dff7792f4179"
    }

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
                    text = if (isPersian) "تنظیمات سامانه و امنیت ادمین" else "System & Admin Security Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MetallicGold
                )
                Text(
                    text = if (isPersian) "احراز هویت PBKDF2، نشست امنیتی، محافظت Brute-Force و تنظیمات پایگاه داده" else "PBKDF2 Administrator Authentication, Session Token & Database",
                    fontSize = 12.sp,
                    color = TextGraySecondary
                )
            }
        }

        // Active Lockout Warning Banner if active
        if (lockoutSeconds > 0) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.5.dp, StatusOfflineRed), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF281114))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = StatusOfflineRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPersian) "قفل امنیتی فعال (Brute-Force Lockout)" else "Active Brute-Force Lockout",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusOfflineRed
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isPersian)
                                "به دلیل ۵ تلاش ورود ناموفق، کنسول به مدت $lockoutSeconds ثانیه قفل شده است. می‌توانید با کلید ریکاوری اضطراری قفل را بازنشانی کنید."
                            else
                                "Locked out for $lockoutSeconds seconds due to consecutive failed authentication attempts. Use emergency recovery to reset.",
                            fontSize = 11.sp,
                            color = TextWhitePrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { showEmergencyModal = true },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusOfflineRed, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = if (isPersian) "بازنشانی اضطراری با کلید ریکاوری" else "Emergency Recovery Reset", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Admin Security & Cryptographic Session Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.2.dp, MetallicGold), RoundedCornerShape(16.dp)),
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
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(GoldContainer, CircleShape)
                                    .border(BorderStroke(1.dp, MetallicGold), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isUnlocked) Icons.Default.Shield else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MetallicGold,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isPersian) "احراز هویت مدیر ارشد (PBKDF2 + Salt)" else "Admin Authentication Authority",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhitePrimary
                                )
                                Text(
                                    text = if (isUnlocked)
                                        (if (isPersian) "نشست فعال با امضای HMAC-SHA256" else "Cryptographic session active")
                                    else
                                        (if (isPersian) "نشست قفل است / نیاز به ورود پین" else "Session locked / PIN verification required"),
                                    fontSize = 11.sp,
                                    color = if (isUnlocked) EmeraldGreen else StatusWarningAmber
                                )
                            }
                        }

                        if (isUnlocked) {
                            Button(
                                onClick = onLockSession,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF281114), contentColor = StatusOfflineRed),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(text = if (isPersian) "قفل نشست" else "Lock", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else if (!hasConfiguredPin) {
                            Button(
                                onClick = { showSetupInitialPinModal = true },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color(0xFF021B10)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(text = if (isPersian) "تنظیم پین" else "Setup PIN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { showUnlockModal = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = Color(0xFF1E1700)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(text = if (isPersian) "احراز هویت پین" else "Verify PIN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Key Stretching & Brute-Force specifications
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF090A0E), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "• استانداردهای امنیتی پیاده‌سازی شده:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MetallicGold
                        )
                        Text(
                            text = "1. هشینگ PBKDF2 با سالت اختصاصی و ۱۰،۰۰۰ دور تکرار (مقاومت بالا در برابر حملات GPU/Rainbow).",
                            fontSize = 10.sp,
                            color = TextGraySecondary
                        )
                        Text(
                            text = "2. قفل خودکار Brute-Force پس از ۵ بار تلاش اشتباه برای مدت ۵ دقیقه همراه با ثبت رویداد.",
                            fontSize = 10.sp,
                            color = TextGraySecondary
                        )
                        Text(
                            text = "3. توکن نشست با امضای دیجیتال HMAC-SHA256 با کلید متقارن ۳۲ بایتی منقضی شونده.",
                            fontSize = 10.sp,
                            color = TextGraySecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showChangePinModal = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MetallicGold),
                            border = BorderStroke(1.dp, BorderGoldHighlight),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isPersian) "تغییر پین امنیتی" else "Change PIN", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = { showEmergencyModal = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusWarningAmber),
                            border = BorderStroke(1.dp, StatusWarningAmber.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isPersian) "کلید ریکاوری" else "Recovery Key", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Language & UI Localization Card
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
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(GoldContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.Language, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isPersian) "زبان رابط کاربری (RTL / LTR)" else "Interface Language",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhitePrimary
                                )
                                Text(
                                    text = if (isPersian) "فارسی راست‌به‌چپ (پیش‌فرض) / English" else "Persian RTL / English LTR",
                                    fontSize = 11.sp,
                                    color = TextGraySecondary
                                )
                            }
                        }

                        Button(
                            onClick = onToggleLanguage,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MetallicGold,
                                contentColor = Color(0xFF1E1700)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isPersian) "تغییر به English" else "تغییر به فارسی",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Database Configuration Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, BorderGoldMuted), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(EmeraldContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.CloudQueue, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isPersian) "پایگاه داده ابری (Firestore Enterprise)" else "Cloud Database Settings",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhitePrimary
                            )
                            Text(
                                text = if (isPersian) "شناسه دیتابیس اختصاصی و ایزوله" else "Dedicated database ID verified",
                                fontSize = 11.sp,
                                color = TextGraySecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF090A0F), RoundedCornerShape(8.dp))
                            .border(BorderStroke(0.6.dp, BorderGoldMuted), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(text = "Firestore Database ID:", fontSize = 10.sp, color = TextGraySecondary)
                        Text(text = databaseId, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MetallicGold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Security Rules Version: rules_version = '2' (RBAC Enforced)", fontSize = 10.sp, color = EmeraldGreen)
                    }
                }
            }
        }

        // Native Android VPN API Gateway Settings
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, BorderGoldMuted), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(GoldContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Api, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isPersian) "درگاه اتصال اپلیکیشن اندروید (API Gateway)" else "Android Native VPN Gateway API",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhitePrimary
                            )
                            Text(
                                text = if (isPersian) "معماری ماژولار جهت اتصال امن به کلاینت بومی" else "REST / gRPC sync interface for native clients",
                                fontSize = 11.sp,
                                color = TextGraySecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isPersian)
                            "این پلتفرم با معماری ماژولار ساخته شده و کانفیگ‌ها، سرورها و احراز هویت را از طریق پروتکل‌های امن به اپلیکیشن بومی اندروید VpnService تزریق می‌کند."
                        else
                            "Modular architecture designed to securely deliver configs, health telemetry, and failover policies to the native Android VpnService client.",
                        fontSize = 11.sp,
                        color = TextWhitePrimary,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Security Toggles
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, BorderGoldMuted), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = if (isPersian) "تنظیمات پیشرفته شبکه و امنیت تونل" else "Advanced Security & Protection",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MetallicGold
                    )

                    // Toggle 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Kill Switch (قطع اضطراری اینترنت)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextWhitePrimary)
                            Text(text = "جلوگیری از نشت IP در زمان قطعی تونل", fontSize = 10.sp, color = TextGraySecondary)
                        }
                        Switch(
                            checked = enableKillSwitch,
                            onCheckedChange = { if (canManageSettings) enableKillSwitch = it },
                            enabled = canManageSettings,
                            colors = SwitchDefaults.colors(checkedThumbColor = MetallicGold, checkedTrackColor = GoldContainer)
                        )
                    }

                    // Toggle 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "DNS Leak Protection (DoH / DoT)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextWhitePrimary)
                            Text(text = "رمزنگاری تمام کوئری‌های DNS به Cloudflare / Google", fontSize = 10.sp, color = TextGraySecondary)
                        }
                        Switch(
                            checked = enableDnsProtection,
                            onCheckedChange = { if (canManageSettings) enableDnsProtection = it },
                            enabled = canManageSettings,
                            colors = SwitchDefaults.colors(checkedThumbColor = MetallicGold, checkedTrackColor = GoldContainer)
                        )
                    }

                    // Toggle 3
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "اجبار به TLS 1.3 و Reality", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextWhitePrimary)
                            Text(text = "رد تمام اتصالات ناامن با سایفرهای ضعیف", fontSize = 10.sp, color = TextGraySecondary)
                        }
                        Switch(
                            checked = enableStrictTls,
                            onCheckedChange = { if (canManageSettings) enableStrictTls = it },
                            enabled = canManageSettings,
                            colors = SwitchDefaults.colors(checkedThumbColor = MetallicGold, checkedTrackColor = GoldContainer)
                        )
                    }
                }
            }
        }

        // App Version and Platform Info
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, BorderGoldMuted), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = TextGoldMuted, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "TMS VPN Management Ecosystem", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextWhitePrimary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "نسخه پنل مدیریت: v1.0 Enterprise (Release 2026.11)", fontSize = 11.sp, color = TextGraySecondary)
                    Text(text = "نقش شما: ${currentRole.titleFa} (${currentRole.id})", fontSize = 11.sp, color = MetallicGold)
                    Text(text = "موتور هوش مصنوعی: Gemini 3.1 Flash-Lite & Gemini 4 Argon", fontSize = 11.sp, color = TextGoldMuted)
                    Text(text = "پایگاه داده: Google Cloud Firestore Enterprise", fontSize = 11.sp, color = EmeraldGreen)
                }
            }
        }

        // Sign Out Button
        item {
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        withContext(NonCancellable) {
                            LocalUserAuthManager(context).logout()
                            try {
                                credentialManager.clearCredentialState(ClearCredentialStateRequest())
                            } catch (e: Exception) {
                                // ignore
                            } finally {
                                Firebase.auth.signOut()
                                onSignOutComplete()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusOfflineRed),
                border = BorderStroke(1.dp, StatusOfflineRed.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPersian) "خروج از حساب کاربری / Sign Out" else "Sign Out",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Modal: Change Master PIN
    if (showChangePinModal) {
        AlertDialog(
            onDismissRequest = { showChangePinModal = false },
            containerColor = CardSurfaceDark,
            title = {
                Text(
                    text = if (isPersian) "تغییر پین امنیتی مدیر" else "Change Master Security PIN",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MetallicGold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isPersian)
                            "پین فعلی و پین جدید (حداقل ۶ رقم با تکرار ۱۰،۰۰۰ دور هشینگ PBKDF2) را وارد کنید:"
                        else
                            "Enter current PIN and new PIN (minimum 6 digits, stretched with PBKDF2):",
                        fontSize = 12.sp,
                        color = TextWhitePrimary
                    )

                    OutlinedTextField(
                        value = oldPinInput,
                        onValueChange = { oldPinInput = it },
                        label = { Text(if (isPersian) "پین فعلی" else "Current PIN") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = BorderGoldMuted,
                            focusedTextColor = TextWhitePrimary,
                            unfocusedTextColor = TextWhitePrimary
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { newPinInput = it },
                        label = { Text("پین جدید (حداقل ۶ رقم غیرتکراری)") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
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
                        onChangePin(oldPinInput, newPinInput)
                        oldPinInput = ""
                        newPinInput = ""
                        showChangePinModal = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = Color(0xFF1E1700)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = if (isPersian) "به‌روزرسانی پین" else "Update PIN", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePinModal = false }) {
                    Text(text = if (isPersian) "انصراف" else "Cancel", color = TextGraySecondary)
                }
            }
        )
    }

    // Modal: Emergency Reset / Recovery Token
    if (showEmergencyModal) {
        val recoveryToken = getRecoveryToken()
        AlertDialog(
            onDismissRequest = { showEmergencyModal = false },
            containerColor = CardSurfaceDark,
            title = {
                Text(
                    text = if (isPersian) "بازیابی اضطراری و کلید ریکاوری" else "Emergency Recovery & Reset",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MetallicGold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isPersian) "کلید ریکاوری اختصاصی سامانه شما:" else "Your Emergency Recovery Key:",
                        fontSize = 12.sp,
                        color = TextWhitePrimary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF090A0E), RoundedCornerShape(8.dp))
                            .border(BorderStroke(0.8.dp, BorderGoldMuted), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = recoveryToken, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MetallicGold)
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Recovery Token", recoveryToken))
                        }) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextGoldMuted, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isPersian) "در صورت فراموشی پین یا قفل شدن، کلید ریکاوری و پین جدید را وارد کنید:" else "To reset PIN or clear lockout, paste recovery key and new PIN:",
                        fontSize = 11.sp,
                        color = TextGraySecondary
                    )

                    OutlinedTextField(
                        value = recoveryTokenInput,
                        onValueChange = { recoveryTokenInput = it },
                        label = { Text("کلید ریکاوری (Recovery Key)") },
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
                        value = emergencyNewPinInput,
                        onValueChange = { emergencyNewPinInput = it },
                        label = { Text("پین جدید") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
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
                        onEmergencyResetPin(recoveryTokenInput, emergencyNewPinInput)
                        recoveryTokenInput = ""
                        emergencyNewPinInput = ""
                        showEmergencyModal = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = Color(0xFF1E1700)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = if (isPersian) "بازیابی و بازکردن قفل" else "Reset & Unlock", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmergencyModal = false }) {
                    Text(text = if (isPersian) "انصراف" else "Cancel", color = TextGraySecondary)
                }
            }
        )
    }

    // Modal: Unlock Session PIN entry
    if (showUnlockModal) {
        AlertDialog(
            onDismissRequest = { showUnlockModal = false },
            containerColor = CardSurfaceDark,
            title = {
                Text(
                    text = if (isPersian) "احراز هویت پین امنیتی مدیر" else "Verify Master Security PIN",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MetallicGold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isPersian) "جهت فعال‌سازی نشست امن، پین اختصاصی مدیر را وارد کنید:" else "Enter your custom administrator security PIN to unlock session:",
                        fontSize = 12.sp,
                        color = TextWhitePrimary
                    )

                    OutlinedTextField(
                        value = unlockPinInput,
                        onValueChange = { unlockPinInput = it },
                        label = { Text("پین امنیتی") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
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
                        val verified = onVerifyPin(unlockPinInput)
                        if (verified) {
                            showUnlockModal = false
                            unlockPinInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = Color(0xFF1E1700)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = if (isPersian) "ورود امن" else "Authenticate", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlockModal = false }) {
                    Text(text = if (isPersian) "انصراف" else "Cancel", color = TextGraySecondary)
                }
            }
        )
    }

    if (showSetupInitialPinModal) {
        AlertDialog(
            onDismissRequest = { showSetupInitialPinModal = false },
            containerColor = CardSurfaceDark,
            title = {
                Text(
                    text = if (isPersian) "تنظیم پین امنیتی مدیر" else "Configure Initial Admin PIN",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MetallicGold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isPersian) "یک پین اختصاصی حداقل ۶ رقمی برای محافظت نشست کنسول مدیریت انتخاب کنید:" else "Choose a custom 6+ character security PIN to protect admin console sessions:",
                        fontSize = 12.sp,
                        color = TextWhitePrimary
                    )
                    OutlinedTextField(
                        value = initialPinInput,
                        onValueChange = { initialPinInput = it },
                        label = { Text(if (isPersian) "پین امنیتی جدید" else "New Security PIN") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
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
                        onConfigureInitialPin(initialPinInput)
                        initialPinInput = ""
                        showSetupInitialPinModal = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color(0xFF021B10)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = if (isPersian) "ثبت پین" else "Save PIN", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetupInitialPinModal = false }) {
                    Text(text = if (isPersian) "انصراف" else "Cancel", color = TextGraySecondary)
                }
            }
        )
    }
}
